package com.mypet.platform;

import com.mypet.platform.PlatformSpans.WindowEntry;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 윈도우 구현 (my_pet_win32.py 포팅, JNA 사용). */
final class WindowsPlatform implements PlatformSupport {
	private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
	private static final int SM_CXSCREEN = 0;
	private static final long PROCESS_NAME_CACHE_MS = 10_000;

	interface User32Ex extends StdCallLibrary {
		User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean IsIconic(HWND hWnd);

		int GetSystemMetrics(int index);
	}

	interface WinMM extends Library {
		WinMM INSTANCE = Native.load("winmm", WinMM.class);

		int timeBeginPeriod(int periodMs);
	}

	private record CachedName(String name, long expiresAt) {
	}

	/** EnumWindows 로 모은 보이는 최상위 창 하나. 좌표는 물리 픽셀. */
	private record RawWindow(HWND hwnd, int pid, int left, int top, int right, int bottom, boolean iconic) {
	}

	private final User32 user32 = User32.INSTANCE;
	private final Kernel32 kernel32 = Kernel32.INSTANCE;
	private final int ownPid = (int) ProcessHandle.current().pid();
	private final Map<Integer, CachedName> processNameCache = new HashMap<>();

	WindowsPlatform() {
		// 초기화 단계에서 네이티브 라이브러리 로드 실패를 미리 드러낸다
		user32.GetForegroundWindow();
	}

	@Override
	public void onStartup() {
		try {
			// 스윙 타이머가 64Hz 로 고르게 돌도록 시스템 타이머 해상도를 1ms 로 올린다
			WinMM.INSTANCE.timeBeginPeriod(1);
		} catch (Throwable ignored) {
			// 실패해도 동작에는 문제 없음 (약간 덜 부드러울 뿐)
		}
	}

	/** 보이는 최상위 창을 z-order(위→아래) 순서로 수집. */
	private List<RawWindow> enumerateVisibleWindows() {
		List<RawWindow> windows = new ArrayList<>();
		IntByReference pid = new IntByReference();
		RECT rect = new RECT();
		user32.EnumWindows((hwnd, data) -> {
			if (hwnd == null || !user32.IsWindowVisible(hwnd)) {
				return true;
			}
			pid.setValue(0);
			user32.GetWindowThreadProcessId(hwnd, pid);
			if (!user32.GetWindowRect(hwnd, rect)) {
				return true;
			}
			boolean iconic = User32Ex.INSTANCE.IsIconic(hwnd);
			windows.add(new RawWindow(hwnd, pid.getValue(), rect.left, rect.top, rect.right, rect.bottom, iconic));
			return true;
		}, null);
		return windows;
	}

	/** 물리 픽셀 → Java 논리 좌표 배율 (DPI 배율이 125%면 1.25). */
	private double physicalToLogicalScale() {
		try {
			Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration().getBounds();
			int physicalWidth = User32Ex.INSTANCE.GetSystemMetrics(SM_CXSCREEN);
			if (bounds.width > 0 && physicalWidth > 0) {
				return (double) physicalWidth / bounds.width;
			}
		} catch (Throwable ignored) {
			// 기본값 사용
		}
		return 1.0;
	}

	@Override
	public List<PlatformRect> windowPlatformRects(int workLeft, int workTop, int workRight, int workBottom, int sampleStep) {
		try {
			double scale = physicalToLogicalScale();
			List<WindowEntry> entries = new ArrayList<>();
			for (RawWindow window : enumerateVisibleWindows()) {
				if (window.pid() == ownPid) {
					continue;
				}
				entries.add(new WindowEntry(
					(int) Math.round(window.left() / scale),
					(int) Math.round(window.top() / scale),
					(int) Math.round(window.right() / scale),
					(int) Math.round(window.bottom() / scale),
					true
				));
			}
			return PlatformSpans.compute(entries, workLeft, workTop, workRight, workBottom, sampleStep);
		} catch (Throwable t) {
			return List.of();
		}
	}

	@Override
	public ForegroundInfo foreground() {
		try {
			HWND hwnd = user32.GetForegroundWindow();
			if (hwnd == null) {
				return null;
			}
			IntByReference pid = new IntByReference();
			user32.GetWindowThreadProcessId(hwnd, pid);
			if (pid.getValue() == 0) {
				return null;
			}
			String processName = processName(pid.getValue());
			if (processName == null) {
				return null;
			}
			char[] buffer = new char[512];
			int length = user32.GetWindowText(hwnd, buffer, buffer.length);
			String title = length > 0 ? new String(buffer, 0, length) : "";
			return new ForegroundInfo(pid.getValue(), processName, title);
		} catch (Throwable t) {
			return null;
		}
	}

	@Override
	public Set<String> visibleProcessNames() {
		Set<String> names = new HashSet<>();
		try {
			Set<Integer> pids = new HashSet<>();
			for (RawWindow window : enumerateVisibleWindows()) {
				if (!window.iconic() && window.pid() != 0) {
					pids.add(window.pid());
				}
			}
			for (int pid : pids) {
				String name = processName(pid);
				if (name != null) {
					names.add(name);
				}
			}
		} catch (Throwable ignored) {
			// 빈 목록 반환
		}
		return names;
	}

	/** 프로세스 실행 파일 이름(소문자, 예: chrome.exe). 짧게 캐시한다. */
	private String processName(int pid) {
		long now = System.currentTimeMillis();
		CachedName cached = processNameCache.get(pid);
		if (cached != null && cached.expiresAt() > now) {
			return cached.name();
		}
		if (processNameCache.size() > 512) {
			processNameCache.clear();
		}
		String name = queryProcessName(pid);
		processNameCache.put(pid, new CachedName(name, now + PROCESS_NAME_CACHE_MS));
		return name;
	}

	private String queryProcessName(int pid) {
		HANDLE process = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid);
		if (process == null) {
			return null;
		}
		try {
			char[] buffer = new char[512];
			IntByReference size = new IntByReference(buffer.length);
			if (kernel32.QueryFullProcessImageName(process, 0, buffer, size)) {
				String path = new String(buffer, 0, size.getValue());
				int slash = path.lastIndexOf('\\');
				return path.substring(slash + 1).toLowerCase(Locale.ROOT);
			}
			return null;
		} finally {
			kernel32.CloseHandle(process);
		}
	}
}
