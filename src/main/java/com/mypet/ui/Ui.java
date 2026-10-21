package com.mypet.ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 설정 창 디자인 공통 요소. 비트가 네모난 픽셀 캐릭터라서 레트로 게임 메뉴처럼 꾸민다:
 * 각진 상자, 진한 테두리, 블록 그림자, 픽셀 비트, 강조색은 비트의 현재 몸 색.
 */
public final class Ui {
	public static final Color PAPER = new Color(0xFBF6EC);
	public static final Color INK = new Color(0x26221E);
	public static final Color MUTED = new Color(0x7D7468);
	public static final Color BOX = Color.WHITE;
	public static final Color DANGER = new Color(0xC8402F);
	/** 블록 그림자: 현실 그림자처럼 살짝 비치게 (잉크색 30%). */
	public static final Color SHADOW_COLOR = new Color(0x26, 0x22, 0x1E, 77);
	private static final int BORDER = 2;
	private static final int SHADOW = 3;

	private static Supplier<Color> accent = () -> new Color(0x4FC3F7);

	private Ui() {
	}

	/** 앱 글꼴: 달서힐링체 (앱 안에 들어 있다: 대구광역시 달서구, 공공누리 제1유형 출처표시). */
	public static final String FONT_RESOURCE = "/com/mypet/fonts/DalseoHealingMedium.ttf";
	private static Font appFont;
	private static boolean appFontTried;

	/** 앱에 들어 있는 달서힐링체 (못 읽으면 null). 한 번 읽어서 시스템 글꼴 목록에도 올린다. */
	public static synchronized Font appFont() {
		if (!appFontTried) {
			appFontTried = true;
			try (java.io.InputStream in = Ui.class.getResourceAsStream(FONT_RESOURCE)) {
				if (in != null) {
					appFont = Font.createFont(Font.TRUETYPE_FONT, in);
					java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(appFont);
				}
			} catch (java.awt.FontFormatException | java.io.IOException | RuntimeException e) {
				appFont = null;
			}
		}
		return appFont;
	}

