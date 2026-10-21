package com.mypet;

import com.mypet.appearance.AppearanceMode;
import com.mypet.parts.PartImages;
import com.mypet.parts.PartLayout;
import com.mypet.ui.Ui;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** 설정 창 3개를 화면에 띄우지 않고 만들어서 build/preview/*.png 로 저장 (디자인 확인용). */
class SettingsDialogPreviewTest {
	@Test
	void settingsDialogsBuildAndRender() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless());
		Ui.installTheme();
		PetApp app = new PetApp();
		set(app, "baseCharacterColor", Constants.DEFAULT_CHARACTER_COLOR);
		set(app, "currentCharacterColor", Constants.DEFAULT_CHARACTER_COLOR);
		set(app, "browserAppearanceMode", AppearanceMode.AUTO);
		set(app, "browserIeAppearanceMode", AppearanceMode.OFF);
		set(app, "discordAppearanceMode", AppearanceMode.AUTO);
		set(app, "pixelHeartAppearanceMode", AppearanceMode.ALWAYS);
		set(app, "headsetAppearanceMode", AppearanceMode.AUTO);

		File out = new File("build/preview");
		assertTrue(out.isDirectory() || out.mkdirs());
		render(app, PetApp::buildSettingsDialog, new File(out, "settings-main.png"));
		render(app, PetApp::buildAppearanceDialog, new File(out, "settings-appearance.png"));
		render(app, PetApp::buildColorDialog, new File(out, "settings-color.png"));
		renderPet(app, new File(out, "pet-standing.png"));
		renderPetAtScale(app, 1.5, new File(out, "pet-150percent.png"));
		renderCustomPet(app, new File(out, "pet-custom.png"));
		System.out.println("preview saved: " + out.getAbsolutePath());
	}

	/** 윈도우 화면 배율(예: 150%)로 그린 뒤, 픽셀이 보이게 6배 확대해 저장. */
	private static void renderPetAtScale(PetApp app, double displayScale, File file) throws Exception {
		set(app, "expression", Constants.EXPR_NEUTRAL);
		java.lang.reflect.Method paint = PetApp.class.getDeclaredMethod("paintScene", Graphics2D.class);
		paint.setAccessible(true);
		int top = Constants.HEART_TOP_HEADROOM - 6;
		int height = Constants.WINDOW_HEIGHT - top;
		int w = (int) Math.ceil(Constants.WINDOW_WIDTH * displayScale);
		int h = (int) Math.ceil(height * displayScale);
		BufferedImage device = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = device.createGraphics();
		g.setColor(java.awt.Color.WHITE);
		g.fillRect(0, 0, w, h);
		g.scale(displayScale, displayScale);
		g.translate(0, -top);
		paint.invoke(app, g);
		g.dispose();
		int zoom = 6;
		BufferedImage big = new BufferedImage(w * zoom, h * zoom, BufferedImage.TYPE_INT_RGB);
		Graphics2D bg = big.createGraphics();
		bg.drawImage(device, 0, 0, w * zoom, h * zoom, null);
		bg.dispose();
		ImageIO.write(big, "png", file);
	}

	/** 모습 꾸미기: 큰 몸통(사진), 긴 팔, 눈 사진 하나로 바꾼 펫을 4배로 저장. */
	private static void renderCustomPet(PetApp app, File file) throws Exception {
		File dir = new File("build/preview/parts-test");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		File photo = new File(dir, "source.png");
		BufferedImage source = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = source.createGraphics();
		sg.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0xff8a65), 120, 80, new java.awt.Color(0x7e57c2)));
		sg.fillOval(0, 0, 120, 80);
		sg.dispose();
		ImageIO.write(source, "png", photo);
		PartImages images = new PartImages(dir.toPath());
		String bodyImage = images.importImage(photo);
		set(app, "partImages", images);

		PartLayout layout = PartLayout.defaults();
		layout.body().w = 60;
		layout.body().h = 40;
		layout.body().image = bodyImage;
		layout.leftArm().x = -16;
		layout.leftArm().y = 16;
		layout.leftArm().w = 16;
		layout.leftArm().h = 6;
		layout.leftLeg().y = 41;
		layout.leftLeg().x = 14;
		layout.leftLeg().h = 12;
		layout.leftArm().rotation = -30;
		layout.leftLeg().rotation = 15;
		layout.mirrorFrom(PartLayout.LEFT_ARM);
		layout.mirrorFrom(PartLayout.LEFT_LEG);
		layout.eyes().rotation = 8;
		layout.eyes().x = 18;
		layout.eyes().y = 14;
		layout.eyes().w = 24;
		layout.eyes().h = 10;
		app.applyLayout(layout);
		try {
			set(app, "expression", Constants.EXPR_NEUTRAL);
			java.lang.reflect.Method paint = PetApp.class.getDeclaredMethod("paintScene", Graphics2D.class);
			paint.setAccessible(true);
			int scale = 4;
			int top = Constants.HEART_TOP_HEADROOM - 6;
			int width = (int) Math.ceil((double) get(app, "windowWidth"));
			int height = (int) get(app, "windowHeight") - top;
			BufferedImage img = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			g.setColor(new java.awt.Color(0x202020));
			g.fillRect(0, 0, img.getWidth(), img.getHeight());
			g.scale(scale, scale);
			g.translate(0, -top);
			paint.invoke(app, g);
			g.dispose();
			ImageIO.write(img, "png", file);
		} finally {
			app.applyLayout(PartLayout.defaults());
		}
	}

	/** 가만히 서 있는 펫을 4배로 확대해 저장. */
	private static void renderPet(PetApp app, File file) throws Exception {
		set(app, "expression", Constants.EXPR_NEUTRAL);
		java.lang.reflect.Method paint = PetApp.class.getDeclaredMethod("paintScene", Graphics2D.class);
		paint.setAccessible(true);
		int scale = 4;
		int top = Constants.HEART_TOP_HEADROOM - 6;
		int height = Constants.WINDOW_HEIGHT - top;
		BufferedImage img = new BufferedImage(Constants.WINDOW_WIDTH * scale, height * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new java.awt.Color(0x202020));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.scale(scale, scale);
		g.translate(0, -top);
		paint.invoke(app, g);
		g.dispose();
		ImageIO.write(img, "png", file);
	}

	private static Object get(Object target, String name) throws ReflectiveOperationException {
		Field field = PetApp.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
		Field field = PetApp.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void render(PetApp app, Function<PetApp, JDialog> builder, File file) throws Exception {
		BufferedImage[] image = new BufferedImage[1];
		SwingUtilities.invokeAndWait(() -> {
			JDialog dialog = builder.apply(app);
			dialog.pack();
			dialog.setSize(Math.max(Constants.SETTINGS_DIALOG_FIXED_WIDTH, dialog.getWidth()), dialog.getHeight());
			Container content = dialog.getContentPane();
			content.setSize(dialog.getContentPane().getSize());
			dialog.validate();
			int scale = 2;
			BufferedImage img = new BufferedImage(content.getWidth() * scale, content.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = img.createGraphics();
			g.scale(scale, scale);
			content.printAll(g);
			g.dispose();
			dialog.dispose();
			image[0] = img;
		});
		ImageIO.write(image[0], "png", file);
	}
}
