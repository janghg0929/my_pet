package com.mypet.parts;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 비트의 부위 배치 (몸통, 양팔, 양다리, 눈). 사용자가 "모습 꾸미기"에서 위치·크기·사진을 바꿀 수 있다.
 * 좌표는 몸통 기본 위치(몸통 왼쪽 위 = 0,0)를 기준으로 한 화면 px 이다.
 * settings.json 의 "parts" 로 저장된다.
 */
public final class PartLayout {
	public static final String BODY = "body";
	public static final String LEFT_ARM = "leftArm";
	public static final String RIGHT_ARM = "rightArm";
	public static final String LEFT_LEG = "leftLeg";
	public static final String RIGHT_LEG = "rightLeg";
	public static final String EYES = "eyes";
	/** 그리는 순서와 같은 순서. */
	public static final List<String> KEYS = List.of(BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, EYES);

	/** 눈 사진을 따로 넣을 수 있는 표정. */
	public static final String EXPR_NEUTRAL = "neutral";
	public static final String EXPR_GRAB = "grab";
	public static final String EXPR_FALL = "fall";
	public static final List<String> EYE_EXPRESSIONS = List.of(EXPR_NEUTRAL, EXPR_GRAB, EXPR_FALL);

	/** 기본 눈 영역 (기본 비트의 평소 눈 두 개를 감싸는 상자). */
	public static final int DEFAULT_EYES_X = 14;
	public static final int DEFAULT_EYES_Y = 12;
	public static final int DEFAULT_EYES_W = 18;
	public static final int DEFAULT_EYES_H = 6;

	private static final int MIN_SIZE = 2;
	private static final int MAX_SIZE = 400;
	private static final int MAX_OFFSET = 400;

	/** 부위 하나. 사진이 없으면 네모(몸 색 + 외곽선)로 그린다. */
	public static final class Part {
		public int x;
		public int y;
		public int w;
		public int h;
		/** 부위 사진 파일 이름 (parts 폴더 안). 눈은 {@link #images} 를 쓴다. */
		public String image;
		/** 사진을 좌우로 뒤집어 그릴지 (좌우 맞추기로 반대쪽에 복사된 사진). */
		public boolean flip;
		/** 눈 전용: 표정별 사진 파일 이름. */
		public final Map<String, String> images = new LinkedHashMap<>();