	/** FlatLaf 테마 적용 (색 선택 창·알림 창 등 기본 창용). 실패하면 OS 기본 테마. */
	public static void installTheme() {
		Font font = appFont();
		if (font != null) {
			// 이름 입력 창 같은 기본 창도 달서힐링체로
			UIManager.put("defaultFont", new javax.swing.plaf.FontUIResource(font.deriveFont(Font.PLAIN, 15f)));
		}
		try {
			FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#0288d1"));
			FlatLightLaf.setup();
			UIManager.put("Button.arc", 0);
			UIManager.put("Component.arc", 0);
		} catch (Throwable t) {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ignored) {
				// 기본 테마 사용
			}
		}
	}

	/** 강조색을 비트의 현재 몸 색에서 가져오도록 설정. */
	public static void setAccent(Supplier<Color> supplier) {
		accent = supplier;
	}

	static Color accent() {
		return accent.get();
	}

	/** 강조색을 흰색과 섞은 옅은 색 (호버 배경). */
	static Color accentLight() {
		Color c = accent();
		return new Color((c.getRed() + 255 * 2) / 3, (c.getGreen() + 255 * 2) / 3, (c.getBlue() + 255 * 2) / 3);
	}

	public static Font font(int style, float size) {
		Font base = appFont();
		if (base == null) {
			base = UIManager.getFont("Label.font");
		}
		if (base == null) {
			base = new Font(Font.DIALOG, Font.PLAIN, 12);
		}
		return base.deriveFont(style, size);
	}

	public static Font mono(int style, float size) {
		return new Font(Font.MONOSPACED, style, Math.round(size));
	}

	public static JLabel label(String text, int style, float size, Color color) {
		JLabel label = new JLabel(text);
		label.setFont(font(style, size));
		label.setForeground(color);
		return label;
	}

	/** 콘솔처럼 앞에 "> " 가 붙는 상태 표시줄. */
	public static JLabel status(String text) {
		JLabel label = new JLabel() {
			@Override
			public void setText(String value) {
				super.setText(value == null || value.isEmpty() ? "" : "> " + value);
			}
		};
		label.setFont(font(Font.PLAIN, 12f));
		label.setForeground(MUTED);
		label.setText(text);
		return label;
	}

	/** 세로로 쌓는 투명 패널. */
	public static JPanel column() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		return panel;
	}

	/** 컴포넌트를 가로로 꽉 채워 column 에 넣기. */
	public static void addFull(JPanel column, JComponent component, int topGap) {
		if (topGap > 0) {
			column.add(Box.createVerticalStrut(topGap));
		}
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		column.add(component);
	}

	/** 창 맨 위 진한 제목 막대: (◀) + 픽셀 비트 + 제목, 아래에 부제목. */
	public static JComponent header(String title, String subtitle, Supplier<Color> petColor, Runnable onBack) {
		JPanel bar = new JPanel(new BorderLayout(10, 0));
		bar.setBackground(INK);
		bar.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
		JPanel left = new JPanel(new BorderLayout(10, 0));
		left.setOpaque(false);
		if (onBack != null) {
			JLabel back = label("◀", Font.BOLD, 14f, PAPER);
			back.setToolTipText("뒤로가기");
			back.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			back.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					back.setForeground(accent());
				}

				@Override
				public void mouseExited(MouseEvent e) {
					back.setForeground(PAPER);
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					onBack.run();
				}
			});
			left.add(back, BorderLayout.WEST);
		}
		left.add(new JLabel(petIcon(0.6, petColor)), BorderLayout.CENTER);
		bar.add(left, BorderLayout.WEST);
		bar.add(label(title, Font.BOLD, 16f, PAPER), BorderLayout.CENTER);

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(bar, BorderLayout.NORTH);
		if (subtitle != null) {
			JLabel sub = label(subtitle, Font.PLAIN, 12f, MUTED);
			sub.setBorder(BorderFactory.createEmptyBorder(10, 16, 0, 16));
			header.add(sub, BorderLayout.SOUTH);
		}
		return header;
	}

	// ---------------------------------------------------------------- 상자/버튼

	/** 각진 테두리 + 오른쪽 아래 블록 그림자가 있는 상자. */
	public static class FramedPanel extends JPanel {
		public FramedPanel(LayoutManager layout, int padding) {
			super(layout);
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(padding + BORDER, padding + BORDER, padding + BORDER + SHADOW, padding + BORDER + SHADOW));
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			int w = getWidth() - SHADOW;
			int h = getHeight() - SHADOW;
			graphics.setColor(SHADOW_COLOR);
			graphics.fillRect(SHADOW, SHADOW, w, h);
			graphics.setColor(INK);
			graphics.fillRect(0, 0, w, h);
			graphics.setColor(BOX);
			graphics.fillRect(BORDER, BORDER, w - BORDER * 2, h - BORDER * 2);
			super.paintComponent(graphics);
		}
	}

	public enum ButtonStyle { NORMAL, PRIMARY, DANGER }

	/** 눌리면 그림자 쪽으로 쑥 들어가는 각진 버튼. */
	private static void paintPixelButton(Graphics graphics, JComponent c, String text, Font font,
		ButtonModel model, Color face, Color textColor) {
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int w = c.getWidth() - SHADOW;
		int h = c.getHeight() - SHADOW;
		boolean pressed = model.isArmed() && model.isPressed();
		int offset = pressed ? SHADOW : 0;
		if (!pressed) {
			g.setColor(SHADOW_COLOR);
			g.fillRect(SHADOW, SHADOW, w, h);
		}
		g.setColor(INK);
		g.fillRect(offset, offset, w, h);
		g.setColor(face);
		g.fillRect(offset + BORDER, offset + BORDER, w - BORDER * 2, h - BORDER * 2);
		g.setFont(font);
		g.setColor(textColor);
		FontMetrics metrics = g.getFontMetrics();
		int tx = offset + (w - metrics.stringWidth(text)) / 2;
		int ty = offset + (h - metrics.getHeight()) / 2 + metrics.getAscent();
		g.drawString(text, tx, ty);
		g.dispose();
	}

	private static void preparePixelButton(javax.swing.AbstractButton button, Font font) {
		button.setFont(font);
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setFocusPainted(false);
		button.setOpaque(false);
		button.setRolloverEnabled(true);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setMargin(new Insets(0, 0, 0, 0));
	}

	public static JButton button(String text, ButtonStyle style) {
		Font font = font(style == ButtonStyle.PRIMARY ? Font.BOLD : Font.PLAIN, 13f);
		JButton button = new JButton(text) {
			@Override
			protected void paintComponent(Graphics graphics) {
				ButtonModel model = getModel();
				Color face = switch (style) {
					case PRIMARY -> model.isRollover() ? accentLight() : accent();
					case NORMAL, DANGER -> model.isRollover() ? new Color(0xF1ECE2) : BOX;
				};
				Color textColor = style == ButtonStyle.DANGER ? DANGER : INK;
				paintPixelButton(graphics, this, getText(), getFont(), model, face, textColor);
			}

			@Override
			public Dimension getPreferredSize() {
				FontMetrics metrics = getFontMetrics(getFont());
				return new Dimension(metrics.stringWidth(getText()) + 32 + SHADOW, 34 + SHADOW);
			}
		};
		preparePixelButton(button, font);
		return button;
	}

	/** 게임 메뉴 한 줄: 마우스를 올리면 ▶ 커서가 나타난다. */
	public static JComponent menuRow(String title, String description, Runnable action) {
		JComponent row = new JComponent() {
			private boolean hover;

			{
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				setPreferredSize(new Dimension(200, 48 + SHADOW));
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						hover = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e) {
						hover = false;
						repaint();
					}

					@Override
					public void mouseClicked(MouseEvent e) {
						action.run();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics graphics) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				int w = getWidth() - SHADOW;
				int h = getHeight() - SHADOW;
				g.setColor(SHADOW_COLOR);
				g.fillRect(SHADOW, SHADOW, w, h);
				g.setColor(INK);
				g.fillRect(0, 0, w, h);
				g.setColor(hover ? accentLight() : BOX);
				g.fillRect(BORDER, BORDER, w - BORDER * 2, h - BORDER * 2);
				int textX = 16;
				if (hover) {
					g.setColor(INK);
					g.setFont(font(Font.BOLD, 12f));
					FontMetrics arrow = g.getFontMetrics();
					g.drawString("▶", 12, (h - arrow.getHeight()) / 2 + arrow.getAscent());
					textX = 30;
				}
				g.setFont(font(Font.BOLD, 14f));
				g.setColor(INK);
				FontMetrics titleMetrics = g.getFontMetrics();
				g.drawString(title, textX, (h - titleMetrics.getHeight()) / 2 + titleMetrics.getAscent());
				g.setFont(font(Font.PLAIN, 12f));
				g.setColor(MUTED);
				FontMetrics descMetrics = g.getFontMetrics();
				g.drawString(description, w - 14 - descMetrics.stringWidth(description),
					(h - descMetrics.getHeight()) / 2 + descMetrics.getAscent());
				g.dispose();
			}
		};
		return row;
	}

	/** 여러 설정 줄을 한 상자에 담고 줄 사이에 점선을 긋는다. */
	public static JComponent optionList(List<JComponent> rows) {
		FramedPanel box = new FramedPanel(new BorderLayout(), 0);
		JPanel list = column();
		for (int i = 0; i < rows.size(); i++) {
			JComponent row = rows.get(i);
			row.setBorder(BorderFactory.createEmptyBorder(9, 12, 10, 12));
			if (i > 0) {
				list.add(dottedDivider());
			}
			addFull(list, row, 0);
		}
		box.add(list, BorderLayout.CENTER);
		return box;
	}

	private static JComponent dottedDivider() {
		JComponent divider = new JComponent() {
			@Override
			protected void paintComponent(Graphics graphics) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.setColor(new Color(0xCFC7B8));
				g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] {2f, 3f}, 0f));
				g.drawLine(10, 0, getWidth() - 10, 0);
				g.dispose();
			}
		};
		divider.setPreferredSize(new Dimension(10, 1));
		divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		divider.setAlignmentX(Component.LEFT_ALIGNMENT);
		return divider;
	}

	/** 설정 한 줄: 제목 + 언제 바뀌는지, 아래에 컨트롤. */
	public static JComponent optionRow(String title, String description, JComponent control) {
		JPanel row = new JPanel(new BorderLayout(0, 7));
		row.setOpaque(false);
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setOpaque(false);
		top.add(label(title, Font.BOLD, 13f, INK), BorderLayout.WEST);
		if (description != null) {
			top.add(label(description, Font.PLAIN, 11f, MUTED), BorderLayout.EAST);
		}
		row.add(top, BorderLayout.NORTH);
		row.add(control, BorderLayout.CENTER);
		return row;
	}

	/** 여러 값 중 하나를 고르는 각진 버튼 묶음. 고른 칸은 비트 색으로 채워진다. */
	public static <T> JComponent segmented(List<T> values, List<String> labels, T selected, Consumer<T> onChange) {
		JPanel row = new JPanel(new GridLayout(1, values.size(), 6, 0));
		row.setOpaque(false);
		ButtonGroup group = new ButtonGroup();
		for (int i = 0; i < values.size(); i++) {
			T value = values.get(i);
			Font plain = font(Font.PLAIN, 12f);
			Font bold = font(Font.BOLD, 12f);
			JToggleButton button = new JToggleButton(labels.get(i)) {
				@Override
				protected void paintComponent(Graphics graphics) {
					ButtonModel model = getModel();
					Color face = isSelected() ? accent() : (model.isRollover() ? new Color(0xF1ECE2) : BOX);
					paintPixelButton(graphics, this, getText(), isSelected() ? bold : plain, model, face, INK);
				}

				@Override
				public Dimension getPreferredSize() {
					return new Dimension(60, 28 + SHADOW);
				}
			};
			preparePixelButton(button, plain);
			button.setSelected(value.equals(selected));
			button.addActionListener(e -> onChange.accept(value));
			group.add(button);
			row.add(button);
		}
		return row;
	}

	// ---------------------------------------------------------------- 비트 아이콘

	/**
	 * 가만히 서 있는 실제 비트 모양 (앱과 같은 치수: 몸통 44x28, 팔/다리 8x8, 외곽선, 동그란 눈).
	 * scale 배로 그리고, 색은 그릴 때마다 supplier 에서 읽는다.
	 */
	public static Icon petIcon(double scale, Supplier<Color> color) {
		// 전체 범위: x -9..53, y 0..37 (몸통 왼쪽 위가 0,0)
		int width = (int) Math.ceil(62 * scale);
		int height = (int) Math.ceil(37 * scale);
		return new Icon() {
			@Override
			public int getIconWidth() {
				return width;
			}

			@Override
			public int getIconHeight() {
				return height;
			}

			@Override
			public void paintIcon(Component c, Graphics graphics, int x, int y) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.translate(x + 9 * scale, y);
				Color body = color.get();
				outlined(g, scale, 0, 0, 44, 28, body); // 몸통
				outlined(g, scale, 10, 29, 18, 37, body); // 왼다리
				outlined(g, scale, 25, 29, 33, 37, body); // 오른다리
				outlined(g, scale, -9, 14, -1, 22, body); // 왼팔
				outlined(g, scale, 45, 14, 53, 22, body); // 오른팔
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(Color.BLACK);
				g.fill(new java.awt.geom.Ellipse2D.Double(14 * scale, 12 * scale, 6 * scale, 6 * scale)); // 왼눈
				g.fill(new java.awt.geom.Ellipse2D.Double(26 * scale, 12 * scale, 6 * scale, 6 * scale)); // 오른눈
				g.dispose();
			}
		};
	}

	/** 외곽선이 맨 바깥에만 오도록 검은 사각형을 채우고 안쪽에 몸 색을 칠한다. */
	private static void outlined(Graphics2D g, double scale, int x1, int y1, int x2, int y2, Color fill) {
		int left = (int) Math.round(x1 * scale);
		int top = (int) Math.round(y1 * scale);
		int right = (int) Math.round(x2 * scale);
		int bottom = (int) Math.round(y2 * scale);
		int border = Math.max(1, (int) Math.round(scale));
		g.setColor(Color.BLACK);
		g.fillRect(left, top, right - left, bottom - top);
		g.setColor(fill);
		g.fillRect(left + border, top + border, right - left - border * 2, bottom - top - border * 2);
	}
}
