package com.mypet.platform;

import com.mypet.platform.PlatformSpans.WindowEntry;
import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.platform.mac.CoreFoundation.CFArrayRef;
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef;
import com.sun.jna.platform.mac.CoreFoundation.CFNumberRef;
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * macOS 구현 (JNA 로 CoreGraphics / Objective-C 런타임 호출).
 * - 창 목록/좌표: CGWindowListCopyWindowInfo (별도 권한 불필요)
 * - 포그라운드 앱: NSWorkspace.frontmostApplication
 * - 창 제목: '화면 기록' 권한이 있을 때만 제공된다 (없으면 빈 문자열 → 유튜브 제목 감지만 동작 안 함)
 */
final class MacPlatform implements PlatformSupport {
	private static final int K_CG_WINDOW_LIST_OPTION_ON_SCREEN_ONLY = 1;
	private static final int K_CG_WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS = 1 << 4;
	private static final int K_CG_NULL_WINDOW_ID = 0;

	interface CoreGraphics extends Library {
		CoreGraphics INSTANCE = Native.load("CoreGraphics", CoreGraphics.class);

		Pointer CGWindowListCopyWindowInfo(int option, int relativeToWindow);
	}

	interface ObjC extends Library {
		ObjC INSTANCE = Native.load("objc", ObjC.class);

		Pointer objc_getClass(String name);

		Pointer sel_registerName(String name);

		Pointer objc_autoreleasePoolPush();

		void objc_autoreleasePoolPop(Pointer pool);
	}

	/** CGWindowList 의 창 하나. 좌표는 포인트(= Java 논리 좌표). */
	private record CgWindow(long pid, String ownerName, String windowName, int layer, int left, int top, int right, int bottom) {
	}

	private final CFStringRef keyOwnerPid = CFStringRef.createCFString("kCGWindowOwnerPID");
	private final CFStringRef keyOwnerName = CFStringRef.createCFString("kCGWindowOwnerName");
	private final CFStringRef keyWindowName = CFStringRef.createCFString("kCGWindowName");
	private final CFStringRef keyLayer = CFStringRef.createCFString("kCGWindowLayer");
	private final CFStringRef keyBounds = CFStringRef.createCFString("kCGWindowBounds");
	private final CFStringRef keyAlpha = CFStringRef.createCFString("kCGWindowAlpha");
	private final CFStringRef keyX = CFStringRef.createCFString("X");
	private final CFStringRef keyY = CFStringRef.createCFString("Y");
	private final CFStringRef keyWidth = CFStringRef.createCFString("Width");
	private final CFStringRef keyHeight = CFStringRef.createCFString("Height");

	private final ObjC objc = ObjC.INSTANCE;
	private final Function msgSend = NativeLibrary.getInstance("objc").getFunction("objc_msgSend");
	private final long ownPid = ProcessHandle.current().pid();

	MacPlatform() {
		// 초기화 단계에서 네이티브 라이브러리 로드 실패를 미리 드러낸다
		Pointer list = CoreGraphics.INSTANCE.CGWindowListCopyWindowInfo(K_CG_WINDOW_LIST_OPTION_ON_SCREEN_ONLY, K_CG_NULL_WINDOW_ID);
		if (list != null) {
			new CFArrayRef(list).release();
		}
		objc.sel_registerName("sharedWorkspace");
	}

