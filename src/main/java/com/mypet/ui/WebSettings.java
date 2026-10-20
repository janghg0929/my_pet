package com.mypet.ui;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

/**
 * HTML/CSS 로 그린 설정 창 (src/main/resources/com/mypet/web/settings.html).
 * Swing 창(JDialog) 안에 JavaFX WebView 를 넣고, 자바스크립트와 Java 를 연결한다.
 * - JS → Java: window.bit.메서드(...)  ({@link Bridge}, JavaFX 스레드에서 불림 → EDT 로 넘긴다)
 * - Java → JS: window.bitUpdate(상태)
 */
public final class WebSettings implements SettingsHost.View {
	private static final int WIDTH = 360;

	private final SettingsHost host;
	private final JDialog dialog;
	private final JFXPanel panel;
	/** JS 가 호출하는 객체. 약한 참조로만 잡히면 GC 되므로 필드로 붙잡아 둔다. */
	private final Bridge bridge = new Bridge();
	private WebView webView;
	private WebEngine engine;
	private boolean loaded;

	public WebSettings(Window owner, SettingsHost host) {
		this.host = host;
		dialog = new JDialog(owner, "비트 설정", JDialog.ModalityType.MODELESS);
		dialog.setAlwaysOnTop(true);
		dialog.setResizable(false);
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				host.closeSettings();
			}
		});
		panel = new JFXPanel(); // JavaFX 초기화
		panel.setBackground(Ui.PAPER);
		dialog.setContentPane(panel);
		dialog.setSize(WIDTH, 300);
		Platform.setImplicitExit(false); // 창을 닫아도 JavaFX 를 끄지 않는다 (다시 열 수 있게)
		Platform.runLater(this::initFx);
	}

	private void initFx() {
		webView = new WebView();
		webView.setContextMenuEnabled(false);
		engine = webView.getEngine();
		engine.setOnAlert(event -> System.err.println("[settings.js] " + event.getData()));
		engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
			if (newState == Worker.State.SUCCEEDED) {
				JSObject window = (JSObject) engine.executeScript("window");
				window.setMember("bit", bridge);
				loaded = true;
				SwingUtilities.invokeLater(this::refresh);
				resizeToContent();
			} else if (newState == Worker.State.FAILED) {
				System.err.println("[MY_PET] 설정 화면을 불러오지 못했습니다: " + engine.getLoadWorker().getException());
			}
		});
		panel.setScene(new Scene(webView, javafx.scene.paint.Color.web("#fbf6ec")));
		URL page = WebSettings.class.getResource("/com/mypet/web/settings.html");
		engine.load(page.toExternalForm());
	}

	@Override
	public JDialog dialog() {
		return dialog;
	}

	@Override
	public void refresh() {
		String state = host.settingsStateJson();
		Platform.runLater(() -> {
			if (loaded) {
				engine.executeScript("window.bitUpdate(" + state + ")");
			}
		});
	}

	/** (JavaFX 스레드) 내용 높이를 재서 창 크기를 맞춘다. */
	private void resizeToContent() {
		Object height = engine.executeScript("window.bitContentHeight ? window.bitContentHeight() : document.body.offsetHeight");
		if (height instanceof Number number) {
			int contentHeight = (int) Math.ceil(number.doubleValue());
			SwingUtilities.invokeLater(() -> resizeTo(contentHeight));
		}
	}

	private void resizeTo(int contentHeight) {
		// 제목 표시줄/테두리 두께: getInsets() 가 틀리게 나오는 경우가 있어 실제 내용 영역 크기와의 차이로 잰다
		int extraWidth = dialog.getWidth() - panel.getWidth();
		int extraHeight = dialog.getHeight() - panel.getHeight();
		if (panel.getWidth() <= 0 || panel.getHeight() <= 0 || extraWidth < 0 || extraHeight < 0) {
			Insets insets = dialog.getInsets();
			extraWidth = insets.left + insets.right;
			extraHeight = insets.top + insets.bottom;
		}
		int width = WIDTH + extraWidth;
		int height = contentHeight + extraHeight;
		if (dialog.getWidth() != width || dialog.getHeight() != height) {
			dialog.setSize(width, height);
			host.placeSettingsDialog(dialog);
		}
	}

	/** 개발용: 현재 화면을 PNG 로 저장 (JavaFX 스레드에서 찍는다). */
	public void snapshotTo(File file, Consumer<Boolean> done) {
		Platform.runLater(() -> {
			try {
				WritableImage image = webView.snapshot(null, null);
				ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
				done.accept(true);
			} catch (IOException | RuntimeException e) {
				done.accept(false);
			}
		});
	}

	/** 개발용: 화면 전환 (main / appearance / color). */
	public void showViewForPreview(String view) {
		Platform.runLater(() -> engine.executeScript("document.querySelector('[data-go=\"" + view + "\"]') ? "
			+ "document.querySelector('[data-go=\"" + view + "\"]').click() : null"));
	}

	public boolean isLoaded() {
		return loaded;
	}

	/** settings.js 의 send("메서드", ...) 가 부르는 객체. 메서드 이름이 그대로 JS 에서 쓰인다. */
	public final class Bridge {
		public void ready() {
			SwingUtilities.invokeLater(WebSettings.this::refresh);
		}

		public void setMode(String key, String mode) {
			SwingUtilities.invokeLater(() -> host.setAppearanceMode(key, mode));
		}

		public void defaultColor() {
			SwingUtilities.invokeLater(host::applyDefaultColor);
		}

		public void pickColor() {
			SwingUtilities.invokeLater(host::pickCustomColor);
		}

		public void close() {
			SwingUtilities.invokeLater(host::closeSettings);
		}

		public void quit() {
			SwingUtilities.invokeLater(host::quit);
		}

		public void resize(Object contentHeight) {
			if (contentHeight instanceof Number number) {
				int height = (int) Math.ceil(number.doubleValue());
				SwingUtilities.invokeLater(() -> resizeTo(height));
			}
		}
	}
}
