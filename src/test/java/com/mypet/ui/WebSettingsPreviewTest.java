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
		settings[0].showViewForPreview("appearance");
		Thread.sleep(700);
		SwingUtilities.invokeAndWait(() -> System.out.println("after main→appearance: dialog at " + settings[0].dialog().getLocation()
			+ " (옮겨 둔 자리 " + moved + " 그대로여야 함)"));
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
		SwingUtilities.invokeAndWait(() -> System.out.println("after back to main: dialog at " + settings[0].dialog().getLocation()
			+ " / search window visible=" + settings[0].searchWindowForPreview().isVisible()));
		settings[0].showViewForPreview("color");
		Thread.sleep(700);
		SwingUtilities.invokeAndWait(() -> System.out.println("after main→color: dialog at " + settings[0].dialog().getLocation()));
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
