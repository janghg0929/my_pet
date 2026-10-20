package com.mypet.platform;

import com.mypet.platform.PlatformSupport.PlatformRect;

import java.util.ArrayList;
import java.util.List;

/**
 * 창 목록(z-order, 위에 있는 창이 먼저)에서 "실제로 보이는 상단 가장자리" 구간을 계산.
 * my_pet_win32.get_window_platform_rects 의 샘플링 로직과 동일하다.
 */
final class PlatformSpans {
	private PlatformSpans() {
	}

	/** 한 창의 정보. occluder 는 다른 창을 가리는지, candidate 는 발판 후보인지. */
	record WindowEntry(int left, int top, int right, int bottom, boolean candidate) {
		boolean contains(int x, int y) {
			return left <= x && x < right && top <= y && y < bottom;
		}
	}

	static List<PlatformRect> compute(
		List<WindowEntry> zOrdered,
		int workLeft,
		int workTop,
		int workRight,
		int workBottom,
		int sampleStep
	) {
		List<PlatformRect> platformRects = new ArrayList<>();
		for (int index = 0; index < zOrdered.size(); index++) {
			WindowEntry window = zOrdered.get(index);
			if (!window.candidate()) {
				continue;
			}
			int left = window.left();
			int top = window.top();
			int right = window.right();
			int bottom = window.bottom();
			if (right - left < 60 || bottom - top < 36) {
				continue;
			}
			if (right <= workLeft || left >= workRight || bottom <= workTop || top >= workBottom) {
				continue;
			}
			collectVisibleSpans(zOrdered, index, sampleStep, platformRects);
		}
		return platformRects;
	}

	/** 상단 라인에서 실제로 보이는 구간을 수집. */
	private static void collectVisibleSpans(List<WindowEntry> zOrdered, int index, int sampleStep, List<PlatformRect> out) {
		WindowEntry window = zOrdered.get(index);
		int y = Math.min(window.top() + 6, window.bottom() - 1);
		int startX = window.left() + 8;
		int endX = window.right() - 8;
		if (endX <= startX) {
			return;
		}
		int step = Math.max(8, sampleStep);
		boolean inVisible = false;
		int spanStart = startX;
		for (int x = startX; x <= endX; x += step) {
			boolean visible = topWindowIndexAt(zOrdered, x, y) == index;
			if (visible && !inVisible) {
				spanStart = x;
				inVisible = true;
			} else if (!visible && inVisible) {
				out.add(new PlatformRect(spanStart, x, window.top()));
				inVisible = false;
			}
		}
		if (inVisible) {
			out.add(new PlatformRect(spanStart, endX, window.top()));
		}
	}

	/** 좌표에 실제로 보이는 최상단 창의 인덱스. 없으면 -1. */
	private static int topWindowIndexAt(List<WindowEntry> zOrdered, int x, int y) {
		for (int i = 0; i < zOrdered.size(); i++) {
			if (zOrdered.get(i).contains(x, y)) {
				return i;
			}
		}
		return -1;
	}
}
