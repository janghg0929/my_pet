package com.mypet;

import com.mypet.appearance.AppearanceDetection;
import com.mypet.appearance.AppearanceDetection.WindowState;
import com.mypet.appearance.AppearanceMode;
import com.mypet.appearance.AppearanceSettings;
import com.mypet.appearance.ColorChooserDialog;
import com.mypet.appearance.ColorSettings;
import com.mypet.appearance.rendering.AppearanceRendering;
import com.mypet.appearance.rendering.Draw;
import com.mypet.parts.PartImages;
import com.mypet.parts.PartLayout;
import com.mypet.parts.SavedLook;
import com.mypet.platform.PlatformSupport;
import com.mypet.platform.PlatformSupport.PlatformRect;
import com.mypet.settings.Json;
import com.mypet.settings.SettingsStore;
import com.mypet.ui.SettingsHost;
import com.mypet.ui.Ui;
import com.mypet.update.UpdateService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
import java.util.function.Consumer;

import static com.mypet.Constants.*;

/**
 * my_pet.py main() 포팅. 파이썬의 nonlocal 변수들은 필드로, Tk Canvas 도형은 매 프레임 직접 그리기로 바꿨다.
 * 모든 메서드는 Swing 이벤트 스레드(EDT)에서만 호출된다.
 */
final class PetApp implements SettingsHost {
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
	private SettingsHost.View webSettings;
	/** 대체 설정 창끼리 화면을 바꿀 때 새 창을 띄울 자리: x = 왼쪽, y = 아래쪽 끝 (null 이면 캐릭터 근처). */
	private Point nextDialogLocation;
	private String webStatus = "";
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

	// 캐릭터 도형 기준 좌표.
	// 부위 배치(모습 꾸미기)의 좌표는 "몸통 기본 위치 = 0,0" 기준이고, charOriginX/Y 가 그 0,0 이 창 안의 어디인지다.
	private PartLayout layout = PartLayout.defaults();
	private final PartImages partImages = PartImages.standard();
	private PartLayout.Bounds layoutBounds = layout.bounds(Math.max(2, LIMB_SIZE));
	private double charOriginX = DRAW_MARGIN + APPEARANCE_EXTRA_MARGIN + SIDE_EXTENSION;
	private double charOriginY = DRAW_MARGIN + HEART_TOP_HEADROOM;
	private int windowHeight = WINDOW_HEIGHT;
	private boolean geometryReady;
	/** 기본 몸통 크기 (눈 모양과 외형 소품 크기 계산용 - 꾸며도 바뀌지 않는다). */
	private final int charFaceWidth = CHAR_WIDTH - (DRAW_MARGIN * 2);
	private final int charFaceHeight = CHAR_HEIGHT - (DRAW_MARGIN * 2);
	private final double eyeShapeScale = Math.min(charFaceWidth, charFaceHeight) / (double) BASE_FACE_SIZE;
	private final double eyeShapeWidth = BASE_FACE_SIZE * eyeShapeScale;
	private final double eyeShapeHeight = BASE_FACE_SIZE * eyeShapeScale;
	/** 기본 눈 모양의 기준점 (몸통 기준 좌표). */
	private final double eyeShapeOffsetX = (charFaceWidth - eyeShapeWidth) / 2;
	private final double eyeShapeOffsetY = (charFaceHeight - eyeShapeHeight) / 2;
	private final int border = Math.max(1, Util.pyRound(2.0 * Math.min(charFaceWidth, charFaceHeight) / BASE_FACE_SIZE));

	private final int armRaiseOffset = Math.max(2, LIMB_SIZE);
	/** 점프/낙하 때 팔을 드는 정도 (0 = 평소, -armRaiseOffset = 든 상태). */
	private double armLiftCurrent = 0;
	private double armLiftTarget = 0;
	/** 창 위쪽에서 발(가장 아래 부위) 바닥까지의 거리. */
	private double footContactOffset = DRAW_MARGIN + HEART_TOP_HEADROOM + 37;

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
		layout = SettingsStore.get().parts();

