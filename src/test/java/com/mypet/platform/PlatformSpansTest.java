package com.mypet.platform;

import com.mypet.platform.PlatformSpans.WindowEntry;
import com.mypet.platform.PlatformSupport.PlatformRect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformSpansTest {
	@Test
	void fullyVisibleWindowGivesOneSpan() {
		List<PlatformRect> rects = PlatformSpans.compute(
			List.of(new WindowEntry(100, 200, 500, 600, true)), 0, 0, 1920, 1080, 24);
		assertEquals(List.of(new PlatformRect(108, 492, 200)), rects);
	}

	@Test
	void coveredPartIsExcluded() {
		// 위에 있는 창이 아래 창 상단의 왼쪽 절반을 가린다
		List<PlatformRect> rects = PlatformSpans.compute(List.of(
			new WindowEntry(0, 100, 300, 400, true),
			new WindowEntry(100, 200, 500, 600, true)
		), 0, 0, 1920, 1080, 24);
		PlatformRect lower = rects.stream().filter(r -> r.top() == 200).findFirst().orElseThrow();
		assertTrue(lower.left() >= 300, "가려진 구간은 발판이 아니어야 함: " + lower);
		assertEquals(492, lower.right());
	}

	@Test
	void smallAndNonCandidateWindowsIgnored() {
		List<PlatformRect> rects = PlatformSpans.compute(List.of(
			new WindowEntry(0, 0, 50, 30, true),
			new WindowEntry(100, 200, 500, 600, false)
		), 0, 0, 1920, 1080, 24);
		assertTrue(rects.isEmpty());
	}
}
