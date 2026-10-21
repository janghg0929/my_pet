package com.mypet.parts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부위 하나의 반복 움직임 ("모습 꾸미기 → 움직임"). 기본 움직임(걷기, 점프 등) 위에 더해진다.
 * 계산은 bit.js 의 motionTransform 과 같다 (설정 화면 미리보기와 실제 펫이 똑같이 움직이게).
 */
public final class PartMotion {
	public static final String NONE = "none";
	public static final String SWING = "swing"; // 흔들기: 가운데를 기준으로 왔다갔다 돌기
	public static final String BOB = "bob"; // 둥실: 위로 떴다 내려오기
	public static final String SWAY = "sway"; // 좌우: 옆으로 왔다갔다
	public static final String BREATHE = "breathe"; // 숨쉬기: 커졌다 작아지기
	public static final String BLINK = "blink"; // 깜빡이기: 가끔 세로로 납작해졌다 돌아오기
	public static final List<String> TYPES = List.of(NONE, SWING, BOB, SWAY, BREATHE, BLINK);

	public static final String ALWAYS = "always";
	public static final String IDLE = "idle"; // 가만히 있을 때
	public static final String WALK = "walk"; // 걸을 때
	public static final String AIR = "air"; // 점프하거나 떨어질 때
	public static final String GRAB = "grab"; // 마우스로 잡았을 때
	public static final List<String> WHEN = List.of(ALWAYS, IDLE, WALK, AIR, GRAB);

	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 10;
	/** 1초에 틱 수 (펫과 미리보기 모두 64Hz). */
	public static final double TICKS_PER_SECOND = 64.0;
	/** 조건이 바뀌었을 때 움직임이 서서히 켜지고 꺼지는 정도 (틱마다). */
	public static final double FADE = 0.12;
	private static final double BLINK_SECONDS = 0.18;

	public String type = NONE;
	/** 크기 1..10. */
	public int size = 5;
	/** 빠르기 1..10. */
	public int speed = 5;
	public String when = ALWAYS;
	/** 좌우 맞추기로 반대쪽에 복사할 때 반 박자 엇갈리게 (팔다리). */
	public boolean alternate;
	/** 좌우 맞추기로 복사된 쪽: 방향을 거울처럼 뒤집는다. */
	public boolean flip;
	/** 좌우 맞추기로 복사된 쪽: 반 박자 늦게 시작한다. */
	public boolean half;

	/** 움직임 한 순간의 모양: 옮기기(dx, dy), 더 돌리기(rotation, 도), 늘이기(scaleX, scaleY). */
	public record Transform(double dx, double dy, double rotation, double scaleX, double scaleY) {
		public static final Transform NONE = new Transform(0, 0, 0, 1, 1);

		public boolean isIdentity() {
			return dx == 0 && dy == 0 && rotation == 0 && scaleX == 1 && scaleY == 1;
		}
	}

	public boolean active() {
		return !NONE.equals(type);
	}

	public PartMotion copy() {
		PartMotion copy = new PartMotion();
		copy.type = type;
		copy.size = size;
		copy.speed = speed;
		copy.when = when;
		copy.alternate = alternate;
		copy.flip = flip;
		copy.half = half;
		return copy;
	}

	/** 좌우 맞추기: 반대쪽 부위에 넣을 움직임. */
	public PartMotion mirrored() {
		PartMotion copy = copy();
		copy.flip = !flip;
		copy.half = alternate != half;
		copy.alternate = alternate;
		return copy;
	}

	/** 이 상태(idle/walk/air/grab)에서 움직여야 하는지. */
	public boolean runsIn(String state) {
		return active() && (ALWAYS.equals(when) || when.equals(state));
	}

	/** ticks 시점의 모양. weight(0..1) 는 서서히 켜고 끄는 정도. */
	public Transform at(long ticks, double weight) {
		if (!active() || weight <= 0) {
			return Transform.NONE;
		}
		double seconds = ticks / TICKS_PER_SECOND;
		double hz = 0.15 * speed;
		double angle = 2 * Math.PI * (hz * seconds + (half ? 0.5 : 0));
		double direction = flip ? -1 : 1;
		double wave = Math.sin(angle);
		double rise = (1 - Math.cos(angle)) / 2; // 0 에서 시작해서 1 까지 올라갔다 내려온다
		return switch (type) {
			case SWING -> new Transform(0, 0, direction * size * 3 * wave * weight, 1, 1);
			case BOB -> new Transform(0, -size * rise * weight, 0, 1, 1);
			case SWAY -> new Transform(direction * size * wave * weight, 0, 0, 1, 1);
			case BREATHE -> {
				double scale = 1 + size * 0.02 * rise * weight;
				yield new Transform(0, 0, 0, scale, scale);
			}
			case BLINK -> {
				double interval = 7 - speed * 0.6;
				double into = (seconds + (half ? interval / 2 : 0)) % interval;
				if (into >= BLINK_SECONDS) {
					yield Transform.NONE;
				}
				double closed = Math.sin(Math.PI * into / BLINK_SECONDS) * (0.5 + size * 0.05);
				yield new Transform(0, 0, 0, 1, 1 - closed * weight);
			}
			default -> Transform.NONE;
		};
	}

	// ---------------------------------------------------------------- 범위 (창 크기 계산용)

	/** 가장 크게 움직였을 때의 값: [더 돌리는 각도, 위로 뜨는 px, 옆으로 가는 px, 커지는 배율]. */
	double[] extremes() {
		if (!active()) {
			return new double[] {0, 0, 0, 1};
		}
		return switch (type) {
			case SWING -> new double[] {size * 3, 0, 0, 1};
			case BOB -> new double[] {0, size, 0, 1};
			case SWAY -> new double[] {0, 0, size, 1};
			case BREATHE -> new double[] {0, 0, 0, 1 + size * 0.02};
			default -> new double[] {0, 0, 0, 1};
		};
	}

	// ---------------------------------------------------------------- JSON

	public Map<String, Object> toJson() {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("type", type);
		item.put("size", size);
		item.put("speed", speed);
		item.put("when", when);
		item.put("alternate", alternate);
		item.put("flip", flip);
		item.put("half", half);
		return item;
	}

	public static PartMotion fromJson(Object value) {
		PartMotion motion = new PartMotion();
		if (!(value instanceof Map<?, ?> item)) {
			return motion;
		}
		if (item.get("type") instanceof String type && TYPES.contains(type)) {
			motion.type = type;
		}
		if (item.get("when") instanceof String when && WHEN.contains(when)) {
			motion.when = when;
		}
		motion.size = level(item.get("size"), motion.size);
		motion.speed = level(item.get("speed"), motion.speed);
		motion.alternate = Boolean.TRUE.equals(item.get("alternate"));
		motion.flip = Boolean.TRUE.equals(item.get("flip"));
		motion.half = Boolean.TRUE.equals(item.get("half"));
		return motion;
	}

	private static int level(Object value, int fallback) {
		if (!(value instanceof Number number) || Double.isNaN(number.doubleValue())) {
			return fallback;
		}
		return (int) Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, Math.round(number.doubleValue())));
	}
}
