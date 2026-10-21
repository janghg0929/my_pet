package com.mypet.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JDialog;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * HTML 설정 창을 화면 밖에 (포커스를 뺏지 않게) 띄워서 화면별로 build/preview/web-*.png 로 저장.
 * JavaFX 가 있는 JDK(Liberica Full 등)에서만 실행된다.
 */
class WebSettingsPreviewTest {
	private static final String STATE = "{\"version\":\"2.0.1\",\"isMac\":false,\"petColor\":\"#4fc3f7\",\"baseColor\":\"#4fc3f7\","
		+ "\"status\":\"\",\"modes\":{\"browserMonocle\":\"auto\",\"browserIeIcon\":\"off\",\"discordColor\":\"auto\","
		+ "\"pixelHeart\":\"always\",\"headset\":\"auto\"}}";

	@Test
	void rendersEachView() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		boolean hasJavaFx;
		try {
			Class.forName("javafx.scene.web.WebView");
			hasJavaFx = true;
		} catch (ClassNotFoundException e) {
			hasJavaFx = false;
		}
		assumeTrue(hasJavaFx, "JavaFX 가 없는 JDK");

		java.util.concurrent.atomic.AtomicReference<String> sentParts = new java.util.concurrent.atomic.AtomicReference<>();
		SettingsHost host = new SettingsHost() {
			@Override
			public String settingsStateJson() {
				return STATE;
			}

			@Override
			public void setAppearanceMode(String key, String mode) {
			}

			@Override
			public void applyDefaultColor() {
			}

			@Override
			public void pickCustomColor() {
			}

			@Override
			public void setParts(String layoutJson) {
				sentParts.set(layoutJson);
			}

			@Override
			public void pickPartImage(String part, String expression) {
			}

			@Override
			public void removePartImage(String part, String expression) {
			}

			@Override
			public String importPartImage() {
				return "vsign.png"; // 사진 고르기 창 대신 고른 셈 친다
			}

			@Override
			public void resetParts() {
			}

			@Override
			public String partImageDataUrl(String fileName) {
				return null;
			}

			@Override
			public void saveLook() {
			}

			@Override
			public void applyLook(String id) {
			}

			@Override
			public void renameLook(String id) {
			}

			@Override
			public void deleteLook(String id) {
			}

			@Override
			public void closeSettings() {
			}

			@Override
			public void quit() {
			}

			@Override
			public void placeSettingsDialog(JDialog dialog) {
				dialog.setLocation(-4000, -4000);
			}
		};

		WebSettings[] settings = new WebSettings[1];
		SwingUtilities.invokeAndWait(() -> {
			Ui.installTheme();
			settings[0] = new WebSettings(null, host);
			JDialog dialog = settings[0].dialog();
			dialog.setFocusableWindowState(false);
			dialog.setLocation(-4000, -4000);
			dialog.setVisible(true);
		});
		long deadline = System.currentTimeMillis() + 15_000;
		while (!settings[0].isLoaded() && System.currentTimeMillis() < deadline) {
			Thread.sleep(100);
		}
		assertTrue(settings[0].isLoaded(), "settings.html 로드 실패");
		Thread.sleep(800);

		File out = new File("build/preview");
		assertTrue(out.isDirectory() || out.mkdirs());
		snapshot(settings[0], new File(out, "web-main.png"));
		// 사용자가 창을 옮긴 것처럼 위치를 바꿔 두고, 화면을 바꿔도 그 자리를 지키는지 본다
		java.awt.Point moved = new java.awt.Point(-3500, -3600);
		SwingUtilities.invokeAndWait(() -> settings[0].dialog().setLocation(moved));
		int[] mainBottom = new int[1];
		SwingUtilities.invokeAndWait(() -> mainBottom[0] = settings[0].dialog().getY() + settings[0].dialog().getHeight());
		settings[0].showViewForPreview("appearance");
		Thread.sleep(700);
		SwingUtilities.invokeAndWait(() -> System.out.println("after main→appearance: " + edges(settings[0].dialog())
			+ " (main 일 때 bottom 과 같아야 함: " + mainBottom[0] + ")"));
		System.out.println("appearance heights: content=" + evalScript(settings[0], "window.bitContentHeight()")
			+ " title=" + evalScript(settings[0], "document.querySelector('.titlebar').offsetHeight")
			+ " view=" + evalScript(settings[0], "document.querySelector('.view[data-view=\"appearance\"]').offsetHeight")
			+ " list=" + evalScript(settings[0], "document.getElementById('appearance-list').offsetHeight")
			+ " minHeight=" + evalScript(settings[0], "document.getElementById('appearance-list').style.minHeight"));
		snapshot(settings[0], new File(out, "web-appearance.png"));

