package com.mypet.settings;

import com.mypet.appearance.AppearanceMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsStoreTest {
	@TempDir
	Path dir;

	private SettingsStore store(Path json, Path color, Path slots, Path appearance) {
		return new SettingsStore(json, List.of(color), List.of(slots), List.of(appearance));
	}

	@Test
	void jsonRoundTrip() {
		Object parsed = Json.read("{\"a\": [1, \"x\\n\\\"y\\\"\", true, null], \"b\": {\"c\": \"한글 \\u00e9\"}}");
		assertEquals(parsed, Json.read(Json.write(parsed)));
		Map<?, ?> root = (Map<?, ?>) parsed;
		assertEquals("한글 é", ((Map<?, ?>) root.get("b")).get("c"));
	}

	@Test
	void migratesLegacyFilesAndDeletesThem() throws Exception {
		Path color = dir.resolve("my_pet_custom_color.txt");
		Path slots = dir.resolve("my_pet_custom_colors.txt");
		Path appearance = dir.resolve("my_pet_appearance_settings.txt");
		Files.writeString(color, "#FF00AA", StandardCharsets.UTF_8);
		Files.writeString(slots, "#111111\n#222222\nnot-a-color\n", StandardCharsets.UTF_8);
		Files.writeString(appearance, "browser_monocle_mode=off\ndiscord_color_mode=always\nsteam_pixel_heart=1\n", StandardCharsets.UTF_8);
		Path json = dir.resolve("MyPet").resolve("settings.json");

		SettingsStore store = store(json, color, slots, appearance);
		store.load();

		assertEquals("#ff00aa", store.characterColor());
		assertEquals("#111111", store.customColors().get(0));
		assertEquals("#222222", store.customColors().get(1));
		assertEquals("#ffffff", store.customColors().get(2));
		assertEquals(16, store.customColors().size());
		assertEquals(AppearanceMode.OFF, store.appearance(SettingsStore.BROWSER_MONOCLE));
		assertEquals(AppearanceMode.OFF, store.appearance(SettingsStore.BROWSER_IE_ICON), "IE 설정이 없던 파일은 돋보기 설정을 따른다");
		assertEquals(AppearanceMode.ALWAYS, store.appearance(SettingsStore.DISCORD_COLOR));
		assertEquals(AppearanceMode.AUTO, store.appearance(SettingsStore.PIXEL_HEART), "예전 bool 값 1 → 자동");
		assertEquals(AppearanceMode.AUTO, store.appearance(SettingsStore.HEADSET));

		assertTrue(Files.isRegularFile(json), "settings.json 이 만들어져야 함");
		assertFalse(Files.exists(color) || Files.exists(slots) || Files.exists(appearance), "예전 파일은 지워져야 함");

		// 다시 읽으면 settings.json 에서 같은 값
		SettingsStore again = store(json, color, slots, appearance);
		again.load();
		assertEquals("#ff00aa", again.characterColor());
		assertEquals(AppearanceMode.ALWAYS, again.appearance(SettingsStore.DISCORD_COLOR));
		System.out.println(Files.readString(json));
	}

	@Test
	void noLegacyFilesGivesDefaults() {
		Path json = dir.resolve("settings.json");
		SettingsStore store = store(json, dir.resolve("a.txt"), dir.resolve("b.txt"), dir.resolve("c.txt"));
		store.load();
		assertNull(store.characterColor());
		assertEquals(AppearanceMode.AUTO, store.appearance(SettingsStore.HEADSET));
		assertTrue(Files.isRegularFile(json));
	}

	@Test
	void brokenJsonStartsWithDefaultsAndKeepsCopy() throws Exception {
		Path json = dir.resolve("settings.json");
		Files.writeString(json, "{ 깨진 파일", StandardCharsets.UTF_8);
		SettingsStore store = store(json, dir.resolve("a.txt"), dir.resolve("b.txt"), dir.resolve("c.txt"));
		store.load();
		assertNull(store.characterColor());
		assertTrue(Files.exists(dir.resolve("settings.json.broken")));
	}

	@Test
	void savesChanges() {
		Path json = dir.resolve("settings.json");
		SettingsStore store = store(json, dir.resolve("a.txt"), dir.resolve("b.txt"), dir.resolve("c.txt"));
		store.load();
		store.setCharacterColor("#123ABC");
		store.setAppearance(SettingsStore.HEADSET, AppearanceMode.ALWAYS);
		assertEquals(json, store.save());

		SettingsStore again = store(json, dir.resolve("a.txt"), dir.resolve("b.txt"), dir.resolve("c.txt"));
		again.load();
		assertEquals("#123abc", again.characterColor());
		assertEquals(AppearanceMode.ALWAYS, again.appearance(SettingsStore.HEADSET));
	}
}
