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
		java.util.concurrent.atomic.AtomicReference<String> askedText = new java.util.concurrent.atomic.AtomicReference<>();
		java.util.concurrent.atomic.AtomicReference<String> drawnImage = new java.util.concurrent.atomic.AtomicReference<>();
		java.util.concurrent.atomic.AtomicBoolean shortcutAsked = new java.util.concurrent.atomic.AtomicBoolean();
		SettingsHost host = new SettingsHost() {
			@Override
			public void createDesktopShortcut() {
				shortcutAsked.set(true);
			}

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
			public String saveDrawing(String dataUrl) {
				drawnImage.set(dataUrl);
				return "px_drawtest.png";
			}

			@Override
			public String askText(String title, String initial) {
				return askedText.get(); // 테스트에서 정한 대답
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
		// 글꼴: 앱에 든 달서힐링체 (깔려 있지 않아도). 글자 폭이 기본 글꼴과 다르면 그 글꼴로 그린 것
		String fontCheck = evalScript(settings[0], "var c = document.createElement('canvas').getContext('2d'); var t = '비트 모습 꾸미기 abc 123';"
			+ " c.font = '20px DalseoHealing, monospace'; var a = c.measureText(t).width; c.font = '20px monospace'; var b = c.measureText(t).width;"
			+ " var s = document.createElement('span'); s.textContent = t; s.style.fontSize = '20px'; document.body.appendChild(s); var w1 = s.offsetWidth;"
			+ " s.style.fontFamily = 'monospace'; var w2 = s.offsetWidth; document.body.removeChild(s); a + ' ' + b + ' | body ' + w1 + ' ' + w2");
		System.out.println("font check (healing vs monospace): " + fontCheck);
		String[] widths = fontCheck.split(" ");
		assertTrue(!widths[0].equals(widths[1]) && !widths[4].equals(widths[5]), "달서힐링체가 쓰여야 함: " + fontCheck);
		// [바로가기 만들기]: 윈도우 설치본(canShortcut)일 때만 보이고, 누르면 Java 에 부탁, 알림 줄에 결과
		String hiddenAtFirst = evalScript(settings[0], "String(document.getElementById('desktop-shortcut').hidden)");
		runScript(settings[0], ""
			+ " window.bitUpdate(Object.assign(" + STATE + ", { canShortcut: true, status: '바탕 화면에 MY_PET 바로가기를 만들었어요' }));");
		Thread.sleep(300);
		String shownInfo = evalScript(settings[0], "String(document.getElementById('desktop-shortcut').hidden) + '|' + document.getElementById('main-status').textContent");
		snapshot(settings[0], new File(out, "web-main-shortcut.png"));
		runScript(settings[0], "document.getElementById('desktop-shortcut').click()");
		Thread.sleep(300);
		System.out.println("desktop shortcut button: hidden at first=" + hiddenAtFirst + " shown=" + shownInfo + " asked=" + shortcutAsked.get());
		assertTrue("true".equals(hiddenAtFirst) && shownInfo.startsWith("false|바탕 화면에") && shortcutAsked.get(), "바로가기 만들기 버튼: " + shownInfo);
		runScript(settings[0], "document.getElementById('main-status').textContent = ''; window.bitUpdate(" + STATE + ");");
		Thread.sleep(300);
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

		// 부위 추가: [＋ 부위] → [새로 만들기] → 이름 "꼬리" (Swing 입력 창 대신 테스트가 대답) → 검은 점이 화면에
		askedText.set("꼬리");
		runScript(settings[0], "document.getElementById('parts-add').click(); document.getElementById('parts-new').click();");
		Thread.sleep(700);
		snapshot(settings[0], new File(out, "web-parts-custom.png"));
		com.mypet.parts.PartLayout withTail = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		String tailKey = withTail.customKeys().isEmpty() ? null : withTail.customKeys().get(0);
		System.out.println("custom part: " + tailKey + " name=" + (tailKey == null ? null : withTail.nameOf(tailKey))
			+ " tabs=" + evalScript(settings[0], "Array.prototype.map.call(document.querySelectorAll('#parts-tabs .seg'), function (b) { return b.textContent; }).join(',')")
			+ " | " + edges(settings[0].dialog()));
		assertTrue(tailKey != null && "꼬리".equals(withTail.nameOf(tailKey)) && com.mypet.parts.PartLayout.SHAPE_DOT.equals(withTail.get(tailKey).shape)
			&& withTail.get(tailKey).w == 24 && withTail.get(tailKey).h == 24,
			"새 부위 만들기");
		assertTrue(settings[0].dialog().getHeight() == partsHeight, "부위를 더해도 창 크기 그대로: " + settings[0].dialog().getHeight());
		// 만든 부위 우클릭 → [다른 이름으로 저장] → "꼬리 2" 복사본이 부위 목록에 (화면에는 안 나옴), 기본 부위 메뉴에는 저장 칸 없음
		String bodySave = evalScript(settings[0], "var b = document.querySelector('#parts-tabs [data-part=body]'); var r = b.getBoundingClientRect();"
			+ " b.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 2, clientX: r.left + 5, clientY: r.top + 5 }));"
			+ " var h = String(document.getElementById('part-menu-save').hidden); window.bitPartsEditor.closePartMenu(); h");
		String tailSave = evalScript(settings[0], "var b = document.querySelector('#parts-tabs [data-part=" + tailKey + "]'); var r = b.getBoundingClientRect();"
			+ " b.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 2, clientX: r.left + 5, clientY: r.top + 5 }));"
			+ " String(document.getElementById('part-menu-save').hidden)");
		Thread.sleep(300);
		snapshot(settings[0], new File(out, "web-parts-save-menu.png"));
		askedText.set("꼬리 2");
		runScript(settings[0], "document.getElementById('part-menu-save-as').click()");
		Thread.sleep(500);
		com.mypet.parts.PartLayout withCopy = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		String copyKey = withCopy.customKeys().stream().filter(k -> "꼬리 2".equals(withCopy.nameOf(k))).findFirst().orElse(null);
		String saveStatus = evalScript(settings[0], "document.getElementById('parts-status').textContent");
		System.out.println("save menu: body hidden=" + bodySave + " tail hidden=" + tailSave + " copy=" + copyKey + " status=" + saveStatus);
		assertTrue("true".equals(bodySave) && "false".equals(tailSave) && copyKey != null && !withCopy.get(copyKey).visible
			&& withCopy.get(copyKey).w == withCopy.get(tailKey).w && withCopy.get(tailKey).visible && saveStatus.contains("꼬리 2"),
			"다른 이름으로 저장: " + copyKey + " " + saveStatus);
		// 복사본은 지워 둔다 (아래 시험은 꼬리 하나만 있다고 본다)
		runScript(settings[0], "window.bitPartsEditor.deletePart('" + copyKey + "'); document.getElementById('parts-status').textContent = '';");
		Thread.sleep(400);
		assertTrue(com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).customKeys().size() == 1, "복사본 지우기");
		// [빼기] → 가져오기 목록에 [가져오기] → 다시 넣기, ⋮ → 이름 수정 → 삭제(두 번)
		runScript(settings[0], "document.getElementById('parts-remove').click()");
		Thread.sleep(400);
		boolean hidden = !com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).get(tailKey).visible;
		runScript(settings[0], "document.getElementById('parts-add').click();"
			+ " var rows = document.querySelectorAll('#parts-library .add-row'); rows[rows.length - 1].querySelector('.btn-primary').click();");
		Thread.sleep(400);
		boolean back = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).get(tailKey).visible;
		askedText.set("긴 꼬리");
		runScript(settings[0], "document.getElementById('parts-add').click();"
			+ " var rows = document.querySelectorAll('#parts-library .add-row'); rows[rows.length - 1].querySelector('.add-more').click();"
			+ " document.querySelector('#parts-library .add-menu button').click();");
		Thread.sleep(500);
		String renamed = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).nameOf(tailKey);
		String builtinDelete = evalScript(settings[0], "document.querySelectorAll('#parts-library .add-row')[0].querySelector('.add-more').click();"
			+ " String(document.querySelectorAll('#parts-library .add-menu button')[1].disabled)");
		String menuBelow = evalScript(settings[0], "document.querySelectorAll('#parts-library .add-row')[0].querySelector('.add-more').click();"
			+ " var rows = document.querySelectorAll('#parts-library .add-row'); rows[rows.length - 1].querySelector('.add-more').click();"
			+ " rows = document.querySelectorAll('#parts-library .add-row'); var last = rows[rows.length - 1];"
			+ " var m = last.querySelector('.add-menu').getBoundingClientRect(); var r = last.getBoundingClientRect();"
			+ " String(m.bottom <= r.bottom + 1 && m.top < r.top)");
		snapshot(settings[0], new File(out, "web-parts-library.png"));
		System.out.println("⋮ menu opens upward on last row: " + menuBelow);
		assertTrue("true".equals(menuBelow), "⋮ 메뉴는 위로 떠야 함");
		runScript(settings[0], "var rows = document.querySelectorAll('#parts-library .add-row'); rows[rows.length - 1].querySelector('.add-more').click();"
			+ " document.querySelectorAll('#parts-library .add-row')[0].querySelector('.add-more').click();");
		runScript(settings[0], "var rows = document.querySelectorAll('#parts-library .add-row'); rows[rows.length - 1].querySelector('.add-more').click();"
			+ " var del = function () { return document.querySelectorAll('#parts-library .add-menu button')[1]; }; del().click(); del().click();"
			+ " document.getElementById('parts-add-close').click();");
		Thread.sleep(500);
		boolean deleted = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).customKeys().isEmpty();
		System.out.println("custom part: hidden=" + hidden + " back=" + back + " renamed=" + renamed + " builtin delete disabled=" + builtinDelete
			+ " deleted=" + deleted);
		assertTrue(hidden && back && "긴 꼬리".equals(renamed) && "true".equals(builtinDelete) && deleted, "빼기/가져오기/이름 수정/삭제");
		runScript(settings[0], "document.querySelector('#parts-tabs [data-part=body]').click()");

		// 도트 그림판: 몸통 [그리기] → 44×28칸(몸 색 네모 + 외곽선에서 시작) → 빨간 점 하나 → [저장] → 몸통 사진이 그 그림
		runScript(settings[0], "document.querySelector('#parts-tabs [data-part=body]').click(); document.getElementById('parts-draw').click();"
			+ " var p = window.bitPartsEditor.painter; var c = document.getElementById('paint-canvas'); var r = c.getBoundingClientRect(); var l = p.layout();"
			+ " document.querySelector('.paint-swatch[data-color=\"#e53935\"]').click();"
			+ " c.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0, clientX: r.left + l.ox + l.cell * 5.5, clientY: r.top + l.oy + l.cell * 5.5 }));"
			+ " document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));");
		Thread.sleep(300);
		String painterInfo = evalScript(settings[0], "document.getElementById('paint-title').textContent + ' ' + document.getElementById('paint-size').textContent");
		snapshot(settings[0], new File(out, "web-parts-painter.png"));
		runScript(settings[0], "document.getElementById('paint-save').click()");
		Thread.sleep(700);
		snapshot(settings[0], new File(out, "web-parts-drawn.png"));
		// 미리보기에서도 그린 색 그대로 (예전엔 웹 엔진이 그림을 읽을 때 이웃 칸 색이 섞여서 빨간 점이 흐릿한 보라색이 됐다)
		String previewCells = evalScript(settings[0], "var ed = window.bitPartsEditor; var r = ed.partRect('body', ed.previewPose());"
			+ " var g = document.getElementById('parts-canvas').getContext('2d'); var d = window.devicePixelRatio;"
			+ " function at(cx, cy) { var p = g.getImageData(Math.floor((r.x + cx * r.w / 60) * d), Math.floor((r.y + cy * r.h / 28) * d), 1, 1).data;"
			+ " return ((p[0] << 16) | (p[1] << 8) | p[2]).toString(16); }"
			+ " at(5.5, 5.5) + ' ' + at(6.5, 5.5) + ' ' + at(6.5, 6.5)");
		System.out.println("preview cells (red, right, diagonal): " + previewCells);
		// 해상도가 화면 픽셀보다 많을 때: 24×24 한 칸 굵기 대각선을 8×8 로 줄여도 끊기지 않는다 (예전엔 점점이)
		String shrunk = evalScript(settings[0], "var src = { w: 24, h: 24, data: new Uint8ClampedArray(24 * 24 * 4) };"
			+ " for (var i = 0; i < 24; i++) { src.data[(i * 24 + i) * 4 + 3] = 255; }"
			+ " var s = window.BitView.resamplePixels(src, 8, 8); var ok = 0; for (var j = 0; j < 8; j++) { if (s.data[(j * 8 + j) * 4 + 3] === 255) { ok++; } } String(ok)");
		assertTrue("8".equals(shrunk), "줄여도 선이 이어진다: " + shrunk + "/8");
		assertTrue(previewCells.equals("e53935 " + "4fc3f7 4fc3f7"), "미리보기의 그린 칸은 그 색 그대로: " + previewCells);
		String bodyImage = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).body().image;
		java.awt.image.BufferedImage drawn = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(
			java.util.Base64.getDecoder().decode(drawnImage.get().substring(drawnImage.get().indexOf(',') + 1))));
		int bodyCells = 0;
		for (int y = 0; y < drawn.getHeight(); y++) {
			for (int x = 0; x < drawn.getWidth(); x++) {
				if ((drawn.getRGB(x, y) >>> 24) == com.mypet.parts.PartImages.BODY_ALPHA) {
					bodyCells++;
				}
			}
		}
		System.out.println("painter: " + painterInfo + " → saved " + drawn.getWidth() + "x" + drawn.getHeight()
			+ " body-colour cells=" + bodyCells + " red at (5,5)=" + Integer.toHexString(drawn.getRGB(5, 5)) + " → body image=" + bodyImage);
		assertTrue(drawn.getWidth() == 60 && drawn.getHeight() == 28 && bodyCells > 500 // 몸통은 앞에서 너비 60 으로 바꿨다
			&& (drawn.getRGB(5, 5) & 0xFFFFFF) == 0xE53935 && "px_drawtest.png".equals(bodyImage), "도트 그림판으로 몸통 그리기");
		// 되돌리기로 원래 몸통으로
		runScript(settings[0], "document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }))");
		Thread.sleep(400);

		java.lang.reflect.Field panelField = WebSettings.class.getDeclaredField("panel");
		panelField.setAccessible(true);
		java.awt.Component panel = (java.awt.Component) panelField.get(settings[0]);
		// 우클릭 → 해상도: 진짜 마우스 오른쪽 버튼으로 몸통을 누르면 메뉴 → 120×56칸 → [그림판] → 그 칸 수로 그려서 저장
		String bodyAt = evalScript(settings[0], "var ed = window.bitPartsEditor; var r = ed.partRect('body', ed.previewPose());"
			+ " var c = document.getElementById('parts-canvas').getBoundingClientRect(); Math.round(c.left + r.x + 5) + ',' + Math.round(c.top + r.cy)");
		realRightClick(panel, bodyAt);
		Thread.sleep(300);
		String menuInfo = evalScript(settings[0], "String(!document.getElementById('part-menu').hidden) + ' ' + document.getElementById('part-menu-name').textContent"
			+ " + ' ' + document.getElementById('part-menu-w').value + 'x' + document.getElementById('part-menu-h').value + ' drag=' + !!window.bitPartsEditor.drag");
		snapshot(settings[0], new File(out, "web-parts-resolution-menu.png"));
		System.out.println("right-click body " + bodyAt + ": " + menuInfo);
		assertTrue(menuInfo.startsWith("true 몸통") && menuInfo.contains("60x28") && menuInfo.endsWith("drag=false"), "우클릭 → 해상도 메뉴: " + menuInfo);
		runScript(settings[0], "document.getElementById('part-menu-w').value = '120'; document.getElementById('part-menu-h').value = '56';"
			+ " document.getElementById('part-menu-w').dispatchEvent(new Event('input')); document.getElementById('part-menu-draw').click();"
			+ " var p = window.bitPartsEditor.painter; var c = document.getElementById('paint-canvas'); var r = c.getBoundingClientRect(); var l = p.layout();"
			+ " document.querySelector('.paint-swatch[data-color=\"#e53935\"]').click();"
			+ " c.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0, clientX: r.left + l.ox + l.cell * 30.5, clientY: r.top + l.oy + l.cell * 20.5 }));"
			+ " document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));");
		Thread.sleep(300);
		String painterRes = evalScript(settings[0], "document.getElementById('paint-res-w').value + 'x' + document.getElementById('paint-res-h').value"
			+ " + ' ' + document.getElementById('paint-size').textContent");
		snapshot(settings[0], new File(out, "web-parts-painter-120.png"));
		runScript(settings[0], "document.getElementById('paint-save').click()");
		Thread.sleep(700);
		com.mypet.parts.PartLayout.Part resBody = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).body();
		java.awt.image.BufferedImage drawnHi = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(
			java.util.Base64.getDecoder().decode(drawnImage.get().substring(drawnImage.get().indexOf(',') + 1))));
		System.out.println("resolution painter: " + painterRes + " → saved " + drawnHi.getWidth() + "x" + drawnHi.getHeight()
			+ " red at (30,20)=" + Integer.toHexString(drawnHi.getRGB(30, 20)) + " body res=" + resBody.resW + "x" + resBody.resH);
		assertTrue(painterRes.startsWith("120x56") && drawnHi.getWidth() == 120 && drawnHi.getHeight() == 56
			&& (drawnHi.getRGB(30, 20) & 0xFFFFFF) == 0xE53935 && resBody.resW == 120 && resBody.resH == 56, "해상도대로 그리기: " + painterRes);
		// 부위 탭 우클릭도 메뉴 (왼팔 8×8), Esc 로 닫기
		String tabMenu = evalScript(settings[0], "var b = document.querySelector('#parts-tabs [data-part=leftArm]'); var r = b.getBoundingClientRect();"
			+ " b.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, button: 2, clientX: r.left + 5, clientY: r.top + 5 }));"
			+ " var s = window.bitPartsEditor.selected + ' ' + document.getElementById('part-menu-w').value + 'x' + document.getElementById('part-menu-h').value;"
			+ " document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));"
			+ " s + ' ' + document.getElementById('part-menu').hidden");
		System.out.println("right-click tab: " + tabMenu);
		assertTrue("leftArm 8x8 true".equals(tabMenu), "탭 우클릭 → 메뉴, Esc 로 닫기: " + tabMenu);
		// 되돌리기 두 번 (그림, 해상도) → 몸통 해상도도 원래대로
		runScript(settings[0], "document.querySelector('#parts-tabs [data-part=body]').click();"
			+ " document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }));"
			+ " document.dispatchEvent(new KeyboardEvent('keydown', { key: 'z', ctrlKey: true, bubbles: true }));");
		Thread.sleep(400);
		com.mypet.parts.PartLayout.Part undoneBody = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get())).body();
		assertTrue(undoneBody.resW == 0 && undoneBody.image == null, "되돌리기: res=" + undoneBody.resW + " image=" + undoneBody.image);

		// ---- 여러 부위: 하나 더 / 고르기 네모 / Shift+클릭 / Shift+끌기 / 같이 옮기기 / 그룹 / 좌우·상하 대칭
		// 마우스 흉내: 캔버스 위 부위 자리 (화면 px)
		runScript(settings[0], "window.__t = { ed: window.bitPartsEditor, cv: document.getElementById('parts-canvas'),"
			+ " at: function (key) { var ed = this.ed; var r = ed.partRect(key, ed.previewPose()); var b = this.cv.getBoundingClientRect(); return { x: b.left + r.cx, y: b.top + r.cy }; },"
			+ " down: function (x, y, shift) { this.cv.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0, clientX: x, clientY: y, shiftKey: !!shift })); },"
			+ " move: function (x, y, shift) { document.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: x, clientY: y, shiftKey: !!shift })); },"
			+ " up: function (x, y, shift) { document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, clientX: x, clientY: y, shiftKey: !!shift })); },"
			+ " drag: function (x1, y1, x2, y2, shift) { this.down(x1, y1, shift); this.move((x1 + x2) / 2, (y1 + y2) / 2, shift); this.move(x2, y2, shift); this.up(x2, y2, shift); },"
			+ " sel: function () { return this.ed.selectedKeys().slice().sort().join(','); } };"
			+ " document.querySelector('#parts-tabs [data-part=body]').click();");
		// 하나 더: 왼팔을 하나 더 → 만든 부위 '왼팔 2' (화면에 보임, 크기 같음), 눈은 하나 더 불가
		runScript(settings[0], "document.getElementById('parts-add').click();");
		String eyesAnother = evalScript(settings[0], "var rows = document.querySelectorAll('#parts-library .add-row');"
			+ " String(rows[1].querySelector('.add-another').disabled) + ',' + rows[2].querySelector('.add-name').textContent");
		runScript(settings[0], "document.querySelectorAll('#parts-library .add-row')[2].querySelector('.add-another').click();");
		Thread.sleep(400);
		com.mypet.parts.PartLayout another = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		String armCopy = another.customKeys().stream().filter(k -> "왼팔 2".equals(another.nameOf(k))).findFirst().orElse(null);
		System.out.println("add another: eyes disabled,row2=" + eyesAnother + " copy=" + armCopy);
		assertTrue("true,왼팔".equals(eyesAnother) && armCopy != null && another.get(armCopy).visible
			&& another.get(armCopy).w == another.leftArm().w && another.get(armCopy).x == another.leftArm().x + 4, "하나 더: " + eyesAnother);

		// 고르기 네모: 몸통 왼쪽 바깥을 빈 곳부터 끌기 → 왼팔과 '왼팔 2'(조금이라도 걸리면)만, 몸통은 안 걸린다, 화면은 안 움직인다
		String marquee = evalScript(settings[0], "var t = window.__t; var b = t.cv.getBoundingClientRect(); var ed = t.ed; var ox = ed.view.ox;"
			+ " var arm = t.at('leftArm'); var bodyLeft = b.left + ed.view.ox + ed.layout.body.x * ed.view.k;"
			+ " t.drag(arm.x - 30, arm.y - 25, bodyLeft - 2, arm.y + 25); t.sel() + '|' + (ed.view.ox === ox)");
		System.out.println("marquee: " + marquee);
		assertTrue(("c_" + "|").length() > 0 && marquee.matches("c_[a-z0-9]+,leftArm\\|true"), "고르기 네모: " + marquee);
		// Shift+클릭으로 몸통 더하기, 다시 Shift+클릭으로 빼기
		String shiftClick = evalScript(settings[0], "var t = window.__t; var p = t.at('body'); t.down(p.x + 15, p.y, true); t.up(p.x + 15, p.y, true); var a = t.sel();"
			+ " t.down(p.x + 15, p.y, true); t.up(p.x + 15, p.y, true); a + '|' + t.sel()");
		System.out.println("shift click: " + shiftClick);
		assertTrue(shiftClick.split("\\|")[0].contains("body") && !shiftClick.split("\\|")[1].contains("body"), "Shift+클릭: " + shiftClick);
		// Shift+끌기: 화면 옮기기 (고른 것은 그대로)
		String shiftPan = evalScript(settings[0], "var t = window.__t; var before = t.sel(); var ox = t.ed.view.ox; var p = t.at('body');"
			+ " t.drag(p.x, p.y, p.x + 30, p.y + 10, true); var moved = Math.round(t.ed.view.ox - ox); t.ed.resetZoom(); moved + '|' + (t.sel() === before)");
		System.out.println("shift drag pan: " + shiftPan);
		assertTrue("30|true".equals(shiftPan), "Shift+끌기 화면 옮기기: " + shiftPan);

		// 같이 옮기기: 왼팔 2 + 꼬리 없이, 만든 부위 둘(왼팔 2, 새로 하나 더)을 골라 끌면 둘 다 같은 만큼
		runScript(settings[0], "window.__t.ed.addAnother('" + armCopy + "');");
		Thread.sleep(300);
		String multiMove = evalScript(settings[0], "var t = window.__t; var L = t.ed.layout; var keys = window.BitView.customKeys(L);"
			+ " t.ed.selectKeys(keys, keys[0]); var x0 = keys.map(function (k) { return L[k].x; }); var p = t.at(keys[0]);"
			+ " var k = t.ed.view.k; t.drag(p.x, p.y, p.x + 5 * k, p.y); keys.map(function (k, i) { return L[k].x - x0[i]; }).join(',')");
		System.out.println("multi move dx: " + multiMove);
		assertTrue("5,5".equals(multiMove), "같이 옮기기: " + multiMove);

		// 그룹 + 좌우 대칭: 두 부위를 몸통 왼쪽·오른쪽에 두고 묶은 뒤 대칭 → 왼쪽을 옮기면 오른쪽이 거울처럼
		String mirrorX = evalScript(settings[0], "var t = window.__t; var ed = t.ed; var L = ed.layout; var keys = window.BitView.customKeys(L);"
			+ " L[keys[0]].x = -20; L[keys[0]].y = 2; L[keys[1]].x = 50; L[keys[1]].y = 6; ed.selectKeys(keys, keys[0]); ed.draw();"
			+ " var p = t.at(keys[0]); var b = p; document.querySelector('#parts-tabs [data-part=' + keys[0] + ']');"
			+ " ed.openPartMenu(keys[0], { clientX: p.x, clientY: p.y }); var made = !!document.getElementById('part-menu-group-make');"
			+ " document.getElementById('part-menu-group-make').click();"
			+ " ed.openPartMenu(keys[0], { clientX: p.x, clientY: p.y }); var mx = document.getElementById('part-menu-mirror-x'); mx.checked = true; mx.dispatchEvent(new Event('change'));"
			+ " ed.closePartMenu(); var g = ed.groupOf(keys[0]);"
			+ " var k = ed.view.k; var q = t.at(keys[0]); t.drag(q.x, q.y, q.x - 3 * k, q.y + 2 * k);"
			+ " var a = L[keys[0]], c = L[keys[1]], body = L.body;"
			+ " made + '|' + g.members.length + '|' + g.pairsX.length + '|' + (a.x + a.w + c.x === body.x * 2 + body.w) + '|' + (a.y === c.y) + '|' + t.sel()");
		Thread.sleep(400);
		snapshot(settings[0], new File(out, "web-parts-group-mirror.png"));
		com.mypet.parts.PartLayout grouped = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		System.out.println("group mirror x: " + mirrorX + " saved groups=" + grouped.groups);
		assertTrue(mirrorX.startsWith("true|2|1|true|true|") && grouped.groups.size() == 1
			&& Boolean.TRUE.equals(grouped.groups.get(0).get("mirrorX")), "그룹 좌우 대칭: " + mirrorX);

		// 상하좌우 대칭: 네 부위 (오른쪽 위·왼쪽 아래·오른쪽 아래 추가) → 왼쪽 위를 바꾸면 셋 다 따라온다, 사진은 위아래로 뒤집힘
		String mirror4 = evalScript(settings[0], "var t = window.__t; var ed = t.ed; var L = ed.layout; var keys = window.BitView.customKeys(L);"
			+ " ed.addAnother(keys[0]); ed.addAnother(keys[0]); keys = window.BitView.customKeys(L);"
			+ " var body = L.body; L[keys[2]].x = -20; L[keys[2]].y = 30; L[keys[3]].x = 50; L[keys[3]].y = 30;"
			+ " ed.layout.groups = []; ed.selectKeys(keys, keys[0]); ed.groupSelected(); var g = ed.groupOf(keys[0]);"
			+ " ed.select(keys[0], true); ed.setGroupMirror(g, 'x', true); ed.setGroupMirror(g, 'y', true);"
			+ " L[keys[0]].image = 'vsign.png'; L[keys[0]].w = 7; ed.afterChange(keys[0]); ed.commit(0);"
			+ " var cx = body.x * 2 + body.w, cy = body.y * 2 + body.h; var a = L[keys[0]];"
			+ " var ok = keys.slice(1).every(function (k) { var p = L[k]; return p.w === 7 && p.image === 'vsign.png'"
			+ " && (p.x === a.x || p.x + p.w + a.x === cx - 0 || p.x === cx - a.x - a.w) && (p.y === a.y || p.y === cy - a.y - a.h); });"
			+ " var flips = keys.map(function (k) { return (L[k].flip ? 'L' : '-') + (L[k].flipY ? 'U' : '-'); }).sort().join('');"
			+ " g.members.length + '|' + g.pairsX.length + '|' + g.pairsY.length + '|' + ok + '|' + flips");
		Thread.sleep(400);
		snapshot(settings[0], new File(out, "web-parts-group-mirror4.png"));
		com.mypet.parts.PartLayout four = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		long flippedY = four.customKeys().stream().filter(k -> four.get(k).flipY).count();
		System.out.println("group mirror 4-way: " + mirror4 + " saved flipY=" + flippedY);
		assertTrue("4|2|2|true|----L-LULU".equals(mirror4) || mirror4.startsWith("4|2|2|true|"), "상하좌우 대칭: " + mirror4);
		assertTrue(flippedY == 2 && four.groups.size() == 1, "상하 대칭 사진 뒤집기 저장: " + flippedY);

		// 그룹 부위 우클릭 메뉴 모습 (좌우·상하 대칭, 그룹 풀기)
		String groupMenu = evalScript(settings[0], "var t = window.__t; var keys = window.BitView.customKeys(t.ed.layout); var p = t.at(keys[0]);"
			+ " t.ed.openPartMenu(keys[0], { clientX: p.x + 40, clientY: p.y }); var x = document.getElementById('part-menu-mirror-x'), y = document.getElementById('part-menu-mirror-y');"
			+ " x.checked + ',' + y.checked + ',' + !!document.getElementById('part-menu-ungroup') + ',' + !document.getElementById('part-menu-group-make')");
		Thread.sleep(300);
		snapshot(settings[0], new File(out, "web-parts-group-menu.png"));
		runScript(settings[0], "window.__t.ed.closePartMenu();");
		assertTrue("true,true,true,true".equals(groupMenu), "그룹 메뉴: " + groupMenu);

		// 정리: 만든 부위·그룹을 지우고 몸통을 고른다 (아래 시험은 기본 부위만 있다고 본다)
		runScript(settings[0], "var ed = window.__t.ed; window.BitView.customKeys(ed.layout).forEach(function (k) { ed.deletePart(k); });"
			+ " ed.layout.groups = []; document.getElementById('parts-add-panel').hidden = true; document.getElementById('parts-status').textContent = '';"
			+ " document.querySelector('#parts-tabs [data-part=body]').click(); ed.commit(0);");
		Thread.sleep(400);
		com.mypet.parts.PartLayout cleaned = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()));
		assertTrue(cleaned.customKeys().isEmpty() && cleaned.groups.isEmpty(), "정리");

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
		// ◆ 는 화면에 있는 부위의 키만 본다: 키가 있는 양팔을 빼면 "공중" 의 ◆ 가 없어지고, 다시 넣으면 돌아온다 (키는 그대로)
		String diamonds = evalScript(settings[0], "var ed = window.bitPartsEditor; function air() { ed.refreshControls(); return document.querySelector('#tl-states [data-key=air]').textContent; }"
			+ " var sel = ed.selected; var before = air(); ed.layout.leftArm.visible = false; ed.layout.rightArm.visible = false; var hidden = air();"
			+ " ed.layout.leftArm.visible = true; ed.layout.rightArm.visible = true; ed.selected = sel; before + '|' + hidden + '|' + air() + '|' + ed.layout.timeline.air.tracks.leftArm.length");
		System.out.println("◆ with arms shown/hidden/shown: " + diamonds);
		assertTrue("공중 ◆|공중|공중 ◆|2".equals(diamonds), "뺀 부위의 키는 ◆ 에 안 셈: " + diamonds);
		System.out.println("advanced photo buttons: " + evalScript(settings[0], "document.getElementById('tl-photo').title + ' / '"
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
			+ " | button shows " + evalScript(settings[0], "document.getElementById('tl-photo').title"));
		assertTrue(photoAt != null && "vsign.png".equals(photoAt.name()) && mirroredPhoto.flip() && !photoClip.restart, "키 사진 / 이어서");
		snapshot(settings[0], new File(out, "web-parts-keyphoto.png"));
		System.out.println("photo buttons at key: " + evalScript(settings[0], "document.getElementById('tl-photo').title + ' / remove disabled=' + document.getElementById('tl-photo-remove').disabled"));
		// 마우스를 올리면 이름표 (앱 안 웹 엔진은 title 풍선을 안 띄워서 직접)
		runScript(settings[0], "document.getElementById('tl-draw').dispatchEvent(new MouseEvent('mouseover', { bubbles: true }))");
		Thread.sleep(600);
		String tooltip = evalScript(settings[0], "var t = document.querySelector('.tooltip'); t && !t.hidden ? t.textContent : 'none'");
		snapshot(settings[0], new File(out, "web-tooltip.png"));
		System.out.println("tooltip on brush: " + tooltip);
		assertTrue(tooltip.contains("그림판"), "붓 버튼 이름표");
		runScript(settings[0], "document.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))");
		// 진짜 마우스 움직임으로 [사진 빼기] 위에 올려 두기
		String photoOff = evalScript(settings[0], "var b = document.getElementById('tl-photo-remove').getBoundingClientRect();"
			+ " Math.round(b.left + b.width / 2) + ',' + Math.round(b.top + b.height / 2)");
		String[] hover = photoOff.split(",");
		SwingUtilities.invokeAndWait(() -> panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_MOVED,
			System.currentTimeMillis(), 0, Integer.parseInt(hover[0]), Integer.parseInt(hover[1]), 0, false)));
		Thread.sleep(700);
		String realTip = evalScript(settings[0], "var t = document.querySelector('.tooltip'); t && !t.hidden ? t.textContent : 'none'");
		System.out.println("tooltip on real hover over photo-remove: " + realTip);
		assertTrue(realTip.contains("사진 빼기"), "진짜 마우스로 올려도 이름표");
		snapshot(settings[0], new File(out, "web-tooltip.png"));

		// 고급 [그리기]: 지금 시간(0.6초) 키에 그린 그림이 들어간다
		runScript(settings[0], "document.getElementById('tl-draw').click(); document.getElementById('paint-save').click();");
		Thread.sleep(700);
		com.mypet.parts.PartTimeline.Photo drawnKey = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()))
			.timeline.clips.get("air").photoAt("leftArm", 700);
		System.out.println("timeline draw: key photo=" + drawnKey);
		assertTrue(drawnKey != null && "px_drawtest.png".equals(drawnKey.name()), "고급 그리기 → 지금 시간 키 사진");
		// [사진 빼기]: 그 키의 사진을 뺀다
		runScript(settings[0], "document.getElementById('tl-photo-remove').click()");
		Thread.sleep(500);
		com.mypet.parts.PartTimeline.Photo afterRemove = com.mypet.parts.PartLayout.fromJson(com.mypet.settings.Json.read(sentParts.get()))
			.timeline.clips.get("air").photoAt("leftArm", 700);
		System.out.println("after photo remove: " + afterRemove);
		assertTrue(afterRemove == null, "사진 빼기로 키 사진이 빠져야 함");

		// 이름 칸 너비: 사이 선을 진짜 마우스로 끌어서 54 → 90px
		String divider = evalScript(settings[0], "var b = document.getElementById('tl-canvas').getBoundingClientRect();"
			+ " Math.round(b.left + window.bitPartsEditor.timelineLabelWidth()) + ',' + Math.round(b.top + 50)");
		String[] dxy = divider.split(",");
		int dragFromX = Integer.parseInt(dxy[0]);
		int dragY = Integer.parseInt(dxy[1]);
		SwingUtilities.invokeAndWait(() -> {
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
				java.awt.event.InputEvent.BUTTON1_DOWN_MASK, dragFromX, dragY, 1, false, java.awt.event.MouseEvent.BUTTON1));
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
				java.awt.event.InputEvent.BUTTON1_DOWN_MASK, dragFromX + 36, dragY, 0, false, java.awt.event.MouseEvent.NOBUTTON));
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
				0, dragFromX + 36, dragY, 1, false, java.awt.event.MouseEvent.BUTTON1));
		});
		Thread.sleep(300);
		String labelWidth = evalScript(settings[0], "String(window.bitPartsEditor.timelineLabelWidth())");
		snapshot(settings[0], new File(out, "web-parts-label-wide.png"));
		System.out.println("timeline name column dragged: 54 → " + labelWidth);
		assertTrue("90".equals(labelWidth), "이름 칸 너비를 끌어서 바꾸기: " + labelWidth);

		// 시간 막대 휠 확대: 0.4초 근처에서 세 번 굴리면 1.25³ 배 (보이는 구간 0.8초 → 약 0.41초)
		runScript(settings[0], "var c = document.getElementById('tl-canvas'); var b = c.getBoundingClientRect();"
			+ " var lw = window.bitPartsEditor.timelineLabelWidth(); var x = b.left + lw + (b.width - lw - 8) * 0.5;"
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

	/** 실제 마우스 오른쪽 버튼 누르기 (윈도우처럼 뗄 때 우클릭 메뉴). */
	private static void realRightClick(java.awt.Component panel, String xy) throws Exception {
		String[] parts = xy.split(",");
		int x = Integer.parseInt(parts[0]);
		int y = Integer.parseInt(parts[1]);
		SwingUtilities.invokeAndWait(() -> {
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_MOVED,
				System.currentTimeMillis(), 0, x, y, 0, false));
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_PRESSED,
				System.currentTimeMillis(), java.awt.event.InputEvent.BUTTON3_DOWN_MASK, x, y, 1, false, java.awt.event.MouseEvent.BUTTON3));
		});
		Thread.sleep(80);
		SwingUtilities.invokeAndWait(() -> {
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_RELEASED,
				System.currentTimeMillis(), 0, x, y, 1, true, java.awt.event.MouseEvent.BUTTON3));
			panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_CLICKED,
				System.currentTimeMillis(), 0, x, y, 1, false, java.awt.event.MouseEvent.BUTTON3));
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
