package com.mypet.parts;

import com.mypet.settings.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartLayoutTest {
	@Test
	void defaultsMatchOriginalBit() {
		PartLayout layout = PartLayout.defaults();
		PartLayout.Bounds bounds = layout.bounds(0);
		assertEquals(-9, bounds.minX());
		assertEquals(53, bounds.maxX());
		assertEquals(0, bounds.minY());
		assertEquals(37, bounds.maxY());
		// 팔을 몸통 위로 넘게 들면 그만큼 위로 넓어진다
		assertEquals(0, layout.bounds(8).minY());
		assertEquals(-6, layout.bounds(20).minY());
	}

	@Test
	void defaultArmsAreMirroredButLegsAreOnePixelOff() {
		PartLayout layout = PartLayout.defaults();
		layout.mirrorFrom(PartLayout.LEFT_ARM);
		assertEquals(45, layout.rightArm().x);
		// 원래 비트의 다리는 1px 비대칭 (설정 화면은 몸통을 바꿀 때 다시 거울 복사하지 않는다)
		layout.mirrorFrom(PartLayout.LEFT_LEG);
		assertEquals(26, layout.rightLeg().x);
		assertEquals(25, PartLayout.defaults().rightLeg().x);
	}

	@Test
	void mirrorCopiesAroundBodyCenterWithFlippedImage() {
		PartLayout layout = PartLayout.defaults();
		PartLayout.Part left = layout.leftArm();
		left.x = -14;
		left.y = 10;
		left.w = 12;
		left.h = 6;
		left.image = "arm.png";
		layout.mirrorFrom(PartLayout.LEFT_ARM);
		PartLayout.Part right = layout.rightArm();
		assertEquals(46, right.x); // 44 - (-14 + 12)
		assertEquals(10, right.y);
		assertEquals(12, right.w);
		assertEquals(6, right.h);
		assertEquals("arm.png", right.image);
		assertTrue(right.flip);

		// 다시 반대로 복사하면 원래 사진 방향으로 돌아온다
		layout.mirrorFrom(PartLayout.RIGHT_ARM);
		assertEquals(-14, layout.leftArm().x);
		assertFalse(layout.leftArm().flip);
	}

	@Test
	void jsonRoundTripKeepsEverything() {
		PartLayout layout = PartLayout.defaults();
		layout.symmetric = false;
		layout.body().w = 60;
		layout.body().image = "body_1.png";
		layout.rightLeg().image = "leg-2.png";
		layout.rightLeg().flip = true;
		layout.eyes().images.put(PartLayout.EXPR_GRAB, "eyes-grab.png");

		String text = Json.write(layout.toJson());
		PartLayout read = PartLayout.fromJson(Json.read(text));
		assertEquals(text, Json.write(read.toJson()));
		assertFalse(read.symmetric);
		assertEquals(Set.of("body_1.png", "leg-2.png", "eyes-grab.png"), read.imageFiles());
		assertEquals("eyes-grab.png", read.eyeImage(PartLayout.EXPR_GRAB));
		assertNull(read.eyeImage(PartLayout.EXPR_FALL));
	}

	@Test
	void badValuesAreClampedOrIgnored() {
		Object json = Json.read("""
				{"body": {"x": 9999, "y": -9999, "w": 0, "h": "big", "image": "../../evil.png"},
				 "leftArm": {"image": "C:\\\\Windows\\\\a.png", "flip": true},
				 "eyes": {"images": {"neutral": "ok.png", "grab": "no/slash.png"}},
				 "symmetric": "yes"}
				""");
		PartLayout layout = PartLayout.fromJson(json);
		assertEquals(400, layout.body().x);
		assertEquals(-400, layout.body().y);
		assertEquals(2, layout.body().w);
		assertEquals(28, layout.body().h);
		assertNull(layout.body().image);
		assertNull(layout.leftArm().image);
		assertFalse(layout.leftArm().flip);
		assertEquals("ok.png", layout.eyeImage(PartLayout.EXPR_NEUTRAL));
		assertNull(layout.eyeImage(PartLayout.EXPR_GRAB));
		assertTrue(layout.symmetric);

		// 값이 아예 없거나 엉뚱하면 기본 비트
		assertEquals(Json.write(PartLayout.defaults().toJson()), Json.write(PartLayout.fromJson("nope").toJson()));
		assertEquals(Json.write(PartLayout.defaults().toJson()), Json.write(PartLayout.fromJson(Map.of()).toJson()));
	}

	@Test
	void importedImagesAreShrunkAndUnusedOnesDeleted(@TempDir Path dir) throws Exception {
		File source = dir.resolve("photo.png").toFile();
		ImageIO.write(new BufferedImage(1000, 500, BufferedImage.TYPE_INT_ARGB), "png", source);
		PartImages images = new PartImages(dir.resolve("parts"));

		String keep = images.importImage(source);
		String drop = images.importImage(source);
		assertNotNull(PartLayout.safeFileName(keep));
		BufferedImage stored = images.get(keep);
		assertEquals(PartImages.MAX_SIDE, stored.getWidth());
		assertEquals(PartImages.MAX_SIDE / 2, stored.getHeight());
		assertTrue(images.dataUrl(keep).startsWith("data:image/png;base64,"));

		images.deleteUnused(Set.of(keep));
		assertTrue(Files.exists(dir.resolve("parts").resolve(keep)));
		assertFalse(Files.exists(dir.resolve("parts").resolve(drop)));
	}
}