		Part(int x, int y, int w, int h) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		Part copy() {
			Part copy = new Part(x, y, w, h);
			copy.image = image;
			copy.flip = flip;
			copy.images.putAll(images);
			return copy;
		}

		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}
	}

	/** 전체 범위 (몸통 기준 좌표). minY 는 점프할 때 팔을 드는 높이까지 포함한다. */
	public record Bounds(int minX, int minY, int maxX, int maxY) {
		public int width() {
			return maxX - minX;
		}
	}

	private final Map<String, Part> parts = new LinkedHashMap<>();
	/** 좌우 맞추기: 왼쪽을 바꾸면 오른쪽도 거울처럼 따라간다. */
	public boolean symmetric = true;

	private PartLayout() {
	}

	/** 기본 비트 (기존 치수와 같다). */
	public static PartLayout defaults() {
		PartLayout layout = new PartLayout();
		layout.parts.put(BODY, new Part(0, 0, 44, 28));
		layout.parts.put(LEFT_ARM, new Part(-9, 14, 8, 8));
		layout.parts.put(RIGHT_ARM, new Part(45, 14, 8, 8));
		layout.parts.put(LEFT_LEG, new Part(10, 29, 8, 8));
		layout.parts.put(RIGHT_LEG, new Part(25, 29, 8, 8));
		layout.parts.put(EYES, new Part(DEFAULT_EYES_X, DEFAULT_EYES_Y, DEFAULT_EYES_W, DEFAULT_EYES_H));
		return layout;
	}

	public PartLayout copy() {
		PartLayout copy = new PartLayout();
		copy.symmetric = symmetric;
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			copy.parts.put(entry.getKey(), entry.getValue().copy());
		}
		return copy;
	}

	public Part get(String key) {
		return parts.get(key);
	}

	public Part body() {
		return parts.get(BODY);
	}

	public Part leftArm() {
		return parts.get(LEFT_ARM);
	}

	public Part rightArm() {
		return parts.get(RIGHT_ARM);
	}

	public Part leftLeg() {
		return parts.get(LEFT_LEG);
	}

	public Part rightLeg() {
		return parts.get(RIGHT_LEG);
	}

	public Part eyes() {
		return parts.get(EYES);
	}

	/** 왼쪽 부위의 반대쪽 (팔/다리만). 없으면 null. */
	public static String mirrorOf(String key) {
		return switch (key) {
			case LEFT_ARM -> RIGHT_ARM;
			case RIGHT_ARM -> LEFT_ARM;
			case LEFT_LEG -> RIGHT_LEG;
			case RIGHT_LEG -> LEFT_LEG;
			default -> null;
		};
	}

	/** source 부위를 몸통 가운데를 기준으로 거울처럼 반대쪽에 복사 (위치·크기, 사진은 뒤집어서). */
	public void mirrorFrom(String sourceKey) {
		String targetKey = mirrorOf(sourceKey);
		if (targetKey == null) {
			return;
		}
		Part source = parts.get(sourceKey);
		Part target = parts.get(targetKey);
		Part body = body();
		int doubledCenter = body.x * 2 + body.w;
		target.w = source.w;
		target.h = source.h;
		target.y = source.y;
		target.x = doubledCenter - (source.x + source.w);
		target.image = source.image;
		target.flip = source.image != null && !source.flip;
	}

	/** 전체 범위. armRaise = 점프할 때 팔을 드는 높이. */
	public Bounds bounds(int armRaise) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			Part part = entry.getValue();
			boolean arm = LEFT_ARM.equals(entry.getKey()) || RIGHT_ARM.equals(entry.getKey());
			minX = Math.min(minX, part.x);
			maxX = Math.max(maxX, part.right());
			minY = Math.min(minY, arm ? part.y - armRaise : part.y);
			maxY = Math.max(maxY, part.bottom());
		}
		return new Bounds(minX, minY, maxX, maxY);
	}

	/** 쓰고 있는 사진 파일 이름들. */
	public Set<String> imageFiles() {
		Set<String> files = new LinkedHashSet<>();
		for (Part part : parts.values()) {
			if (part.image != null) {
				files.add(part.image);
			}
			for (String name : part.images.values()) {
				if (name != null) {
					files.add(name);
				}
			}
		}
		return files;
	}

	// ---------------------------------------------------------------- JSON

	/** settings.json 에 쓸 형태. */
	public Map<String, Object> toJson() {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("symmetric", symmetric);
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			Part part = entry.getValue();
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("x", part.x);
			item.put("y", part.y);
			item.put("w", part.w);
			item.put("h", part.h);
			if (EYES.equals(entry.getKey())) {
				Map<String, Object> images = new LinkedHashMap<>();
				for (String expression : EYE_EXPRESSIONS) {
					images.put(expression, part.images.get(expression));
				}
				item.put("images", images);
			} else {
				item.put("image", part.image);
				item.put("flip", part.flip);
			}
			root.put(entry.getKey(), item);
		}
		return root;
	}

	/** settings.json / 설정 화면에서 받은 값을 읽는다. 틀린 값은 기본값/허용 범위로 맞춘다. */
	public static PartLayout fromJson(Object value) {
		PartLayout layout = defaults();
		if (!(value instanceof Map<?, ?> root)) {
			return layout;
		}
		if (root.get("symmetric") instanceof Boolean flag) {
			layout.symmetric = flag;
		}
		for (String key : KEYS) {
			if (!(root.get(key) instanceof Map<?, ?> item)) {
				continue;
			}
			Part part = layout.parts.get(key);
			part.x = clampInt(item.get("x"), part.x, -MAX_OFFSET, MAX_OFFSET);
			part.y = clampInt(item.get("y"), part.y, -MAX_OFFSET, MAX_OFFSET);
			part.w = clampInt(item.get("w"), part.w, MIN_SIZE, MAX_SIZE);
			part.h = clampInt(item.get("h"), part.h, MIN_SIZE, MAX_SIZE);
			if (EYES.equals(key)) {
				if (item.get("images") instanceof Map<?, ?> images) {
					for (String expression : EYE_EXPRESSIONS) {
						part.images.put(expression, safeFileName(images.get(expression)));
					}
				}
			} else {
				part.image = safeFileName(item.get("image"));
				part.flip = part.image != null && Boolean.TRUE.equals(item.get("flip"));
			}
		}
		return layout;
	}

	private static int clampInt(Object value, int fallback, int min, int max) {
		if (!(value instanceof Number number)) {
			return fallback;
		}
		double v = number.doubleValue();
		if (Double.isNaN(v)) {
			return fallback;
		}
		return (int) Math.max(min, Math.min(max, Math.round(v)));
	}

	/** parts 폴더 안의 파일 이름만 허용 (경로 문자 금지). */
	static String safeFileName(Object value) {
		if (!(value instanceof String name) || name.isBlank()) {
			return null;
		}
		return name.matches("[A-Za-z0-9_-]{1,64}\\.png") ? name : null;
	}

	/** 실제로 그려지는 표정별 눈 사진 (없으면 null). */
	public String eyeImage(String expression) {
		return eyes().images.get(EYE_EXPRESSIONS.contains(expression) ? expression : EXPR_NEUTRAL);
	}
}
