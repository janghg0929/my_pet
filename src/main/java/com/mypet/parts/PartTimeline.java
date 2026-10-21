package com.mypet.parts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "모습 꾸미기 → 고급": 상황마다 시간 막대(클립) 하나, 부위마다 키프레임 줄.
 * 키프레임 사이는 부드럽게(또는 일정하게) 이어진다. 상황이 시작되면 0초부터 재생하고(이어서 = 멈췄던 데서 계속),
 * 반복이면 되풀이, 아니면 마지막 키프레임 모양으로 멈춘다.
 * 키에 사진을 넣으면 그 시간부터 다음 사진 키까지 부위 사진이 바뀐다 (섞이지 않고 딱 바뀜).
 * 계산은 bit.js 의 clipAt / clipPhotoAt 과 같다.
 */
public final class PartTimeline {
	public static final int MIN_DURATION_MS = 100;
	public static final int MAX_DURATION_MS = 60_000;
	/** 키 사진: 원래(부위) 사진으로 돌아가기. */
	public static final String BASE_PHOTO = "@base";
	public static final int DEFAULT_DURATION_MS = 1_000;
	public static final int MAX_KEYS = 60;
	private static final int MAX_OFFSET = 200;
	private static final int MAX_TURN = 1_080;
	private static final int MIN_SCALE = 10;
	private static final int MAX_SCALE = 400;

	/** 키프레임 하나: 그 순간의 옮기기(dx, dy), 돌리기(rotation, 도), 가로/세로 크기(%). ease = 다음 키까지 부드럽게. */
	public static final class Key {
		public int t;
		public int dx;
		public int dy;
		public int rotation;
		public int scaleX = 100;
		public int scaleY = 100;
		public boolean ease = true;
		/** 이 시간부터 바꿀 사진 (null = 그대로, {@link #BASE_PHOTO} = 원래 사진, 아니면 parts 폴더의 파일 이름). */
		public String photo;
		/** 사진을 좌우로 뒤집어서 (좌우 맞추기로 반대쪽에 복사된 키). */
		public boolean flip;

		Key copy() {
			Key copy = new Key();
			copy.t = t;
			copy.dx = dx;
			copy.dy = dy;
			copy.rotation = rotation;
			copy.scaleX = scaleX;
			copy.scaleY = scaleY;
			copy.ease = ease;
			copy.photo = photo;
			copy.flip = flip;
			return copy;
		}

		Key mirrored() {
			Key copy = copy();
			copy.dx = -dx;
			copy.rotation = -rotation;
			copy.flip = photo != null && !BASE_PHOTO.equals(photo) && !flip;
			return copy;
		}
	}

	/** 상황 하나의 클립. */
	public static final class Clip {
		public int duration = DEFAULT_DURATION_MS;
		public boolean loop = true;
		/** true = 상황이 시작될 때마다 0초부터, false = 이어서 (그 상황이었던 시간만 쌓인다). */
		public boolean restart = true;
		/** 부위 → 시간 순 키프레임. */
		public final Map<String, List<Key>> tracks = new LinkedHashMap<>();

		Clip copy() {
			Clip copy = new Clip();
			copy.duration = duration;
			copy.loop = loop;
			copy.restart = restart;
			for (Map.Entry<String, List<Key>> entry : tracks.entrySet()) {
				List<Key> keys = new ArrayList<>();
				for (Key key : entry.getValue()) {
					keys.add(key.copy());
				}
				copy.tracks.put(entry.getKey(), keys);
			}
			return copy;
		}

		public List<Key> track(String part) {
			return tracks.getOrDefault(part, List.of());
		}

