package com.mypet.parts;

import com.mypet.settings.Json;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartTimelineTest {
	private static PartTimeline.Key key(int t, int rotation, boolean ease) {
		PartTimeline.Key key = new PartTimeline.Key();
		key.t = t;
		key.rotation = rotation;
		key.ease = ease;
		return key;
	}

	private static PartTimeline.Clip clip(int duration, boolean loop, PartTimeline.Key... keys) {
		PartTimeline.Clip clip = new PartTimeline.Clip();
		clip.duration = duration;
		clip.loop = loop;
		clip.tracks.put(PartLayout.LEFT_ARM, new java.util.ArrayList<>(List.of(keys)));
		return clip;
	}

	@Test
	void keysAreInterpolated() {
		PartTimeline.Clip linear = clip(1000, false, key(0, 0, false), key(500, 90, false));
		assertEquals(45, linear.at(PartLayout.LEFT_ARM, 250).rotation(), 1e-9);
		PartTimeline.Clip eased = clip(1000, false, key(0, 0, true), key(500, 90, true));
		assertEquals(45, eased.at(PartLayout.LEFT_ARM, 250).rotation(), 1e-9); // 가운데는 같고
		assertTrue(eased.at(PartLayout.LEFT_ARM, 100).rotation() < linear.at(PartLayout.LEFT_ARM, 100).rotation()); // 시작은 천천히
		assertNull(eased.at(PartLayout.RIGHT_ARM, 100)); // 키 없는 부위
	}

	@Test
	void loopWrapsAndOnceHoldsLastKey() {
		PartTimeline.Clip once = clip(1000, false, key(0, 0, false), key(500, 90, false));
		assertEquals(90, once.at(PartLayout.LEFT_ARM, 800).rotation(), 1e-9);
		assertEquals(90, once.at(PartLayout.LEFT_ARM, 5000).rotation(), 1e-9); // 끝나면 멈춘다

		PartTimeline.Clip loop = clip(1000, true, key(0, 0, false), key(500, 90, false));
		assertEquals(45, loop.at(PartLayout.LEFT_ARM, 750).rotation(), 1e-9); // 마지막 키 → 다음 바퀴 첫 키
		assertEquals(45, loop.at(PartLayout.LEFT_ARM, 1250).rotation(), 1e-9); // 두 번째 바퀴
	}

	@Test
	void transformUsesStateWeightsAndElapsedTime() {
		PartTimeline timeline = new PartTimeline();
		timeline.clips.put(PartAnimation.AIR, clip(1000, false, key(0, 0, false), key(1000, 100, false)));
		Map<String, Double> weights = new HashMap<>();
		Map<String, Double> elapsed = new HashMap<>();
		assertSame(PartAnimation.Transform.NONE, timeline.transform(PartLayout.LEFT_ARM, weights, elapsed));
		weights.put(PartAnimation.AIR, 0.5);
		elapsed.put(PartAnimation.AIR, 500.0);
		assertEquals(25, timeline.transform(PartLayout.LEFT_ARM, weights, elapsed).rotation(), 1e-9); // 50도 × 반만 켜짐

		PartAnimation.Transform sum = PartTimeline.combine(new PartAnimation.Transform(1, 2, 10, 2, 1),
			new PartAnimation.Transform(3, 4, 5, 0.5, 3));
		assertEquals(4, sum.dx(), 1e-9);
		assertEquals(15, sum.rotation(), 1e-9);
		assertEquals(1, sum.scaleX(), 1e-9);
		assertEquals(3, sum.scaleY(), 1e-9);
	}

	@Test
	void jsonIsCleanedAndRoundTrips() {
		Object json = Json.read("{\"timeline\": {\"walk\": {\"duration\": 99999, \"loop\": false, \"tracks\": {"
			+ "\"leftLeg\": [{\"t\": 800, \"rotation\": 30}, {\"t\": 0, \"scaleX\": 5000}, {\"t\": 800, \"rotation\": 40}],"
			+ "\"tail\": [{\"t\": 0}]}}}}");
		PartLayout layout = PartLayout.fromJson(json);
		PartTimeline.Clip walk = layout.timeline.clips.get(PartAnimation.WALK);
		assertEquals(PartTimeline.MAX_DURATION_MS, walk.duration);
		assertFalse(walk.loop);
		List<PartTimeline.Key> keys = walk.track(PartLayout.LEFT_LEG);
		assertEquals(2, keys.size()); // 시간 순, 같은 시간은 마지막 것만
		assertEquals(0, keys.get(0).t);
		assertEquals(1000, keys.get(0).scaleX);
		assertEquals(40, keys.get(1).rotation);
		assertFalse(walk.tracks.containsKey("tail"));

		PartLayout again = PartLayout.fromJson(Json.read(Json.write(layout.toJson())));
		assertEquals(Json.write(layout.toJson()), Json.write(again.toJson()));
	}

	private static PartTimeline.Key photoKey(int t, String photo) {
		PartTimeline.Key key = key(t, 0, true);
		key.photo = photo;
		return key;
	}

	@Test
	void photoKeysSwitchPhotosUntilTheNextPhotoKey() {
		// 쉴 때 20초마다 19초~20초 브이: 0초 원래 사진, 19초 브이 사진, 반복
		PartTimeline.Clip idle = clip(20_000, true, photoKey(0, PartTimeline.BASE_PHOTO), key(5_000, 30, true), photoKey(19_000, "vsign.png"));
		assertTrue(idle.photoAt(PartLayout.LEFT_ARM, 10_000).base());
		assertEquals("vsign.png", idle.photoAt(PartLayout.LEFT_ARM, 19_500).name());
		assertTrue(idle.photoAt(PartLayout.LEFT_ARM, 20_100).base()); // 다음 바퀴
		assertEquals("vsign.png", idle.photoAt(PartLayout.LEFT_ARM, 39_000).name());
		assertNull(idle.photoAt(PartLayout.RIGHT_ARM, 19_500)); // 사진 키가 없는 부위

		// 첫 사진 키 전에는 반복이어도 원래 사진(null): 0.5초에만 넣어도 원래 ↔ 바꾼 사진이 번갈아
		PartTimeline.Clip loop = clip(1_000, true, photoKey(500, "a.png"));
		assertNull(loop.photoAt(PartLayout.LEFT_ARM, 100));
		assertEquals("a.png", loop.photoAt(PartLayout.LEFT_ARM, 700).name());
		assertNull(loop.photoAt(PartLayout.LEFT_ARM, 1_200));
		PartTimeline.Clip once = clip(1_000, false, photoKey(500, "a.png"));
		assertNull(once.photoAt(PartLayout.LEFT_ARM, 100));
		assertEquals("a.png", once.photoAt(PartLayout.LEFT_ARM, 5_000).name());

		// 반 넘게 켜진 상황의 사진이 먼저, 아니면 "항상"
		PartLayout layout = PartLayout.defaults();
		layout.timeline.clips.put(PartAnimation.IDLE, idle);
		layout.timeline.clips.put(PartAnimation.ALWAYS, clip(1_000, true, photoKey(0, "always.png")));
		java.util.Map<String, Double> weights = new java.util.HashMap<>();
		java.util.Map<String, Double> elapsed = new java.util.HashMap<>(java.util.Map.of(PartAnimation.IDLE, 19_200.0));
		assertEquals("always.png", layout.timeline.photo(PartLayout.LEFT_ARM, weights, elapsed).name());
		weights.put(PartAnimation.IDLE, 0.6);
		assertEquals("vsign.png", layout.timeline.photo(PartLayout.LEFT_ARM, weights, elapsed).name());

		// 키 사진은 지우면 안 되는 사진, 좌우 맞추기로 복사하면 뒤집혀서
		assertTrue(layout.imageFiles().contains("vsign.png"));
		layout.mirrorFrom(PartLayout.LEFT_ARM);
		PartTimeline.Photo mirrored = layout.timeline.clips.get(PartAnimation.IDLE).photoAt(PartLayout.RIGHT_ARM, 19_500);
		assertEquals("vsign.png", mirrored.name());
		assertTrue(mirrored.flip());

		// JSON: 사진, 이어서
		layout.timeline.clips.get(PartAnimation.IDLE).restart = false;
		PartLayout again = PartLayout.fromJson(Json.read(Json.write(layout.toJson())));
		assertFalse(again.timeline.clips.get(PartAnimation.IDLE).restart);
		assertEquals("vsign.png", again.timeline.clips.get(PartAnimation.IDLE).photoAt(PartLayout.LEFT_ARM, 19_500).name());
		assertEquals(Json.write(layout.toJson()), Json.write(again.toJson()));
		// 경로가 섞인 사진 이름은 버린다
		PartLayout bad = PartLayout.fromJson(Json.read("{\"timeline\": {\"idle\": {\"tracks\": {\"leftArm\": [{\"t\": 0, \"photo\": \"../x.png\"}]}}}}"));
		assertNull(bad.timeline.clips.get(PartAnimation.IDLE).track(PartLayout.LEFT_ARM).get(0).photo);
	}

	@Test
	void mirrorCopiesTrackAndWindowGrows() {
		PartLayout layout = PartLayout.defaults();
		PartTimeline.Key raised = key(300, 120, true);
		raised.dx = -3;
		layout.timeline.clips.put(PartAnimation.AIR, clip(600, false, key(0, 0, true), raised));
		layout.mirrorFrom(PartLayout.LEFT_ARM);
		List<PartTimeline.Key> right = layout.timeline.clips.get(PartAnimation.AIR).track(PartLayout.RIGHT_ARM);
		assertEquals(-120, right.get(1).rotation);
		assertEquals(3, right.get(1).dx);

		// 팔을 어깨 기준으로 120도 돌리면 창이 옆으로 넓어진다 (팔 길이만큼의 원)
		assertTrue(layout.motionBounds(0).minX() < layout.bounds(0).minX());
		assertEquals(layout.bounds(0).maxY(), PartLayout.defaults().bounds(0).maxY()); // 발 높이는 그대로
	}
}