	/** 화면에 보이는 창을 앞→뒤 순서로 수집. */
	private List<CgWindow> listOnScreenWindows() {
		List<CgWindow> windows = new ArrayList<>();
		Pointer listPointer = CoreGraphics.INSTANCE.CGWindowListCopyWindowInfo(
			K_CG_WINDOW_LIST_OPTION_ON_SCREEN_ONLY | K_CG_WINDOW_LIST_EXCLUDE_DESKTOP_ELEMENTS,
			K_CG_NULL_WINDOW_ID
		);
		if (listPointer == null) {
			return windows;
		}
		CFArrayRef list = new CFArrayRef(listPointer);
		try {
			int count = list.getCount();
			for (int i = 0; i < count; i++) {
				Pointer item = list.getValueAtIndex(i);
				if (item == null) {
					continue;
				}
				CFDictionaryRef dict = new CFDictionaryRef(item);
				double alpha = numberValue(dict, keyAlpha, 1.0);
				if (alpha <= 0.0) {
					continue;
				}
				Pointer boundsPointer = dict.getValue(keyBounds);
				if (boundsPointer == null) {
					continue;
				}
				CFDictionaryRef bounds = new CFDictionaryRef(boundsPointer);
				double x = numberValue(bounds, keyX, 0);
				double y = numberValue(bounds, keyY, 0);
				double width = numberValue(bounds, keyWidth, 0);
				double height = numberValue(bounds, keyHeight, 0);
				windows.add(new CgWindow(
					(long) numberValue(dict, keyOwnerPid, 0),
					stringValue(dict, keyOwnerName),
					stringValue(dict, keyWindowName),
					(int) numberValue(dict, keyLayer, 0),
					(int) Math.round(x),
					(int) Math.round(y),
					(int) Math.round(x + width),
					(int) Math.round(y + height)
				));
			}
		} finally {
			list.release();
		}
		return windows;
	}

	private static double numberValue(CFDictionaryRef dict, CFStringRef key, double fallback) {
		Pointer value = dict.getValue(key);
		if (value == null) {
			return fallback;
		}
		return new CFNumberRef(value).doubleValue();
	}

	private static String stringValue(CFDictionaryRef dict, CFStringRef key) {
		Pointer value = dict.getValue(key);
		if (value == null) {
			return "";
		}
		String text = new CFStringRef(value).stringValue();
		return text == null ? "" : text;
	}

	@Override
	public List<PlatformRect> windowPlatformRects(int workLeft, int workTop, int workRight, int workBottom, int sampleStep) {
		try {
			List<WindowEntry> entries = new ArrayList<>();
			for (CgWindow window : listOnScreenWindows()) {
				if (window.pid() == ownPid || window.layer() < 0) {
					continue;
				}
				// 일반 창(layer 0)만 발판이 되고, 그 위 레이어(메뉴바/독 등)는 가리는 용도로만 쓴다
				entries.add(new WindowEntry(window.left(), window.top(), window.right(), window.bottom(), window.layer() == 0));
			}
			return PlatformSpans.compute(entries, workLeft, workTop, workRight, workBottom, sampleStep);
		} catch (Throwable t) {
			return List.of();
		}
	}

	@Override
	public ForegroundInfo foreground() {
		Pointer pool = null;
		try {
			pool = objc.objc_autoreleasePoolPush();
			Pointer workspace = send(objc.objc_getClass("NSWorkspace"), "sharedWorkspace");
			Pointer app = send(workspace, "frontmostApplication");
			if (app == null) {
				return null;
			}
			long pid = msgSend.invokeInt(new Object[] {app, objc.sel_registerName("processIdentifier")});
			String name = nsStringToJava(send(app, "localizedName"));
			if (name == null || name.isEmpty()) {
				return null;
			}
			String title = "";
			for (CgWindow window : listOnScreenWindows()) {
				if (window.pid() == pid && window.layer() == 0) {
					title = window.windowName();
					break;
				}
			}
			return new ForegroundInfo(pid, name.toLowerCase(Locale.ROOT), title);
		} catch (Throwable t) {
			return null;
		} finally {
			if (pool != null) {
				objc.objc_autoreleasePoolPop(pool);
			}
		}
	}

	@Override
	public Set<String> visibleProcessNames() {
		Set<String> names = new HashSet<>();
		try {
			for (CgWindow window : listOnScreenWindows()) {
				if (window.layer() == 0 && window.pid() != ownPid && !window.ownerName().isEmpty()) {
					names.add(window.ownerName().toLowerCase(Locale.ROOT));
				}
			}
		} catch (Throwable ignored) {
			// 빈 목록 반환
		}
		return names;
	}

	private Pointer send(Pointer receiver, String selector) {
		if (receiver == null) {
			return null;
		}
		return msgSend.invokePointer(new Object[] {receiver, objc.sel_registerName(selector)});
	}

	private String nsStringToJava(Pointer nsString) {
		Pointer utf8 = send(nsString, "UTF8String");
		return utf8 == null ? null : utf8.getString(0, "UTF-8");
	}
}
