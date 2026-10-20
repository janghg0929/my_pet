package com.mypet.appearance.rendering;

import com.mypet.Constants;

import java.awt.Font;
import java.awt.Graphics2D;

/**
 * 외형 렌더링 (appearance_rendering/*.py 포팅).
 * 파이썬 버전은 캔버스 도형의 좌표를 갱신했고, 여기서는 같은 좌표 계산으로 매 프레임 직접 그린다.
 */
public final class AppearanceRendering {
	private AppearanceRendering() {
	}

	private static final int[][] PIXEL_MAP = {
		{1, 0}, {2, 0}, {4, 0}, {5, 0},
		{0, 1}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {5, 1}, {6, 1},
		{0, 2}, {1, 2}, {2, 2}, {3, 2}, {4, 2}, {5, 2}, {6, 2},
		{1, 3}, {2, 3}, {3, 3}, {4, 3}, {5, 3},
		{2, 4}, {3, 4}, {4, 4},
		{3, 5},
	};
	private static final int[][] SHINE_MAP = {
		{4, 1},
		{5, 1},
		{5, 2},
	};

	/** 브라우저 돋보기 (browser_monocle_rendering.py). 손잡이를 그린 뒤 렌즈를 위에 그린다. */
	public static void renderMonocle(
		Graphics2D g,
		double rightArmRight,
		double armTopCurrent,
		double limbOffsetX,
		double limbOffsetY,
		double limbSwayX,
		double limbSize,
		double monocleRadius,
		int ringStroke,
		int handleStroke
	) {
		double armTop = armTopCurrent + limbOffsetY;
		double handX = rightArmRight + limbOffsetX - limbSwayX;
		double handY = armTop + (limbSize * 0.55);
		double handleLength = monocleRadius * Constants.MAGNIFIER_HANDLE_LENGTH;
		double handleAngle = Math.toRadians(Constants.MAGNIFIER_HANDLE_ANGLE_DEG);
		double handleDx = Math.cos(handleAngle) * handleLength;
		double handleDy = Math.sin(handleAngle) * handleLength;
		double centerX = handX + handleDx;
		double centerY = handY + handleDy;

		Draw.line(g, handX, handY, centerX - (handleDx * 0.2), centerY - (handleDy * 0.2),
			Constants.BROWSER_MONOCLE_CHAIN_COLOR, handleStroke, false);
		Draw.oval(g, centerX - monocleRadius, centerY - monocleRadius, centerX + monocleRadius, centerY + monocleRadius,
			Constants.BROWSER_MAGNIFIER_FILL_COLOR, Constants.BROWSER_MONOCLE_COLOR, ringStroke);
	}

	/** 브라우저 IE 아이콘 (browser_ie_icon_rendering.py). */
	public static void renderIeIcon(
		Graphics2D g,
		double leftArmLeft,
		double armTopCurrent,
		double limbOffsetX,
		double limbOffsetY,
		double limbSwayX,
		double limbSize,
		double ieIconRadius,
		int orbitStroke,
		int barStroke,
		Font letterFont
	) {
		double armTop = armTopCurrent + limbOffsetY;
		double handX = leftArmLeft + limbOffsetX + limbSwayX;
		double handY = armTop + (limbSize * 0.45);
		double centerX = handX - (ieIconRadius * 0.62);
		double centerY = handY - (ieIconRadius * 0.32);
		double orbitPadX = ieIconRadius * 0.34;
		double orbitPadY = ieIconRadius * 0.18;
		double orbitLeft = centerX - ieIconRadius - orbitPadX;
		double orbitTop = centerY - ieIconRadius + orbitPadY;
		double orbitRight = centerX + ieIconRadius + orbitPadX;
		double orbitBottom = centerY + ieIconRadius - orbitPadY;
		double barLeft = centerX - (ieIconRadius * 0.72);
		double barY = centerY + (ieIconRadius * 0.18);
		double barRight = centerX + (ieIconRadius * 0.62);

		Draw.arc(g, orbitLeft, orbitTop, orbitRight, orbitBottom, 202, 316, Constants.BROWSER_IE_ICON_ORBIT_COLOR, orbitStroke);
		Draw.textCentered(g, centerX, centerY - (ieIconRadius * 0.04), "e", letterFont, Constants.BROWSER_IE_ICON_COLOR);
		Draw.line(g, barLeft, barY, barRight, barY, Constants.BROWSER_IE_ICON_COLOR, barStroke, true);
	}

	private static double pixelSize(double eyeShapeScale) {
		return Math.max(2.0, Math.rint(eyeShapeScale * 3) * 3);
	}

