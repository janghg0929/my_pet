package com.mypet.platform;

import com.mypet.Constants;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 실제 OS 의 창 감지 API 가 동작하는지 확인 (윈도우/맥에서만, 결과를 출력). */
class NativePlatformSmokeTest {
	@Test
	void nativeCallsWork() {
		assumeTrue((Constants.IS_WINDOWS || Constants.IS_MACOS) && !GraphicsEnvironment.isHeadless());
		PlatformSupport platform = PlatformSupport.create();
		Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		List<PlatformSupport.PlatformRect> rects = platform.windowPlatformRects(
			work.x, work.y, work.x + work.width, work.y + work.height, 24);
		Set<String> visible = platform.visibleProcessNames();
		PlatformSupport.ForegroundInfo foreground = platform.foreground();
		System.out.println("platform=" + platform.getClass().getSimpleName());
		System.out.println("workArea=" + work);
		System.out.println("platformRects=" + rects);
		System.out.println("visibleProcesses=" + visible);
		System.out.println("foreground=" + foreground);
		assertNotNull(rects);
		assertNotNull(visible);
	}
}
