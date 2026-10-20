package com.mypet;

import com.mypet.appearance.AppearanceDetection;
import com.mypet.appearance.AppearanceDetection.WindowState;
import com.mypet.appearance.AppearanceMode;
import com.mypet.appearance.AppearanceSettings;
import com.mypet.appearance.ColorChooserDialog;
import com.mypet.appearance.ColorSettings;
import com.mypet.appearance.rendering.AppearanceRendering;
import com.mypet.appearance.rendering.Draw;
import com.mypet.platform.PlatformSupport;
import com.mypet.platform.PlatformSupport.PlatformRect;
import com.mypet.update.UpdateService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.mypet.Constants.*;

/**
 * my_pet.py main() 포팅. 파이썬의 nonlocal 변수들은 필드로, Tk Canvas 도형은 매 프레임 직접 그리기로 바꿨다.
 * 모든 메서드는 Swing 이벤트 스레드(EDT)에서만 호출된다.
 */
final class PetApp {
	private final PlatformSupport platform = PlatformSupport.create();
	private final Random random = new Random();

	private JFrame root;
	private PetCanvas canvas;
	private Timer frameTimer;
	private long lastTimerNanos;
	private double tickAccumulatorMs;
	private boolean geometryChanged;
	private Rectangle appliedBounds;

	// 작업 영역/창 크기
	private int workLeft;
	private int workTop;
	private int workRight;
	private int workBottom;
	private double windowWidth = WINDOW_WIDTH;
	private double appearanceLeftMargin = APPEARANCE_EXTRA_MARGIN;
	private double appearanceRightMargin = APPEARANCE_EXTRA_MARGIN;
	private double maxX;
	private double maxY;
	private double minY;
	private List<PlatformRect> windowPlatformRects = new ArrayList<>();
	private int platformRefreshMs = 0;

	// 위치/물리
	private double posX;
	private double posY;
	private double targetX;
	private double targetY;
	private double prevPosX;
	private double prevPosY;
	private boolean autoMoveActive = false;
	private double autoMoveTargetX;
	private double autoMoveSpeed = 0.0;
	private int autoWaitMs;
	private double walkPhase = 0.0;
	private double walkBobAmp = 0.0;
	private double walkBobCurrent = 0.0;
	private double velocityY = 0.0;
	private double velocityX = 0.0;
	private boolean throwActive = false;
	private boolean gravityActive;
	private double fallElapsedMs = 0.0;
	private double fallPeakY;
	private int supportGraceMs = SUPPORT_GRACE_MS;
	private Double lastSupportTop = null;
	private double limbOffsetX = 0.0;
	private double limbOffsetY = 0.0;
	private double limbTargetX = 0.0;
	private double limbTargetY = 0.0;
	private double limbSwayPhase = 0.0;
	private double limbSwayAmp = 0.0;
	private double limbSwayX = 0.0;

	// 드래그
	private boolean dragging = false;
	private double dragOffsetX = 0.0;
	private double dragOffsetY = 0.0;
	private double dragPrevX;
	private double dragPrevY;
	private long dragPrevTimeNanos = System.nanoTime();
	private double dragVelocityX = 0.0;
	private double dragVelocityY = 0.0;

	// 표정/눈
	private String expression = "";
	private double eyeDirShiftCurrent = 0.0;
	private double eyeDirShiftTarget = 0.0;
	private double eyeDirShiftYCurrent = 0.0;
	private double eyeDirShiftYTarget = 0.0;
	private double eyeDrawnShift = 0.0;
	private double eyeDrawnShiftY = 0.0;
	private double eyeDrawnInvalidOffset = 0.0;

	// 쓰다듬기
	private Double petPrevCursorX = null;
	private int petPrevMoveDir = 0;
	private int petFlipCount = 0;
	private int petWindowMs = 0;
	private int petFaceMs = 0;
	private int petHeartSpawnMs = 0;
	private final List<PetHeart> petHearts = new ArrayList<>();

	// 색상/외형 설정
	private String baseCharacterColor;
	private AppearanceMode browserAppearanceMode;
	private AppearanceMode browserIeAppearanceMode;
	private AppearanceMode discordAppearanceMode;
	private AppearanceMode pixelHeartAppearanceMode;
	private AppearanceMode headsetAppearanceMode;
	private JDialog settingsDialog;
	private JDialog colorDialog;
	private JDialog appearanceDialog;
	private boolean colorDialogOpen = false;
	private boolean colorErrorShakeActive = false;
	private double colorErrorShakePhase = 0.0;
	private double colorErrorEyeOffsetX = 0.0;

	// 앱 감지
	private String appMode;
	private boolean discordVisible;
	private int appStateCheckMs = APP_STATE_CHECK_INTERVAL_MS;
	private int appModeHoldMs = 0;
	private String currentCharacterColor;
	private int jumpChainRemaining = 0;
	private int cursorResumeDelayMs = 0;
	private int clickChainCount = 0;
	private long lastClickTimeNanos = System.nanoTime() - 60_000_000_000L;
	private boolean rainbowActive = false;
	private double rainbowElapsedMs = 0.0;
	private double rainbowPhase = 0.0;

	// 캐릭터 도형 기준 좌표
	private double charOriginX = DRAW_MARGIN + APPEARANCE_EXTRA_MARGIN + SIDE_EXTENSION;
	private final double charOriginY = DRAW_MARGIN + HEART_TOP_HEADROOM;
	private final int charFaceWidth = CHAR_WIDTH - (DRAW_MARGIN * 2);
	private final int charFaceHeight = CHAR_HEIGHT - (DRAW_MARGIN * 2);
	private final double eyeShapeScale = Math.min(charFaceWidth, charFaceHeight) / (double) BASE_FACE_SIZE;
	private final double eyeShapeWidth = BASE_FACE_SIZE * eyeShapeScale;
	private final double eyeShapeHeight = BASE_FACE_SIZE * eyeShapeScale;
	private double eyeShapeOffsetX = charOriginX + ((charFaceWidth - eyeShapeWidth) / 2);
	private final double eyeShapeOffsetY = charOriginY + ((charFaceHeight - eyeShapeHeight) / 2);
	private final int border = Math.max(1, Util.pyRound(2.0 * Math.min(charFaceWidth, charFaceHeight) / BASE_FACE_SIZE));

	private final double armY = charOriginY + Math.floorDiv(charFaceHeight * 2, 3) - Math.floorDiv(LIMB_SIZE, 2);
	private final int armRaiseOffset = Math.max(2, LIMB_SIZE);
	private double leftArmLeft = charOriginX - LIMB_GAP - LIMB_SIZE;
	private double leftArmRight = charOriginX - LIMB_GAP;
	private double rightArmLeft = charOriginX + charFaceWidth + LIMB_GAP;
	private double rightArmRight = charOriginX + charFaceWidth + LIMB_GAP + LIMB_SIZE;
	private double armTopCurrent = armY;
	private double armTopTarget = armY;
	private final double legY = charOriginY + charFaceHeight + LIMB_GAP;
	private double leftLegX = charOriginX + Math.floorDiv(charFaceWidth, 3) - Math.floorDiv(LIMB_SIZE, 2);
	private double rightLegX = charOriginX + Math.floorDiv(charFaceWidth * 2, 3) - Math.floorDiv(LIMB_SIZE, 2);
	private final double footContactOffset = legY + LIMB_SIZE;

	private final double monocleRadius = Math.max(2.0, Math.min(charFaceWidth, charFaceHeight) * 0.2);
	private final int monocleStroke = Math.max(2, Util.pyRound((border * 2) * MAGNIFIER_RING_THICKNESS));
	private final int monocleHandleStroke = Math.max(2, Util.pyRound(monocleStroke * MAGNIFIER_HANDLE_THICKNESS));
	private final double ieIconRadius = Math.max(6.0, Math.min(charFaceWidth, charFaceHeight) * 0.18);
	private final int ieIconOrbitStroke = Math.max(2, Util.pyRound(border * 1.5));
	private final int ieIconBarStroke = Math.max(2, Util.pyRound(border * 1.4));
	private final int ieIconTextSize = Math.max(12, Util.pyRound(ieIconRadius * 2.2));
	private final Font ieIconFont = new Font("Arial", Font.BOLD, 1).deriveFont(AppearanceRendering.tkPointsToPixels(ieIconTextSize));
	private final int headsetStroke = Math.max(2, Util.pyRound(border * 1.4));

	/** 쓰다듬기 하트 하나. render* 는 마지막으로 그린 모양. */
	private static final class PetHeart {
		double x;
		double y;
		double size;
		double vx;
		double rise;
		double ageMs;
		double lifeMs;
		double renderX;
		double renderY;
		double renderSize;
		String renderColor;
	}

	// ---------------------------------------------------------------- 시작

