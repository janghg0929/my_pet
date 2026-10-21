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
	/** 기본 부위 (그리는 순서와 같은 순서). 사용자가 만든 부위는 "c_" 로 시작하는 이름으로 더 붙는다. */
	public static final List<String> KEYS = List.of(BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, EYES);
	/** 사용자가 만든 부위 이름 앞머리, 최대 개수. */
	public static final String CUSTOM_PREFIX = "c_";
	public static final int MAX_CUSTOM = 20;
	/** 새로 만든 부위의 크기 (가로·세로 px). */
	public static final int CUSTOM_SIZE = 24;
	public static final int MAX_NAME_LENGTH = 20;
	/** 사진이 없을 때 모양: 네모(몸 색 + 외곽선) 또는 검은 점(새로 만든 부위). */
	public static final String SHAPE_RECT = "rect";
	public static final String SHAPE_DOT = "dot";

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

	/** 움직임(돌리기, 늘이기)의 중심점: 부위 가운데 또는 한쪽 가장자리 가운데. */
	public static final String PIVOT_CENTER = "center";
	public static final String PIVOT_TOP = "top";
	public static final String PIVOT_BOTTOM = "bottom";
	public static final String PIVOT_LEFT = "left";
	public static final String PIVOT_RIGHT = "right";
	public static final List<String> PIVOTS = List.of(PIVOT_CENTER, PIVOT_TOP, PIVOT_BOTTOM, PIVOT_LEFT, PIVOT_RIGHT);

	private static final int MIN_SIZE = 2;
	// 부위 크기·자리 한계 (사실상 없음: 화면보다 훨씬 크게)
	private static final int MAX_SIZE = 2000;
	private static final int MAX_OFFSET = 2000;
	/** 그림판 해상도 최대 (한쪽 칸 수, painter.js 의 MAX_SIDE 와 같다). */
	public static final int MAX_RESOLUTION = 256;

	/** 부위 하나. 사진이 없으면 네모(몸 색 + 외곽선) 또는 검은 점으로 그린다. */
	public static final class Part {
		/** 보이는 이름 (null 이면 기본 부위 이름). */
		public String name;
		/** 화면에 있는지 (빼면 false: 설정은 남아 있어서 "가져오기"로 다시 넣을 수 있다). */
		public boolean visible = true;
		/** 사진이 없을 때 모양 ({@link #SHAPE_RECT} / {@link #SHAPE_DOT}). */
		public String shape = SHAPE_RECT;
		public int x;
		public int y;
		public int w;
		public int h;
		/** 부위 사진 파일 이름 (parts 폴더 안). 눈은 {@link #images} 를 쓴다. */
		public String image;
		/** 사진을 좌우로 뒤집어 그릴지 (좌우 맞추기로 반대쪽에 복사된 사진). */
		public boolean flip;
		/** 회전 (도, 시계 방향, -179..180). 부위 가운데를 기준으로 돌린다. */
		public int rotation;
		/** 그림판 해상도 (가로·세로 칸 수, 0 이면 부위 크기). 부위를 우클릭해서 정한다. */
		public int resW;
		public int resH;
		/** 움직임 중심점 (팔은 몸 쪽, 다리는 위쪽이 기본: 어깨·엉덩이를 기준으로 돈다). */
		public String pivot = PIVOT_CENTER;
		/** 상황별 애니메이션 (항상, 쉴 때, 걸을 때, 공중, 잡힐 때 — 5개 모두 있다). */
		public final Map<String, PartAnimation> animations = PartAnimation.emptySet();
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
			copy.name = name;
			copy.visible = visible;
			copy.shape = shape;
			copy.image = image;
			copy.flip = flip;
			copy.rotation = rotation;
			copy.resW = resW;
			copy.resH = resH;
			copy.pivot = pivot;
			for (Map.Entry<String, PartAnimation> entry : animations.entrySet()) {
				copy.animations.put(entry.getKey(), entry.getValue().copy());
			}
			copy.images.putAll(images);
			return copy;
		}

		/** 돌린 부위를 감싸는 사각형의 가로 반지름, 세로 반지름 (회전이 없으면 w/2, h/2). */
		double[] halfExtents() {
			return halfExtents(rotation, 1);
		}

		double[] halfExtents(double degrees, double scale) {
			double rad = Math.toRadians(degrees);
			double cos = Math.abs(Math.cos(rad));
			double sin = Math.abs(Math.sin(rad));
			return new double[] {(w / 2.0 * cos + h / 2.0 * sin) * scale, (w / 2.0 * sin + h / 2.0 * cos) * scale};
		}

		/** 중심점 (몸통 기준 좌표, 부위를 돌린 것까지 반영). */
		public double[] pivotPoint() {
			double ox = switch (pivot) {
				case PIVOT_LEFT -> -w / 2.0;
				case PIVOT_RIGHT -> w / 2.0;
				default -> 0;
			};
			double oy = switch (pivot) {
				case PIVOT_TOP -> -h / 2.0;
				case PIVOT_BOTTOM -> h / 2.0;
				default -> 0;
			};
			double rad = Math.toRadians(rotation);
			double cos = Math.cos(rad);
			double sin = Math.sin(rad);
			return new double[] {x + w / 2.0 + ox * cos - oy * sin, y + h / 2.0 + ox * sin + oy * cos};
		}

		public boolean animated() {
			for (PartAnimation animation : animations.values()) {
				if (!animation.isEmpty()) {
					return true;
				}
			}
			return false;
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
	/** 고급: 상황별 시간 막대 애니메이션 (키프레임). */
	public PartTimeline timeline = new PartTimeline();

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
		for (Map.Entry<String, Part> entry : layout.parts.entrySet()) {
			entry.getValue().pivot = defaultPivot(entry.getKey());
		}
		return layout;
	}

	public PartLayout copy() {
		PartLayout copy = new PartLayout();
		copy.symmetric = symmetric;
		copy.timeline = timeline.copy();
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			copy.parts.put(entry.getKey(), entry.getValue().copy());
		}
		return copy;
	}

	public Part get(String key) {
		return parts.get(key);
	}

	/** 모든 부위 이름 (기본 부위 + 만든 부위, 화면에서 뺀 것도). */
	public List<String> keys() {
		return new java.util.ArrayList<>(parts.keySet());
	}

	/** 만든 부위 이름들 (만든 순서). */
	public List<String> customKeys() {
		List<String> keys = new java.util.ArrayList<>();
		for (String key : parts.keySet()) {
			if (isCustom(key)) {
				keys.add(key);
			}
		}
		return keys;
	}

	/** 사용자가 만든 부위 이름인지 ("c_" + 영문/숫자). */
	public static boolean isCustom(String key) {
		return key != null && key.matches("c_[A-Za-z0-9]{1,16}");
	}

	/** 새로 만든 부위: 몸통 가운데의 작은 검은 점. */
	public static Part newCustomPart(Part body, String name) {
		Part part = new Part(body.x + body.w / 2 - CUSTOM_SIZE / 2, body.y + body.h / 2 - CUSTOM_SIZE / 2, CUSTOM_SIZE, CUSTOM_SIZE);
		part.shape = SHAPE_DOT;
		part.name = cleanName(name);
		return part;
	}

	/** 부위 이름: 정한 이름, 없으면 기본 부위 이름 (만든 부위는 "새 부위"). */
	public String nameOf(String key) {
		Part part = parts.get(key);
		if (part != null && part.name != null) {
			return part.name;
		}
		return switch (key) {
			case BODY -> "몸통";
			case LEFT_ARM -> "왼팔";
			case RIGHT_ARM -> "오른팔";
			case LEFT_LEG -> "왼다리";
			case RIGHT_LEG -> "오른다리";
			case EYES -> "눈";
			default -> "새 부위";
		};
	}

	static String cleanName(Object value) {
		if (!(value instanceof String text)) {
			return null;
		}
		String name = text.strip().replaceAll("\\s+", " ");
		if (name.isEmpty()) {
			return null;
		}
		return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
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
		target.rotation = normalizeRotation(-source.rotation);
		target.resW = source.resW;
		target.resH = source.resH;
		target.pivot = mirrorPivot(source.pivot);
		for (Map.Entry<String, PartAnimation> entry : source.animations.entrySet()) {
			target.animations.put(entry.getKey(), entry.getValue().mirrored());
		}
		timeline.mirrorTrack(sourceKey, targetKey);
	}

	static String mirrorPivot(String pivot) {
		return switch (pivot) {
			case PIVOT_LEFT -> PIVOT_RIGHT;
			case PIVOT_RIGHT -> PIVOT_LEFT;
			default -> pivot;
		};
	}

	/** 부위의 기본 중심점: 팔은 몸 쪽 가장자리, 다리는 위쪽, 나머지는 가운데. */
	public static String defaultPivot(String key) {
		return switch (key) {
			case LEFT_ARM -> PIVOT_RIGHT;
			case RIGHT_ARM -> PIVOT_LEFT;
			case LEFT_LEG, RIGHT_LEG -> PIVOT_TOP;
			default -> PIVOT_CENTER;
		};
	}

	/** 전체 범위 (돌린 부위는 돌린 모양을 감싸는 사각형). armRaise = 점프할 때 팔을 드는 높이. */
	public Bounds bounds(int armRaise) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		boolean any = false;
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			Part part = entry.getValue();
			// 화면에서 뺀 부위는 빼고 잰다 (다 뺐으면 몸통 자리로)
			if (!part.visible && !(BODY.equals(entry.getKey()) && !anyVisible())) {
				continue;
			}
			any = true;
			boolean arm = LEFT_ARM.equals(entry.getKey()) || RIGHT_ARM.equals(entry.getKey());
			double[] half = part.halfExtents();
			double cx = part.x + part.w / 2.0;
			double cy = part.y + part.h / 2.0;
			// 소수점 오차로 1px 넓어지지 않게 아주 조금 안쪽으로 반올림
			int left = (int) Math.floor(cx - half[0] + 1e-6);
			int right = (int) Math.ceil(cx + half[0] - 1e-6);
			int top = (int) Math.floor(cy - half[1] + 1e-6);
			int bottom = (int) Math.ceil(cy + half[1] - 1e-6);
			minX = Math.min(minX, left);
			maxX = Math.max(maxX, right);
			minY = Math.min(minY, arm ? top - armRaise : top);
			maxY = Math.max(maxY, bottom);
		}
		if (!any) {
			return new Bounds(0, 0, 44, 28);
		}
		return new Bounds(minX, minY, maxX, maxY);
	}

	private boolean anyVisible() {
		for (Part part : parts.values()) {
			if (part.visible) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 애니메이션까지 넣은 전체 범위 (창 크기용, 넉넉하게 잡는다).
	 * 발 높이는 움직임 없는 {@link #bounds} 의 maxY 를 쓴다.
	 */
	public Bounds motionBounds(int armRaise) {
		Bounds base = bounds(armRaise);
		double minX = base.minX();
		double minY = base.minY();
		double maxX = base.maxX();
		double maxY = base.maxY();
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			Part part = entry.getValue();
			if (!part.visible) {
				continue;
			}
			double[] clipAlways = timeline.reach(entry.getKey(), true);
			double[] clipMost = timeline.reach(entry.getKey(), false);
			boolean clipped = clipAlways[0] + clipAlways[1] + clipAlways[2] + clipAlways[3] > 0 || clipAlways[4] != 1
				|| clipMost[0] + clipMost[1] + clipMost[2] + clipMost[3] > 0 || clipMost[4] != 1;
			if (!part.animated() && !clipped) {
				continue;
			}
			// 가장 크게 움직일 때: "항상" + (다른 상황 중 가장 큰 것) — 한 번에 한 상황만 켜진다.
			// 고급 시간 막대는 일반 애니메이션에 더해진다
			double[] always = reach(part.animations.get(PartAnimation.ALWAYS), false);
			double[] most = {0, 0, 0, 0, 1};
			for (String state : PartAnimation.STATES) {
				if (!PartAnimation.ALWAYS.equals(state)) {
					double[] r = reach(part.animations.get(state), true);
					for (int i = 0; i < 4; i++) {
						most[i] = Math.max(most[i], r[i]);
					}
					most[4] = Math.max(most[4], r[4]);
				}
			}
			for (int i = 0; i < 4; i++) {
				always[i] += clipAlways[i];
				most[i] += clipMost[i];
			}
			always[4] *= clipAlways[4];
			most[4] *= clipMost[4];
			double turn = always[0] + most[0];
			double side = always[1] + most[1];
			double up = always[2] + most[2];
			double down = always[3] + most[3];
			double scale = always[4] * most[4];
			double[] pivot = part.pivotPoint();
			double[] half = part.halfExtents();
			double cx = part.x + part.w / 2.0;
			double cy = part.y + part.h / 2.0;
			double left;
			double right;
			double top;
			double bottom;
			if (turn > 0) {
				// 돌면 중심점에서 가장 먼 모서리까지를 반지름으로 하는 원 안에 있다
				double radius = (Math.hypot(Math.abs(cx - pivot[0]) + half[0], Math.abs(cy - pivot[1]) + half[1])) * scale;
				left = pivot[0] - radius;
				right = pivot[0] + radius;
				top = pivot[1] - radius;
				bottom = pivot[1] + radius;
			} else {
				left = pivot[0] + (cx - half[0] - pivot[0]) * scale;
				right = pivot[0] + (cx + half[0] - pivot[0]) * scale;
				top = pivot[1] + (cy - half[1] - pivot[1]) * scale;
				bottom = pivot[1] + (cy + half[1] - pivot[1]) * scale;
			}
			boolean arm = LEFT_ARM.equals(entry.getKey()) || RIGHT_ARM.equals(entry.getKey());
			minX = Math.min(minX, left - side);
			maxX = Math.max(maxX, right + side);
			minY = Math.min(minY, top - up - (arm ? armRaise : 0));
			maxY = Math.max(maxY, bottom + down);
		}
		return new Bounds((int) Math.floor(minX), (int) Math.floor(minY), (int) Math.ceil(maxX), (int) Math.ceil(maxY));
	}

	/** 애니메이션 하나가 가장 크게 움직이는 값: [돌기(도), 옆(px), 위(px), 아래(px), 커지는 배율]. */
	private static double[] reach(PartAnimation animation, boolean withPose) {
		double[] e = animation.motion.extremes();
		double turn = e[0] + (withPose ? Math.abs(animation.rotation) : 0);
		double side = e[2] + (withPose ? Math.abs(animation.dx) : 0);
		double up = e[1] + (withPose ? Math.max(0, -animation.dy) : 0);
		double down = withPose ? Math.max(0, animation.dy) : 0;
		return new double[] {turn, side, up, down, e[3]};
	}

	/** 각도를 -179..180 도로 맞춘다. */
	public static int normalizeRotation(int degrees) {
		int r = Math.floorMod(degrees, 360);
		return r > 180 ? r - 360 : r;
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
		files.addAll(timeline.photoFiles());
		return files;
	}

	// ---------------------------------------------------------------- JSON

	/** settings.json 에 쓸 형태. */
	public Map<String, Object> toJson() {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("symmetric", symmetric);
		root.put("timeline", timeline.toJson());
		for (Map.Entry<String, Part> entry : parts.entrySet()) {
			Part part = entry.getValue();
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("name", part.name);
			item.put("visible", part.visible);
			item.put("shape", part.shape);
			item.put("x", part.x);
			item.put("y", part.y);
			item.put("w", part.w);
			item.put("h", part.h);
			item.put("rotation", part.rotation);
			item.put("resW", part.resW);
			item.put("resH", part.resH);
			item.put("pivot", part.pivot);
			Map<String, Object> animations = new LinkedHashMap<>();
			for (Map.Entry<String, PartAnimation> animation : part.animations.entrySet()) {
				animations.put(animation.getKey(), animation.getValue().toJson());
			}
			item.put("animations", animations);
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
		layout.timeline = PartTimeline.fromJson(root.get("timeline"));
		// 만든 부위: "c_…" 이름으로 (최대 MAX_CUSTOM 개)
		List<String> keys = new java.util.ArrayList<>(KEYS);
		for (Object rawKey : root.keySet()) {
			if (rawKey instanceof String key && isCustom(key) && root.get(key) instanceof Map<?, ?>
				&& keys.size() - KEYS.size() < MAX_CUSTOM) {
				keys.add(key);
				layout.parts.put(key, newCustomPart(layout.body(), null));
			}
		}
		for (String key : keys) {
			if (!(root.get(key) instanceof Map<?, ?> item)) {
				continue;
			}
			Part part = layout.parts.get(key);
			part.name = cleanName(item.get("name"));
			part.visible = !Boolean.FALSE.equals(item.get("visible"));
			if (item.get("shape") instanceof String shape && (SHAPE_RECT.equals(shape) || SHAPE_DOT.equals(shape))) {
				part.shape = shape;
			}
			part.x = clampInt(item.get("x"), part.x, -MAX_OFFSET, MAX_OFFSET);
			part.y = clampInt(item.get("y"), part.y, -MAX_OFFSET, MAX_OFFSET);
			part.w = clampInt(item.get("w"), part.w, MIN_SIZE, MAX_SIZE);
			part.h = clampInt(item.get("h"), part.h, MIN_SIZE, MAX_SIZE);
			part.rotation = normalizeRotation(clampInt(item.get("rotation"), 0, -100_000, 100_000));
			part.resW = clampInt(item.get("resW"), 0, 0, MAX_RESOLUTION);
			part.resH = clampInt(item.get("resH"), 0, 0, MAX_RESOLUTION);
			if (part.resW == 0 || part.resH == 0) {
				part.resW = 0;
				part.resH = 0;
			}
			if (item.get("pivot") instanceof String pivot && PIVOTS.contains(pivot)) {
				part.pivot = pivot;
			}
			if (item.get("animations") instanceof Map<?, ?> animations) {
				for (String state : PartAnimation.STATES) {
					part.animations.put(state, PartAnimation.fromJson(animations.get(state)));
				}
			} else if (item.get("motion") instanceof Map<?, ?> legacy) {
				// 2.0.3 미리보기 5: 부위마다 움직임 하나 + "언제"
				String state = legacy.get("when") instanceof String when && PartAnimation.STATES.contains(when) ? when : PartAnimation.ALWAYS;
				part.animations.get(state).motion = PartMotion.fromJson(legacy);
			}
			// 이 부위에 넣을 수 없는 움직임은 뺀다 (예: 팔 깜빡이기)
			for (PartAnimation animation : part.animations.values()) {
				if (!PartMotion.typesFor(key).contains(animation.motion.type)) {
					animation.motion.type = PartMotion.NONE;
				}
			}
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
