package com.mypet.parts;

import com.mypet.Util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "저장한 모습" 하나: 이름, 몸 색, 부위 배치(사진 포함).
 * settings.json 의 "looks" 배열에 저장된다.
 *
 * @param color 몸 색 (#rrggbb). 기본 색이면 null.
 */
public record SavedLook(String id, String name, String color, PartLayout parts) {
	public static final int MAX_NAME_LENGTH = 20;
	/** 저장할 수 있는 모습 수. */
	public static final int MAX_LOOKS = 50;

	public SavedLook {
		parts = parts == null ? PartLayout.defaults() : parts.copy();
	}

	public static SavedLook create(String name, String color, PartLayout parts) {
		return new SavedLook(UUID.randomUUID().toString().substring(0, 8), cleanName(name), color, parts);
	}

	public SavedLook withName(String newName) {
		return new SavedLook(id, cleanName(newName), color, parts);
	}

	/** 앞뒤 공백을 빼고 길이를 줄인다. 비면 "이름 없음". */
	public static String cleanName(String name) {
		String text = name == null ? "" : name.strip().replaceAll("\\s+", " ");
		if (text.isEmpty()) {
			return "이름 없음";
		}
		return text.length() > MAX_NAME_LENGTH ? text.substring(0, MAX_NAME_LENGTH) : text;
	}

	public Map<String, Object> toJson() {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", id);
		item.put("name", name);
		item.put("color", color);
		item.put("parts", parts.toJson());
		return item;
	}

	/** settings.json 에서 읽는다. 틀린 값이면 null. */
	public static SavedLook fromJson(Object value) {
		if (!(value instanceof Map<?, ?> item) || !(item.get("id") instanceof String id) || !id.matches("[A-Za-z0-9_-]{1,40}")) {
			return null;
		}
		String color = item.get("color") instanceof String text ? Util.normalizeHexColor(text) : null;
		return new SavedLook(id, cleanName(item.get("name") instanceof String text ? text : null), color,
			PartLayout.fromJson(item.get("parts")));
	}
}
