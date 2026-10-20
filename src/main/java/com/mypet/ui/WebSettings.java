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
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLayeredPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
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
 *
 * 외형 검색창은 Swing 입력칸({@link #searchField})을 HTML 검색창 자리에 겹쳐 놓는다.
 * JFXPanel 안의 WebView 는 한글 조합 중인 글자를 받지 못해서(글자가 완성돼야 나타남),
 * 한글 입력이 자연스러운 Swing 입력칸으로 받고 글자가 바뀔 때마다 window.bitSearch(검색어) 로 넘긴다.
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
	private final JTextField searchField = new JTextField();
	private Color searchAccent = new Color(0x4FC3F7);
	/** 창 테두리/제목 표시줄 두께 (처음 한 번 잰다). */
	private int frameExtraWidth = -1;
	private int frameExtraHeight = -1;

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
		setupSearchField();
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
				engine.executeScript("window.bitUseNativeSearch && window.bitUseNativeSearch()");
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

	/** HTML 검색창 자리에 겹쳐 놓을 Swing 입력칸. 처음에는 숨겨 두고 JS 가 위치를 알려주면 보인다. */
	private void setupSearchField() {
		searchField.setVisible(false);
		searchField.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
		searchField.setForeground(Ui.INK);
		searchField.setBackground(Ui.PAPER);
		searchField.setCaretColor(Ui.INK);
		searchField.setOpaque(true);
		searchField.putClientProperty("JTextField.placeholderText", "외형·앱 검색");
		updateSearchBorder();
		searchField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				updateSearchBorder();
			}

			@Override
			public void focusLost(FocusEvent e) {
				updateSearchBorder();
			}
		});
		// 한글 조합 중인 글자도 문서에 들어오므로, 바뀔 때마다 바로 검색한다
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				sendSearch();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				sendSearch();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				sendSearch();
			}
		});
		// Esc: 검색어 지우기
		searchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "mypet-clear-search");
		searchField.getActionMap().put("mypet-clear-search", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchField.setText("");
			}
		});
		dialog.getLayeredPane().add(searchField, JLayeredPane.PALETTE_LAYER);
	}

	private void updateSearchBorder() {
		Color edge = searchField.isFocusOwner() ? searchAccent : Ui.PAPER;
		searchField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(edge, 2),
			BorderFactory.createEmptyBorder(0, 6, 0, 6)
		));
	}

	private void sendSearch() {
		String query = searchField.getText();
		Platform.runLater(() -> {
			if (loaded) {
				engine.executeScript("window.bitSearch && window.bitSearch(" + jsString(query) + ")");
			}
		});
	}

	/** (EDT) JS 가 알려준 HTML 검색창 자리에 Swing 입력칸을 놓는다. */
	private void placeSearchField(int x, int y, int width, int height, boolean visible, String accent) {
		try {
			searchAccent = Color.decode(accent);
		} catch (RuntimeException ignored) {
			// 기본 색 유지
		}
		if (!visible) {
			if (searchField.isVisible()) {
				searchField.setText("");
			}
			searchField.setVisible(false);
			return;
		}
		// 레이어드 패널 기준 좌표 = 내용(JFXPanel) 기준 좌표 + 내용 패널 위치
		java.awt.Point origin = SwingUtilities.convertPoint(panel, 0, 0, dialog.getLayeredPane());
		searchField.setBounds(origin.x + x, origin.y + y, width, height);
		updateSearchBorder();
		searchField.setVisible(true);
		searchField.revalidate();
		searchField.repaint();
	}

	/** JS 문자열 리터럴로 감싸기. */
	private static String jsString(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (char ch : value.toCharArray()) {
			switch (ch) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\u2028' -> out.append("\\u2028");
				case '\u2029' -> out.append("\\u2029");
				default -> {
					if (ch < 0x20) {
						out.append(String.format("\\u%04x", (int) ch));
					} else {
						out.append(ch);
					}
				}
			}
		}
		return out.append('"').toString();
	}

	/** 개발용: 검색 입력칸 (테스트에서 글자를 넣어 볼 때 사용). */
	public JTextField searchFieldForPreview() {
		return searchField;
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
		// 제목 표시줄/테두리 두께: getInsets() 가 틀리게 나오는 경우가 있어 실제 내용 영역 크기와의 차이로 잰다.
		// 크기를 바꾼 직후에는 내용 영역이 아직 예전 크기라 잘못 재지므로, 처음 한 번만 재서 기억한다.
		if (frameExtraWidth < 0 || frameExtraHeight < 0) {
			int extraWidth = dialog.getWidth() - panel.getWidth();
			int extraHeight = dialog.getHeight() - panel.getHeight();
			if (panel.getWidth() <= 0 || panel.getHeight() <= 0 || extraWidth < 0 || extraHeight < 0) {
				Insets insets = dialog.getInsets();
				extraWidth = insets.left + insets.right;
				extraHeight = insets.top + insets.bottom;
			}
			frameExtraWidth = extraWidth;
			frameExtraHeight = extraHeight;
		}
		int width = WIDTH + frameExtraWidth;
		int height = contentHeight + frameExtraHeight;
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

		public void searchBox(Object x, Object y, Object width, Object height, Object visible, Object accent) {
			if (x instanceof Number nx && y instanceof Number ny && width instanceof Number nw && height instanceof Number nh) {
				boolean show = Boolean.TRUE.equals(visible);
				String color = accent == null ? "#4fc3f7" : String.valueOf(accent);
				SwingUtilities.invokeLater(() -> placeSearchField(
					(int) Math.round(nx.doubleValue()), (int) Math.round(ny.doubleValue()),
					(int) Math.round(nw.doubleValue()), (int) Math.round(nh.doubleValue()), show, color));
			}
		}

		public void resize(Object contentHeight) {
			if (contentHeight instanceof Number number) {
				int height = (int) Math.ceil(number.doubleValue());
				SwingUtilities.invokeLater(() -> resizeTo(height));
			}
		}
	}
}
