package com.mypet.parts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부위 하나의 상황별 애니메이션 하나 ("모습 꾸미기 → 움직임").
 * 자세(옮기기 dx/dy, 돌리기 rotation)를 잡고, 그 위에 반복 움직임(motion)을 더한다.
 * "항상"은 자세 없이 반복 움직임만 쓴다. 계산은 bit.js 의 partTransform 과 같다.
 */
public final class PartAnimation {
	public static final String ALWAYS = "always";
	public static final String IDLE = "idle"; // 가만히 있을 때
	public static final String WALK = "walk"; // 걸을 때
	public static final String AIR = "air"; // 점프하거나 떨어질 때
	public static final String GRAB = "grab"; // 마우스로 잡았을 때
	public static final List<String> STATES = List.of(ALWAYS, IDLE, WALK, AIR, GRAB);
	/** 상황이 바뀌었을 때 자세와 움직임이 서서히 바뀌는 정도 (틱마다). */
	public static final double FADE = 0.15;

	private static final int MAX_OFFSET = 200;

	public int dx;
	public int dy;
	/** 도, 시계 방향 (부위 중심점을 기준으로 돈다). */
	public int rotation;
	public PartMotion motion = new PartMotion();

	public boolean hasPose() {
		return dx != 0 || dy != 0 || rotation != 0;
	}

	public boolean isEmpty() {
		return !hasPose() && !motion.active();
	}

	public PartAnimation copy() {
		PartAnimation copy = new PartAnimation();
		copy.dx = dx;
		copy.dy = dy;
		copy.rotation = rotation;
		copy.motion = motion.copy();
		return copy;
	}

	/** 좌우 맞추기: 반대쪽 부위에 넣을 애니메이션 (가로 방향과 회전을 거울처럼). */
	public PartAnimation mirrored() {
		PartAnimation copy = copy();
		copy.dx = -dx;
		copy.rotation = PartLayout.normalizeRotation(-rotation);
		copy.motion = motion.mirrored();
		return copy;
	}

	/** 부위 하나가 지금 어떻게 움직였는지: 옮기기 + 중심점 기준 돌리기/늘이기. */
	public record Transform(double dx, double dy, double rotation, double scaleX, double scaleY) {
		public static final Transform NONE = new Transform(0, 0, 0, 1, 1);

		public boolean isIdentity() {
			return dx == 0 && dy == 0 && rotation == 0 && scaleX == 1 && scaleY == 1;
		}
	}

	/**
	 * 상황별 켜짐 정도(weights: 상황 → 0..1)로 섞은 지금 모양.
	 * "항상"은 늘 1, 나머지는 지금 상황 쪽으로 서서히 바뀐다.
	 */
	public static Transform transform(Map<String, PartAnimation> animations, long ticks, Map<String, Double> weights) {
		double dx = 0;
		double dy = 0;
		double rotation = 0;
		double scaleX = 1;
		double scaleY = 1;
		for (String state : STATES) {
			PartAnimation animation = animations.get(state);
			double weight = ALWAYS.equals(state) ? 1.0 : weights.getOrDefault(state, 0.0);
			if (animation == null || weight <= 0) {
				continue;
			}
			if (!ALWAYS.equals(state)) {
				dx += animation.dx * weight;
				dy += animation.dy * weight;
				rotation += animation.rotation * weight;
			}
			PartMotion.Transform m = animation.motion.at(ticks, weight);
			dx += m.dx();
			dy += m.dy();
			rotation += m.rotation();
			scaleX *= m.scaleX();
			scaleY *= m.scaleY();
		}
		if (dx == 0 && dy == 0 && rotation == 0 && scaleX == 1 && scaleY == 1) {
			return Transform.NONE;
		}
		return new Transform(dx, dy, rotation, scaleX, scaleY);
	}

	/** 지금 상황 쪽으로 켜짐 정도를 한 틱 옮긴다. */
	public static void fadeWeights(Map<String, Double> weights, String state) {
		for (String s : STATES) {
			if (ALWAYS.equals(s)) {
				continue;
			}
			double target = s.equals(state) ? 1.0 : 0.0;
			double weight = weights.getOrDefault(s, 0.0);
			weight += (target - weight) * FADE;
			if (Math.abs(target - weight) < 0.01) {
				weight = target;
			}
			weights.put(s, weight);
		}
	}

	// ---------------------------------------------------------------- JSON

	public Map<String, Object> toJson() {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("dx", dx);
		item.put("dy", dy);
		item.put("rotation", rotation);
		item.put("motion", motion.toJson());
		return item;
	}

	public static PartAnimation fromJson(Object value) {
		PartAnimation animation = new PartAnimation();
		if (!(value instanceof Map<?, ?> item)) {
			return animation;
		}
		animation.dx = clamp(item.get("dx"), MAX_OFFSET);
		animation.dy = clamp(item.get("dy"), MAX_OFFSET);
		animation.rotation = PartLayout.normalizeRotation(clamp(item.get("rotation"), 100_000));
		animation.motion = PartMotion.fromJson(item.get("motion"));
		return animation;
	}

	private static int clamp(Object value, int limit) {
		if (!(value instanceof Number number) || Double.isNaN(number.doubleValue())) {
			return 0;
		}
		return (int) Math.max(-limit, Math.min(limit, Math.round(number.doubleValue())));
	}

	/** 빈 애니메이션 5개 (상황마다 하나). */
	public static Map<String, PartAnimation> emptySet() {
		Map<String, PartAnimation> map = new LinkedHashMap<>();
		for (String state : STATES) {
			map.put(state, new PartAnimation());
		}
		return map;
	}
}
