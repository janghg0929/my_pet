package com.mypet;

import java.awt.Color;
import java.util.Locale;

/** my_pet_utils.py + 색상 관련 보조 함수. */
public final class Util {
	private Util() {
	}

	/** 값을 최소/최대 범위로 제한. */
	public static double clamp(double value, double minValue, double maxValue) {
		return Math.max(minValue, Math.min(value, maxValue));
	}

	/** 파이썬 int(round(x)) 와 동일 (반올림 시 짝수 쪽으로). */
	public static int pyRound(double value) {
		return (int) Math.rint(value);
	}

	/** #RRGGBB 색상 문자열 정규화. 잘못된 값이면 null. */
	public static String normalizeHexColor(String rawColor) {
		if (rawColor == null) {
			return null;
		}
		String text = rawColor.strip();
		if (text.isEmpty()) {
			return null;
		}
		if (!text.startsWith("#")) {
			text = "#" + text;
		}
		if (text.length() != 7) {
			return null;
		}
		String hexPart = text.substring(1);
		for (char ch : hexPart.toCharArray()) {
			if (Character.digit(ch, 16) < 0) {
				return null;
			}
		}
		return "#" + hexPart.toLowerCase(Locale.ROOT);
	}

	public static Color toColor(String hex) {
		return new Color(Integer.parseInt(hex.substring(1, 7), 16));
	}

	public static String toHex(Color color) {
		return String.format(Locale.ROOT, "#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	/** 두 색상을 보간. */
	public static String lerpHexColors(String color1, String color2, double t) {
		t = clamp(t, 0.0, 1.0);
		int r1 = Integer.parseInt(color1.substring(1, 3), 16);
		int g1 = Integer.parseInt(color1.substring(3, 5), 16);
		int b1 = Integer.parseInt(color1.substring(5, 7), 16);
		int r2 = Integer.parseInt(color2.substring(1, 3), 16);
		int g2 = Integer.parseInt(color2.substring(3, 5), 16);
		int b2 = Integer.parseInt(color2.substring(5, 7), 16);
		int r = (int) (r1 + (r2 - r1) * t);
		int g = (int) (g1 + (g2 - g1) * t);
		int b = (int) (b1 + (b2 - b1) * t);
		return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
	}

	/** HSV를 RGB hex로 변환. */
	public static String hsvToHex(double hue, double saturation, double value) {
		hue = ((hue % 1.0) + 1.0) % 1.0;
		saturation = clamp(saturation, 0.0, 1.0);
		value = clamp(value, 0.0, 1.0);
		double c = value * saturation;
		double x = c * (1 - Math.abs(((hue * 6) % 2) - 1));
		double m = value - c;
		double r;
		double g;
		double b;
		if (hue < 1.0 / 6) {
			r = c; g = x; b = 0;
		} else if (hue < 2.0 / 6) {
			r = x; g = c; b = 0;
		} else if (hue < 3.0 / 6) {
			r = 0; g = c; b = x;
		} else if (hue < 4.0 / 6) {
			r = 0; g = x; b = c;
		} else if (hue < 5.0 / 6) {
			r = x; g = 0; b = c;
		} else {
			r = c; g = 0; b = x;
		}
		return String.format(Locale.ROOT, "#%02x%02x%02x", (int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255));
	}
}
