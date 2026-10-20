package com.mypet.platform;

import com.mypet.Constants;

import java.util.List;
import java.util.Set;

/**
 * 플랫폼(윈도우/맥/기타)별 창 감지 기능 (my_pet_platform.py / my_pet_win32.py 포팅).
 * 모든 좌표는 Java(AWT) 논리 좌표계 기준이다.
 */
public interface PlatformSupport {

	/** 걷기 발판으로 쓸 수 있는 다른 창의 상단 구간. */
	record PlatformRect(int left, int right, int top) {
	}

	/** 현재 포그라운드 앱 정보. 알 수 없으면 null 로 표현한다. */
	record ForegroundInfo(long pid, String processName, String windowTitle) {
	}

	/** 앱 시작 시 한 번 호출 (윈도우: 타이머 해상도 향상 등). */
	default void onStartup() {
	}

	/** 걷기 발판으로 쓸 다른 창 영역을 수집. */
	List<PlatformRect> windowPlatformRects(int workLeft, int workTop, int workRight, int workBottom, int sampleStep);

	/** 현재 포그라운드 앱. 알 수 없으면 null. */
	ForegroundInfo foreground();

	/** 화면에 보이는(최소화되지 않은) 창을 가진 프로세스 이름 목록 (소문자). */
	Set<String> visibleProcessNames();

	static PlatformSupport create() {
		try {
			if (Constants.IS_WINDOWS) {
				return new WindowsPlatform();
			}
			if (Constants.IS_MACOS) {
				return new MacPlatform();
			}
		} catch (Throwable t) {
			System.err.println("[MY_PET] 네이티브 창 감지 초기화 실패, 기본 모드로 동작: " + t);
		}
		return new FallbackPlatform();
	}
}
