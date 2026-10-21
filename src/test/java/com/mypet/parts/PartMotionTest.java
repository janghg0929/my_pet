package com.mypet.parts;

import com.mypet.settings.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartMotionTest {
	private static PartMotion motion(String type, int size, int speed) {
		PartMotion m = new PartMotion();
		m.type = type;
		m.size = size;
		m.speed = speed;
		return m;
	}

	@Test
	void transformsFollowTheWave() {
		// 빠르기 5 = 0.75Hz → 한 바퀴 64/0.75 틱. 1/4 바퀴에서 sin = 1
		long quarter = Math.round(64 / 0.75 / 4);
		PartMotion swing = motion(PartMotion.SWING, 5, 5);
		assertEquals(15, swing.at(quarter, 1).rotation(), 0.2);
		assertEquals(7.5, swing.at(quarter, 0.5).rotation(), 0.1);
		assertSame(PartMotion.Transform.NONE, swing.at(quarter, 0));

		PartMotion bob = motion(PartMotion.BOB, 4, 5);
		assertEquals(0, bob.at(0, 1).dy(), 1e-9); // 처음엔 제자리
		assertEquals(-4, bob.at(quarter * 2, 1).dy(), 0.05); // 반 바퀴에서 가장 높이

		PartMotion breathe = motion(PartMotion.BREATHE, 10, 5);
		assertEquals(1.2, breathe.at(quarter * 2, 1).scaleX(), 0.01);

		PartMotion blink = motion(PartMotion.BLINK, 10, 5);
		assertTrue(blink.at(6, 1).scaleY() < 0.2); // 깜빡이는 중
		assertTrue(blink.at(64, 1).isIdentity()); // 평소
	}

	@Test
	void mirroredMotionFlipsAndAlternates() {
		PartMotion left = motion(PartMotion.SWING, 5, 5);
		long quarter = Math.round(64 / 0.75 / 4);
		PartMotion same = left.mirrored();
		assertEquals(-left.at(quarter, 1).rotation(), same.at(quarter, 1).rotation(), 1e-9);

		left.alternate = true;
		PartMotion alternate = left.mirrored();
		assertTrue(alternate.half);
		// 반 박자 늦고 방향도 반대 → 화면에서는 같은 쪽으로 기운다 (엇갈려 휘젓기)
		assertEquals(left.at(quarter, 1).rotation(), alternate.at(quarter, 1).rotation(), 1e-9);
	}

	@Test
	void statePoseFadesInAndAlwaysStaysOn() {
		java.util.Map<String, PartAnimation> animations = PartAnimation.emptySet();
		animations.get(PartAnimation.AIR).rotation = 90;
		animations.get(PartAnimation.AIR).dy = -4;
		animations.get(PartAnimation.ALWAYS).motion = motion(PartMotion.SWAY, 3, 5);
		animations.get(PartAnimation.ALWAYS).rotation = 45; // "항상"의 자세는 쓰지 않는다
		java.util.Map<String, Double> weights = new java.util.HashMap<>();

		// 쉴 때: 공중 자세 없음, 항상 움직임만
		PartAnimation.fadeWeights(weights, PartAnimation.IDLE);
		assertEquals(0, PartAnimation.transform(animations, 0, weights).rotation(), 1e-9);
		// 공중으로 바뀌면 서서히 → 결국 다 든다
		PartAnimation.fadeWeights(weights, PartAnimation.AIR);
		double first = PartAnimation.transform(animations, 0, weights).rotation();
		assertTrue(first > 0 && first < 30, "한 틱 만에 다 들지 않는다: " + first);
		for (int i = 0; i < 60; i++) {
			PartAnimation.fadeWeights(weights, PartAnimation.AIR);
		}
		PartAnimation.Transform air = PartAnimation.transform(animations, 0, weights);
		assertEquals(90, air.rotation(), 1e-9);
		assertEquals(-4, air.dy(), 1e-9);
	}

	@Test
	void jsonIsCheckedAndMirroredByLayout() {
		PartMotion read = PartMotion.fromJson(Json.read("{\"type\": \"dance\", \"size\": 99, \"speed\": -3}"));
		assertEquals(PartMotion.NONE, read.type);
		assertEquals(10, read.size);
		assertEquals(1, read.speed);

		PartLayout layout = PartLayout.defaults();
		PartAnimation walk = layout.leftLeg().animations.get(PartAnimation.WALK);
		walk.motion = motion(PartMotion.BOB, 3, 7);
		walk.dx = 2;
		walk.rotation = 20;
		layout.mirrorFrom(PartLayout.LEFT_LEG);
		PartAnimation mirrored = layout.rightLeg().animations.get(PartAnimation.WALK);
		assertEquals(PartMotion.BOB, mirrored.motion.type);
		assertEquals(-2, mirrored.dx);
		assertEquals(-20, mirrored.rotation);
		assertEquals(PartLayout.PIVOT_LEFT, layout.rightArm().pivot);
		PartLayout again = PartLayout.fromJson(Json.read(Json.write(layout.toJson())));
		assertEquals(Json.write(layout.toJson()), Json.write(again.toJson()));
	}

	@Test
	void eachPartOnlyKeepsItsOwnMotions() {
		assertTrue(PartMotion.typesFor(PartLayout.EYES).contains(PartMotion.BLINK));
		assertFalse(PartMotion.typesFor(PartLayout.LEFT_ARM).contains(PartMotion.BLINK));
		assertFalse(PartMotion.typesFor(PartLayout.LEFT_LEG).contains(PartMotion.BREATHE));
		assertTrue(PartMotion.typesFor(PartLayout.BODY).contains(PartMotion.BREATHE));
		// 팔 깜빡이기는 읽을 때 빠진다
		PartLayout layout = PartLayout.fromJson(Json.read("{\"leftArm\": {\"animations\": {\"air\": {\"motion\": {\"type\": \"blink\"}}}},"
			+ " \"eyes\": {\"animations\": {\"idle\": {\"motion\": {\"type\": \"blink\"}}}}}"));
		assertEquals(PartMotion.NONE, layout.leftArm().animations.get(PartAnimation.AIR).motion.type);
		assertEquals(PartMotion.BLINK, layout.eyes().animations.get(PartAnimation.IDLE).motion.type);
	}

	@Test
	void legacyMotionMovesToItsState() {
		PartLayout layout = PartLayout.fromJson(Json.read("{\"leftArm\": {\"motion\": {\"type\": \"swing\", \"when\": \"walk\", \"size\": 7}}}"));
		assertEquals(PartMotion.SWING, layout.leftArm().animations.get(PartAnimation.WALK).motion.type);
		assertEquals(7, layout.leftArm().animations.get(PartAnimation.WALK).motion.size);
		assertEquals(PartMotion.NONE, layout.leftArm().animations.get(PartAnimation.ALWAYS).motion.type);
	}

	@Test
	void pivotsDefaultToJointsAndRotatePose() {
		PartLayout layout = PartLayout.defaults();
		assertEquals(PartLayout.PIVOT_RIGHT, layout.leftArm().pivot); // 어깨
		assertEquals(PartLayout.PIVOT_TOP, layout.leftLeg().pivot); // 엉덩이
		double[] shoulder = layout.leftArm().pivotPoint();
		assertEquals(-1, shoulder[0], 1e-9); // 왼팔 오른쪽 가장자리
		assertEquals(18, shoulder[1], 1e-9);
	}

	@Test
	void animationWidensWindowButNotFeet() {
		PartLayout layout = PartLayout.defaults();
		layout.body().animations.get(PartAnimation.ALWAYS).motion = motion(PartMotion.BOB, 6, 5);
		layout.leftArm().animations.get(PartAnimation.IDLE).motion = motion(PartMotion.SWAY, 4, 5);
		PartLayout.Bounds still = layout.bounds(0);
		PartLayout.Bounds moving = layout.motionBounds(0);
		assertEquals(-6, moving.minY()); // 몸통이 6px 뜬다
		assertEquals(-13, moving.minX()); // 왼팔이 왼쪽으로 4px 더
		assertEquals(still.maxY(), moving.maxY()); // 발 높이는 그대로

		// 팔을 어깨 기준으로 돌리면 팔 길이만큼의 원 안
		layout.leftArm().animations.get(PartAnimation.AIR).rotation = 90;
		assertTrue(layout.motionBounds(0).minY() <= 18 - 8);
	}
}