		// 검색: 한글/영어로 앱 이름이나 외형 이름을 찾는다
		// (앱 안에서는 HTML 검색창 자리에 Swing 입력칸이 겹쳐져 있다: 한글 조합 입력 때문)
		JTextField field = settings[0].searchFieldForPreview();
		String slot = evalScript(settings[0], "var r = document.getElementById('appearance-search').getBoundingClientRect();"
			+ " Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height)");
		SwingUtilities.invokeAndWait(() -> {
			javax.swing.JWindow window = settings[0].searchWindowForPreview();
			java.awt.Point panelOnScreen = settings[0].dialog().getContentPane().getLocationOnScreen();
			System.out.println("search window visible=" + window.isVisible() + " at " + window.getBounds()
				+ " | expected panel" + panelOnScreen + " + slot(" + slot + ")");
		});
		// 창을 옮기면 검색 창도 따라오는지
		SwingUtilities.invokeAndWait(() -> settings[0].dialog().setLocation(-3400, -3550));
		Thread.sleep(300);
		SwingUtilities.invokeAndWait(() -> System.out.println("after moving dialog to (-3400,-3550): search window at "
			+ settings[0].searchWindowForPreview().getLocation() + ", panel at " + settings[0].dialog().getContentPane().getLocationOnScreen()));
		for (String[] query : new String[][] {{"chrome", "web-search-chrome.png"}, {"크", "web-search-k.png"}, {"크롬", "web-search-krom.png"},
			{"없는거", "web-search-none.png"}}) {
			SwingUtilities.invokeAndWait(() -> field.setText(query[0]));
			Thread.sleep(600);
			System.out.println("search '" + query[0] + "' → " + evalScript(settings[0],
				"Array.prototype.map.call(document.querySelectorAll('#appearance-list .option b'), function (b) { return b.textContent; }).join(', ')")
				+ " | list height=" + evalScript(settings[0], "document.getElementById('appearance-list').offsetHeight")
				+ " | dialog=" + settings[0].dialog().getSize());
			snapshot(settings[0], new File(out, query[1]));
		}
		SwingUtilities.invokeAndWait(() -> field.setText(""));
		Thread.sleep(400);

		// 페이지 나누기: 외형이 많아졌을 때(가짜 외형 70개 = 15쪽)를 흉내 내서 3, 8, 13쪽을 찍는다
		runScript(settings[0], "for (var i = 6; i <= 75; i++) { window.bitAppearances.items.push("
			+ "{ key: 'test' + i, title: '테스트 외형 ' + i, when: '미리보기용', keywords: [] }); }");
		for (int page : new int[] {3, 8, 13}) {
			runScript(settings[0], "window.bitAppearances.goToPage(" + page + ")");
			Thread.sleep(500);
			System.out.println("page " + page + " pager → " + evalScript(settings[0],
				"Array.prototype.map.call(document.querySelectorAll('#appearance-pager .page'), function (b) {"
					+ " return b.classList.contains('current') ? '[' + b.textContent + ']' : b.textContent; }).join(' ')"));
			snapshot(settings[0], new File(out, "web-page-" + page + ".png"));
		}
		runScript(settings[0], "window.bitAppearances.items.splice(5); window.bitAppearances.goToPage(1);");
		SwingUtilities.invokeAndWait(() -> { });
		// 메인으로 돌아갔다가 색깔 화면
		settings[0].showViewForPreview("back");
		runScript(settings[0], "document.querySelector('.back').click()");
		Thread.sleep(400);
		SwingUtilities.invokeAndWait(() -> System.out.println("after back to main: " + edges(settings[0].dialog())
			+ " / search window visible=" + settings[0].searchWindowForPreview().isVisible()));
		settings[0].showViewForPreview("color");
		Thread.sleep(700);
		SwingUtilities.invokeAndWait(() -> System.out.println("after main→color: " + edges(settings[0].dialog())));
		snapshot(settings[0], new File(out, "web-color.png"));

