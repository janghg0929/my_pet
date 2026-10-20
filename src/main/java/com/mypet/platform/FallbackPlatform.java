package com.mypet.platform;

import java.util.List;
import java.util.Set;

/** 창 감지를 지원하지 않는 환경: 바닥에서만 걷고 외형 자동 전환은 하지 않는다. */
final class FallbackPlatform implements PlatformSupport {
	@Override
	public List<PlatformRect> windowPlatformRects(int workLeft, int workTop, int workRight, int workBottom, int sampleStep) {
		return List.of();
	}

	@Override
	public ForegroundInfo foreground() {
		return null;
	}

	@Override
	public Set<String> visibleProcessNames() {
		return Set.of();
	}
}
