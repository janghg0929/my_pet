package com.mypet.settings;

import com.mypet.Constants;
import com.mypet.Util;
import com.mypet.appearance.AppearanceMode;
import com.mypet.parts.PartLayout;
import com.mypet.parts.SavedLook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 비트 설정을 settings.json 하나에 저장한다 (앱이 설치된 폴더 = 앱 데이터 폴더).
 * <pre>
 * {
 *   "version": 1,
 *   "characterColor": "#4fc3f7",        // 직접 고른 몸 색 (기본 색이면 null)
 *   "customColors": ["#ffffff", ...],    // 색 선택 창의 사용자 지정 색 16칸
 *   "appearance": { "browserMonocle": "auto", ... },  // 외형별 auto / off / always
 *   "parts": { "symmetric": true, "body": {"x":0,"y":0,"w":44,"h":28,"image":null,"flip":false}, ... }  // 모습 꾸미기
 *   "looks": [ {"id": "1a2b3c4d", "name": "고양이", "color": null, "parts": {...}}, ... ]  // 저장한 모습
 * }
 * </pre>
 * settings.json 이 없으면 예전 형식의 텍스트 파일(파이썬 버전, 2.0.0)을 찾아서 옮기고 예전 파일은 지운다.
 */
public final class SettingsStore {
	public static final int FORMAT_VERSION = 1;

	/** 외형 key (settings.json / 설정 화면 JS 와 같은 이름). */
	public static final String BROWSER_MONOCLE = "browserMonocle";
	public static final String BROWSER_IE_ICON = "browserIeIcon";
	public static final String DISCORD_COLOR = "discordColor";
	public static final String PIXEL_HEART = "pixelHeart";
	public static final String HEADSET = "headset";
	public static final List<String> APPEARANCE_KEYS = List.of(BROWSER_MONOCLE, BROWSER_IE_ICON, DISCORD_COLOR, PIXEL_HEART, HEADSET);

	private static SettingsStore instance;

	private final Path file;
	private final List<Path> legacyColorPaths;
	private final List<Path> legacyCustomColorPaths;
	private final List<Path> legacyAppearancePaths;

	private String characterColor;
	private List<String> customColors = defaultCustomColors();
	private final Map<String, AppearanceMode> appearance = new LinkedHashMap<>();
	/** 모습 꾸미기 (부위 위치·크기·사진). */
	private PartLayout parts = PartLayout.defaults();
	private List<SavedLook> looks = new ArrayList<>();

	public SettingsStore(Path file, List<Path> legacyColorPaths, List<Path> legacyCustomColorPaths, List<Path> legacyAppearancePaths) {
		this.file = file;
		this.legacyColorPaths = legacyColorPaths;
		this.legacyCustomColorPaths = legacyCustomColorPaths;
		this.legacyAppearancePaths = legacyAppearancePaths;
		for (String key : APPEARANCE_KEYS) {
			appearance.put(key, AppearanceMode.AUTO);
		}
	}

	/** 앱 전체에서 쓰는 설정 (처음 부를 때 읽고, 필요하면 예전 파일을 옮긴다). */
	public static synchronized SettingsStore get() {
		if (instance == null) {
			instance = new SettingsStore(
				Constants.SETTINGS_FILE,
				Constants.LEGACY_CHARACTER_COLOR_PATHS,
				Constants.LEGACY_CUSTOM_COLORS_PATHS,
				Constants.LEGACY_APPEARANCE_SETTINGS_PATHS
			);
			instance.load();
		}
		return instance;
	}

	public Path file() {
		return file;
	}

	// ---------------------------------------------------------------- 값

	/** 직접 고른 몸 색. 기본 색이면 null. */
	public synchronized String characterColor() {
		return characterColor;
	}

	public synchronized void setCharacterColor(String color) {
		characterColor = color == null ? null : Util.normalizeHexColor(color);
	}

	public synchronized List<String> customColors() {
		return new ArrayList<>(customColors);
	}

	public synchronized void setCustomColors(List<String> colors) {
		customColors = normalizeSlots(colors);
	}

	public synchronized AppearanceMode appearance(String key) {
		return appearance.getOrDefault(key, AppearanceMode.AUTO);
	}

