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
	void rotationMirrorsBoundsAndNormalizes() {
		PartLayout layout = PartLayout.defaults();
		layout.leftArm().rotation = 30;
		layout.mirrorFrom(PartLayout.LEFT_ARM);
		assertEquals(-30, layout.rightArm().rotation);

		// 44x28 몸통을 90도 돌리면 28x44 로 서 있다 (가운데 22,14 기준)
		PartLayout body = PartLayout.defaults();
		body.body().rotation = 90;
		PartLayout.Bounds b = body.bounds(0);
		assertEquals(-9, b.minX()); // 왼팔이 더 바깥
		assertEquals(-8, b.minY()); // 14 - 22
		assertEquals(37, b.maxY()); // 다리 (36 보다 다리 37 이 더 아래)

		assertEquals(180, PartLayout.normalizeRotation(-180));
		assertEquals(-90, PartLayout.normalizeRotation(270));
		assertEquals(10, PartLayout.normalizeRotation(370));
		Object json = com.mypet.settings.Json.read("{\"body\": {\"rotation\": 405}, \"eyes\": {\"rotation\": \"x\"}}");
		assertEquals(45, PartLayout.fromJson(json).body().rotation);
		assertEquals(0, PartLayout.fromJson(json).eyes().rotation);
	}

	@Test
	void customPartsRoundTripAndHiddenPartsDoNotCount() {
		Object json = Json.read("{\"c_tail1\": {\"name\": \"  꼬리   \", \"x\": 40, \"y\": 20, \"w\": 6, \"h\": 3},"
			+ " \"c_bad/../x\": {\"name\": \"나쁨\"}, \"leftArm\": {\"visible\": false},"
			+ " \"timeline\": {\"idle\": {\"tracks\": {\"c_tail1\": [{\"t\": 0, \"rotation\": 30}], \"tail\": [{\"t\": 0}]}}}}");
		PartLayout layout = PartLayout.fromJson(json);
		assertEquals(java.util.List.of("c_tail1"), layout.customKeys());
		PartLayout.Part tail = layout.get("c_tail1");
		assertEquals("꼬리", tail.name);
		assertEquals(PartLayout.SHAPE_DOT, tail.shape); // 만든 부위는 기본이 검은 점
		assertTrue(tail.visible);
		assertEquals("꼬리", layout.nameOf("c_tail1"));
		assertEquals("왼팔", layout.nameOf(PartLayout.LEFT_ARM));
		assertFalse(layout.leftArm().visible);
		assertEquals(1, layout.timeline.clips.get(PartAnimation.IDLE).track("c_tail1").size());
		assertTrue(PartMotion.typesFor("c_tail1").contains(PartMotion.BLINK)); // 만든 부위는 움직임 다 된다

		// 화면에서 뺀 왼팔은 크기 계산에서 빠진다 (왼팔이 맨 왼쪽이었다)
		assertEquals(0, layout.bounds(0).minX());
		assertEquals(53, layout.bounds(0).maxX()); // 오른팔 45+8 (꼬리 40+6 보다 바깥)
		PartLayout again = PartLayout.fromJson(Json.read(Json.write(layout.toJson())));
		assertEquals(Json.write(layout.toJson()), Json.write(again.toJson()));

		// 새로 만들기: 몸통 가운데 4x4 점
		PartLayout.Part made = PartLayout.newCustomPart(layout.body(), "점");
		assertEquals(20, made.x);
		assertEquals(12, made.y);
		assertEquals(4, made.w);
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
		assertEquals(2000, layout.body().x);
		assertEquals(-2000, layout.body().y);
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
	void pixelArtKeepsSizeAndBodyColorCellsFollowTheColor(@TempDir Path dir) throws Exception {
		BufferedImage drawn = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
		drawn.setRGB(0, 0, 0xFF000000); // 검은 칸
		drawn.setRGB(1, 0, (PartImages.BODY_ALPHA << 24)); // 몸 색 칸
		PartImages images = new PartImages(dir.resolve("parts"));
		String name = images.importPixelArt(drawn);
		assertTrue(PartImages.isPixelArt(name) && PartLayout.safeFileName(name) != null);
		// 파일로 저장했다 다시 읽어도 몸 색 칸 표시(알파 254)가 남는다
		BufferedImage read = new PartImages(dir.resolve("parts")).get(name);
		assertEquals(4, read.getWidth());
		assertEquals(PartImages.BODY_ALPHA, read.getRGB(1, 0) >>> 24);
		BufferedImage blue = images.tinted(name, "#4fc3f7");
		assertEquals(0xFF4FC3F7, blue.getRGB(1, 0));
		assertEquals(0xFF000000, blue.getRGB(0, 0));
		assertEquals(0, blue.getRGB(2, 1) >>> 24); // 빈칸은 투명 그대로
		assertEquals(0xFFF48FB1, images.tinted(name, "#f48fb1").getRGB(1, 0));
	}

	@Test
	void importedImagesAreShrunkAndUnusedOnesDeleted(@TempDir Path dir) throws Exception {
		File source = dir.resolve("photo.png").toFile();
		ImageIO.write(new BufferedImage(4000, 2000, BufferedImage.TYPE_INT_ARGB), "png", source); // 아주 큰 사진만 줄인다
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

	@Test
	void paintResolutionRoundTripsAndMirrors() {
		PartLayout layout = PartLayout.defaults();
		layout.leftArm().resW = 32;
		layout.leftArm().resH = 24;
		layout.mirrorFrom(PartLayout.LEFT_ARM);
		assertEquals(32, layout.rightArm().resW);
		PartLayout back = PartLayout.fromJson(Json.read(Json.write(layout.toJson())));
		assertEquals(32, back.leftArm().resW);
		assertEquals(24, back.leftArm().resH);
		assertEquals(0, back.body().resW);
		// 너무 크면 256, 한쪽만 있으면 없앤다
		Map<String, Object> json = layout.toJson();
		@SuppressWarnings("unchecked")
		Map<String, Object> body = (Map<String, Object>) json.get(PartLayout.BODY);
		body.put("resW", 5000);
		body.put("resH", 300);
		@SuppressWarnings("unchecked")
		Map<String, Object> eyes = (Map<String, Object>) json.get(PartLayout.EYES);
		eyes.put("resW", 10);
		eyes.put("resH", 0);
		PartLayout clamped = PartLayout.fromJson(json);
		assertEquals(PartLayout.MAX_RESOLUTION, clamped.body().resW);
		assertEquals(PartLayout.MAX_RESOLUTION, clamped.body().resH);
		assertEquals(0, clamped.get(PartLayout.EYES).resW);
	}
}