		/** 상황이 elapsedMs 만큼 지났을 때 부위의 모양 (키프레임이 없으면 null). */
		public PartAnimation.Transform at(String part, double elapsedMs) {
			List<Key> keys = track(part);
			if (keys.isEmpty()) {
				return null;
			}
			double t = loop ? elapsedMs % duration : Math.min(elapsedMs, duration);
			Key first = keys.get(0);
			Key last = keys.get(keys.size() - 1);
			if (keys.size() == 1) {
				return shape(first, first, 0);
			}
			if (t < first.t || t >= last.t) {
				if (!loop) {
					return t < first.t ? shape(first, first, 0) : shape(last, last, 0);
				}
				// 반복: 마지막 키 → (다음 바퀴의) 첫 키로 이어진다
				double span = first.t + duration - last.t;
				double into = t >= last.t ? t - last.t : t + duration - last.t;
				return shape(last, first, span <= 0 ? 0 : into / span);
			}
			for (int i = 0; i < keys.size() - 1; i++) {
				Key a = keys.get(i);
				Key b = keys.get(i + 1);
				if (t >= a.t && t < b.t) {
					return shape(a, b, b.t == a.t ? 0 : (t - a.t) / (double) (b.t - a.t));
				}
			}
			return shape(last, last, 0);
		}

		/**
		 * 상황이 elapsedMs 만큼 지났을 때 부위의 사진 (null = 원래 사진).
		 * 지금 시간보다 앞선 마지막 사진 키를 따르고, 첫 사진 키 전에는 (반복이어도) 원래 사진.
		 * 그래서 0.5초에만 사진 키를 넣어도 반복하면 원래 → 바꾼 사진 → 원래 … 로 번갈아 나온다.
		 */
		public Photo photoAt(String part, double elapsedMs) {
			Key current = null;
			double t = loop ? elapsedMs % duration : Math.min(elapsedMs, duration);
			for (Key key : track(part)) {
				if (key.photo != null && key.t <= t) {
					current = key;
				}
			}
			return current == null ? null : new Photo(current.photo, current.flip);
		}

		private static PartAnimation.Transform shape(Key a, Key b, double u) {
			double s = a.ease ? u * u * (3 - 2 * u) : u;
			return new PartAnimation.Transform(
				mix(a.dx, b.dx, s), mix(a.dy, b.dy, s), mix(a.rotation, b.rotation, s),
				mix(a.scaleX, b.scaleX, s) / 100.0, mix(a.scaleY, b.scaleY, s) / 100.0);
		}

		private static double mix(double a, double b, double s) {
			return a + (b - a) * s;
		}

		boolean isEmpty() {
			for (List<Key> keys : tracks.values()) {
				if (!keys.isEmpty()) {
					return false;
				}
			}
			return true;
		}
	}

	/** 키가 정한 부위 사진: name 이 {@link #BASE_PHOTO} 면 원래 사진. */
	public record Photo(String name, boolean flip) {
		public boolean base() {
			return BASE_PHOTO.equals(name);
		}
	}

	/** 상황 → 클립 (5개 모두 있다). */
	public final Map<String, Clip> clips = new LinkedHashMap<>();

	public PartTimeline() {
		for (String state : PartAnimation.STATES) {
			clips.put(state, new Clip());
		}
	}

	public PartTimeline copy() {
		PartTimeline copy = new PartTimeline();
		for (Map.Entry<String, Clip> entry : clips.entrySet()) {
			copy.clips.put(entry.getKey(), entry.getValue().copy());
		}
		return copy;
	}