	/** 스팀 픽셀 하트 본체 (pixel_heart_rendering.py). */
	public static void renderPixelHeart(Graphics2D g, double charOriginX, double charOriginY, double charFaceWidth, double eyeShapeScale) {
		drawPixels(g, PIXEL_MAP, Constants.PIXEL_HEART_COLOR, charOriginX, charOriginY, charFaceWidth, eyeShapeScale);
	}

	/** 스팀 픽셀 하트 반짝임 (본체보다 위 레이어). */
	public static void renderPixelHeartShine(Graphics2D g, double charOriginX, double charOriginY, double charFaceWidth, double eyeShapeScale) {
		drawPixels(g, SHINE_MAP, Constants.PIXEL_HEART_SHINE_COLOR, charOriginX, charOriginY, charFaceWidth, eyeShapeScale);
	}

	private static void drawPixels(Graphics2D g, int[][] map, String color, double charOriginX, double charOriginY, double charFaceWidth, double eyeShapeScale) {
		double pixelSize = pixelSize(eyeShapeScale);
		double heartWidth = pixelSize * 7;
		double heartHeight = pixelSize * 6;
		double baseX = charOriginX + ((charFaceWidth - heartWidth) / 2);
		double baseY = charOriginY - heartHeight - (pixelSize * 1.4);
		for (int[] pixel : map) {
			double left = baseX + (pixel[0] * pixelSize);
			double top = baseY + (pixel[1] * pixelSize);
			Draw.rect(g, left, top, left + pixelSize, top + pixelSize, color, null, 0);
		}
	}

	/** 음악/미디어 헤드셋 (headset_rendering.py). */
	public static void renderHeadset(
		Graphics2D g,
		double charOriginX,
		double charOriginY,
		double charFaceWidth,
		double charFaceHeight,
		int arcStroke
	) {
		double faceLeft = charOriginX;
		double faceTop = charOriginY;
		double faceRight = faceLeft + charFaceWidth;
		double faceBottom = faceTop + charFaceHeight;
		double headsetHeight = Math.max(6.0, (faceBottom - faceTop) * 0.28) + 4.0;
		double headsetWidth = ((faceRight - faceLeft) * 0.88) + 8.0;
		double offsetY = -(headsetHeight * 0.23);
		double arcLeft = faceLeft + ((faceRight - faceLeft) - headsetWidth) / 2;
		double arcRight = arcLeft + headsetWidth;
		double arcTop = faceTop - (headsetHeight * 0.65) + offsetY;
		double arcBottom = faceTop + headsetHeight + offsetY;
		double padWidth = Math.max(6.0, headsetWidth * 0.12);
		double padHeight = Math.max(10.0, headsetHeight * 0.55);
		double padTop = faceTop + (headsetHeight * 0.1) + offsetY;
		double padBottom = padTop + padHeight;

		Draw.arc(g, arcLeft, arcTop, arcRight, arcBottom, 0, 180, Constants.HEADSET_COLOR, arcStroke);
		Draw.rect(g, arcLeft - (padWidth * 0.2), padTop, arcLeft + padWidth, padBottom, Constants.HEADSET_COLOR, null, 0);
		Draw.rect(g, arcRight - padWidth, padTop, arcRight + (padWidth * 0.2), padBottom, Constants.HEADSET_COLOR, null, 0);
	}

	/** 쓰다듬기 하트 도형 (my_pet.py render_pet_heart_shape). */
	public static void renderPetHeart(Graphics2D g, double x, double y, double size, String color) {
		double lobeRadius = size * 0.24;
		double leftCx = x - (size * 0.22);
		double rightCx = x + (size * 0.22);
		double lobeCy = y - (size * 0.12);
		Draw.oval(g, leftCx - lobeRadius, lobeCy - lobeRadius, leftCx + lobeRadius, lobeCy + lobeRadius, color, null, 0);
		Draw.oval(g, rightCx - lobeRadius, lobeCy - lobeRadius, rightCx + lobeRadius, lobeCy + lobeRadius, color, null, 0);
		Draw.polygon(g, new double[] {
			x - (size * 0.5), y - (size * 0.03),
			x + (size * 0.5), y - (size * 0.03),
			x, y + (size * 0.64),
		}, color);
	}

	/** Tk 폰트 포인트 크기를 화면 픽셀 크기로 변환 (윈도우 Tk 기준 96dpi). */
	public static float tkPointsToPixels(int points) {
		return points * 96f / 72f;
	}
}