	/** 모습 꾸미기 배치 (복사본). */
	public synchronized PartLayout parts() {
		return parts.copy();
	}

	public synchronized void setParts(PartLayout layout) {
		parts = layout == null ? PartLayout.defaults() : layout.copy();
	}

	/** 저장한 모습 목록 (복사본, 저장한 순서). */
	public synchronized List<SavedLook> looks() {
		return new ArrayList<>(looks);
	}

	public synchronized void setLooks(List<SavedLook> newLooks) {
		looks = new ArrayList<>();
		for (SavedLook look : newLooks) {
			if (look != null && looks.size() < SavedLook.MAX_LOOKS) {
				looks.add(look);
			}
		}
	}

	/** 지금 모습과 저장한 모습들이 쓰는 부위 사진 파일 (이 밖의 사진은 지워도 된다). */
	public synchronized Set<String> usedImageFiles() {
		Set<String> files = new LinkedHashSet<>(parts.imageFiles());
		for (SavedLook look : looks) {
			files.addAll(look.parts().imageFiles());
		}
		return files;
	}

	public synchronized void setAppearance(String key, AppearanceMode mode) {
		appearance.put(key, mode == null ? AppearanceMode.AUTO : mode);
	}

	// ---------------------------------------------------------------- 저장/읽기

	/** settings.json 에 저장. 성공하면 경로, 실패하면 null. */
	public synchronized Path save() {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("version", FORMAT_VERSION);
		root.put("characterColor", characterColor);
		root.put("customColors", new ArrayList<>(customColors));
		Map<String, Object> modes = new LinkedHashMap<>();
		for (Map.Entry<String, AppearanceMode> entry : appearance.entrySet()) {
			modes.put(entry.getKey(), entry.getValue().key);
		}
		root.put("appearance", modes);
		root.put("parts", parts.toJson());
		List<Object> lookItems = new ArrayList<>();
		for (SavedLook look : looks) {
			lookItems.add(look.toJson());
		}
		root.put("looks", lookItems);
		try {
			Files.createDirectories(file.getParent());
			// 쓰다가 꺼져도 파일이 깨지지 않게: 임시 파일에 쓰고 바꿔 끼운다
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, Json.write(root), StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			return file;
		} catch (IOException | RuntimeException e) {
			System.err.println("[MY_PET] 설정 저장 실패: " + e);
			return null;
		}
	}

	/** settings.json 을 읽는다. 없으면 예전 파일을 옮겨 온다. */
	public synchronized void load() {
		if (Files.isRegularFile(file)) {
			try {
				apply(Json.read(Files.readString(file, StandardCharsets.UTF_8)));
				return;
			} catch (IOException | RuntimeException e) {
				// 깨진 파일은 옆에 남겨 두고 기본값으로 시작한다
				System.err.println("[MY_PET] settings.json 을 읽지 못해 기본값으로 시작합니다: " + e);
				try {
					Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
					// 무시
				}
				return;
			}
		}
		migrateLegacy();
	}