		// 작업 영역 (윈도우 작업표시줄, 맥 메뉴바/독 제외)
		Rectangle work = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		workLeft = work.x;
		workTop = work.y;
		workRight = work.x + work.width;
		workBottom = work.y + work.height;
		recomputeGeometry();

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
		geometryReady = true;

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
		Rectangle bounds = new Rectangle((int) posX, (int) drawY, (int) windowWidth, windowHeight);
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
		appearanceLeftMargin = leftMargin;
		appearanceRightMargin = rightMargin;
		recomputeGeometry();
	}

	/** 모습 꾸미기 배치를 바꾼다 (화면 위 비트의 몸통 자리와 발 높이는 그대로). */
	void applyLayout(PartLayout newLayout) {
		layout = newLayout.copy();
		recomputeGeometry();
	}

	/**
	 * 부위 배치와 외형 여백으로 창 크기·기준점·발 높이를 다시 계산한다.
	 * 이미 화면에 있으면 몸통의 가로 위치와 발 높이가 그대로이도록 창 위치를 옮긴다.
	 */
	private void recomputeGeometry() {
		double oldOriginX = charOriginX;
		double oldOriginY = charOriginY;
		double oldFoot = footContactOffset;

		layoutBounds = layout.bounds(armRaiseOffset);
		double topExtra = Math.max(0, -layoutBounds.minY());
		charOriginX = DRAW_MARGIN + appearanceLeftMargin - layoutBounds.minX();
		charOriginY = DRAW_MARGIN + HEART_TOP_HEADROOM + topExtra;
		windowWidth = charOriginX + layoutBounds.maxX() + (DRAW_MARGIN * 3) + appearanceRightMargin;
		windowHeight = (int) Math.ceil(charOriginY + layoutBounds.maxY() + (DRAW_MARGIN * 3));
		footContactOffset = charOriginY + layoutBounds.maxY();

		maxX = workRight - windowWidth;
		maxY = workBottom - windowHeight;
		minY = workTop - (DRAW_MARGIN + HEART_TOP_HEADROOM + topExtra);
		if (!geometryReady) {
			return;
		}

		double dx = charOriginX - oldOriginX;
		double dy = footContactOffset - oldFoot;
		double heartDy = charOriginY - oldOriginY;
		for (PetHeart heart : petHearts) {
			heart.x += dx;
			heart.renderX += dx;
			heart.y += heartDy;
			heart.renderY += heartDy;
		}
		posX -= dx;
		prevPosX -= dx;
		targetX -= dx;
		autoMoveTargetX -= dx;
		dragPrevX -= dx;
		dragOffsetX += dx;
		posY -= dy;
		prevPosY -= dy;
		targetY -= dy;
		fallPeakY -= dy;
		dragPrevY -= dy;
		dragOffsetY += dy;

		posX = Util.clamp(posX, workLeft, maxX);
		prevPosX = Util.clamp(prevPosX, workLeft, maxX);
		targetX = Util.clamp(targetX, workLeft, maxX);
		autoMoveTargetX = Util.clamp(autoMoveTargetX, workLeft, maxX);
		posY = Util.clamp(posY, minY, maxY);
		applyGeometry();
		canvas.repaint();
	}

	/** 몸통 왼쪽 위 (창 안 좌표). */
	private double bodyLeft() {
		return charOriginX + layout.body().x;
	}

	private double bodyTop() {
		return charOriginY + layout.body().y;
	}

	// ---------------------------------------------------------------- 표정/색상

	/** 표정에 따른 팔 높이 갱신. */
	private void updateArmPose(String targetExpression) {
		boolean raised = EXPR_FALL.equals(targetExpression);
		armLiftTarget = raised ? -armRaiseOffset : 0;
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
		heart.x = bodyLeft() + (layout.body().w * uniform(PET_HEART_BODY_X_MIN, PET_HEART_BODY_X_MAX));
		double heartGap = uniform(PET_HEART_ABOVE_GAP_MIN, PET_HEART_ABOVE_GAP_MAX);
		heart.y = bodyTop() - (heart.size * 0.64) - heartGap;
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
			preferredY = posY + walkBobCurrent + windowHeight + 12;
		}
		double dialogY = Util.clamp(preferredY, 0.0, Math.max(0, screen.height - dialogHeight));
		dialog.setLocation((int) dialogX, (int) dialogY);
	}

	/** 설정 창 틀: 위(헤더) / 가운데(내용) / 아래(상태·버튼) 로 나뉜다. */
	private record SettingsFrame(JDialog dialog, JPanel body, JPanel footer) {
	}

	/** 공통 설정 창 틀 (항상 위, 크기 고정). */
	private SettingsFrame createSettingsDialog(String title, JComponent header, Runnable onClose) {
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
		// 제목 막대는 창 끝까지 꽉 채우고, 나머지는 안쪽 여백을 둔다
		JPanel content = new JPanel(new BorderLayout());
		content.setBackground(Ui.PAPER);
		content.add(header, BorderLayout.NORTH);
		JPanel inner = new JPanel(new BorderLayout(0, 14));
		inner.setOpaque(false);
		inner.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
		JPanel body = Ui.column();
		JPanel bodyWrap = new JPanel(new BorderLayout());
		bodyWrap.setOpaque(false);
		bodyWrap.add(body, BorderLayout.NORTH);
		inner.add(bodyWrap, BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout(8, 0));
		footer.setOpaque(false);
		inner.add(footer, BorderLayout.SOUTH);
		content.add(inner, BorderLayout.CENTER);
		dialog.setContentPane(content);
		return new SettingsFrame(dialog, body, footer);
	}

	/** 내용에 맞춰 크기를 정하고 캐릭터 근처에 띄운다. */
	private void showSettingsDialog(JDialog dialog) {
		dialog.pack();
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		dialog.setSize(Math.max(SETTINGS_DIALOG_FIXED_WIDTH, dialog.getWidth()), Math.min(dialog.getHeight(), screen.height - 40));
		if (nextDialogLocation != null) {
			// 화면을 바꾼 경우: 캐릭터 위로 돌아가지 않고 보던 창 자리 그대로 (화면 밖으로는 안 나가게)
			int x = (int) Util.clamp(nextDialogLocation.x, 0, Math.max(0, screen.width - dialog.getWidth()));
			// 아래쪽 끝을 기준으로: 새 창이 길어도 위로 자라서 바닥에 붙지 않게
			int y = (int) Util.clamp(nextDialogLocation.y - dialog.getHeight(), 0, Math.max(0, screen.height - dialog.getHeight()));
			dialog.setLocation(x, y);
			nextDialogLocation = null;
		} else {
			placeDialogNearCharacter(dialog);
		}
		dialog.setVisible(true);
		refreshSettingsDialogOpenState();
	}

	/** 다음에 여는 대체 설정 창을 지금 창 자리에 띄우도록 기억한다. */
	private void rememberDialogLocation(JDialog dialog) {
		nextDialogLocation = isDialogAlive(dialog) ? new Point(dialog.getX(), dialog.getY() + dialog.getHeight()) : null;
	}

	private static JLabel statusLabel(String text) {
		return Ui.status(text);
	}

	/** 설정 창에 쓰는 비트 색 (디스코드 색이 적용돼 있으면 그 색). */
	private Color petColor() {
		return Util.toColor(currentCharacterColor);
	}

	private void closeSettingsDialog() {
		if (isDialogAlive(settingsDialog)) {
			settingsDialog.dispose();
		}
		settingsDialog = null;
		webSettings = null;
		refreshSettingsDialogOpenState();
		cursorResumeDelayMs = CURSOR_RESUME_DELAY_MS;
	}

	/** 외형 모드를 저장하고 바로 반영. 상태 문구를 돌려준다. */
	private String saveAndApplyAppearance() {
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
		return savedPath == null ? "외형 설정 적용됨(저장 실패)" : "외형 설정 적용됨";
	}

	// ---------------------------------------------------------------- HTML 설정 창 (SettingsHost)

	/** HTML 설정 창이 열려 있으면 화면을 다시 그린다. */
	private void refreshWebSettings(String status) {
		webStatus = status;
		if (webSettings != null) {
			webSettings.refresh();
		}
	}

	@Override
	public String settingsStateJson() {
		return "{"
			+ "\"version\":" + jsonString(UpdateService.currentVersion()) + ","
			+ "\"isMac\":" + IS_MACOS + ","
			+ "\"petColor\":" + jsonString(currentCharacterColor) + ","
			+ "\"baseColor\":" + jsonString(baseCharacterColor) + ","
			+ "\"status\":" + jsonString(webStatus) + ","
			+ "\"parts\":" + Json.write(layout.toJson()) + ","
			+ "\"looks\":" + Json.write(SettingsStore.get().looks().stream().map(SavedLook::toJson).toList()) + ","
			+ "\"modes\":{"
			+ "\"browserMonocle\":" + jsonString(browserAppearanceMode.key) + ","
			+ "\"browserIeIcon\":" + jsonString(browserIeAppearanceMode.key) + ","
			+ "\"discordColor\":" + jsonString(discordAppearanceMode.key) + ","
			+ "\"pixelHeart\":" + jsonString(pixelHeartAppearanceMode.key) + ","
			+ "\"headset\":" + jsonString(headsetAppearanceMode.key)
			+ "}}";
	}

	private static String jsonString(String value) {
		StringBuilder out = new StringBuilder("\"");
		for (char ch : (value == null ? "" : value).toCharArray()) {
			switch (ch) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '<' -> out.append("\\u003c");
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

	@Override
	public void setAppearanceMode(String key, String mode) {
		AppearanceMode value = AppearanceMode.fromKey(mode);
		if (value == null || key == null) {
			return;
		}
		switch (key) {
			case "browserMonocle" -> browserAppearanceMode = value;
			case "browserIeIcon" -> browserIeAppearanceMode = value;
			case "discordColor" -> discordAppearanceMode = value;
			case "pixelHeart" -> pixelHeartAppearanceMode = value;
			case "headset" -> headsetAppearanceMode = value;
			default -> {
				return;
			}
		}
		refreshWebSettings(saveAndApplyAppearance());
	}

	@Override
	public void applyDefaultColor() {
		applyBaseColor(DEFAULT_CHARACTER_COLOR, false);
		refreshWebSettings("기본 색 적용: " + DEFAULT_CHARACTER_COLOR);
	}

	@Override
	public void pickCustomColor() {
		String[] liveSelectedColor = {null};
		ColorChooserDialog.Result result = ColorChooserDialog.show(
			settingsDialog,
			baseCharacterColor,
			ColorSettings.loadCustomColorSlots(),
			colorCode -> {
				liveSelectedColor[0] = colorCode;
				previewBaseColor(colorCode);
				refreshWebSettings("미리 적용: " + colorCode);
			}
		);
		String selected = result.selected();
		if (selected == null) {
			selected = liveSelectedColor[0];
			if (selected == null) {
				refreshWebSettings("색 선택 취소");
				return;
			}
		}
		ColorSettings.saveCustomColorSlots(result.customSlots());
		Path savedPath = applyBaseColor(selected, true);
		refreshWebSettings(savedPath == null ? "선택 색 적용됨(저장 실패): " + selected : "선택 색 적용: " + selected);
	}

	// ---------------------------------------------------------------- 모습 꾸미기 (SettingsHost)

	/** 배치를 화면 비트에 적용하고 settings.json 에 저장, 안 쓰는 사진은 지운다. */
	private void saveLayout(PartLayout newLayout, String status) {
		applyLayout(newLayout);
		SettingsStore store = SettingsStore.get();
		store.setParts(layout);
		Path saved = store.save();
		partImages.deleteUnused(store.usedImageFiles());
		refreshWebSettings(saved == null ? status + " (저장 실패)" : status);
	}

	@Override
	public void setParts(String layoutJson) {
		try {
			saveLayout(PartLayout.fromJson(Json.read(layoutJson)), "모습을 바꿨어요");
		} catch (RuntimeException e) {
			refreshWebSettings("모습을 바꾸지 못했어요");
		}
	}

	@Override
	public void pickPartImage(String part, String expression) {
		if (part == null || layout.get(part) == null) {
			return;
		}
		java.awt.FileDialog chooser = new java.awt.FileDialog(settingsDialog, "부위 사진 고르기", java.awt.FileDialog.LOAD);
		chooser.setFile("*.png;*.jpg;*.jpeg;*.gif;*.bmp");
		chooser.setFilenameFilter((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g|gif|bmp)"));
		chooser.setVisible(true); // 고를 때까지 기다린다
		if (chooser.getFile() == null) {
			refreshWebSettings("사진 고르기를 취소했어요");
			return;
		}
		java.io.File file = new java.io.File(chooser.getDirectory(), chooser.getFile());
		String name;
		try {
			name = partImages.importImage(file);
		} catch (java.io.IOException | RuntimeException e) {
			refreshWebSettings("사진을 읽지 못했어요: " + e.getMessage());
			return;
		}
		BufferedImage image = partImages.get(name);
		PartLayout copy = layout.copy();
		PartLayout.Part target = copy.get(part);
		boolean eyes = PartLayout.EYES.equals(part);
		String expressionKey = PartLayout.EYE_EXPRESSIONS.contains(expression) ? expression : PartLayout.EXPR_NEUTRAL;
		if (eyes) {
			target.images.put(expressionKey, name);
		} else {
			target.image = name;
			target.flip = false;
		}
		// 가로 크기는 그대로 두고 사진 비율에 맞춰 세로 크기를 맞춘다 (눈은 평소 표정 사진일 때만)
		if (image != null && (!eyes || PartLayout.EXPR_NEUTRAL.equals(expressionKey))) {
			target.h = Math.max(2, (int) Math.round(target.w * image.getHeight() / (double) image.getWidth()));
		}
		if (copy.symmetric && PartLayout.mirrorOf(part) != null) {
			copy.mirrorFrom(part);
		}
		saveLayout(copy, "사진을 넣었어요");
	}

	@Override
	public void removePartImage(String part, String expression) {
		if (part == null || layout.get(part) == null) {
			return;
		}
		PartLayout copy = layout.copy();
		PartLayout.Part target = copy.get(part);
		if (PartLayout.EYES.equals(part)) {
			target.images.put(PartLayout.EYE_EXPRESSIONS.contains(expression) ? expression : PartLayout.EXPR_NEUTRAL, null);
		} else {
			target.image = null;
			target.flip = false;
		}
		if (copy.symmetric && PartLayout.mirrorOf(part) != null) {
			copy.mirrorFrom(part);
		}
		saveLayout(copy, "사진을 뺐어요");
	}

	@Override
	public void resetParts() {
		saveLayout(PartLayout.defaults(), "기본 비트로 되돌렸어요");
	}

	@Override
	public String partImageDataUrl(String fileName) {
		return partImages.dataUrl(fileName);
	}

	// ---------------------------------------------------------------- 저장한 모습 (SettingsHost)

	/** 모습 이름 입력 창 (Swing 창이라 한글이 바로 입력된다). 취소하면 null. */
	private String askLookName(String title, String initial) {
		Object answer = JOptionPane.showInputDialog(settingsDialog, "모습 이름 (" + SavedLook.MAX_NAME_LENGTH + "자까지)", title,
			JOptionPane.PLAIN_MESSAGE, null, null, initial);
		return answer == null ? null : SavedLook.cleanName(String.valueOf(answer));
	}

	/** 저장한 모습에 넣을 몸 색 (기본 색이면 null). */
	private String currentLookColor() {
		return DEFAULT_CHARACTER_COLOR.equalsIgnoreCase(baseCharacterColor) ? null : baseCharacterColor;
	}

	private static SavedLook findLook(List<SavedLook> looks, String id) {
		for (SavedLook look : looks) {
			if (look.id().equals(id)) {
				return look;
			}
		}
		return null;
	}

	private void saveLooks(List<SavedLook> looks, String status) {
		SettingsStore store = SettingsStore.get();
		store.setLooks(looks);
		Path saved = store.save();
		partImages.deleteUnused(store.usedImageFiles());
		refreshWebSettings(saved == null ? status + " (저장 실패)" : status);
	}

	@Override
	public void saveLook() {
		List<SavedLook> looks = SettingsStore.get().looks();
		if (looks.size() >= SavedLook.MAX_LOOKS) {
			refreshWebSettings("모습은 " + SavedLook.MAX_LOOKS + "개까지 저장할 수 있어요");
			return;
		}
		String name = askLookName("지금 모습 저장", "모습 " + (looks.size() + 1));
		if (name == null) {
			refreshWebSettings("저장을 취소했어요");
			return;
		}
		looks.add(SavedLook.create(name, currentLookColor(), layout));
		saveLooks(looks, "'" + name + "' 저장했어요");
	}

	@Override
	public void applyLook(String id) {
		SavedLook look = findLook(SettingsStore.get().looks(), id);
		if (look == null) {
			return;
		}
		if (look.color() == null) {
			applyBaseColor(DEFAULT_CHARACTER_COLOR, false);
		} else {
			applyBaseColor(look.color(), true);
		}
		saveLayout(look.parts(), "'" + look.name() + "' 모습으로 바꿨어요");
	}

	@Override
	public void renameLook(String id) {
		List<SavedLook> looks = SettingsStore.get().looks();
		SavedLook look = findLook(looks, id);
		if (look == null) {
			return;
		}
		String name = askLookName("이름 바꾸기", look.name());
		if (name == null) {
			refreshWebSettings("이름 바꾸기를 취소했어요");
			return;
		}
		looks.set(looks.indexOf(look), look.withName(name));
		saveLooks(looks, "이름을 '" + name + "'(으)로 바꿨어요");
	}

	@Override
	public void deleteLook(String id) {
		List<SavedLook> looks = SettingsStore.get().looks();
		SavedLook look = findLook(looks, id);
		if (look == null) {
			return;
		}
		looks.remove(look);
		saveLooks(looks, "'" + look.name() + "' 지웠어요");
	}

	@Override
	public void closeSettings() {
		closeSettingsDialog();
	}

	@Override
	public void quit() {
		exitApplication();
	}

	@Override
	public void placeSettingsDialog(JDialog dialog) {
		placeDialogNearCharacter(dialog);
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
		// HTML 설정 창 (JavaFX 가 있는 설치본). 없으면 아래의 Swing 설정 창으로 대신한다.
		webStatus = "";
		SettingsHost.View web = SettingsHost.createWebView(root, this);
		if (web != null) {
			webSettings = web;
			settingsDialog = web.dialog();
			placeDialogNearCharacter(settingsDialog);
			settingsDialog.setVisible(true);
			refreshSettingsDialogOpenState();
			return;
		}
		settingsDialog = buildSettingsDialog();
		showSettingsDialog(settingsDialog);
	}

	/** 메인 설정 창 구성 (띄우지는 않음). */
	JDialog buildSettingsDialog() {
		Ui.setAccent(this::petColor);
		JComponent header = Ui.header("비트 설정", "설정할 항목을 선택하세요", this::petColor, null);
		SettingsFrame frame = createSettingsDialog("캐릭터 설정", header, this::closeSettingsDialog);

		Ui.addFull(frame.body(), Ui.menuRow("외형 설정", "앱 따라 변신", () -> {
			rememberDialogLocation(settingsDialog);
			closeSettingsDialog();
			openAppearanceDialog();
		}), 0);
		Ui.addFull(frame.body(), Ui.menuRow("색깔 설정", "몸 색 바꾸기", () -> {
			rememberDialogLocation(settingsDialog);
			closeSettingsDialog();
			openColorDialog();
		}), 8);

		JButton closeButton = Ui.button("닫기", Ui.ButtonStyle.PRIMARY);
		closeButton.addActionListener(e -> closeSettingsDialog());
		// macOS 는 독/메뉴바 없이 실행되므로 종료 버튼을 둔다 (Esc 키로도 종료 가능)
		JButton quitButton = Ui.button("종료", Ui.ButtonStyle.DANGER);
		quitButton.addActionListener(e -> exitApplication());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		buttons.setOpaque(false);
		buttons.add(quitButton);
		buttons.add(closeButton);
		JLabel version = Ui.label("v" + UpdateService.currentVersion(), Font.PLAIN, 12f, Ui.MUTED);
		version.setFont(Ui.mono(Font.PLAIN, 12f));
		frame.footer().add(version, BorderLayout.WEST);
		frame.footer().add(buttons, BorderLayout.EAST);
		return frame.dialog();
	}

	/** 외형 설정 창 열기. */
	private void openAppearanceDialog() {
		if (isDialogAlive(appearanceDialog)) {
			appearanceDialog.toFront();
			appearanceDialog.requestFocus();
			return;
		}
		freezeMotionForSettings();
		appearanceDialog = buildAppearanceDialog();
		showSettingsDialog(appearanceDialog);
	}

	/** 외형 설정 창 구성 (띄우지는 않음). */
	JDialog buildAppearanceDialog() {
		Ui.setAccent(this::petColor);
		JComponent header = Ui.header("외형 설정", "앱을 쓸 때 비트의 모습이 바뀌어요", this::petColor, () -> {
			rememberDialogLocation(appearanceDialog);
			closeAppearanceDialog();
			openSettingsDialog();
		});
		SettingsFrame frame = createSettingsDialog("외형 설정", header, this::closeAppearanceDialog);
		JLabel status = statusLabel("");

		Runnable applyAppearanceSettings = () -> status.setText(saveAndApplyAppearance());

		String browserWhen = IS_MACOS ? "브라우저를 쓸 때" : "크롬·엣지를 쓸 때";
		List<JComponent> rows = List.of(
			appearanceRow("브라우저 돋보기", browserWhen, browserAppearanceMode, mode -> {
				browserAppearanceMode = mode;
				applyAppearanceSettings.run();
			}),
			appearanceRow("브라우저 IE 아이콘", browserWhen, browserIeAppearanceMode, mode -> {
				browserIeAppearanceMode = mode;
				applyAppearanceSettings.run();
			}),
			appearanceRow("디스코드 색상 변경", "디스코드를 쓸 때", discordAppearanceMode, mode -> {
				discordAppearanceMode = mode;
				applyAppearanceSettings.run();
			}),
			appearanceRow("스팀 픽셀 하트", "스팀을 쓸 때", pixelHeartAppearanceMode, mode -> {
				pixelHeartAppearanceMode = mode;
				applyAppearanceSettings.run();
			}),
			appearanceRow("음악 헤드셋", "유튜브·스포티파이", headsetAppearanceMode, mode -> {
				headsetAppearanceMode = mode;
				applyAppearanceSettings.run();
			})
		);
		Ui.addFull(frame.body(), Ui.optionList(rows), 0);
		Ui.addFull(frame.body(), Ui.label("자동: 그 앱을 쓸 때만 · 끔: 안 바뀜 · 항상 켬: 언제나", Font.PLAIN, 11f, Ui.MUTED), 10);

		frame.footer().add(status, BorderLayout.CENTER);
		return frame.dialog();
	}

	/** 외형 한 줄: 제목 + 언제 바뀌는지 + 자동/끔/항상 켬 선택. */
	private static JComponent appearanceRow(String title, String when, AppearanceMode selected, Consumer<AppearanceMode> onChange) {
		List<AppearanceMode> modes = List.of(AppearanceMode.values());
		List<String> labels = modes.stream().map(mode -> mode.label).toList();
		return Ui.optionRow(title, when, Ui.segmented(modes, labels, selected, onChange));
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
		colorDialog = buildColorDialog();
		showSettingsDialog(colorDialog);
	}

	/** 색상 설정 창 구성 (띄우지는 않음). */
	JDialog buildColorDialog() {
		Ui.setAccent(this::petColor);
		JComponent header = Ui.header("색깔 설정", "비트의 몸 색을 바꿀 수 있어요", this::petColor, () -> {
			rememberDialogLocation(colorDialog);
			closeColorDialog();
			openSettingsDialog();
		});
		SettingsFrame frame = createSettingsDialog("캐릭터 색상 설정", header, this::closeColorDialog);
		JDialog dialog = frame.dialog();
		JLabel status = statusLabel("현재 색상: " + baseCharacterColor);

		// 현재 색 미리보기
		Ui.FramedPanel preview = new Ui.FramedPanel(new BorderLayout(0, 10), 14);
		JLabel previewIcon = new JLabel(Ui.petIcon(1.6, () -> Util.toColor(baseCharacterColor)));
		previewIcon.setHorizontalAlignment(SwingConstants.CENTER);
		JLabel previewHex = Ui.label(baseCharacterColor.toUpperCase(), Font.BOLD, 13f, Ui.INK);
		previewHex.setFont(Ui.mono(Font.BOLD, 14f));
		previewHex.setHorizontalAlignment(SwingConstants.CENTER);
		preview.add(previewIcon, BorderLayout.CENTER);
		preview.add(previewHex, BorderLayout.SOUTH);
		Runnable refreshPreview = () -> {
			previewHex.setText(baseCharacterColor.toUpperCase());
			preview.repaint();
			dialog.repaint();
		};

		JButton defaultButton = Ui.button("기본 색", Ui.ButtonStyle.NORMAL);
		defaultButton.addActionListener(e -> {
			applyBaseColor(DEFAULT_CHARACTER_COLOR, false);
			refreshPreview.run();
			status.setText("기본 색 적용: " + DEFAULT_CHARACTER_COLOR);
		});
		JButton customButton = Ui.button("직접 고르기", Ui.ButtonStyle.PRIMARY);
		customButton.addActionListener(e -> {
			String[] liveSelectedColor = {null};
			ColorChooserDialog.Result result = ColorChooserDialog.show(
				dialog,
				baseCharacterColor,
				ColorSettings.loadCustomColorSlots(),
				colorCode -> {
					liveSelectedColor[0] = colorCode;
					previewBaseColor(colorCode);
					refreshPreview.run();
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
			refreshPreview.run();
			status.setText(savedPath == null ? "선택 색 적용됨(저장 실패): " + selected : "선택 색 적용: " + selected);
		});

		JPanel buttonRow = new JPanel(new GridLayout(1, 2, 8, 0));
		buttonRow.setOpaque(false);
		buttonRow.add(defaultButton);
		buttonRow.add(customButton);
		Ui.addFull(frame.body(), preview, 0);
		Ui.addFull(frame.body(), buttonRow, 12);
		frame.footer().add(status, BorderLayout.CENTER);
		return dialog;
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

		double charCenterX = posX + bodyLeft() + (layout.body().w / 2.0);
		double charCenterY = posY + walkBobCurrent + bodyTop() + (layout.body().h / 2.0);
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

		double faceLeft = posX + bodyLeft();
		double faceTop = posY + walkBobCurrent + bodyTop();
		double faceRight = faceLeft + layout.body().w;
		double headBottom = faceTop + (layout.body().h * PET_HEAD_ZONE_RATIO);
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
			double charLeft = posX + charOriginX + layoutBounds.minX() + limbOffsetX - Math.abs(limbSwayX);
			double charRight = posX + charOriginX + layoutBounds.maxX() + limbOffsetX + Math.abs(limbSwayX);
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

		armLiftCurrent += (armLiftTarget - armLiftCurrent) * ARM_POSE_SMOOTHING;
		if (Math.abs(armLiftTarget - armLiftCurrent) < 0.05) {
			armLiftCurrent = armLiftTarget;
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
		PartLayout.Part body = layout.body();
		PartLayout.Part leftArm = layout.leftArm();
		PartLayout.Part rightArm = layout.rightArm();
		PartLayout.Part leftLeg = layout.leftLeg();
		PartLayout.Part rightLeg = layout.rightLeg();
		double bodyLeft = bodyLeft();
		double bodyTop = bodyTop();

		// 몸통
		drawPart(g, body, bodyLeft, bodyTop, color);
		// 다리
		drawPart(g, leftLeg, charOriginX + leftLeg.x + limbOffsetX - limbSwayX, charOriginY + leftLeg.y + limbOffsetY, color);
		drawPart(g, rightLeg, charOriginX + rightLeg.x + limbOffsetX + limbSwayX, charOriginY + rightLeg.y + limbOffsetY, color);

		boolean pixelHeartVisible = isPixelHeartEnabled();
		if (pixelHeartVisible) {
			AppearanceRendering.renderPixelHeart(g, bodyLeft, bodyTop, body.w, eyeShapeScale);
		}
		if (isMediaHeadsetEnabled()) {
			AppearanceRendering.renderHeadset(g, bodyLeft, bodyTop, body.w, body.h, headsetStroke);
		}
		paintEyes(g);
		if (pixelHeartVisible) {
			AppearanceRendering.renderPixelHeartShine(g, bodyLeft, bodyTop, body.w, eyeShapeScale);
		}
		for (PetHeart heart : petHearts) {
			AppearanceRendering.renderPetHeart(g, heart.renderX, heart.renderY, heart.renderSize, heart.renderColor);
		}

		// 팔
		drawPart(g, leftArm, charOriginX + leftArm.x + limbOffsetX + limbSwayX,
			charOriginY + leftArm.y + armLiftCurrent + limbOffsetY, color);
		drawPart(g, rightArm, charOriginX + rightArm.x + limbOffsetX - limbSwayX,
			charOriginY + rightArm.y + armLiftCurrent + limbOffsetY, color);

		if (isBrowserMonocleEnabled()) {
			AppearanceRendering.renderMonocle(g, charOriginX + rightArm.right(), charOriginY + rightArm.y + armLiftCurrent,
				limbOffsetX, limbOffsetY, limbSwayX, rightArm.h, monocleRadius, monocleStroke, monocleHandleStroke);
		}
		if (isBrowserIeIconEnabled()) {
			AppearanceRendering.renderIeIcon(g, charOriginX + leftArm.x, charOriginY + leftArm.y + armLiftCurrent,
				limbOffsetX, limbOffsetY, limbSwayX, leftArm.h, ieIconRadius, ieIconOrbitStroke, ieIconBarStroke, ieIconFont);
		}
	}

	/** 부위 하나: 사진이 있으면 사진, 없으면 몸 색 네모 + 외곽선. */
	private void drawPart(Graphics2D g, PartLayout.Part part, double x, double y, String color) {
		BufferedImage image = partImages.get(part.image);
		if (image == null) {
			Draw.rect(g, x, y, x + part.w, y + part.h, color, "#000000", border);
			return;
		}
		drawImage(g, image, x, y, part.w, part.h, part.flip);
	}

	private static void drawImage(Graphics2D g, BufferedImage image, double x, double y, double w, double h, boolean flip) {
		Graphics2D gi = (Graphics2D) g.create();
		try {
			gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			gi.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			gi.translate(x, y);
			if (flip) {
				gi.translate(w, 0);
				gi.scale(-1, 1);
			}
			gi.scale(w / image.getWidth(), h / image.getHeight());
			gi.drawImage(image, 0, 0, null);
		} finally {
			gi.dispose();
		}
	}

	/**
	 * 표정에 맞는 눈 (파이썬 draw_eyes). 마지막으로 다시 그린 시점의 시선 값을 사용한다.
	 * 표정별 눈 사진이 있으면 눈 영역에 사진을, 없으면 기본 눈을 눈 영역에 맞춰 늘려 그린다.
	 */
	private void paintEyes(Graphics2D graphics) {
		PartLayout.Part eyes = layout.eyes();
		String expressionKey = switch (expression) {
			case EXPR_GRAB -> PartLayout.EXPR_GRAB;
			case EXPR_FALL -> PartLayout.EXPR_FALL;
			default -> PartLayout.EXPR_NEUTRAL;
		};
		BufferedImage eyeImage = partImages.get(layout.eyeImage(expressionKey));
		if (eyeImage != null) {
			drawImage(graphics, eyeImage, charOriginX + eyes.x + eyeDrawnShift + eyeDrawnInvalidOffset,
				charOriginY + eyes.y + (eyeDrawnShiftY * eyeShapeScale), eyes.w, eyes.h, false);
			return;
		}

		// 기본 눈 영역(PartLayout.DEFAULT_EYES_*)을 꾸민 눈 영역으로 옮기고 늘린다 (기본 배치면 그대로)
		double scaleX = eyes.w / (double) PartLayout.DEFAULT_EYES_W;
		double scaleY = eyes.h / (double) PartLayout.DEFAULT_EYES_H;
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			// 기본 눈 좌표는 창 기준으로 반올림해야 원래 모양과 똑같다 → 창 기준으로 그리고, 기본 눈 영역을 꾸민 영역으로 옮긴다
			g.translate(charOriginX + eyes.x, charOriginY + eyes.y);
			g.scale(scaleX, scaleY);
			g.translate(-(charOriginX + PartLayout.DEFAULT_EYES_X), -(charOriginY + PartLayout.DEFAULT_EYES_Y));
			paintDefaultEyes(g);
		} finally {
			g.dispose();
		}
	}

	/** 기본 눈 (창 기준 좌표). */
	private void paintDefaultEyes(Graphics2D g) {
		double shiftX = eyeDrawnShift;
		double shiftY = eyeDrawnShiftY;
		double invalidOffset = eyeDrawnInvalidOffset;
		double scale = eyeShapeScale;
		double offsetX = charOriginX + eyeShapeOffsetX;
		double offsetY = charOriginY + eyeShapeOffsetY;
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