		// 색깔 화면의 걷기/점프 애니메이션: 0.3초 간격으로 20장 찍어 한 장으로 이어 붙인다
		java.util.List<java.awt.image.BufferedImage> frames = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			File frame = new File(out, "web-color-frame.png");
			snapshot(settings[0], frame);
			frames.add(javax.imageio.ImageIO.read(frame));
			Thread.sleep(300);
		}
		int cropTop = 60;
		int cropHeight = 230;
		int frameWidth = frames.get(0).getWidth();
		java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(frameWidth * 5, cropHeight * 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		for (int i = 0; i < frames.size(); i++) {
			java.awt.image.BufferedImage f = frames.get(i);
			int h = Math.min(cropHeight, f.getHeight() - cropTop);
			g.drawImage(f.getSubimage(0, cropTop, frameWidth, h), (i % 5) * frameWidth, (i / 5) * cropHeight, null);
			g.setColor(java.awt.Color.RED);
			g.drawString(String.format("%.1fs", i * 0.3), (i % 5) * frameWidth + 6, (i / 5) * cropHeight + 14);
		}
		g.dispose();
		javax.imageio.ImageIO.write(sheet, "png", new File(out, "web-color-motion.png"));

		// 모습 꾸미기: 화면을 찍고, 몸통 너비를 숫자로 바꾸면 Java 로 배치가 가는지 본다
		settings[0].showViewForPreview("back");
		runScript(settings[0], "document.querySelector('.back').click()");
		Thread.sleep(300);
		settings[0].showViewForPreview("parts");
		Thread.sleep(700);
		SwingUtilities.invokeAndWait(() -> System.out.println("after main→parts: " + edges(settings[0].dialog())));
		int partsHeight = settings[0].dialog().getHeight();
		// 모습 꾸미기는 넓은 창 (내용 540px), 다른 화면은 360px
		int partsWidth = settings[0].dialog().getWidth();
		System.out.println("parts width=" + partsWidth + " canvas=" + evalScript(settings[0], "String(document.getElementById('parts-canvas').clientWidth)"));
		assertTrue(partsWidth >= 540, "모습 꾸미기 창 너비: " + partsWidth);
		snapshot(settings[0], new File(out, "web-parts.png"));
		runScript(settings[0], "var w = document.getElementById('parts-w'); w.value = '60'; w.dispatchEvent(new Event('input'));");
		Thread.sleep(700);
		String parts = sentParts.get();
		assertTrue(parts != null && parts.contains("\"w\":60"), "setParts 가 안 왔거나 너비가 다름: " + parts);
		com.mypet.parts.PartLayout sent = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(parts));
		System.out.println("parts sent: body w=" + sent.body().w + ", rightArm x=" + sent.rightArm().x + ", rightLeg x=" + sent.rightLeg().x);
		// 몸통을 바꿔도 팔/다리는 그대로 (부위마다 따로 움직인다)
		assertTrue(sent.rightArm().x == 45 && sent.rightLeg().x == 25, "오른쪽 부위 위치: " + sent.rightArm().x + ", " + sent.rightLeg().x);
		runScript(settings[0], "document.querySelector('#parts-tabs [data-part=eyes]').click()");
		Thread.sleep(300);
		snapshot(settings[0], new File(out, "web-parts-eyes.png"));
		assertTrue(settings[0].dialog().getHeight() == partsHeight, "눈을 골라도 창 크기 그대로: " + settings[0].dialog().getHeight());

		// 회전: 몸통 20도, 왼팔 35도 (좌우 맞추기로 오른팔은 -35도), 눈 -10도
		runScript(settings[0], "function turn(part, deg) { document.querySelector('#parts-tabs [data-part=' + part + ']').click();"
			+ " var r = document.getElementById('parts-rotation'); r.value = String(deg); r.dispatchEvent(new Event('input')); }"
			+ " turn('body', 20); turn('leftArm', 35); turn('eyes', -10); turn('body', 20);");
		Thread.sleep(700);
		com.mypet.parts.PartLayout turned = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		System.out.println("rotation sent: body=" + turned.body().rotation + " leftArm=" + turned.leftArm().rotation
			+ " rightArm=" + turned.rightArm().rotation + " eyes=" + turned.eyes().rotation);
		assertTrue(turned.body().rotation == 20 && turned.leftArm().rotation == 35 && turned.rightArm().rotation == -35
			&& turned.eyes().rotation == -10, "회전 값");
		snapshot(settings[0], new File(out, "web-parts-rotated.png"));

		// 움직임: 왼팔 "공중" 자세 = 어깨 기준 60도 들기 + 흔들기(엇갈리게) → 오른팔은 거울처럼. 편집 화면은 계속 움직인다
		runScript(settings[0], "document.querySelector('#parts-tabs [data-part=leftArm]').click();"
			+ " document.querySelector('#parts-mode [data-mode=motion]').click();"
			+ " document.querySelector('#anim-states [data-key=air]').click();"
			+ " var r = document.getElementById('anim-rotation'); r.value = '60'; r.dispatchEvent(new Event('input'));"
			+ " document.querySelector('#motion-types [data-key=swing]').click();"
			+ " document.getElementById('motion-alternate').click();");
		Thread.sleep(700);
		com.mypet.parts.PartLayout moving = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		com.mypet.parts.PartAnimation leftAir = moving.leftArm().animations.get("air");
		com.mypet.parts.PartAnimation rightAir = moving.rightArm().animations.get("air");
		System.out.println("animation sent: left air rot=" + leftAir.rotation + " " + leftAir.motion.type
			+ " | right air rot=" + rightAir.rotation + " " + rightAir.motion.type + " half=" + rightAir.motion.half
			+ " pivots=" + moving.leftArm().pivot + "/" + moving.rightArm().pivot + " | " + edges(settings[0].dialog()));
		assertTrue(leftAir.rotation == 60 && rightAir.rotation == -60 && "swing".equals(rightAir.motion.type) && rightAir.motion.half,
			"공중 자세와 움직임");
		// 팔에는 깜빡이 없고, 눈에는 있다
		String blinkOnArm = evalScript(settings[0], "document.querySelector('#motion-types [data-key=blink]').hidden");
		runScript(settings[0], "document.querySelector('#parts-tabs [data-part=eyes]').click()");
		String blinkOnEyes = evalScript(settings[0], "document.querySelector('#motion-types [data-key=blink]').hidden");
		runScript(settings[0], "document.querySelector('#parts-tabs [data-part=leftArm]').click()");
		System.out.println("blink hidden on arm=" + blinkOnArm + ", on eyes=" + blinkOnEyes);
		assertTrue("true".equals(blinkOnArm) && "false".equals(blinkOnEyes), "부위별 움직임 종류");
		Thread.sleep(400);
		// 모양 탭, 움직임 탭, 움직임 있음/없음 모두 창 크기가 같아야 한다
		assertTrue(settings[0].dialog().getHeight() == partsHeight, "움직임 탭에서도 창 크기 그대로: " + settings[0].dialog().getHeight());

		// Ctrl+Z: 공중 자세·움직임 이전으로, Ctrl+Y: 다시
		java.util.function.Function<String, Integer> airRotation = json ->
			com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(json)).leftArm().animations.get("air").rotation;
		runScript(settings[0], "document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }))");
		Thread.sleep(300);
		int afterUndo = airRotation.apply(sentParts.get());
		runScript(settings[0], "document.dispatchEvent(new KeyboardEvent('keydown', { key: 'y', ctrlKey: true, bubbles: true }))");
		Thread.sleep(300);
		int afterRedo = airRotation.apply(sentParts.get());
		System.out.println("undo → air rotation " + afterUndo + ", redo → " + afterRedo
			+ " | input shows " + evalScript(settings[0], "document.getElementById('anim-rotation').value"));
		assertTrue(afterUndo == 0 && afterRedo == 60, "되돌리기/다시 하기");

		// 마우스 휠 확대 (마우스 자리를 기준으로 1.2배씩)
		runScript(settings[0], "document.querySelector('#parts-mode [data-mode=shape]').click()");
		Thread.sleep(300);
		String beforeZoomK = evalScript(settings[0], "String(window.bitPartsEditor.view.k)");
		System.out.println("wheel support: " + evalScript(settings[0],
			"typeof WheelEvent + ' onwheel=' + ('onwheel' in document.getElementById('parts-canvas'))"));
		// 실제 마우스 휠처럼 Swing 쪽에서 휠 이벤트를 보낸다 (웹 엔진이 알아서 wheel/mousewheel 로 바꾼다)
		String canvasCenter = evalScript(settings[0], "var b = document.getElementById('parts-canvas').getBoundingClientRect();"
			+ " Math.round(b.left + 100) + ',' + Math.round(b.top + 80)");
		String[] xy = canvasCenter.split(",");
		java.lang.reflect.Field panelField = WebSettings.class.getDeclaredField("panel");
		panelField.setAccessible(true);
		java.awt.Component panel = (java.awt.Component) panelField.get(settings[0]);
		SwingUtilities.invokeAndWait(() -> {
			for (int i = 0; i < 2; i++) {
				panel.dispatchEvent(new java.awt.event.MouseWheelEvent(panel, java.awt.event.MouseEvent.MOUSE_WHEEL,
					System.currentTimeMillis(), 0, Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), 0, false,
					java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, -1));
			}
		});
		Thread.sleep(300);
		String zoomBySwing = evalScript(settings[0], "String(window.bitPartsEditor.view.k)");
		// 웹 엔진 안에서 직접 휠 이벤트 보내기
		runScript(settings[0], "var c = document.getElementById('parts-canvas'); var b = c.getBoundingClientRect();"
			+ " c.dispatchEvent(new WheelEvent('wheel', { deltaY: -100, clientX: b.left + 100, clientY: b.top + 80, bubbles: true, cancelable: true }));");
		Thread.sleep(200);
		System.out.println("zoom k: start=" + beforeZoomK + " after swing wheel=" + zoomBySwing
			+ " after js wheel=" + evalScript(settings[0], "String(window.bitPartsEditor.view.k)"));
		snapshot(settings[0], new File(out, "web-parts-zoom.png"));
		assertTrue(Double.parseDouble(zoomBySwing) > Double.parseDouble(beforeZoomK) * 1.4, "마우스 휠 두 번에 1.2 × 1.2 배 확대");
		assertTrue(settings[0].dialog().getHeight() == partsHeight, "모양 탭 창 크기 그대로");
		runScript(settings[0], "document.querySelector('#parts-mode [data-mode=motion]').click()");
		Thread.sleep(300);
		String frameA = evalScript(settings[0], "document.getElementById('parts-canvas').toDataURL()");
		snapshot(settings[0], new File(out, "web-parts-motion.png"));
		Thread.sleep(250);
		String frameB = evalScript(settings[0], "document.getElementById('parts-canvas').toDataURL()");
		assertTrue(!frameA.equals(frameB), "편집 화면 미리보기가 움직여야 함");

		// 고급(시간 막대): "공중"에서 왼팔 0초 0도 → 0.4초 120도 (좌우 맞추기로 오른팔은 -120도)
		runScript(settings[0], "document.querySelector('#parts-level [data-level=advanced]').click();"
			+ " document.querySelector('#parts-tabs [data-part=leftArm]').click();"
			+ " document.querySelector('#tl-states [data-key=air]').click();"
			+ " var ed = window.bitPartsEditor; ed.tl.playhead = 0; ed.refreshControls(); document.getElementById('tl-add').click();"
			+ " ed.tl.playhead = 400; ed.refreshControls();"
			+ " var r = document.getElementById('tl-rotation'); r.value = '120'; r.dispatchEvent(new Event('input'));"
			+ " document.getElementById('tl-duration').value = '0.8'; document.getElementById('tl-duration').dispatchEvent(new Event('change'));"
			+ " document.getElementById('tl-loop').click();"
			+ " ed.tl.playhead = 200; ed.refreshControls(); ed.draw();");
		Thread.sleep(700);
		com.mypet.parts.PartLayout timed = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		com.mypet.parts.PartTimeline.Clip airClip = timed.timeline.clips.get("air");
		System.out.println("timeline sent: air duration=" + airClip.duration + " loop=" + airClip.loop
			+ " left=" + airClip.track("leftArm").size() + " keys, right last=" + airClip.track("rightArm").get(1).rotation
			+ " | middle shows " + evalScript(settings[0], "document.getElementById('tl-rotation').value")
			+ " | " + edges(settings[0].dialog()));
		assertTrue(airClip.duration == 800 && !airClip.loop && airClip.track("leftArm").size() == 2
			&& airClip.track("rightArm").get(1).rotation == -120, "고급 시간 막대 키");
		assertTrue(settings[0].dialog().getHeight() == partsHeight, "고급 탭도 창 크기 그대로: " + settings[0].dialog().getHeight());
		snapshot(settings[0], new File(out, "web-parts-advanced.png"));
		System.out.println("advanced photo buttons: " + evalScript(settings[0], "document.getElementById('tl-photo').textContent + ' / '"
			+ " + document.getElementById('tl-photo-remove').disabled + ' / transport fits=' + (document.querySelector('.tl-transport').scrollWidth <= document.querySelector('.tl-transport').clientWidth)"));
		assertTrue(evalScript(settings[0], "String(document.querySelector('.tl-transport').scrollWidth <= document.querySelector('.tl-transport').clientWidth)").equals("true"),
			"고급 줄에 사진 버튼까지 한 줄에 들어가야 함");
		// 키 사진: 0.6초에서 [사진 넣기] → (가짜) 고른 사진이 그 시간의 키에 저장되고, 이어서를 켠다
		runScript(settings[0], "var ed = window.bitPartsEditor; ed.tl.playhead = 600; ed.refreshControls();"
			+ " document.getElementById('tl-photo').click();"
			+ " document.getElementById('tl-continue').click();");
		Thread.sleep(800);
		com.mypet.parts.PartTimeline.Clip photoClip = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()))
			.timeline.clips.get("air");
		com.mypet.parts.PartTimeline.Photo photoAt = photoClip.photoAt("leftArm", 700);
		com.mypet.parts.PartTimeline.Photo mirroredPhoto = photoClip.photoAt("rightArm", 700);
		System.out.println("key photo: left=" + photoAt + " right=" + mirroredPhoto + " restart=" + photoClip.restart
			+ " | select shows " + evalScript(settings[0], "document.getElementById('tl-key-photo').selectedOptions[0].textContent"));
		assertTrue(photoAt != null && "vsign.png".equals(photoAt.name()) && mirroredPhoto.flip() && !photoClip.restart, "키 사진 / 이어서");
		snapshot(settings[0], new File(out, "web-parts-keyphoto.png"));
		System.out.println("photo buttons at key: " + evalScript(settings[0], "document.getElementById('tl-photo').textContent + ' / remove disabled=' + document.getElementById('tl-photo-remove').disabled"));
		// [사진 빼기]: 그 키의 사진을 뺀다
		runScript(settings[0], "document.getElementById('tl-photo-remove').click()");
		Thread.sleep(500);
		com.mypet.parts.PartTimeline.Photo afterRemove = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()))
			.timeline.clips.get("air").photoAt("leftArm", 700);
		System.out.println("after photo remove: " + afterRemove);
		assertTrue(afterRemove == null, "사진 빼기로 키 사진이 빠져야 함");

		// 시간 막대 휠 확대: 0.4초 근처에서 세 번 굴리면 1.25³ 배 (보이는 구간 0.8초 → 약 0.41초)
		runScript(settings[0], "var c = document.getElementById('tl-canvas'); var b = c.getBoundingClientRect();"
			+ " var x = b.left + 46 + (b.width - 54) * 0.5;"
			+ " for (var i = 0; i < 3; i++) { c.dispatchEvent(new WheelEvent('wheel', { deltaY: -100, clientX: x, clientY: b.top + 40, bubbles: true, cancelable: true })); }");
		Thread.sleep(200);
		String zoomedView = evalScript(settings[0], "var v = window.bitPartsEditor.timeView(); Math.round(v.start) + '~' + Math.round(v.start + v.span)");
		snapshot(settings[0], new File(out, "web-parts-timeline-zoom.png"));
		System.out.println("timeline zoom: visible " + zoomedView + " ms");
		String[] range = zoomedView.split("~");
		int visible = Integer.parseInt(range[1]) - Integer.parseInt(range[0]);
		assertTrue(visible > 380 && visible < 440 && Integer.parseInt(range[0]) > 150, "시간 막대 확대: " + zoomedView);
		runScript(settings[0], "var c = document.getElementById('tl-canvas'); var b = c.getBoundingClientRect();"
			+ " c.dispatchEvent(new MouseEvent('dblclick', { clientX: b.left + 100, clientY: b.top + 5, bubbles: true }));");
		Thread.sleep(100);
		assertTrue("null".equals(evalScript(settings[0], "String(window.bitPartsEditor.tl.view)")), "눈금 두 번 누르면 전체 보기");
		// 재생하면 재생 위치가 움직이고, 반복이 아니니 끝에서 멈춘다
		runScript(settings[0], "document.getElementById('tl-play').click()");
		Thread.sleep(1200);
		System.out.println("after play: playhead=" + evalScript(settings[0], "String(window.bitPartsEditor.tl.playhead)")
			+ " playing=" + evalScript(settings[0], "String(window.bitPartsEditor.tl.playing)"));
		assertTrue("800".equals(evalScript(settings[0], "String(window.bitPartsEditor.tl.playhead)")), "끝에서 멈춤");

		// 진짜 마우스로 누르기(누른 채 0.15초): 재생 중인 [▶/❚❚] 가운데를 눌러도 멈춰야 한다 (예전엔 매 프레임 글자를 바꿔서 안 멈춤)
		runScript(settings[0], "document.querySelector('#tl-states [data-key=idle]').click();"
			+ " var ed = window.bitPartsEditor; ed.tl.playhead = 0; ed.refreshControls();");
		String[][] spots = {{"center", "0.5", "0.5"}, {"top-left edge", "0.06", "0.1"}};
		for (String[] spot : spots) {
			runScript(settings[0], "if (!window.bitPartsEditor.tl.playing) { document.getElementById('tl-play').click(); }");
			Thread.sleep(300);
			String at = evalScript(settings[0], "var b = document.getElementById('tl-play').getBoundingClientRect();"
				+ " Math.round(b.left + b.width * " + spot[1] + ") + ',' + Math.round(b.top + b.height * " + spot[2] + ")");
			realClick(panel, at, 150);
			Thread.sleep(250);
			String playing = evalScript(settings[0], "String(window.bitPartsEditor.tl.playing)");
			System.out.println("real click on playing ▶ (" + spot[0] + " " + at + "): playing=" + playing);
			assertTrue("false".equals(playing), "재생 중 버튼 " + spot[0] + " 을 눌러도 멈춰야 함");
		}
		// 다른 버튼도 가장자리를 눌러서 되는지: [일반] 탭 왼쪽 위 가장자리
		String levelEdge = evalScript(settings[0], "var b = document.querySelector('#parts-level [data-level=basic]').getBoundingClientRect();"
			+ " Math.round(b.left + 2) + ',' + Math.round(b.top + 2)");
		realClick(panel, levelEdge, 150);
		Thread.sleep(250);
		String level = evalScript(settings[0], "window.bitPartsEditor.level");
		System.out.println("real click on [일반] edge " + levelEdge + ": level=" + level);
		assertTrue("basic".equals(level), "탭 가장자리를 눌러도 바뀌어야 함");
		runScript(settings[0], "document.querySelector('#parts-level [data-level=advanced]').click()");
		Thread.sleep(200);
		runScript(settings[0], "document.querySelector('#parts-level [data-level=basic]').click()");
		Thread.sleep(200);

		// 저장한 모습: 예시 모습 7개(2쪽)를 넣어 보고, 뒤로가기가 모습 꾸미기로 가는지 본다
		runScript(settings[0], "var s = " + STATE + "; s.looks = [];"
			+ " for (var i = 1; i <= 7; i++) { s.looks.push({ id: 'l' + i, name: '모습 ' + i + (i == 2 ? ' 이름이 아주 긴 모습' : ''),"
			+ " color: i % 2 ? null : '#f48fb1', parts: null }); } window.bitUpdate(s);");
		settings[0].showViewForPreview("looks");
		Thread.sleep(600);
		System.out.println("looks: " + evalScript(settings[0], "document.getElementById('title').textContent")
			+ " rows=" + evalScript(settings[0], "document.querySelectorAll('#looks-list .look').length")
			+ " count=" + evalScript(settings[0], "document.getElementById('looks-count').textContent")
			+ " | " + edges(settings[0].dialog()));
		snapshot(settings[0], new File(out, "web-looks.png"));
		runScript(settings[0], "document.querySelector('.back').click()");
		Thread.sleep(300);
		assertTrue("모습 꾸미기".equals(evalScript(settings[0], "document.getElementById('title').textContent")), "저장한 모습 → 뒤로가기는 모습 꾸미기");
		runScript(settings[0], "document.querySelector('.back').click()");
		Thread.sleep(500);
		System.out.println("back to main: " + edges(settings[0].dialog()) + " width=" + settings[0].dialog().getWidth());
		assertTrue(settings[0].dialog().getWidth() < partsWidth - 100, "메인으로 돌아가면 원래 너비");

		SwingUtilities.invokeAndWait(() -> settings[0].dialog().dispose());
		System.out.println("web preview saved: " + out.getAbsolutePath());
	}

	private static void runScript(WebSettings settings, String script) throws Exception {
		CompletableFuture<Void> done = new CompletableFuture<>();
		javafx.application.Platform.runLater(() -> {
			try {
				java.lang.reflect.Field field = WebSettings.class.getDeclaredField("engine");
				field.setAccessible(true);
				((javafx.scene.web.WebEngine) field.get(settings)).executeScript(script);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			} finally {
				done.complete(null);
			}
		});
		done.get(5, TimeUnit.SECONDS);
	}

	/** Swing 입력칸까지 포함해 창 내용 전체를 그림으로 저장 (WebView 스냅샷에는 Swing 입력칸이 안 찍힌다). */
	private static void printWholeDialog(WebSettings settings, File file) throws Exception {
		java.awt.image.BufferedImage[] image = new java.awt.image.BufferedImage[1];
		SwingUtilities.invokeAndWait(() -> {
			javax.swing.JRootPane rootPane = settings.dialog().getRootPane();
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(rootPane.getWidth(), rootPane.getHeight(),
				java.awt.image.BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics2D g = img.createGraphics();
			rootPane.printAll(g);
			g.dispose();
			image[0] = img;
		});
		javax.imageio.ImageIO.write(image[0], "png", file);
	}

	/** 실제 마우스처럼 누르고(hold ms 기다렸다) 떼기: Swing → JavaFX → 웹 엔진이 mousedown/up/click 을 만든다. */
	private static void realClick(java.awt.Component panel, String xy, int holdMs) throws Exception {
		String[] parts = xy.split(",");
		int x = Integer.parseInt(parts[0]);
		int y = Integer.parseInt(parts[1]);
		SwingUtilities.invokeAndWait(() -> {
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_MOVED,
				System.currentTimeMillis(), 0, x, y, 0, false));
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
		});
		Thread.sleep(holdMs);
		SwingUtilities.invokeAndWait(() -> {
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_RELEASED,
				System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_CLICKED,
				System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON1));
		});
	}

	private static String edges(JDialog dialog) {
		return "x=" + dialog.getX() + " top=" + dialog.getY() + " bottom=" + (dialog.getY() + dialog.getHeight()) + " height=" + dialog.getHeight();
	}

	private static String evalScript(WebSettings settings, String script) throws Exception {
		CompletableFuture<String> done = new CompletableFuture<>();
		javafx.application.Platform.runLater(() -> {
			try {
				java.lang.reflect.Field field = WebSettings.class.getDeclaredField("engine");
				field.setAccessible(true);
				done.complete(String.valueOf(((javafx.scene.web.WebEngine) field.get(settings)).executeScript(script)));
			} catch (ReflectiveOperationException | RuntimeException e) {
				done.complete("오류: " + e);
			}
		});
		return done.get(5, TimeUnit.SECONDS);
	}

	private static void snapshot(WebSettings settings, File file) throws Exception {
		SwingUtilities.invokeAndWait(() -> System.out.println(file.getName() + " dialog=" + settings.dialog().getSize()
			+ " insets=" + settings.dialog().getInsets()));
		CompletableFuture<Boolean> done = new CompletableFuture<>();
		settings.snapshotTo(file, done::complete);
		assertTrue(done.get(10, TimeUnit.SECONDS), "스냅샷 실패: " + file);
	}
}