	void start() {
		platform.onStartup();
		root = new JFrame("MY_PET");
		configureOverlayWindow(root);

		baseCharacterColor = ColorSettings.loadSavedCharacterColor();
		AppearanceSettings settings = AppearanceSettings.load();
		browserAppearanceMode = settings.browserMonocle();
		browserIeAppearanceMode = settings.browserIeIcon();
		discordAppearanceMode = settings.discordColor();
		pixelHeartAppearanceMode = settings.pixelHeart();
		headsetAppearanceMode = settings.headset();

		// 작업 영역 (윈도우 작업표시줄, 맥 메뉴바/독 제외)
		Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		workLeft = work.x;
		workTop = work.y;
		workRight = work.x + work.width;
		workBottom = work.y + work.height;
		maxX = workRight - windowWidth;
		maxY = workBottom - WINDOW_HEIGHT;
		minY = workTop - (DRAW_MARGIN + HEART_TOP_HEADROOM);

		posX = Math.floor((workLeft + maxX) / 2);
		posY = Math.floor((workTop + maxY) / 2);
		targetX = posX;
		targetY = posY;
		prevPosX = posX;
		prevPosY = posY;
		autoMoveTargetX = posX;
		autoWaitMs = randInt(AUTO_WAIT_MIN_MS, AUTO_WAIT_MAX_MS);
		gravityActive = posY < maxY;
		fallPeakY = posY;
		dragPrevX = posX;
		dragPrevY = posY;

		WindowState windowState = AppearanceDetection.getWindowState(platform);
		appMode = AppearanceDetection.resolveAppMode(windowState, null);
		discordVisible = windowState.discordVisible();
		currentCharacterColor = isDiscordColorEnabled() ? DISCORD_ICON_BG_COLOR : baseCharacterColor;

		canvas = new PetCanvas();
		root.setContentPane(canvas);
		installInputHandlers();

		setExpression(EXPR_NEUTRAL, false);
		double[] margins = getAppearanceMargins();
		applyAppearanceMargins(margins[0], margins[1]);

		applyGeometry();
		root.setVisible(true);

		lastTimerNanos = System.nanoTime();
		frameTimer = new Timer(4, event -> onFrameTimer());
		frameTimer.setCoalesce(true);
		frameTimer.start();
		tick();

		// 시작 시 업데이트 확인 (백그라운드)
		UpdateService.checkForUpdatesAsync(root, this::exitApplication);
	}

