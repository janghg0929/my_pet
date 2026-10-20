package com.mypet.appearance;

import com.mypet.Constants;
import com.mypet.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 캐릭터 색상/사용자 지정 색 슬롯 저장·로드 (my_pet_color_settings.py 포팅, 파일 형식 동일). */
public final class ColorSettings {
	private ColorSettings() {
	}

	/** 사용자 지정 색 슬롯을 로드. */
	public static List<String> loadCustomColorSlots() {
		List<String> slots = new ArrayList<>();
		for (Path savePath : Constants.CUSTOM_COLORS_SAVE_PATHS) {
			List<String> lines;
			try {
				lines = Files.readAllLines(savePath, StandardCharsets.UTF_8);
			} catch (IOException | RuntimeException e) {
				continue;
			}
			for (String line : lines) {
				String normalized = Util.normalizeHexColor(line);
				if (normalized != null) {
					slots.add(normalized);
				}
			}
			break;
		}
		while (slots.size() < Constants.CUSTOM_COLOR_SLOT_COUNT) {
			slots.add(Constants.CUSTOM_COLOR_DEFAULT);
		}
		return new ArrayList<>(slots.subList(0, Constants.CUSTOM_COLOR_SLOT_COUNT));
	}

	/** 사용자 지정 색 슬롯을 저장. */
	public static Path saveCustomColorSlots(List<String> customColors) {
		List<String> colors = new ArrayList<>();
		for (String colorCode : customColors) {
			String normalized = Util.normalizeHexColor(colorCode);
			if (normalized != null) {
				colors.add(normalized);
			}
			if (colors.size() >= Constants.CUSTOM_COLOR_SLOT_COUNT) {
				break;
			}
		}
		while (colors.size() < Constants.CUSTOM_COLOR_SLOT_COUNT) {
			colors.add(Constants.CUSTOM_COLOR_DEFAULT);
		}
		return writeFirstWritable(Constants.CUSTOM_COLORS_SAVE_PATHS, String.join("\n", colors));
	}

	/** 저장된 캐릭터 색상을 로드. */
	public static String loadSavedCharacterColor() {
		for (Path savePath : Constants.CHARACTER_COLOR_SAVE_PATHS) {
			String saved;
			try {
				saved = Files.readString(savePath, StandardCharsets.UTF_8).strip();
			} catch (IOException | RuntimeException e) {
				continue;
			}
			String normalized = Util.normalizeHexColor(saved);
			if (normalized != null) {
				return normalized;
			}
		}
		return Constants.DEFAULT_CHARACTER_COLOR;
	}

	/** 캐릭터 색상을 저장. */
	public static Path saveCustomCharacterColor(String colorCode) {
		return writeFirstWritable(Constants.CHARACTER_COLOR_SAVE_PATHS, colorCode);
	}

	/** 저장된 캐릭터 색상을 삭제. */
	public static void clearSavedCharacterColor() {
		for (Path savePath : Constants.CHARACTER_COLOR_SAVE_PATHS) {
			try {
				Files.deleteIfExists(savePath);
			} catch (IOException | RuntimeException e) {
				// 무시
			}
		}
	}

	private static Path writeFirstWritable(List<Path> paths, String text) {
		for (Path savePath : paths) {
			try {
				Path parent = savePath.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.writeString(savePath, text, StandardCharsets.UTF_8);
				return savePath;
			} catch (IOException | RuntimeException e) {
				// 다음 경로 시도
			}
		}
		return null;
	}
}
