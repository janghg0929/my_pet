package com.mypet.ui;

import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiIconsTest {
	@Test
	void appIconsCoverSmallToLargeSizes() {
		List<Image> icons = Ui.appIcons();
		assertEquals(9, icons.size());
		assertEquals(16, new javax.swing.ImageIcon(icons.get(0)).getIconWidth());
		assertEquals(256, new javax.swing.ImageIcon(icons.get(icons.size() - 1)).getIconWidth());
		assertTrue(Ui.class.getResource("/com/mypet/icons/icon-32.png") != null);
	}
}