	private void apply(Object parsed) {
		if (!(parsed instanceof Map<?, ?> root)) {
			throw new IllegalArgumentException("최상위가 객체가 아닙니다");
		}
		Object color = root.get("characterColor");
		characterColor = color instanceof String text ? Util.normalizeHexColor(text) : null;
		if (root.get("customColors") instanceof List<?> list) {
			List<String> colors = new ArrayList<>();
			for (Object item : list) {
				if (item instanceof String text) {
					colors.add(text);
				}
			}
			customColors = normalizeSlots(colors);
		}
		if (root.containsKey("parts")) {
			parts = PartLayout.fromJson(root.get("parts"));
		}
		if (root.get("looks") instanceof List<?> items) {
			List<SavedLook> read = new ArrayList<>();
			for (Object item : items) {
				read.add(SavedLook.fromJson(item));
			}
			setLooks(read);
		}
		if (root.get("appearance") instanceof Map<?, ?> modes) {
			for (String key : APPEARANCE_KEYS) {
				if (modes.get(key) instanceof String text) {
					AppearanceMode mode = AppearanceMode.fromKey(text);
					if (mode != null) {
						appearance.put(key, mode);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- 예전 파일 옮기기

	/**
	 * 예전 형식(파이썬 버전 ~ 2.0.0)의 텍스트 파일을 읽어 settings.json 으로 옮기고 예전 파일은 지운다.
	 * 예전 파일이 하나도 없으면 기본값으로 settings.json 을 만든다.
	 */
	private void migrateLegacy() {
		boolean found = false;

		List<String> colorLines = readFirst(legacyColorPaths);
		if (colorLines != null) {
			found = true;
			characterColor = Util.normalizeHexColor(String.join("", colorLines).strip());
		}

		List<String> slotLines = readFirst(legacyCustomColorPaths);
		if (slotLines != null) {
			found = true;
			customColors = normalizeSlots(slotLines);
		}

		List<String> appearanceLines = readFirst(legacyAppearancePaths);
		if (appearanceLines != null) {
			found = true;
			applyLegacyAppearance(appearanceLines);
		}

		Path saved = save();
		if (found && saved != null) {
			System.err.println("[MY_PET] 예전 설정 파일을 " + saved + " 로 옮겼습니다");
			deleteAll(legacyColorPaths);
			deleteAll(legacyCustomColorPaths);
			deleteAll(legacyAppearancePaths);
		}
	}

	/** 예전 외형 설정 파일 (key=value 줄) 읽기. my_pet_appearance_settings.py 와 같은 규칙. */
	private void applyLegacyAppearance(List<String> lines) {
		boolean ieLoaded = false;
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
						appearance.put(BROWSER_MONOCLE, mode);
					}
				}
				case "browser_ie_icon_mode" -> {
					if (mode != null) {
						appearance.put(BROWSER_IE_ICON, mode);
						ieLoaded = true;
					}
				}
				case "discord_color_mode" -> {
					if (mode != null) {
						appearance.put(DISCORD_COLOR, mode);
					}
				}
				case "pixel_heart_mode", "steam_pixel_heart" ->
					appearance.put(PIXEL_HEART, mode != null ? mode : legacyBoolMode(value));
				case "headset_mode", "steam_headset" ->
					appearance.put(HEADSET, mode != null ? mode : legacyBoolMode(value));
				default -> {
				}
			}
		}
		// IE 아이콘 설정이 없던 시절 파일이면 돋보기 설정을 따른다
		if (!ieLoaded) {
			appearance.put(BROWSER_IE_ICON, appearance.get(BROWSER_MONOCLE));
		}
	}

	/** 예전 bool 스타일 설정값을 외형 모드로 변환. */
	private static AppearanceMode legacyBoolMode(String value) {
		return List.of("1", "true", "on", "yes").contains(value) ? AppearanceMode.AUTO : AppearanceMode.OFF;
	}

	/** 목록에서 처음으로 읽히는 파일의 줄들. 하나도 없으면 null. */
	private static List<String> readFirst(List<Path> paths) {
		for (Path path : paths) {
			try {
				if (Files.isRegularFile(path)) {
					return Files.readAllLines(path, StandardCharsets.UTF_8);
				}
			} catch (IOException | RuntimeException e) {
				// 다음 경로
			}
		}
		return null;
	}

	private static void deleteAll(List<Path> paths) {
		for (Path path : paths) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException | RuntimeException e) {
				// 지우지 못해도 settings.json 이 생겼으므로 다시 옮기지 않는다
			}
		}
	}

	private static List<String> defaultCustomColors() {
		List<String> slots = new ArrayList<>();
		for (int i = 0; i < Constants.CUSTOM_COLOR_SLOT_COUNT; i++) {
			slots.add(Constants.CUSTOM_COLOR_DEFAULT);
		}
		return slots;
	}

	/** 사용자 지정 색 슬롯을 #rrggbb 16칸으로 맞춘다. */
	private static List<String> normalizeSlots(List<String> colors) {
		List<String> slots = new ArrayList<>();
		for (String color : colors) {
			String normalized = Util.normalizeHexColor(color);
			if (normalized != null) {
				slots.add(normalized);
			}
			if (slots.size() >= Constants.CUSTOM_COLOR_SLOT_COUNT) {
				break;
			}
		}
		while (slots.size() < Constants.CUSTOM_COLOR_SLOT_COUNT) {
			slots.add(Constants.CUSTOM_COLOR_DEFAULT);
		}
		return slots;
	}
}