	public boolean isEmpty() {
		for (Clip clip : clips.values()) {
			if (!clip.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** 좌우 맞추기: source 부위의 줄을 거울처럼 target 부위에 복사. */
	void mirrorTrack(String source, String target) {
		for (Clip clip : clips.values()) {
			List<Key> keys = new ArrayList<>();
			for (Key key : clip.track(source)) {
				keys.add(key.mirrored());
			}
			if (keys.isEmpty()) {
				clip.tracks.remove(target);
			} else {
				clip.tracks.put(target, keys);
			}
		}
	}

	/**
	 * 상황별 켜짐 정도와 상황별로 지난 시간으로 섞은 부위의 모양 (없으면 NONE).
	 * elapsedMs: 상황 → 그 상황이 켜진 뒤 지난 시간.
	 */
	public PartAnimation.Transform transform(String part, Map<String, Double> weights, Map<String, Double> elapsedMs) {
		double dx = 0;
		double dy = 0;
		double rotation = 0;
		double scaleX = 1;
		double scaleY = 1;
		boolean any = false;
		for (String state : PartAnimation.STATES) {
			double weight = PartAnimation.ALWAYS.equals(state) ? 1.0 : weights.getOrDefault(state, 0.0);
			if (weight <= 0) {
				continue;
			}
			PartAnimation.Transform t = clips.get(state).at(part, elapsedMs.getOrDefault(state, 0.0));
			if (t == null) {
				continue;
			}
			any = true;
			dx += t.dx() * weight;
			dy += t.dy() * weight;
			rotation += t.rotation() * weight;
			scaleX *= 1 + (t.scaleX() - 1) * weight;
			scaleY *= 1 + (t.scaleY() - 1) * weight;
		}
		return any ? new PartAnimation.Transform(dx, dy, rotation, scaleX, scaleY) : PartAnimation.Transform.NONE;
	}

	/**
	 * 지금 보일 부위 사진 (없으면 null = 원래대로). 사진은 섞을 수 없어서
	 * 반 넘게 켜진 상황의 사진이 먼저, 없으면 "항상"의 사진.
	 */
	public Photo photo(String part, Map<String, Double> weights, Map<String, Double> elapsedMs) {
		for (String state : PartAnimation.STATES) {
			if (PartAnimation.ALWAYS.equals(state) || weights.getOrDefault(state, 0.0) < 0.5) {
				continue;
			}
			Photo photo = clips.get(state).photoAt(part, elapsedMs.getOrDefault(state, 0.0));
			if (photo != null) {
				return photo;
			}
		}
		return clips.get(PartAnimation.ALWAYS).photoAt(part, elapsedMs.getOrDefault(PartAnimation.ALWAYS, 0.0));
	}

	/** 키에 쓰인 사진 파일 이름들 (지우면 안 되는 사진). */
	public java.util.Set<String> photoFiles() {
		java.util.Set<String> files = new java.util.LinkedHashSet<>();
		for (Clip clip : clips.values()) {
			for (List<Key> keys : clip.tracks.values()) {
				for (Key key : keys) {
					if (key.photo != null && !BASE_PHOTO.equals(key.photo)) {
						files.add(key.photo);
					}
				}
			}
		}
		return files;
	}

	/** 두 모양 더하기 (옮기기·돌리기는 더하고, 크기는 곱한다). */
	public static PartAnimation.Transform combine(PartAnimation.Transform a, PartAnimation.Transform b) {
		if (b.isIdentity()) {
			return a;
		}
		if (a.isIdentity()) {
			return b;
		}
		return new PartAnimation.Transform(a.dx() + b.dx(), a.dy() + b.dy(), a.rotation() + b.rotation(),
			a.scaleX() * b.scaleX(), a.scaleY() * b.scaleY());
	}

	/** 부위가 상황(always 면 "항상", 아니면 다른 상황 중 하나)에서 가장 크게 움직이는 값: [돌기, 옆, 위, 아래, 커지는 배율]. */
	double[] reach(String part, boolean always) {
		double[] most = {0, 0, 0, 0, 1};
		for (Map.Entry<String, Clip> entry : clips.entrySet()) {
			if (PartAnimation.ALWAYS.equals(entry.getKey()) != always) {
				continue;
			}
			for (Key key : entry.getValue().track(part)) {
				most[0] = Math.max(most[0], Math.abs(key.rotation));
				most[1] = Math.max(most[1], Math.abs(key.dx));
				most[2] = Math.max(most[2], Math.max(0, -key.dy));
				most[3] = Math.max(most[3], Math.max(0, key.dy));
				most[4] = Math.max(most[4], Math.max(key.scaleX, key.scaleY) / 100.0);
			}
		}
		return most;
	}

	// ---------------------------------------------------------------- JSON

	public Map<String, Object> toJson() {
		Map<String, Object> root = new LinkedHashMap<>();
		for (Map.Entry<String, Clip> entry : clips.entrySet()) {
			Clip clip = entry.getValue();
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("duration", clip.duration);
			item.put("loop", clip.loop);
			item.put("restart", clip.restart);
			Map<String, Object> tracks = new LinkedHashMap<>();
			for (Map.Entry<String, List<Key>> track : clip.tracks.entrySet()) {
				if (track.getValue().isEmpty()) {
					continue;
				}
				List<Object> keys = new ArrayList<>();
				for (Key key : track.getValue()) {
					Map<String, Object> k = new LinkedHashMap<>();
					k.put("t", key.t);
					k.put("dx", key.dx);
					k.put("dy", key.dy);
					k.put("rotation", key.rotation);
					k.put("scaleX", key.scaleX);
					k.put("scaleY", key.scaleY);
					k.put("ease", key.ease);
					k.put("photo", key.photo);
					k.put("flip", key.flip);
					keys.add(k);
				}
				tracks.put(track.getKey(), keys);
			}
			item.put("tracks", tracks);
			root.put(entry.getKey(), item);
		}
		return root;
	}

	public static PartTimeline fromJson(Object value) {
		PartTimeline timeline = new PartTimeline();
		if (!(value instanceof Map<?, ?> root)) {
			return timeline;
		}
		for (String state : PartAnimation.STATES) {
			if (!(root.get(state) instanceof Map<?, ?> item)) {
				continue;
			}
			Clip clip = timeline.clips.get(state);
			clip.duration = clamp(item.get("duration"), DEFAULT_DURATION_MS, MIN_DURATION_MS, MAX_DURATION_MS);
			clip.loop = !Boolean.FALSE.equals(item.get("loop"));
			clip.restart = !Boolean.FALSE.equals(item.get("restart"));
			if (!(item.get("tracks") instanceof Map<?, ?> tracks)) {
				continue;
			}
			for (Object rawPart : tracks.keySet()) {
				if (!(rawPart instanceof String part) || !(PartLayout.KEYS.contains(part) || PartLayout.isCustom(part))
					|| !(tracks.get(part) instanceof List<?> list)) {
					continue;
				}
				List<Key> keys = new ArrayList<>();
				for (Object raw : list) {
					if (!(raw instanceof Map<?, ?> k) || keys.size() >= MAX_KEYS) {
						continue;
					}
					Key key = new Key();
					key.t = clamp(k.get("t"), 0, 0, clip.duration);
					key.dx = clamp(k.get("dx"), 0, -MAX_OFFSET, MAX_OFFSET);
					key.dy = clamp(k.get("dy"), 0, -MAX_OFFSET, MAX_OFFSET);
					key.rotation = clamp(k.get("rotation"), 0, -MAX_TURN, MAX_TURN);
					key.scaleX = clamp(k.get("scaleX"), 100, MIN_SCALE, MAX_SCALE);
					key.scaleY = clamp(k.get("scaleY"), 100, MIN_SCALE, MAX_SCALE);
					key.ease = !Boolean.FALSE.equals(k.get("ease"));
					if (BASE_PHOTO.equals(k.get("photo"))) {
						key.photo = BASE_PHOTO;
					} else {
						key.photo = PartLayout.safeFileName(k.get("photo"));
					}
					key.flip = key.photo != null && !key.photo.equals(BASE_PHOTO) && Boolean.TRUE.equals(k.get("flip"));
					keys.add(key);
				}
				keys.sort(Comparator.comparingInt(k -> k.t));
				// 같은 시간에 키가 여럿이면 마지막 것만
				List<Key> unique = new ArrayList<>();
				for (Key key : keys) {
					if (!unique.isEmpty() && unique.get(unique.size() - 1).t == key.t) {
						unique.set(unique.size() - 1, key);
					} else {
						unique.add(key);
					}
				}
				if (!unique.isEmpty()) {
					clip.tracks.put(part, unique);
				}
			}
		}
		return timeline;
	}

	private static int clamp(Object value, int fallback, int min, int max) {
		if (!(value instanceof Number number) || Double.isNaN(number.doubleValue())) {
			return fallback;
		}
		return (int) Math.max(min, Math.min(max, Math.round(number.doubleValue())));
	}
}
