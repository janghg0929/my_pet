package com.mypet.appearance;

import java.util.Locale;

/** 외형 표시 모드: 자동 / 끔 / 항상 켬. */
public enum AppearanceMode {
	AUTO("auto", "자동"),
	OFF("off", "끔"),
	ALWAYS("always", "항상 켬");

	public final String key;
	public final String label;

	AppearanceMode(String key, String label) {
		this.key = key;
		this.label = label;
	}

	/** 저장된 문자열을 모드로 변환. 알 수 없으면 null. */
	public static AppearanceMode fromKey(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.strip().toLowerCase(Locale.ROOT);
		for (AppearanceMode mode : values()) {
			if (mode.key.equals(normalized)) {
				return mode;
			}
		}
		return null;
	}

	/** 콤보박스 표시용. */
	@Override
	public String toString() {
		return label;
	}

	/** 자동/끔/항상 켬 공통 판정. */
	public boolean isEnabled(boolean automaticActive) {
		return switch (this) {
			case OFF -> false;
			case ALWAYS -> true;
			case AUTO -> automaticActive;
		};
	}
}
