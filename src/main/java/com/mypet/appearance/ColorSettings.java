package com.mypet.appearance;

import com.mypet.Constants;
import com.mypet.settings.SettingsStore;

import java.nio.file.Path;
import java.util.List;

/** 캐릭터 색상/사용자 지정 색 슬롯 저장·로드 (settings.json 의 "characterColor", "customColors"). */
public final class ColorSettings {
	private ColorSettings() {
	}

	/** 사용자 지정 색 슬롯을 로드 (항상 16칸). */
	public static List<String> loadCustomColorSlots() {
		return SettingsStore.get().customColors();
	}

	/** 사용자 지정 색 슬롯을 저장. */
	public static Path saveCustomColorSlots(List<String> customColors) {
		SettingsStore store = SettingsStore.get();
		store.setCustomColors(customColors);
		return store.save();
	}

	/** 저장된 캐릭터 색상을 로드 (없으면 기본 색). */
	public static String loadSavedCharacterColor() {
		String saved = SettingsStore.get().characterColor();
		return saved != null ? saved : Constants.DEFAULT_CHARACTER_COLOR;
	}

	/** 캐릭터 색상을 저장. */
	public static Path saveCustomCharacterColor(String colorCode) {
		SettingsStore store = SettingsStore.get();
		store.setCharacterColor(colorCode);
		return store.save();
	}

	/** 저장된 캐릭터 색상을 지운다 (기본 색으로). */
	public static void clearSavedCharacterColor() {
		SettingsStore store = SettingsStore.get();
		store.setCharacterColor(null);
		store.save();
	}
}