	/** 테두리 없음·항상 위·투명 배경 오버레이 창 구성 (my_pet_platform.configure_overlay_window). */
	private static void configureOverlayWindow(JFrame frame) {
		frame.setUndecorated(true);
		if (IS_WINDOWS) {
			// 작업표시줄/Alt+Tab 에 표시하지 않음
			frame.setType(Window.Type.UTILITY);
		}
		frame.setAlwaysOnTop(true);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.getRootPane().putClientProperty("Window.shadow", Boolean.FALSE);
		frame.getRootPane().putClientProperty("apple.awt.draggableWindowBackground", Boolean.FALSE);
		GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
		if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
			frame.setBackground(new Color(0, 0, 0, 0));
		}
	}

	private void exitApplication() {
		if (frameTimer != null) {
			frameTimer.stop();
		}
		System.exit(0);
	}

	private int randInt(int min, int max) {
		return min + random.nextInt(max - min + 1);
	}

	private double uniform(double min, double max) {
		return min + (max - min) * random.nextDouble();
	}

	/** 실제 시간 기준으로 64Hz 고정 틱을 돌린다 (파이썬 Tk 버전의 실제 속도와 동일). */
	private void onFrameTimer() {
		long now = System.nanoTime();
		tickAccumulatorMs += (now - lastTimerNanos) / 1_000_000.0;
		lastTimerNanos = now;
		int steps = 0;
		while (tickAccumulatorMs >= TICK_PERIOD_MS && steps < 4) {
			tick();
			tickAccumulatorMs -= TICK_PERIOD_MS;
			steps++;
		}
		if (tickAccumulatorMs > TICK_PERIOD_MS * 4) {
			// 오래 멈췄다가 깨어난 경우 따라잡지 않는다
			tickAccumulatorMs = 0;
		}
	}

	// ---------------------------------------------------------------- 창 좌표

	/** 창 좌표를 현재 상태로 적용. */
	private void applyGeometry() {
		double drawY = posY + walkBobCurrent;
		Rectangle bounds = new Rectangle((int) posX, (int) drawY, (int) windowWidth, WINDOW_HEIGHT);
		if (!bounds.equals(appliedBounds)) {
			appliedBounds = bounds;
			root.setBounds(bounds);
		}
	}

	/** 캐릭터 아래 플랫폼 상단 좌표를 수집. */
	private List<Double> getPlatformTops(double spanLeft, double spanRight) {
		List<Double> tops = new ArrayList<>();
		tops.add((double) workBottom);
		for (PlatformRect rect : windowPlatformRects) {
			if (rect.top() >= workBottom - TASKBAR_PLATFORM_IGNORE_MARGIN) {
				continue;
			}
			double overlap = Math.min(spanRight, rect.right()) - Math.max(spanLeft, rect.left());
			if (overlap >= PLATFORM_MIN_OVERLAP) {
				tops.add((double) rect.top());
			}
		}
		return tops;
	}

	// ---------------------------------------------------------------- 외형 판정

	private boolean isDiscordColorEnabled() {
		return discordAppearanceMode.isEnabled(APP_MODE_DISCORD.equals(appMode) && discordVisible);
	}

	private boolean isBrowserMonocleEnabled() {
		return browserAppearanceMode.isEnabled(APP_MODE_BROWSER.equals(appMode));
	}

	private boolean isBrowserIeIconEnabled() {
		return browserIeAppearanceMode.isEnabled(APP_MODE_BROWSER.equals(appMode));
	}

	private boolean isPixelHeartEnabled() {
		return pixelHeartAppearanceMode.isEnabled(APP_MODE_STEAM.equals(appMode));
	}

	private boolean isMediaHeadsetEnabled() {
		return headsetAppearanceMode.isEnabled(APP_MODE_MEDIA.equals(appMode));
	}

	/** 현재 켜진 외형에 필요한 좌우 추가 여백 [left, right]. */
	private double[] getAppearanceMargins() {
		double leftMargin = APPEARANCE_EXTRA_MARGIN;
		double rightMargin = APPEARANCE_EXTRA_MARGIN;
		if (isBrowserIeIconEnabled()) {
			leftMargin = Math.max(leftMargin, 32.0);
		}
		if (isBrowserMonocleEnabled()) {
			rightMargin = Math.max(rightMargin, 32.0);
		}
		if (isMediaHeadsetEnabled()) {
			leftMargin = Math.max(leftMargin, 4.0);
			rightMargin = Math.max(rightMargin, 4.0);
		}
		return new double[] {leftMargin, rightMargin};
	}

	/** 외형 여백 변경을 창 크기와 캔버스 좌표에 반영. */
	private void applyAppearanceMargins(double leftMargin, double rightMargin) {
		if (Math.abs(leftMargin - appearanceLeftMargin) < 0.01 && Math.abs(rightMargin - appearanceRightMargin) < 0.01) {
			return;
		}

		double deltaLeft = leftMargin - appearanceLeftMargin;
		appearanceLeftMargin = leftMargin;
		appearanceRightMargin = rightMargin;
		windowWidth = CHAR_WIDTH + (SIDE_EXTENSION * 2) + (DRAW_MARGIN * 2) + appearanceLeftMargin + appearanceRightMargin;
		maxX = workRight - windowWidth;

		charOriginX += deltaLeft;
		eyeShapeOffsetX += deltaLeft;
		leftArmLeft += deltaLeft;
		leftArmRight += deltaLeft;
		rightArmLeft += deltaLeft;
		rightArmRight += deltaLeft;
		leftLegX += deltaLeft;
		rightLegX += deltaLeft;
		for (PetHeart heart : petHearts) {
			heart.x += deltaLeft;
			heart.renderX += deltaLeft;
		}

		posX -= deltaLeft;
		prevPosX -= deltaLeft;
		targetX -= deltaLeft;
		autoMoveTargetX -= deltaLeft;
		dragPrevX -= deltaLeft;
		dragOffsetX += deltaLeft;

		posX = Util.clamp(posX, workLeft, maxX);
		prevPosX = Util.clamp(prevPosX, workLeft, maxX);
		targetX = Util.clamp(targetX, workLeft, maxX);
		autoMoveTargetX = Util.clamp(autoMoveTargetX, workLeft, maxX);
		applyGeometry();
		canvas.repaint();
	}

	// ---------------------------------------------------------------- 표정/색상

	/** 표정에 따른 팔 높이 갱신. */
	private void updateArmPose(String targetExpression) {
		boolean raised = EXPR_FALL.equals(targetExpression);
		armTopTarget = raised ? armY - armRaiseOffset : armY;
	}

	/** 표정 변경 및 눈 리드로잉. */
	private void setExpression(String newExpression, boolean forceRedraw) {
		if (!expression.equals(newExpression) || forceRedraw) {
			expression = newExpression;
			eyeDrawnShift = eyeDirShiftCurrent;
			eyeDrawnShiftY = eyeDirShiftYCurrent;
			eyeDrawnInvalidOffset = EXPR_INVALID.equals(newExpression) ? colorErrorEyeOffsetX : 0.0;
		}
		updateArmPose(newExpression);
	}

	/** 캐릭터 색상 변경. */
	private void setCharacterColor(String newColor) {
		currentCharacterColor = newColor;
	}

	/** 하트 생명 주기에 따른 색상 선택. */
	private static String getPetHeartColor(double progress) {
		int colorIndex = Math.min(PET_HEART_COLORS.size() - 1, (int) (progress * PET_HEART_COLORS.size()));
		String baseColor = PET_HEART_COLORS.get(colorIndex);
		double fadeProgress = Util.clamp((progress - 0.7) / 0.3, 0.0, 1.0);
		if (fadeProgress > 0.0) {
			return Util.lerpHexColors(baseColor, "#ffffff", fadeProgress);
		}
		return baseColor;
	}

	/** 쓰다듬기 하트 생성. */
	private void spawnPetHeart() {
		if (petHearts.size() >= PET_HEART_MAX_VISIBLE) {
			return;
		}
		PetHeart heart = new PetHeart();
		heart.size = uniform(PET_HEART_SIZE_MIN, PET_HEART_SIZE_MAX);
		heart.x = charOriginX + (charFaceWidth * uniform(PET_HEART_BODY_X_MIN, PET_HEART_BODY_X_MAX));
		double heartGap = uniform(PET_HEART_ABOVE_GAP_MIN, PET_HEART_ABOVE_GAP_MAX);
		heart.y = charOriginY - (heart.size * 0.64) - heartGap;
		heart.vx = uniform(-PET_HEART_DRIFT_X, PET_HEART_DRIFT_X);
		heart.rise = uniform(PET_HEART_RISE_MIN, PET_HEART_RISE_MAX);
		heart.ageMs = 0.0;
		heart.lifeMs = randInt(PET_HEART_LIFETIME_MIN_MS, PET_HEART_LIFETIME_MAX_MS);
		heart.renderX = heart.x;
		heart.renderY = heart.y;
		heart.renderSize = heart.size;
		heart.renderColor = PET_HEART_COLORS.get(0);
		petHearts.add(heart);
	}

	// ---------------------------------------------------------------- 설정 창

	private static boolean isDialogAlive(JDialog dialog) {
		return dialog != null && dialog.isDisplayable();
	}

	/** 설정 관련 다이얼로그 열림 상태 갱신. */
	private void refreshSettingsDialogOpenState() {
		colorDialogOpen = isDialogAlive(settingsDialog) || isDialogAlive(colorDialog) || isDialogAlive(appearanceDialog);
	}

	/** 설정 창 열릴 때 움직임 정지. */
	private void freezeMotionForSettings() {
		autoMoveActive = false;
		autoMoveTargetX = posX;
		targetX = posX;
		targetY = posY;
		velocityY = 0.0;
		velocityX = 0.0;
		throwActive = false;
		gravityActive = false;
		fallElapsedMs = 0.0;
		fallPeakY = posY;
		appModeHoldMs = APP_MODE_HOLD_ON_DRAG_MS;
	}

	/** 캐릭터 주변에 다이얼로그 배치. */
	private void placeDialogNearCharacter(JDialog dialog) {
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int dialogWidth = dialog.getWidth();
		int dialogHeight = dialog.getHeight();
		double dialogX = Util.clamp(posX + (windowWidth / 2) - (dialogWidth / 2.0), 0.0, Math.max(0, screen.width - dialogWidth));
		double preferredY = posY + walkBobCurrent - dialogHeight - 12;
		if (preferredY < 0) {
			preferredY = posY + walkBobCurrent + WINDOW_HEIGHT + 12;
		}
		double dialogY = Util.clamp(preferredY, 0.0, Math.max(0, screen.height - dialogHeight));
		dialog.setLocation((int) dialogX, (int) dialogY);
	}

	/** 공통 설정 창 틀 (항상 위, 크기 고정, 위에서부터 쌓는 세로 배치). */
	private JDialog createSettingsDialog(String title, Runnable onClose) {
		JDialog dialog = new JDialog(root, title, JDialog.ModalityType.MODELESS);
		dialog.setAlwaysOnTop(true);
		dialog.setResizable(false);
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				onClose.run();
			}
		});
		JPanel content = new JPanel(new GridBagLayout());
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		dialog.setContentPane(content);
		return dialog;
	}

	/** 위에서부터 한 줄씩 가로로 꽉 차게 추가 (Tk pack(fill="x") 대응). */
	private static void addRow(JDialog dialog, JComponent component, int topGap) {
		JPanel content = (JPanel) dialog.getContentPane();
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = content.getComponentCount();
		c.weightx = 1.0;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(topGap, 0, 0, 0);
		content.add(component, c);
	}

	/** 남는 공간을 아래로 밀고 크기를 고정한 뒤 캐릭터 근처에 띄운다. */
	private void showSettingsDialog(JDialog dialog) {
		JPanel content = (JPanel) dialog.getContentPane();
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = 0;
		filler.gridy = content.getComponentCount();
		filler.weighty = 1.0;
		content.add(new JPanel(), filler);
		dialog.setSize(SETTINGS_DIALOG_FIXED_WIDTH, SETTINGS_DIALOG_FIXED_HEIGHT);
		placeDialogNearCharacter(dialog);
		dialog.setVisible(true);
		refreshSettingsDialogOpenState();
	}

	private static JLabel statusLabel(String text) {
		JLabel label = new JLabel(text);
		label.setHorizontalAlignment(SwingConstants.LEFT);
		return label;
	}

	private void closeSettingsDialog() {
		if (isDialogAlive(settingsDialog)) {
			settingsDialog.dispose();
		}
		settingsDialog = null;
		refreshSettingsDialogOpenState();
		cursorResumeDelayMs = CURSOR_RESUME_DELAY_MS;
	}

	private void closeColorDialog() {
		if (isDialogAlive(colorDialog)) {
			colorDialog.dispose();
		}
		colorDialog = null;
		refreshSettingsDialogOpenState();
		cursorResumeDelayMs = CURSOR_RESUME_DELAY_MS;
	}

	private void closeAppearanceDialog() {
		if (isDialogAlive(appearanceDialog)) {
			appearanceDialog.dispose();
		}
		appearanceDialog = null;
		refreshSettingsDialogOpenState();
		cursorResumeDelayMs = CURSOR_RESUME_DELAY_MS;
	}

	/** 메인 설정 창 열기. */
	private void openSettingsDialog() {
		if (isDialogAlive(settingsDialog)) {
			settingsDialog.toFront();
			settingsDialog.requestFocus();
			return;
		}
		freezeMotionForSettings();

		JDialog dialog = createSettingsDialog("캐릭터 설정", this::closeSettingsDialog);
		settingsDialog = dialog;

		JButton appearanceButton = new JButton("외형 설정");
		appearanceButton.addActionListener(e -> {
			closeSettingsDialog();
			openAppearanceDialog();
		});
		JButton colorButton = new JButton("색깔 설정");
		colorButton.addActionListener(e -> {
			closeSettingsDialog();
			openColorDialog();
		});
		JButton closeButton = new JButton("닫기");
		closeButton.addActionListener(e -> closeSettingsDialog());
		// macOS 는 독/메뉴바 없이 실행되므로 종료 버튼을 둔다 (Esc 키로도 종료 가능)
		JButton quitButton = new JButton("MY_PET 종료");
		quitButton.addActionListener(e -> exitApplication());

		addRow(dialog, appearanceButton, 0);
		addRow(dialog, colorButton, 8);
		addRow(dialog, statusLabel("설정할 항목을 선택하세요"), 8);
		addRow(dialog, closeButton, 8);
		addRow(dialog, quitButton, 8);
		addRow(dialog, statusLabel("버전 " + UpdateService.currentVersion()), 8);
		showSettingsDialog(dialog);
	}

	/** 외형 설정 창 열기. */
	private void openAppearanceDialog() {
		if (isDialogAlive(appearanceDialog)) {
			appearanceDialog.toFront();
			appearanceDialog.requestFocus();
			return;
		}
		freezeMotionForSettings();

		JDialog dialog = createSettingsDialog("외형 설정", this::closeAppearanceDialog);
		appearanceDialog = dialog;
		JLabel status = statusLabel("캐릭터 외형을 설정할 수 있습니다");

		JComboBox<AppearanceMode> browserMenu = modeCombo(browserAppearanceMode);
		JComboBox<AppearanceMode> browserIeMenu = modeCombo(browserIeAppearanceMode);
		JComboBox<AppearanceMode> discordMenu = modeCombo(discordAppearanceMode);
		JComboBox<AppearanceMode> pixelMenu = modeCombo(pixelHeartAppearanceMode);
		JComboBox<AppearanceMode> headsetMenu = modeCombo(headsetAppearanceMode);

		Runnable applyAppearanceSettings = () -> {
			browserAppearanceMode = (AppearanceMode) browserMenu.getSelectedItem();
			browserIeAppearanceMode = (AppearanceMode) browserIeMenu.getSelectedItem();
			discordAppearanceMode = (AppearanceMode) discordMenu.getSelectedItem();
			pixelHeartAppearanceMode = (AppearanceMode) pixelMenu.getSelectedItem();
			headsetAppearanceMode = (AppearanceMode) headsetMenu.getSelectedItem();
			Path savedPath = new AppearanceSettings(
				browserAppearanceMode,
				browserIeAppearanceMode,
				discordAppearanceMode,
				pixelHeartAppearanceMode,
				headsetAppearanceMode
			).save();
			appModeHoldMs = APP_MODE_HOLD_ON_DRAG_MS;
			setCharacterColor(isDiscordColorEnabled() ? DISCORD_ICON_BG_COLOR : baseCharacterColor);
			double[] margins = getAppearanceMargins();
			applyAppearanceMargins(margins[0], margins[1]);
			canvas.repaint();
			status.setText(savedPath == null ? "외형 설정 적용됨(저장 실패)" : "외형 설정 적용됨");
		};

		String[] labels = {"브라우저 돋보기", "브라우저 IE 아이콘", "디스코드 색상 변경", "스팀 픽셀 하트", "음악 헤드셋"};
		List<JComboBox<AppearanceMode>> menus = List.of(browserMenu, browserIeMenu, discordMenu, pixelMenu, headsetMenu);
		for (int i = 0; i < labels.length; i++) {
			addRow(dialog, statusLabel(labels[i]), i == 0 ? 0 : 8);
			addRow(dialog, menus.get(i), 4);
			menus.get(i).addActionListener(e -> applyAppearanceSettings.run());
		}

		JButton back = new JButton("뒤로가기");
		back.addActionListener(e -> {
			closeAppearanceDialog();
			openSettingsDialog();
		});
		addRow(dialog, status, 16);
		addRow(dialog, back, 8);
		showSettingsDialog(dialog);
	}

	private static JComboBox<AppearanceMode> modeCombo(AppearanceMode selected) {
		JComboBox<AppearanceMode> combo = new JComboBox<>(AppearanceMode.values());
		combo.setSelectedItem(selected);
		combo.setEditable(false);
		DefaultListCellRenderer renderer = new DefaultListCellRenderer();
		renderer.setHorizontalAlignment(SwingConstants.CENTER);
		combo.setRenderer(renderer);
		return combo;
	}

	/** 기본 색 미리 적용 (저장하지 않음). */
	private void previewBaseColor(String newColor) {
		baseCharacterColor = newColor;
		appModeHoldMs = APP_MODE_HOLD_ON_DRAG_MS;
		if (!isDiscordColorEnabled()) {
			setCharacterColor(baseCharacterColor);
		}
		canvas.repaint();
	}

	/** 기본 색 적용 및 저장. */
	private Path applyBaseColor(String newColor, boolean persistCustom) {
		baseCharacterColor = newColor;
		Path savedPath = null;
		if (persistCustom) {
			savedPath = ColorSettings.saveCustomCharacterColor(baseCharacterColor);
		} else {
			ColorSettings.clearSavedCharacterColor();
		}
		appModeHoldMs = APP_MODE_HOLD_ON_DRAG_MS;
		if (!isDiscordColorEnabled()) {
			setCharacterColor(baseCharacterColor);
		}
		canvas.repaint();
		return savedPath;
	}

	/** 색상 설정 창 열기. */
	private void openColorDialog() {
		if (isDialogAlive(colorDialog)) {
			colorDialog.toFront();
			colorDialog.requestFocus();
			return;
		}
		freezeMotionForSettings();

		JDialog dialog = createSettingsDialog("캐릭터 색상 설정", this::closeColorDialog);
		colorDialog = dialog;
		JLabel status = statusLabel("현재 색상: " + baseCharacterColor);

		JButton defaultButton = new JButton("기본 색");
		defaultButton.addActionListener(e -> {
			applyBaseColor(DEFAULT_CHARACTER_COLOR, false);
			status.setText("기본 색 적용: " + DEFAULT_CHARACTER_COLOR);
		});
		JButton customButton = new JButton("사용자 지정 적용");
		customButton.addActionListener(e -> {
			String[] liveSelectedColor = {null};
			ColorChooserDialog.Result result = ColorChooserDialog.show(
				dialog,
				baseCharacterColor,
				ColorSettings.loadCustomColorSlots(),
				colorCode -> {
					liveSelectedColor[0] = colorCode;
					previewBaseColor(colorCode);
					status.setText("미리 적용: " + colorCode);
				}
			);
			String selected = result.selected();
			if (selected == null) {
				selected = liveSelectedColor[0];
				if (selected == null) {
					status.setText("색 선택 취소");
					return;
				}
			}
			ColorSettings.saveCustomColorSlots(result.customSlots());
			Path savedPath = applyBaseColor(selected, true);
			status.setText(savedPath == null ? "선택 색 적용됨(저장 실패): " + selected : "선택 색 적용: " + selected);
		});

		JPanel buttonRow = new JPanel(new GridLayout(1, 2, 8, 0));
		buttonRow.add(defaultButton);
		buttonRow.add(customButton);
		JButton back = new JButton("뒤로가기");
		back.addActionListener(e -> {
			closeColorDialog();
			openSettingsDialog();
		});
		addRow(dialog, buttonRow, 0);
		addRow(dialog, status, 8);
		addRow(dialog, back, 8);
		showSettingsDialog(dialog);
	}

	// ---------------------------------------------------------------- 입력

	private void installInputHandlers() {
		MouseAdapter mouse = new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (!canvas.hitTest(e.getX(), e.getY())) {
					return;
				}
				if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
					onRightClick();
				} else if (e.getButton() == MouseEvent.BUTTON1) {
					onPress(e.getXOnScreen(), e.getYOnScreen());
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) {
					onDrag(e.getXOnScreen(), e.getYOnScreen());
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1) {
					onRelease();
				}
			}
		};
		canvas.addMouseListener(mouse);
		canvas.addMouseMotionListener(mouse);

		root.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "mypet-exit");
		root.getRootPane().getActionMap().put("mypet-exit", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				exitApplication();
			}
		});
	}

	/** 우클릭으로 설정 창 열기. */
	private void onRightClick() {
		openSettingsDialog();
	}

	/** 클릭 시작(드래그) 처리. */
	private void onPress(int xRoot, int yRoot) {
		if (colorDialogOpen) {
			return;
		}

		long now = System.nanoTime();
		if ((now - lastClickTimeNanos) / 1_000_000.0 > RAINBOW_CLICK_CHAIN_TIMEOUT_MS) {
			clickChainCount = 0;
		}
		lastClickTimeNanos = now;
		clickChainCount++;
		if (clickChainCount >= RAINBOW_SPARKLE_CLICK_COUNT) {
			clickChainCount = 0;
			rainbowActive = true;
			rainbowElapsedMs = 0.0;
			rainbowPhase = 0.0;
		}

		dragging = true;
		appModeHoldMs = APP_MODE_HOLD_ON_DRAG_MS;
		velocityY = 0.0;
		velocityX = 0.0;
		throwActive = false;
		gravityActive = false;
		fallElapsedMs = 0.0;
		fallPeakY = posY;
		jumpChainRemaining = 0;
		autoMoveActive = false;
		autoMoveTargetX = posX;
		autoWaitMs = randInt(AUTO_WAIT_MIN_MS, AUTO_WAIT_MAX_MS);
		targetX = posX;
		targetY = posY;
		dragOffsetX = xRoot - posX;
		dragOffsetY = yRoot - (posY + walkBobCurrent);
		dragPrevX = posX;
		dragPrevY = posY;
		dragPrevTimeNanos = System.nanoTime();
		dragVelocityX = 0.0;
		dragVelocityY = 0.0;
		setExpression(EXPR_GRAB, false);
		canvas.repaint();
	}

	/** 드래그 이동 처리. */
	private void onDrag(int xRoot, int yRoot) {
		if (!dragging || colorDialogOpen) {
			return;
		}
		appModeHoldMs = APP_MODE_HOLD_ON_DRAG_MS;
		double newX = xRoot - dragOffsetX;
		double newY = yRoot - dragOffsetY;
		targetX = Util.clamp(newX, workLeft, maxX);
		targetY = Util.clamp(newY, minY, maxY);
		long now = System.nanoTime();
		double dtMs = Math.max(1.0, (now - dragPrevTimeNanos) / 1_000_000.0);
		double sampleVx = ((targetX - dragPrevX) / dtMs) * FRAME_MS;
		double sampleVy = ((targetY - dragPrevY) / dtMs) * FRAME_MS;
		dragVelocityX += (sampleVx - dragVelocityX) * DRAG_VELOCITY_SMOOTHING;
		dragVelocityY += (sampleVy - dragVelocityY) * DRAG_VELOCITY_SMOOTHING;
		dragPrevX = targetX;
		dragPrevY = targetY;
		dragPrevTimeNanos = now;
	}

	/** 클릭 해제(던지기) 처리. */
	private void onRelease() {
		if (!dragging || colorDialogOpen) {
			return;
		}

		dragging = false;
		appModeHoldMs = APP_MODE_HOLD_ON_DRAG_MS;
		autoWaitMs = randInt(AUTO_WAIT_MIN_MS, AUTO_WAIT_MAX_MS);
		double speedMag = Math.hypot(dragVelocityX, dragVelocityY);
		if (speedMag >= RELEASE_THROW_MIN_SPEED) {
			throwActive = true;
			fallElapsedMs = 0.0;
			fallPeakY = posY;
			velocityX = Util.clamp(dragVelocityX * RELEASE_THROW_MULT, -RELEASE_THROW_MAX_SPEED, RELEASE_THROW_MAX_SPEED);
			velocityY = Util.clamp(dragVelocityY * RELEASE_THROW_MULT, -RELEASE_THROW_MAX_SPEED, RELEASE_THROW_MAX_SPEED);
			autoMoveActive = false;
			autoMoveTargetX = posX;
			targetX = posX;
			targetY = posY;
			gravityActive = true;
			setExpression(EXPR_FALL, false);
			canvas.repaint();
			return;
		}

		throwActive = false;
		velocityX = 0.0;
		velocityY = 0.0;
		fallElapsedMs = 0.0;
		fallPeakY = posY;
		targetY = posY;
		if (posY < maxY) {
			gravityActive = true;
			setExpression(EXPR_FALL, false);
		} else {
			gravityActive = false;
			setExpression(EXPR_NEUTRAL, false);
		}
		canvas.repaint();
	}

	// ---------------------------------------------------------------- 물리

	/** 연속 점프 시작. */
	private boolean startNextJump() {
		if (jumpChainRemaining <= 0) {
			return false;
		}
		jumpChainRemaining--;
		velocityY = -DOUBLE_JUMP_VELOCITY;
		gravityActive = true;
		fallElapsedMs = 0.0;
		fallPeakY = posY;
		autoMoveActive = false;
		autoMoveTargetX = posX;
		targetX = posX;
		setExpression(EXPR_FALL, false);
		return true;
	}

	/** 플랫폼 정보 갱신. */
	private void refreshPlatformCache(int refreshIntervalMs, int sampleStep) {
		if (platformRefreshMs <= 0) {
			windowPlatformRects = platform.windowPlatformRects(workLeft, workTop, workRight, workBottom, sampleStep);
			platformRefreshMs = refreshIntervalMs;
		} else {
			platformRefreshMs -= FRAME_MS;
		}
	}

	/** 플랫폼 지지 여부 갱신. */
	private void updateSupportState(List<Double> platformTops) {
		double currentBottom = posY + footContactOffset;
		Double supportTop = null;
		for (double platformTop : platformTops) {
			if (Math.abs(currentBottom - platformTop) <= PLATFORM_SNAP_TOLERANCE) {
				supportTop = platformTop;
				break;
			}
		}
		if (supportTop != null) {
			supportGraceMs = SUPPORT_GRACE_MS;
			lastSupportTop = supportTop;
			return;
		}
		// 마지막으로 서 있던 플랫폼이 아직 목록에 있는지 확인
		boolean lastPlatformStillExists = false;
		if (lastSupportTop != null) {
			for (double platformTop : platformTops) {
				if (Math.abs(lastSupportTop - platformTop) <= PLATFORM_SNAP_TOLERANCE) {
					lastPlatformStillExists = true;
					break;
				}
			}
		}
		// 서 있던 플랫폼이 사라졌으면 바로 낙하
		if (lastSupportTop != null && !lastPlatformStillExists) {
			gravityActive = true;
			fallElapsedMs = 0.0;
			fallPeakY = posY;
			lastSupportTop = null;
			return;
		}
		if (lastSupportTop != null && Math.abs(currentBottom - lastSupportTop) <= PLATFORM_SNAP_TOLERANCE) {
			supportGraceMs = SUPPORT_GRACE_MS;
			return;
		}
		if (supportGraceMs > 0) {
			supportGraceMs = Math.max(0, supportGraceMs - FRAME_MS);
			return;
		}
		gravityActive = true;
		fallElapsedMs = 0.0;
		fallPeakY = posY;
	}

	/** 중력/착지 처리. */
	private boolean applyGravity(List<Double> platformTops) {
		fallElapsedMs = Math.min(GRAVITY_RAMP_MS, fallElapsedMs + FRAME_MS);
		fallPeakY = Math.min(fallPeakY, posY);
		double prevFallY = posY;
		double rampT = fallElapsedMs / GRAVITY_RAMP_MS;
		double gravityScale = 1.0 + ((rampT * rampT) * GRAVITY_RAMP_GAIN);
		velocityY = Math.min(MAX_FALL_SPEED, velocityY + (GRAVITY * gravityScale));
		double nextFallY = posY + velocityY;
		double prevBottom = prevFallY + footContactOffset;
		double nextBottom = nextFallY + footContactOffset;

		Double landingTop = null;
		for (double platformTop : platformTops) {
			if (prevBottom <= platformTop && platformTop <= nextBottom) {
				if (landingTop == null || platformTop < landingTop) {
					landingTop = platformTop;
				}
			}
		}

		if (landingTop == null) {
			posY = nextFallY;
			setExpression(EXPR_FALL, false);
		} else {
			posY = landingTop - footContactOffset;
			lastSupportTop = landingTop;
			double fallDistance = Math.max(0.0, posY - fallPeakY);
			double bounceSpeed = 0.0;
			if (fallDistance >= BOUNCE_MIN_FALL_PX) {
				bounceSpeed = Math.min(BOUNCE_MAX_SPEED, Math.sqrt(fallDistance) * BOUNCE_HEIGHT_GAIN);
			}
			if (bounceSpeed >= RELEASE_BOUNCE_MIN_SPEED) {
				velocityY = -bounceSpeed;
				gravityActive = true;
				fallElapsedMs = 0.0;
				fallPeakY = posY;
				setExpression(EXPR_FALL, false);
			} else if (throwActive && Math.abs(velocityY) > RELEASE_BOUNCE_MIN_SPEED) {
				velocityY = -velocityY * RELEASE_BOUNCE_RESTITUTION;
				setExpression(EXPR_FALL, false);
			} else {
				velocityY = 0.0;
				gravityActive = false;
				velocityX = 0.0;
				throwActive = false;
				fallPeakY = posY;
				supportGraceMs = SUPPORT_GRACE_MS;
				if (!(jumpChainRemaining > 0 && startNextJump())) {
					autoWaitMs = randInt(AUTO_WAIT_MIN_MS, AUTO_WAIT_MAX_MS);
					setExpression(petFaceMs > 0 ? EXPR_FALL : EXPR_NEUTRAL, false);
				}
			}
		}
		if (posY < minY) {
			posY = minY;
			if (Math.abs(velocityY) > RELEASE_BOUNCE_MIN_SPEED) {
				velocityY = Math.abs(velocityY) * RELEASE_BOUNCE_RESTITUTION;
			} else {
				velocityY = 0.0;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- 매 프레임

	/** 매 프레임 업데이트 (파이썬 tick()). */
	private void tick() {
		geometryChanged = false;
		double prevBob = walkBobCurrent;
		boolean wasMoving = Math.abs(posX - prevPosX) + Math.abs(posY - prevPosY) > 0.01;
		boolean isAir = gravityActive || throwActive;
		boolean isAutoMove = autoMoveActive;
		boolean isIdle = !(dragging || isAir || isAutoMove || wasMoving);
		int refreshIntervalMs;
		int sampleStep;
		if (dragging) {
			refreshIntervalMs = WINDOW_PLATFORM_REFRESH_MS;
			sampleStep = WINDOW_PLATFORM_SAMPLE_STEP;
		} else if (isAir) {
			refreshIntervalMs = WINDOW_PLATFORM_REFRESH_AIR_MS;
			sampleStep = WINDOW_PLATFORM_SAMPLE_STEP_AIR;
		} else if (isAutoMove) {
			refreshIntervalMs = WINDOW_PLATFORM_REFRESH_MOVE_MS;
			sampleStep = WINDOW_PLATFORM_SAMPLE_STEP_MOVE;
		} else if (isIdle) {
			refreshIntervalMs = WINDOW_PLATFORM_REFRESH_IDLE_MS;
			sampleStep = WINDOW_PLATFORM_SAMPLE_STEP_IDLE;
		} else {
			refreshIntervalMs = WINDOW_PLATFORM_REFRESH_MS;
			sampleStep = WINDOW_PLATFORM_SAMPLE_STEP;
		}
		refreshPlatformCache(refreshIntervalMs, sampleStep);

		if (appModeHoldMs > 0) {
			appModeHoldMs = Math.max(0, appModeHoldMs - FRAME_MS);
		}

		if (appStateCheckMs <= 0) {
			if (appModeHoldMs <= 0 && !dragging) {
				WindowState windowState = AppearanceDetection.getWindowState(platform);
				appMode = AppearanceDetection.resolveAppMode(windowState, appMode);
				discordVisible = windowState.discordVisible();
			}
			appStateCheckMs = APP_STATE_CHECK_INTERVAL_MS;
		} else {
			appStateCheckMs -= FRAME_MS;
		}

		if (rainbowActive) {
			rainbowElapsedMs += FRAME_MS;
			rainbowPhase = (rainbowPhase + RAINBOW_SPARKLE_SPEED) % 1.0;
			setCharacterColor(Util.hsvToHex(rainbowPhase, 0.85, 1.0));
			if (rainbowElapsedMs >= RAINBOW_SPARKLE_DURATION_MS) {
				rainbowActive = false;
				rainbowElapsedMs = 0.0;
			}
		} else if (isDiscordColorEnabled()) {
			setCharacterColor(DISCORD_ICON_BG_COLOR);
		} else {
			setCharacterColor(baseCharacterColor);
		}

		double charCenterX = posX + charOriginX + (charFaceWidth / 2.0);
		double charCenterY = posY + walkBobCurrent + charOriginY + (charFaceHeight / 2.0);
		Point cursor = cursorLocation();
		double cursorX = cursor.x;
		double cursorY = cursor.y;
		double cursorDx = cursorX - charCenterX;
		double cursorDy = cursorY - charCenterY;
		boolean cursorNear = !dragging && ((cursorDx * cursorDx + cursorDy * cursorDy) <= (CURSOR_STOP_RADIUS * CURSOR_STOP_RADIUS));
		if (cursorNear) {
			cursorResumeDelayMs = CURSOR_RESUME_DELAY_MS;
		} else if (!dragging && cursorResumeDelayMs > 0) {
			cursorResumeDelayMs = Math.max(0, cursorResumeDelayMs - FRAME_MS);
		}
		boolean cursorStopActive = cursorNear || (!dragging && cursorResumeDelayMs > 0);
		boolean colorPauseActive = colorDialogOpen;

		if (colorErrorShakeActive) {
			colorErrorShakePhase += COLOR_ERROR_SHAKE_SPEED;
			double maxShakePhase = Math.PI * 2 * COLOR_ERROR_SHAKE_CYCLES;
			if (colorErrorShakePhase >= maxShakePhase) {
				colorErrorShakeActive = false;
				colorErrorShakePhase = 0.0;
				colorErrorEyeOffsetX = 0.0;
			} else {
				colorErrorEyeOffsetX = Math.sin(colorErrorShakePhase) * COLOR_ERROR_SHAKE_AMPLITUDE;
				setExpression(EXPR_INVALID, EXPR_INVALID.equals(expression));
			}
		} else {
			colorErrorEyeOffsetX = 0.0;
		}

		double faceLeft = posX + charOriginX;
		double faceTop = posY + walkBobCurrent + charOriginY;
		double faceRight = faceLeft + charFaceWidth;
		double headBottom = faceTop + (charFaceHeight * PET_HEAD_ZONE_RATIO);
		boolean cursorOnHead = faceLeft <= cursorX && cursorX <= faceRight && faceTop <= cursorY && cursorY <= headBottom;

		if (petFaceMs > 0) {
			petFaceMs = Math.max(0, petFaceMs - FRAME_MS);
		}

		if (!dragging && cursorOnHead) {
			if (petPrevCursorX == null) {
				petPrevCursorX = cursorX;
			} else {
				double stepX = cursorX - petPrevCursorX;
				double speedX = Math.abs(stepX) * (1000.0 / FRAME_MS);
				petPrevCursorX = cursorX;
				if (speedX >= PET_MIN_SPEED_PX_PER_S && Math.abs(stepX) >= PET_MIN_STEP_PX) {
					int moveDir = stepX > 0 ? 1 : -1;
					if (petPrevMoveDir != 0 && moveDir != petPrevMoveDir) {
						if (petWindowMs <= 0) {
							petWindowMs = PET_SHAKE_WINDOW_MS;
							petFlipCount = 0;
						}
						petFlipCount++;
						if (petFlipCount >= PET_SHAKE_FLIPS_TO_TRIGGER) {
							petFaceMs = PET_FACE_HOLD_MS;
							petFlipCount = 0;
							petWindowMs = 0;
						}
					}
					petPrevMoveDir = moveDir;
					if (petFaceMs > 0) {
						petFaceMs = PET_FACE_HOLD_MS;
					}
				}
			}

			if (petWindowMs > 0) {
				petWindowMs = Math.max(0, petWindowMs - FRAME_MS);
				if (petWindowMs == 0) {
					petFlipCount = 0;
					petPrevMoveDir = 0;
				}
			}
		} else {
			petPrevCursorX = null;
			petPrevMoveDir = 0;
			petFlipCount = 0;
			petWindowMs = 0;
		}

		if (petFaceMs > 0) {
			petHeartSpawnMs = Math.max(0, petHeartSpawnMs - FRAME_MS);
			if (petHeartSpawnMs <= 0 && petHearts.size() < PET_HEART_MAX_VISIBLE) {
				spawnPetHeart();
				petHeartSpawnMs = randInt(PET_HEART_SPAWN_MIN_MS, PET_HEART_SPAWN_MAX_MS);
			}
		} else {
			petHeartSpawnMs = 0;
		}

		if (cursorStopActive || colorPauseActive) {
			autoMoveActive = false;
			autoMoveTargetX = posX;
			targetX = posX;
			if (!colorPauseActive
				&& !colorErrorShakeActive
				&& !EXPR_NEUTRAL.equals(expression)
				&& !gravityActive
				&& petFaceMs <= 0) {
				setExpression(EXPR_NEUTRAL, false);
			}
		}

		if (colorPauseActive) {
			autoMoveActive = false;
			autoMoveTargetX = posX;
			targetX = posX;
			targetY = posY;
			velocityY = 0.0;
			velocityX = 0.0;
			throwActive = false;
			gravityActive = false;
			fallElapsedMs = 0.0;
			fallPeakY = posY;
			if (!colorErrorShakeActive && petFaceMs <= 0 && !EXPR_NEUTRAL.equals(expression)) {
				setExpression(EXPR_NEUTRAL, false);
			}
		} else if (dragging) {
			posX += (targetX - posX) * DRAG_SMOOTHING;
			posY += (targetY - posY) * DRAG_SMOOTHING;
			if (Math.abs(targetX - posX) < 0.05) {
				posX = targetX;
			}
			if (Math.abs(targetY - posY) < 0.05) {
				posY = targetY;
			}
			geometryChanged = true;
		} else if (throwActive) {
			posX += velocityX;
			velocityX *= RELEASE_AIR_FRICTION;
			if (Math.abs(velocityX) < 0.03) {
				velocityX = 0.0;
			}
			if (posX < workLeft) {
				posX = workLeft;
				velocityX = Math.abs(velocityX) > RELEASE_BOUNCE_MIN_SPEED ? Math.abs(velocityX) * RELEASE_BOUNCE_RESTITUTION : 0.0;
			} else if (posX > maxX) {
				posX = maxX;
				velocityX = Math.abs(velocityX) > RELEASE_BOUNCE_MIN_SPEED ? -Math.abs(velocityX) * RELEASE_BOUNCE_RESTITUTION : 0.0;
			}
			geometryChanged = true;
		} else if (cursorStopActive) {
			// 커서가 가까우면 제자리
		} else if (autoMoveActive) {
			double deltaX = autoMoveTargetX - posX;
			if (Math.abs(deltaX) <= autoMoveSpeed) {
				if (autoMoveTargetX != posX) {
					posX = autoMoveTargetX;
					geometryChanged = true;
				}
				autoMoveActive = false;
				autoWaitMs = randInt(AUTO_WAIT_MIN_MS, AUTO_WAIT_MAX_MS);
			} else {
				posX += deltaX > 0 ? autoMoveSpeed : -autoMoveSpeed;
				geometryChanged = true;
			}
		} else {
			autoWaitMs -= FRAME_MS;
			if (autoWaitMs <= 0 && !gravityActive && jumpChainRemaining == 0) {
				if (random.nextDouble() < AUTO_WALK_PROBABILITY) {
					boolean found = false;
					for (int attempt = 0; attempt < 4; attempt++) {
						double randomDistance = uniform(AUTO_STEP_MIN_PX, AUTO_STEP_MAX_PX);
						double direction = random.nextBoolean() ? -1.0 : 1.0;
						double candidateTarget = Util.clamp(posX + (direction * randomDistance), workLeft, maxX);
						if (Math.abs(candidateTarget - posX) >= 0.5) {
							autoMoveTargetX = candidateTarget;
							autoMoveSpeed = uniform(AUTO_MOVE_SPEED_MIN, AUTO_MOVE_SPEED_MAX);
							autoMoveActive = true;
							found = true;
							break;
						}
					}
					if (!found) {
						autoWaitMs = randInt(AUTO_WAIT_MIN_MS, AUTO_WAIT_MAX_MS);
					}
				} else {
					jumpChainRemaining = DOUBLE_JUMP_COUNT;
					startNextJump();
					autoWaitMs = randInt(AUTO_WAIT_MIN_MS, AUTO_WAIT_MAX_MS);
				}
			}
		}

		if (!colorPauseActive) {
			double charLeft = posX + leftArmLeft + limbOffsetX - Math.abs(limbSwayX);
			double charRight = posX + rightArmRight + limbOffsetX + Math.abs(limbSwayX);
			List<Double> platformTops = getPlatformTops(charLeft, charRight);

			if (!dragging && !gravityActive) {
				updateSupportState(platformTops);
			}

			if (gravityActive && !dragging) {
				geometryChanged = applyGravity(platformTops) || geometryChanged;
			} else if (!dragging
				&& !colorErrorShakeActive
				&& !EXPR_NEUTRAL.equals(expression)
				&& !EXPR_INVALID.equals(expression)
				&& !gravityActive
				&& petFaceMs <= 0) {
				setExpression(EXPR_NEUTRAL, false);
			}

			if (!gravityActive && !dragging) {
				fallElapsedMs = 0.0;
				fallPeakY = posY;
			}
		} else {
			fallElapsedMs = 0.0;
			fallPeakY = posY;
		}

		if (throwActive && !gravityActive) {
			velocityX = 0.0;
			throwActive = false;
		}

		if (petFaceMs > 0 && !dragging && !gravityActive && !colorPauseActive && !colorErrorShakeActive) {
			if (!EXPR_FALL.equals(expression) && !EXPR_INVALID.equals(expression)) {
				setExpression(EXPR_FALL, false);
			}
		}

		double dx = posX - prevPosX;
		double dy = posY - prevPosY;
		prevPosX = posX;
		prevPosY = posY;
		double speedMag = Math.hypot(dx, dy);
		double phaseStep = 0.0;
		if (speedMag > 0.01) {
			phaseStep = Util.clamp(speedMag * LIMB_SWAY_SPEED_SCALE, LIMB_SWAY_SPEED_MIN, LIMB_SWAY_SPEED_MAX);
		}

		if (cursorNear) {
			eyeDirShiftTarget = Util.clamp(cursorDx * EYE_CURSOR_LOOK_GAIN, -EYE_LOOK_MAX, EYE_LOOK_MAX);
			eyeDirShiftYTarget = Util.clamp(cursorDy * EYE_CURSOR_LOOK_GAIN_Y, -EYE_LOOK_MAX_Y, EYE_LOOK_MAX_Y);
		} else if (Math.abs(dx) < 0.01) {
			eyeDirShiftTarget = 0.0;
			eyeDirShiftYTarget = 0.0;
		} else {
			eyeDirShiftTarget = Util.clamp(dx * EYE_LOOK_GAIN, -EYE_LOOK_MAX, EYE_LOOK_MAX);
			eyeDirShiftYTarget = 0.0;
		}
		eyeDirShiftCurrent += (eyeDirShiftTarget - eyeDirShiftCurrent) * EYE_LOOK_SMOOTHING;
		if (Math.abs(eyeDirShiftTarget - eyeDirShiftCurrent) < 0.02) {
			eyeDirShiftCurrent = eyeDirShiftTarget;
		}
		eyeDirShiftYCurrent += (eyeDirShiftYTarget - eyeDirShiftYCurrent) * EYE_LOOK_SMOOTHING;
		if (Math.abs(eyeDirShiftYTarget - eyeDirShiftYCurrent) < 0.02) {
			eyeDirShiftYCurrent = eyeDirShiftYTarget;
		}

		if (Math.abs(eyeDirShiftCurrent - eyeDrawnShift) >= EYE_REDRAW_THRESHOLD
			|| Math.abs(eyeDirShiftYCurrent - eyeDrawnShiftY) >= EYE_REDRAW_THRESHOLD) {
			setExpression(expression, true);
		}

		double[] margins = getAppearanceMargins();
		applyAppearanceMargins(margins[0], margins[1]);

		for (int i = petHearts.size() - 1; i >= 0; i--) {
			PetHeart heart = petHearts.get(i);
			heart.ageMs += FRAME_MS;
			double lifeMs = Math.max(1.0, heart.lifeMs);
			double progress = heart.ageMs / lifeMs;
			if (progress >= 1.0) {
				petHearts.remove(i);
				continue;
			}
			heart.x += heart.vx;
			heart.y -= heart.rise;
			double sizeScale = progress < 0.2
				? 0.65 + ((progress / 0.2) * 0.55)
				: 1.2 - (((progress - 0.2) / 0.8) * 0.35);
			heart.renderX = heart.x;
			heart.renderY = heart.y;
			heart.renderSize = heart.size * sizeScale;
			heart.renderColor = getPetHeartColor(progress);
		}

		boolean movingHorizontally = Math.abs(dx) > 0.01 && !dragging && !cursorStopActive;
		double bobTargetAmp = movingHorizontally ? WALK_BOB_MAX : 0.0;
		walkBobAmp += (bobTargetAmp - walkBobAmp) * WALK_BOB_SMOOTHING;
		if (Math.abs(bobTargetAmp - walkBobAmp) < 0.01) {
			walkBobAmp = bobTargetAmp;
		}

		if (movingHorizontally) {
			walkPhase += phaseStep * 2.0;
		}

		walkBobCurrent = -((1.0 - Math.cos(walkPhase)) * 0.5) * walkBobAmp;
		if (walkBobAmp == 0.0 && Math.abs(walkBobCurrent) < 0.01) {
			walkBobCurrent = 0.0;
		}

		if (Math.abs(walkBobCurrent - prevBob) > 0.01) {
			geometryChanged = true;
		}

		if (geometryChanged) {
			applyGeometry();
		}

		if (Math.abs(dx) + Math.abs(dy) < 0.01) {
			limbTargetX = 0.0;
			limbTargetY = 0.0;
		} else {
			limbTargetX = Util.clamp(-dx * LIMB_REACT_GAIN, -LIMB_REACT_MAX_X, LIMB_REACT_MAX_X);
			limbTargetY = Util.clamp(-dy * LIMB_REACT_GAIN, -LIMB_REACT_MAX_Y, LIMB_REACT_MAX_Y);
		}

		limbOffsetX += (limbTargetX - limbOffsetX) * LIMB_REACT_SMOOTHING;
		limbOffsetY += (limbTargetY - limbOffsetY) * LIMB_REACT_SMOOTHING;
		if (Math.abs(limbOffsetX) < 0.02) {
			limbOffsetX = 0.0;
		}
		if (Math.abs(limbOffsetY) < 0.02) {
			limbOffsetY = 0.0;
		}

		boolean movingForSway = speedMag > 0.01;
		double swayTargetAmp = movingForSway ? 1.0 : 0.0;
		limbSwayAmp += (swayTargetAmp - limbSwayAmp) * LIMB_SWAY_SMOOTHING;
		if (Math.abs(swayTargetAmp - limbSwayAmp) < 0.01) {
			limbSwayAmp = swayTargetAmp;
		}

		if (movingForSway) {
			limbSwayPhase += phaseStep;
		}

		limbSwayX = Math.sin(limbSwayPhase) * LIMB_SWAY_MAX_X * limbSwayAmp;
		if (limbSwayAmp == 0.0 && Math.abs(limbSwayX) < 0.02) {
			limbSwayX = 0.0;
		}

		armTopCurrent += (armTopTarget - armTopCurrent) * ARM_POSE_SMOOTHING;
		if (Math.abs(armTopTarget - armTopCurrent) < 0.05) {
			armTopCurrent = armTopTarget;
		}
		canvas.repaint();
	}

	private Point lastCursor = new Point(-10_000, -10_000);

	private Point cursorLocation() {
		try {
			PointerInfo info = MouseInfo.getPointerInfo();
			if (info != null) {
				lastCursor = info.getLocation();
			}
		} catch (RuntimeException ignored) {
			// 마지막 위치 사용
		}
		return lastCursor;
	}

	// ---------------------------------------------------------------- 그리기

	/** 캐릭터 전체를 그린다. 순서는 파이썬 Tk 캔버스의 쌓임 순서와 같다. */
	private void paintScene(Graphics2D g) {
		String color = currentCharacterColor;
		// 몸통
		Draw.rect(g, charOriginX, charOriginY, charOriginX + charFaceWidth, charOriginY + charFaceHeight, color, "#000000", border);
		// 다리
		Draw.rect(g, leftLegX + limbOffsetX - limbSwayX, legY + limbOffsetY,
			leftLegX + limbOffsetX - limbSwayX + LIMB_SIZE, legY + limbOffsetY + LIMB_SIZE, color, "#000000", border);
		Draw.rect(g, rightLegX + limbOffsetX + limbSwayX, legY + limbOffsetY,
			rightLegX + limbOffsetX + limbSwayX + LIMB_SIZE, legY + limbOffsetY + LIMB_SIZE, color, "#000000", border);

		boolean pixelHeartVisible = isPixelHeartEnabled();
		if (pixelHeartVisible) {
			AppearanceRendering.renderPixelHeart(g, charOriginX, charOriginY, charFaceWidth, eyeShapeScale);
		}
		if (isMediaHeadsetEnabled()) {
			AppearanceRendering.renderHeadset(g, charOriginX, charOriginY, charFaceWidth, charFaceHeight, headsetStroke);
		}
		paintEyes(g);
		if (pixelHeartVisible) {
			AppearanceRendering.renderPixelHeartShine(g, charOriginX, charOriginY, charFaceWidth, eyeShapeScale);
		}
		for (PetHeart heart : petHearts) {
			AppearanceRendering.renderPetHeart(g, heart.renderX, heart.renderY, heart.renderSize, heart.renderColor);
		}

		// 팔
		double armTop = armTopCurrent + limbOffsetY;
		Draw.rect(g, leftArmLeft + limbOffsetX + limbSwayX, armTop,
			leftArmRight + limbOffsetX + limbSwayX, armTop + LIMB_SIZE, color, "#000000", border);
		Draw.rect(g, rightArmLeft + limbOffsetX - limbSwayX, armTop,
			rightArmRight + limbOffsetX - limbSwayX, armTop + LIMB_SIZE, color, "#000000", border);

		if (isBrowserMonocleEnabled()) {
			AppearanceRendering.renderMonocle(g, rightArmRight, armTopCurrent, limbOffsetX, limbOffsetY, limbSwayX,
				LIMB_SIZE, monocleRadius, monocleStroke, monocleHandleStroke);
		}
		if (isBrowserIeIconEnabled()) {
			AppearanceRendering.renderIeIcon(g, leftArmLeft, armTopCurrent, limbOffsetX, limbOffsetY, limbSwayX,
				LIMB_SIZE, ieIconRadius, ieIconOrbitStroke, ieIconBarStroke, ieIconFont);
		}
	}

	/** 표정에 맞는 눈 (파이썬 draw_eyes). 마지막으로 다시 그린 시점의 시선 값을 사용한다. */
	private void paintEyes(Graphics2D g) {
		double shiftX = eyeDrawnShift;
		double shiftY = eyeDrawnShiftY;
		double invalidOffset = eyeDrawnInvalidOffset;
		double scale = eyeShapeScale;
		double offsetX = eyeShapeOffsetX;
		double offsetY = eyeShapeOffsetY;

		// 눈 좌표 변환 (sx/sy), 왼쪽/오른쪽 눈 간격 보정 (lx/rx), 눈 Y 보정 (ey)
		java.util.function.DoubleUnaryOperator sx = v -> Util.pyRound(offsetX + EYE_X_SHIFT + shiftX + invalidOffset + (v * scale));
		java.util.function.DoubleUnaryOperator sy = v -> Util.pyRound(offsetY + (v * scale));
		java.util.function.DoubleUnaryOperator lx = v -> v - 2;
		java.util.function.DoubleUnaryOperator rx = v -> v + 2;
		java.util.function.DoubleUnaryOperator ey = v -> v + EYE_Y_SHIFT + shiftY;
		int eyeStroke = Math.max(2, Math.max(1, Util.pyRound(3 * scale)) + 1);

		switch (expression) {
			case EXPR_NEUTRAL -> {
				Draw.oval(g, sx.applyAsDouble(lx.applyAsDouble(30)), sy.applyAsDouble(ey.applyAsDouble(42)),
					sx.applyAsDouble(lx.applyAsDouble(50)), sy.applyAsDouble(ey.applyAsDouble(62)), "#000000", null, 0);
				Draw.oval(g, sx.applyAsDouble(rx.applyAsDouble(70)), sy.applyAsDouble(ey.applyAsDouble(42)),
					sx.applyAsDouble(rx.applyAsDouble(90)), sy.applyAsDouble(ey.applyAsDouble(62)), "#000000", null, 0);
			}
			case EXPR_GRAB -> {
				Draw.arc(g, sx.applyAsDouble(lx.applyAsDouble(27)), sy.applyAsDouble(ey.applyAsDouble(40)),
					sx.applyAsDouble(lx.applyAsDouble(53)), sy.applyAsDouble(ey.applyAsDouble(66)), 0, 180, "#000000", eyeStroke);
				Draw.arc(g, sx.applyAsDouble(rx.applyAsDouble(67)), sy.applyAsDouble(ey.applyAsDouble(40)),
					sx.applyAsDouble(rx.applyAsDouble(93)), sy.applyAsDouble(ey.applyAsDouble(66)), 0, 180, "#000000", eyeStroke);
			}
			case EXPR_FALL -> {
				Draw.line(g, sx.applyAsDouble(lx.applyAsDouble(29)), sy.applyAsDouble(ey.applyAsDouble(43)),
					sx.applyAsDouble(lx.applyAsDouble(50)), sy.applyAsDouble(ey.applyAsDouble(52)), "#000000", eyeStroke, true);
				Draw.line(g, sx.applyAsDouble(lx.applyAsDouble(29)), sy.applyAsDouble(ey.applyAsDouble(61)),
					sx.applyAsDouble(lx.applyAsDouble(50)), sy.applyAsDouble(ey.applyAsDouble(52)), "#000000", eyeStroke, true);
				Draw.line(g, sx.applyAsDouble(rx.applyAsDouble(91)), sy.applyAsDouble(ey.applyAsDouble(43)),
					sx.applyAsDouble(rx.applyAsDouble(70)), sy.applyAsDouble(ey.applyAsDouble(52)), "#000000", eyeStroke, true);
				Draw.line(g, sx.applyAsDouble(rx.applyAsDouble(91)), sy.applyAsDouble(ey.applyAsDouble(61)),
					sx.applyAsDouble(rx.applyAsDouble(70)), sy.applyAsDouble(ey.applyAsDouble(52)), "#000000", eyeStroke, true);
			}
			case EXPR_INVALID -> {
				Draw.line(g, sx.applyAsDouble(lx.applyAsDouble(31)), sy.applyAsDouble(ey.applyAsDouble(52)),
					sx.applyAsDouble(lx.applyAsDouble(49)), sy.applyAsDouble(ey.applyAsDouble(52)), "#000000", eyeStroke, true);
				Draw.line(g, sx.applyAsDouble(rx.applyAsDouble(69)), sy.applyAsDouble(ey.applyAsDouble(52)),
					sx.applyAsDouble(rx.applyAsDouble(87)), sy.applyAsDouble(ey.applyAsDouble(52)), "#000000", eyeStroke, true);
			}
			default -> {
			}
		}
	}

	/** 투명 배경 위에 캐릭터를 그리는 캔버스. */
	private final class PetCanvas extends JComponent {
		PetCanvas() {
			setOpaque(false);
			setLayout(new BorderLayout());
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				paintScene(g);
			} finally {
				g.dispose();
			}
		}

		/** 그려진 픽셀 위를 클릭했는지 (파이썬의 "character" 태그 바인딩과 같은 역할). */
		boolean hitTest(int x, int y) {
			int width = Math.max(1, getWidth());
			int height = Math.max(1, getHeight());
			if (x < 0 || y < 0 || x >= width || y >= height) {
				return false;
			}
			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			try {
				paintScene(g);
			} finally {
				g.dispose();
			}
			// 가장자리 클릭이 잘 잡히도록 주변 1px 도 확인
			for (int oy = -1; oy <= 1; oy++) {
				for (int ox = -1; ox <= 1; ox++) {
					int px = x + ox;
					int py = y + oy;
					if (px >= 0 && py >= 0 && px < width && py < height && (image.getRGB(px, py) >>> 24) != 0) {
						return true;
					}
				}
			}
			return false;
		}
	}
}
