package com.mypet.appearance;

import com.mypet.Constants;
import com.mypet.Util;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 사용자 지정 색 팔레트(16칸)와 실시간 미리보기를 포함한 색상 선택 창.
 * 파이썬 버전의 윈도우 ChooseColor 대화상자를 Swing 으로 대체 (윈도우/맥 공통).
 */
public final class ColorChooserDialog {

	/** selected: 확인 시 선택 색(취소면 null), customSlots: 갱신된 사용자 지정 색 슬롯. */
	public record Result(String selected, List<String> customSlots) {
	}

	private ColorChooserDialog() {
	}

	/** 모달로 열고 닫힐 때까지 기다린다. */
	public static Result show(Window owner, String initialColor, List<String> customColors, Consumer<String> livePreview) {
		String initial = Util.normalizeHexColor(initialColor);
		if (initial == null) {
			initial = Constants.DEFAULT_CHARACTER_COLOR;
		}
		List<String> slots = new ArrayList<>(customColors);
		while (slots.size() < Constants.CUSTOM_COLOR_SLOT_COUNT) {
			slots.add(Constants.CUSTOM_COLOR_DEFAULT);
		}
		List<String> slotColors = new ArrayList<>(slots.subList(0, Constants.CUSTOM_COLOR_SLOT_COUNT));

		JDialog dialog = new JDialog(owner, "캐릭터 색상 선택", JDialog.ModalityType.APPLICATION_MODAL);
		dialog.setAlwaysOnTop(true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		JColorChooser chooser = new JColorChooser(Util.toColor(initial));
		String[] lastPreview = {initial};
		if (livePreview != null) {
			chooser.getSelectionModel().addChangeListener(event -> {
				String colorCode = Util.toHex(chooser.getColor());
				if (colorCode.equals(lastPreview[0])) {
					return;
				}
				lastPreview[0] = colorCode;
				try {
					livePreview.accept(colorCode);
				} catch (RuntimeException ignored) {
					// 미리보기 실패는 무시
				}
			});
		}

		// 사용자 지정 색 슬롯 (윈도우 색 대화상자처럼 2줄 x 8칸)
		int[] currentSlot = {0};
		List<Swatch> swatches = new ArrayList<>();
		JPanel slotGrid = new JPanel(new GridLayout(2, 8, 4, 4));
		for (int i = 0; i < Constants.CUSTOM_COLOR_SLOT_COUNT; i++) {
			int index = i;
			Swatch swatch = new Swatch(Util.toColor(slotColors.get(i)));
			swatch.addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					currentSlot[0] = index;
					for (int s = 0; s < swatches.size(); s++) {
						swatches.get(s).setSelected(s == index);
					}
					chooser.setColor(swatches.get(index).color);
				}
			});
			swatches.add(swatch);
			slotGrid.add(swatch);
		}
		swatches.get(0).setSelected(true);

		JButton addCustom = new JButton("사용자 지정 색에 추가");
		addCustom.addActionListener(event -> {
			int index = currentSlot[0];
			String colorCode = Util.toHex(chooser.getColor());
			slotColors.set(index, colorCode);
			swatches.get(index).setColor(chooser.getColor());
			swatches.get(index).setSelected(false);
			currentSlot[0] = (index + 1) % Constants.CUSTOM_COLOR_SLOT_COUNT;
			swatches.get(currentSlot[0]).setSelected(true);
		});

		JPanel customPanel = new JPanel(new BorderLayout(0, 6));
		customPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createTitledBorder("사용자 지정 색"),
			BorderFactory.createEmptyBorder(4, 6, 6, 6)
		));
		customPanel.add(slotGrid, BorderLayout.CENTER);
		JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		addRow.add(addCustom);
		customPanel.add(addRow, BorderLayout.SOUTH);

		String[] selected = {null};
		JButton ok = new JButton("확인");
		ok.addActionListener(event -> {
			selected[0] = Util.toHex(chooser.getColor());
			dialog.dispose();
		});
		JButton cancel = new JButton("취소");
		cancel.addActionListener(event -> dialog.dispose());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		buttons.add(ok);
		buttons.add(cancel);

		JPanel bottom = new JPanel(new BorderLayout());
		bottom.add(customPanel, BorderLayout.CENTER);
		bottom.add(buttons, BorderLayout.SOUTH);

		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(chooser, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		dialog.setContentPane(content);
		dialog.getRootPane().setDefaultButton(ok);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				selected[0] = null;
			}
		});
		dialog.pack();
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true); // 모달: 닫힐 때까지 대기

		return new Result(selected[0], slotColors);
	}

	/** 클릭 가능한 색 견본 칸. */
	private static final class Swatch extends JComponent {
		private Color color;
		private boolean selected;

		Swatch(Color color) {
			this.color = color;
			setPreferredSize(new Dimension(24, 20));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		void setColor(Color color) {
			this.color = color;
			repaint();
		}

		void setSelected(boolean selected) {
			this.selected = selected;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			int w = getWidth();
			int h = getHeight();
			g.setColor(color);
			g.fillRect(0, 0, w, h);
			g.setColor(Color.DARK_GRAY);
			g.drawRect(0, 0, w - 1, h - 1);
			if (selected) {
				g.setColor(Color.BLACK);
				g.drawRect(1, 1, w - 3, h - 3);
				g.setColor(Color.WHITE);
				g.drawRect(2, 2, w - 5, h - 5);
			}
		}
	}
}
