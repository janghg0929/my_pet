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
	void runsOnlyInChosenState() {
		PartMotion m = motion(PartMotion.SWAY, 3, 3);
		m.when = PartMotion.WALK;
		assertTrue(m.runsIn(PartMotion.WALK));
		assertFalse(m.runsIn(PartMotion.IDLE));
		m.when = PartMotion.ALWAYS;
		assertTrue(m.runsIn(PartMotion.GRAB));
		assertFalse(new PartMotion().runsIn(PartMotion.IDLE));
	}

	@Test
	void jsonIsCheckedAndMirroredByLayout() {
		PartMotion read = PartMotion.fromJson(Json.read("{\"type\": \"dance\", \"size\": 99, \"speed\": -3, \"when\": \"walk\"}"));
		assertEquals(PartMotion.NONE, read.type);
		assertEquals(10, read.size);
		assertEquals(1, read.speed);
		assertEquals(PartMotion.WALK, read.when);

		PartLayout layout = PartLayout.defaults();
		layout.leftLeg().motion = motion(PartMotion.BOB, 3, 7);
		layout.mirrorFrom(PartLayout.LEFT_LEG);
		assertEquals(PartMotion.BOB, layout.rightLeg().motion.type);
		PartLayout again = PartLayout.fromJson(Json.read(Json.write(layout.toJson())));
		assertEquals(Json.write(layout.toJson()), Json.write(again.toJson()));
	}

	@Test
	void motionWidensWindowButNotFeet() {
		PartLayout layout = PartLayout.defaults();
		layout.body().motion = motion(PartMotion.BOB, 6, 5);
		layout.leftArm().motion = motion(PartMotion.SWAY, 4, 5);
		PartLayout.Bounds still = layout.bounds(0);
		PartLayout.Bounds moving = layout.motionBounds(0);
		assertEquals(-6, moving.minY()); // 몸통이 6px 뜬다
		assertEquals(-13, moving.minX()); // 왼팔이 왼쪽으로 4px 더
		assertEquals(still.maxY(), moving.maxY()); // 발 높이는 그대로
	}
}
