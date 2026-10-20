package com.mypet.appearance;

import com.mypet.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** 외형 설정 저장/로드 (my_pet_appearance_settings.py 포팅, 파일 형식 동일). */
public record AppearanceSettings(
	AppearanceMode browserMonocle,
	AppearanceMode browserIeIcon,
	AppearanceMode discordColor,
	AppearanceMode pixelHeart,
	AppearanceMode headset
) {
	/** 예전 bool 스타일 설정값을 외형 모드로 변환. */
	private static AppearanceMode normalizeLegacyBoolMode(String value) {
		return List.of("1", "true", "on", "yes").contains(value) ? AppearanceMode.AUTO : AppearanceMode.OFF;
	}

	/** 외형 설정을 로드. */
	public static AppearanceSettings load() {
		AppearanceMode browserMode = AppearanceMode.AUTO;
		AppearanceMode browserIeMode = AppearanceMode.AUTO;
		boolean browserIeModeLoaded = false;
		AppearanceMode discordMode = AppearanceMode.AUTO;
		AppearanceMode pixelHeartMode = AppearanceMode.AUTO;
		AppearanceMode headsetMode = AppearanceMode.AUTO;

		for (Path savePath : Constants.APPEARANCE_SETTINGS_SAVE_PATHS) {
			List<String> lines;
			try {
				lines = Files.readAllLines(savePath, StandardCharsets.UTF_8);
			} catch (IOException | RuntimeException e) {
				continue;
			}

			for (String line : lines) {
				int eq = line.indexOf('=');
				if (eq < 0) {
					continue;
				}
				String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
				String value = line.substring(eq + 1).strip().toLowerCase(Locale.ROOT);
				AppearanceMode mode = AppearanceMode.fromKey(value);
				switch (key) {
					case "browser_monocle_mode" -> {
						if (mode != null) {
							browserMode = mode;
						}
					}
					case "browser_ie_icon_mode" -> {
						if (mode != null) {
							browserIeMode = mode;
							browserIeModeLoaded = true;
						}
					}
					case "discord_color_mode" -> {
						if (mode != null) {
							discordMode = mode;
						}
					}
					case "pixel_heart_mode", "steam_pixel_heart" ->
						pixelHeartMode = mode != null ? mode : normalizeLegacyBoolMode(value);
					case "headset_mode", "steam_headset" ->
						headsetMode = mode != null ? mode : normalizeLegacyBoolMode(value);
					default -> {
					}
				}
			}
			break;
		}

		if (!browserIeModeLoaded) {
			browserIeMode = browserMode;
		}
		return new AppearanceSettings(browserMode, browserIeMode, discordMode, pixelHeartMode, headsetMode);
	}

	/** 외형 설정을 저장. 성공한 경로를 반환하고 모두 실패하면 null. */
	public Path save() {
		String settingsText = "browser_monocle_mode=" + browserMonocle.key + "\n"
			+ "browser_ie_icon_mode=" + browserIeIcon.key + "\n"
			+ "discord_color_mode=" + discordColor.key + "\n"
			+ "pixel_heart_mode=" + pixelHeart.key + "\n"
			+ "headset_mode=" + headset.key + "\n";

		for (Path savePath : Constants.APPEARANCE_SETTINGS_SAVE_PATHS) {
			try {
				Path parent = savePath.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.writeString(savePath, settingsText, StandardCharsets.UTF_8);
				return savePath;
			} catch (IOException | RuntimeException e) {
				// 다음 경로 시도
			}
		}
		return null;
	}
}
