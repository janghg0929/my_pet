package com.mypet.appearance;

import com.mypet.settings.SettingsStore;

import java.nio.file.Path;

/** 외형 설정 저장/로드 (settings.json 의 "appearance"). */
public record AppearanceSettings(
	AppearanceMode browserMonocle,
	AppearanceMode browserIeIcon,
	AppearanceMode discordColor,
	AppearanceMode pixelHeart,
	AppearanceMode headset
) {
	/** 외형 설정을 로드. */
	public static AppearanceSettings load() {
		SettingsStore store = SettingsStore.get();
		return new AppearanceSettings(
			store.appearance(SettingsStore.BROWSER_MONOCLE),
			store.appearance(SettingsStore.BROWSER_IE_ICON),
			store.appearance(SettingsStore.DISCORD_COLOR),
			store.appearance(SettingsStore.PIXEL_HEART),
			store.appearance(SettingsStore.HEADSET)
		);
	}

	/** 외형 설정을 저장. 성공한 경로를 반환하고 실패하면 null. */
	public Path save() {
		SettingsStore store = SettingsStore.get();
		store.setAppearance(SettingsStore.BROWSER_MONOCLE, browserMonocle);
		store.setAppearance(SettingsStore.BROWSER_IE_ICON, browserIeIcon);
		store.setAppearance(SettingsStore.DISCORD_COLOR, discordColor);
		store.setAppearance(SettingsStore.PIXEL_HEART, pixelHeart);
		store.setAppearance(SettingsStore.HEADSET, headset);
		return store.save();
	}
}
