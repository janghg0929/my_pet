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
import javax.swing.JWindow;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
 * 외형 검색창은 Swing 입력칸({@link #searchField})을 담은 작은 창({@link #searchWindow})을 HTML 검색창 자리에 띄운다.
 * JFXPanel 안의 WebView 는 한글 조합 중인 글자를 받지 못하고(글자가 완성돼야 나타남),
 * 같은 창 안에 Swing 입력칸을 겹쳐도 한글 입력기가 조합 글자를 화면 구석의 기본 입력 상자에 띄웠다.
 * 그래서 입력칸을 별도 창으로 분리하고, 글자가 바뀔 때마다 window.bitSearch(검색어) 로 넘긴다.
 */
public final class WebSettings implements SettingsHost.View {
	/** 기본 내용 너비. 화면마다 더 넓게 달라고 할 수 있다 (모습 꾸미기 등: settings.js 의 VIEW_WIDTHS). */
	private static final int WIDTH = 360;
	private static final int MIN_WIDTH = 300;
	private static final int MAX_WIDTH = 900;

	private final SettingsHost host;
	private final JDialog dialog;
	private final JFXPanel panel;
	/** JS 가 호출하는 객체. 약한 참조로만 잡히면 GC 되므로 필드로 붙잡아 둔다. */
	private final Bridge bridge = new Bridge();
	private WebView webView;
	private WebEngine engine;
	private boolean loaded;
	private final JTextField searchField = new JTextField();
	/** 검색 입력칸을 담는 작은 창 (설정 창에 딸린 창이라 설정 창을 따라다닌다). */
	private JWindow searchWindow;
	/** HTML 검색창 자리 (JFXPanel 기준 좌표). null 이면 숨김. */
	private Rectangle searchSlot;
	/** 처음 한 번만 캐릭터 근처에 놓고, 그 뒤로는 사용자가 옮긴 자리를 지킨다. */
	private boolean placedOnce;
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
		searchField.setFont(Ui.font(Font.PLAIN, 12));
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
		searchField.setVisible(true);
		searchWindow = new JWindow(dialog);
		searchWindow.setAlwaysOnTop(true);
		searchWindow.setFocusableWindowState(true);
		searchWindow.setContentPane(searchField);
		// 설정 창을 옮기거나 크기가 바뀌면 검색 창도 따라간다
		dialog.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				positionSearchWindow();
			}

			@Override
			public void componentResized(ComponentEvent e) {
				positionSearchWindow();
			}

			@Override
			public void componentHidden(ComponentEvent e) {
				searchWindow.setVisible(false);
			}
		});
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowIconified(WindowEvent e) {
				searchWindow.setVisible(false);
			}

			@Override
			public void windowDeiconified(WindowEvent e) {
				positionSearchWindow();
			}

			@Override
			public void windowClosed(WindowEvent e) {
				searchWindow.dispose();
			}
		});
	}

	/** (EDT) 검색 창을 HTML 검색창 자리(화면 좌표)에 놓는다. */
	private void positionSearchWindow() {
		if (searchWindow == null) {
			return;
		}
		if (searchSlot == null || !dialog.isShowing() || !panel.isShowing()) {
			searchWindow.setVisible(false);
			return;
		}
		Point origin = panel.getLocationOnScreen();
		searchWindow.setBounds(origin.x + searchSlot.x, origin.y + searchSlot.y, searchSlot.width, searchSlot.height);
		if (!searchWindow.isVisible()) {
			searchWindow.setVisible(true);
		}
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
			if (searchSlot != null) {
				searchField.setText("");
			}
			searchSlot = null;
			positionSearchWindow();
			return;
		}
		searchSlot = new Rectangle(x, y, width, height);
		updateSearchBorder();
		positionSearchWindow();
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

	/** 개발용: 검색 입력칸을 담은 작은 창. */
	public JWindow searchWindowForPreview() {
		return searchWindow;
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
		Object width = engine.executeScript("window.bitContentWidth ? window.bitContentWidth() : " + WIDTH);
		if (height instanceof Number number) {
			int contentHeight = (int) Math.ceil(number.doubleValue());
			int contentWidth = width instanceof Number w ? w.intValue() : WIDTH;
			SwingUtilities.invokeLater(() -> resizeTo(contentHeight, contentWidth));
		}
	}

	private void resizeTo(int contentHeight, int contentWidth) {
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
		int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, contentWidth)) + frameExtraWidth;
		int height = contentHeight + frameExtraHeight;
		if (dialog.getWidth() != width || dialog.getHeight() != height) {
			if (!placedOnce) {
				dialog.setSize(width, height);
				host.placeSettingsDialog(dialog);
			} else {
				// 창 아래쪽 끝을 기준으로 크기를 바꾼다 (길어지면 위로 자라서 바닥에 붙지 않게).
				// 너비가 바뀌면 가운데를 기준으로 양쪽으로 넓어지고 좁아진다
				int bottom = dialog.getY() + dialog.getHeight();
				int centerX = dialog.getX() + dialog.getWidth() / 2;
				dialog.setBounds(centerX - width / 2, bottom - height, width, height);
				keepOnScreen();
			}
		}
		placedOnce = true;
	}

	/** 화면을 바꿔 창이 길어졌을 때 화면 밖으로 나가지 않게만 살짝 옮긴다 (그 외에는 그 자리 그대로). */
	private void keepOnScreen() {
		Rectangle screen = dialog.getGraphicsConfiguration() != null
			? dialog.getGraphicsConfiguration().getBounds()
			: new Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
		if (!screen.intersects(dialog.getBounds())) {
			return; // 화면 밖에 일부러 둔 창(미리보기 테스트 등)은 건드리지 않는다
		}
		int x = Math.max(screen.x, Math.min(dialog.getX(), screen.x + screen.width - dialog.getWidth()));
		int y = Math.max(screen.y, Math.min(dialog.getY(), screen.y + screen.height - dialog.getHeight()));
		if (x != dialog.getX() || y != dialog.getY()) {
			dialog.setLocation(x, y);
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

		// ---- 모습 꾸미기

		public void setParts(Object layoutJson) {
			if (layoutJson != null) {
				String json = String.valueOf(layoutJson);
				SwingUtilities.invokeLater(() -> host.setParts(json));
			}
		}

		public void pickPartImage(Object part, Object expression) {
			String partKey = part == null ? null : String.valueOf(part);
			String expressionKey = expression == null ? null : String.valueOf(expression);
			SwingUtilities.invokeLater(() -> host.pickPartImage(partKey, expressionKey));
		}

		public void removePartImage(Object part, Object expression) {
			String partKey = part == null ? null : String.valueOf(part);
			String expressionKey = expression == null ? null : String.valueOf(expression);
			SwingUtilities.invokeLater(() -> host.removePartImage(partKey, expressionKey));
		}

		/** 고급 시간 막대: 키 사진 고르기 → window.bitPickedImage(파일 이름 또는 null). */
		public void pickKeyImage() {
			SwingUtilities.invokeLater(() -> {
				String name = host.importPartImage();
				Platform.runLater(() -> {
					if (loaded) {
						engine.executeScript("window.bitPickedImage && window.bitPickedImage(" + (name == null ? "null" : jsString(name)) + ")");
					}
				});
			});
		}

		/** 도트 그림판 그림 저장 → window.bitDrawingSaved(파일 이름 또는 null). */
		public void saveDrawing(Object dataUrl) {
			String data = dataUrl == null ? "" : String.valueOf(dataUrl);
			SwingUtilities.invokeLater(() -> {
				String name = host.saveDrawing(data);
				Platform.runLater(() -> {
					if (loaded) {
						engine.executeScript("window.bitDrawingSaved && window.bitDrawingSaved(" + (name == null ? "null" : jsString(name)) + ")");
					}
				});
			});
		}

		/** 글자 묻기 (부위 이름 등) → window.bitTextAnswer(글자 또는 null). */
		public void askText(Object title, Object initial) {
			String titleText = title == null ? "" : String.valueOf(title);
			String initialText = initial == null ? "" : String.valueOf(initial);
			SwingUtilities.invokeLater(() -> {
				String answer = host.askText(titleText, initialText);
				Platform.runLater(() -> {
					if (loaded) {
						engine.executeScript("window.bitTextAnswer && window.bitTextAnswer(" + (answer == null ? "null" : jsString(answer)) + ")");
					}
				});
			});
		}

		public void resetParts() {
			SwingUtilities.invokeLater(host::resetParts);
		}

		// ---- 저장한 모습

		public void saveLook() {
			SwingUtilities.invokeLater(host::saveLook);
		}

		public void applyLook(Object id) {
			if (id != null) {
				String key = String.valueOf(id);
				SwingUtilities.invokeLater(() -> host.applyLook(key));
			}
		}

		public void renameLook(Object id) {
			if (id != null) {
				String key = String.valueOf(id);
				SwingUtilities.invokeLater(() -> host.renameLook(key));
			}
		}

		public void deleteLook(Object id) {
			if (id != null) {
				String key = String.valueOf(id);
				SwingUtilities.invokeLater(() -> host.deleteLook(key));
			}
		}

		/** JS 가 아직 없는 부위 사진을 달라고 하면 data URL (도트 그림은 픽셀도) 을 window.bitImage(이름, url, 픽셀) 로 보낸다. */
		public void requestImage(Object fileName) {
			if (fileName == null) {
				return;
			}
			String name = String.valueOf(fileName);
			SwingUtilities.invokeLater(() -> {
				String url = host.partImageDataUrl(name);
				String pixels = url == null ? null : host.partImagePixels(name);
				Platform.runLater(() -> {
					if (loaded) {
						engine.executeScript("window.bitImage && window.bitImage(" + jsString(name) + ", "
							+ (url == null ? "null" : jsString(url)) + ", " + (pixels == null ? "null" : jsString(pixels)) + ")");
					}
				});
			});
		}

		public void resize(Object contentHeight, Object contentWidth) {
			if (contentHeight instanceof Number number) {
				int height = (int) Math.ceil(number.doubleValue());
				int width = contentWidth instanceof Number w ? w.intValue() : WIDTH;
				SwingUtilities.invokeLater(() -> resizeTo(height, width));
			}
		}
	}
}
