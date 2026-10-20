package com.mypet;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** my_pet_constants.py 포팅. 값은 파이썬 버전과 동일하게 유지한다. */
public final class Constants {
	private Constants() {
	}

	// 실행 중인 운영체제 구분 (크로스 플랫폼 분기용)
	private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	public static final boolean IS_WINDOWS = OS_NAME.startsWith("windows"); // 윈도우 여부
	public static final boolean IS_MACOS = OS_NAME.startsWith("mac"); // macOS 여부
	public static final boolean IS_LINUX = OS_NAME.startsWith("linux"); // 리눅스 여부

	// 비윈도우 환경에서 설정 파일을 저장할 홈 디렉토리 경로
	public static final Path USER_CONFIG_DIR = Path.of(System.getProperty("user.home"), ".my_pet"); // 사용자별 설정 폴더

	/**
	 * 앱 데이터 폴더. 윈도우는 %LOCALAPPDATA%\MyPet, 그 외는 ~/.my_pet
	 * 윈도우 실행기(MyPet.exe)가 앱(app, runtime)을 설치하는 폴더이기도 하다.
	 * 안에 settings.json(설정), app\MyPet-x.y.z.jar(자동 업데이트), my_pet.lock(중복 실행 방지)이 있다.
	 */
	public static Path appDataDir() {
		if (IS_WINDOWS) {
			String localAppData = System.getenv("LOCALAPPDATA");
			if (localAppData != null && !localAppData.isBlank()) {
				return Path.of(localAppData, "MyPet");
			}
			return Path.of(System.getProperty("user.home"), "AppData", "Local", "MyPet");
		}
		return USER_CONFIG_DIR;
	}

	public static final int BASE_FACE_SIZE = 96; // 얼굴 기본 크기(px)
	public static final int CHAR_WIDTH = BASE_FACE_SIZE / 2; // 얼굴 가로 크기
	public static final int CHAR_HEIGHT = BASE_FACE_SIZE / 3; // 얼굴 세로 크기
	public static final int DRAW_MARGIN = 2; // 내부 여백
	public static final int HEART_TOP_HEADROOM = 90; // 하트가 뜨는 상단 여유 공간
	public static final int LIMB_SIZE = Math.max(4, BASE_FACE_SIZE / 12); // 팔/다리 기본 크기
	public static final int LIMB_GAP = 1; // 팔/다리 간격
	public static final int SIDE_EXTENSION = LIMB_SIZE + LIMB_GAP; // 좌우 확장 폭
	public static final int BODY_INSET_X = 0; // 몸통 좌우를 줄이는 폭 (서 있을 때 팔과 띄우기)
	public static final int BODY_INSET_BOTTOM = 0; // 몸통 아래를 줄이는 높이 (서 있을 때 다리와 띄우기)
	public static final int BOTTOM_EXTENSION = LIMB_SIZE + LIMB_GAP; // 하단 확장 높이
	public static final int APPEARANCE_EXTRA_MARGIN = 0; // 기본 외형 여유 공간(외형별 추가 여백은 런타임에 적용)
	public static final double MAGNIFIER_HANDLE_LENGTH = 2.4; // 돋보기 손잡이 길이 배율
	public static final double MAGNIFIER_HANDLE_ANGLE_DEG = -60.0; // 돋보기 손잡이 각도(도)
	public static final double MAGNIFIER_HANDLE_THICKNESS = 1.4; // 돋보기 손잡이 두께 배율
	public static final double MAGNIFIER_RING_THICKNESS = 0.8; // 돋보기 테두리 두께 배율
	public static final int WINDOW_WIDTH = CHAR_WIDTH + (SIDE_EXTENSION * 2) + (DRAW_MARGIN * 2) + (APPEARANCE_EXTRA_MARGIN * 2); // 창 가로 크기
	public static final int WINDOW_HEIGHT = CHAR_HEIGHT + BOTTOM_EXTENSION + (DRAW_MARGIN * 2) + HEART_TOP_HEADROOM; // 창 세로 크기
	public static final String DEFAULT_CHARACTER_COLOR = "#4fc3f7"; // 기본 캐릭터 색상

	/** 설정 파일 (색깔, 사용자 지정 색, 외형 설정). com.mypet.settings.SettingsStore 참고 */
	public static final Path SETTINGS_FILE = appDataDir().resolve("settings.json");

	// 예전 형식(파이썬 버전 ~ 2.0.0)의 설정 파일 위치. settings.json 이 없을 때 여기서 읽어 옮기고 지운다.
	public static final List<Path> LEGACY_CHARACTER_COLOR_PATHS = IS_WINDOWS
		? List.of(Path.of("C:\\my_pet_custom_color.txt"), Path.of("C:\\Users\\Public\\my_pet_custom_color.txt"))
		: List.of(USER_CONFIG_DIR.resolve("custom_color.txt"));
	public static final List<Path> LEGACY_CUSTOM_COLORS_PATHS = IS_WINDOWS
		? List.of(Path.of("C:\\my_pet_custom_colors.txt"), Path.of("C:\\Users\\Public\\my_pet_custom_colors.txt"))
		: List.of(USER_CONFIG_DIR.resolve("custom_colors.txt"));
	public static final List<Path> LEGACY_APPEARANCE_SETTINGS_PATHS = IS_WINDOWS
		? List.of(Path.of("C:\\my_pet_appearance_settings.txt"), Path.of("C:\\Users\\Public\\my_pet_appearance_settings.txt"))
		: List.of(USER_CONFIG_DIR.resolve("appearance_settings.txt"));
	public static final int CUSTOM_COLOR_SLOT_COUNT = 16; // 사용자 지정 색 슬롯 개수
	public static final String CUSTOM_COLOR_DEFAULT = "#ffffff"; // 사용자 지정 색 기본값

	// 업데이트: GitHub Releases 의 최신 릴리스를 확인한다
	public static final String GITHUB_REPOSITORY = "janghg0929/my_pet"; // 소유자/저장소 이름
	public static final String UPDATE_JAR_ASSET_NAME = "MyPet.jar"; // 릴리스에 첨부되는 업데이트 jar 이름
	public static final int UPDATE_CHECK_TIMEOUT_SEC = 8; // 업데이트 체크 타임아웃(초)

	public static final int SETTINGS_DIALOG_FIXED_WIDTH = 340; // 설정 창 고정 너비
	public static final int SETTINGS_DIALOG_FIXED_HEIGHT = 450; // 설정 창 고정 높이
	public static final double COLOR_ERROR_SHAKE_AMPLITUDE = 7.0; // 색상 오류 흔들림 진폭
	public static final double COLOR_ERROR_SHAKE_SPEED = 0.475; // 색상 오류 흔들림 속도
	public static final int COLOR_ERROR_SHAKE_CYCLES = 3; // 색상 오류 흔들림 횟수
	public static final double GRAVITY = 0.65; // 중력 가속도
	public static final double MAX_FALL_SPEED = 30.0; // 낙하 최대 속도
	public static final int GRAVITY_RAMP_MS = 480; // 중력 램프 지속 시간
	public static final double GRAVITY_RAMP_GAIN = 1.0; // 중력 램프 강화 비율
	public static final int FRAME_MS = 10; // 프레임 간격(ms) - 로직 내부 시간 단위
	/**
	 * 실제 틱 주기(ms). 파이썬(Tk) 버전은 after(10)을 요청했지만 윈도우 타이머 해상도 때문에
	 * 실제로는 약 15.6ms(초당 64회)마다 실행됐다. 움직임 속도/느낌을 그대로 맞추기 위해 64Hz 로 고정한다.
	 */
	public static final double TICK_PERIOD_MS = 1000.0 / 64.0;
	public static final double DRAG_SMOOTHING = 0.4; // 드래그 보간 비율
	public static final double DRAG_VELOCITY_SMOOTHING = 0.45; // 드래그 속도 보간 비율
	public static final double RELEASE_THROW_MULT = 1.0; // 던지기 속도 배율
	public static final double RELEASE_THROW_MAX_SPEED = 30.0; // 던지기 최대 속도
	public static final double RELEASE_THROW_MIN_SPEED = 0.35; // 던지기 최소 속도
	public static final double RELEASE_AIR_FRICTION = 0.985; // 공중 마찰 계수
	public static final double RELEASE_BOUNCE_RESTITUTION = 0.55; // 바운스 탄성 계수
	public static final double RELEASE_BOUNCE_MIN_SPEED = 1.2; // 바운스 최소 속도
	public static final double BOUNCE_MIN_FALL_PX = 65.0; // 바운스 최소 낙하 거리
	public static final double BOUNCE_HEIGHT_GAIN = 0.5; // 낙하 높이 대비 바운스 배율
	public static final double BOUNCE_MAX_SPEED = 20.0; // 바운스 최대 속도
	public static final int SUPPORT_GRACE_MS = 300; // 플랫폼 갱신 유예 시간
	public static final double ARM_POSE_SMOOTHING = 0.35; // 팔 포즈 보간 비율
	public static final int AUTO_WAIT_MIN_MS = 500; // 자동 이동 대기 최소값
	public static final int AUTO_WAIT_MAX_MS = 2200; // 자동 이동 대기 최대값
	public static final double AUTO_STEP_MIN_PX = 60.0; // 자동 이동 최소 거리
	public static final double AUTO_STEP_MAX_PX = 280.0; // 자동 이동 최대 거리
	public static final double AUTO_MOVE_SPEED_MIN = 0.45; // 자동 이동 최소 속도
	public static final double AUTO_MOVE_SPEED_MAX = 1.2; // 자동 이동 최대 속도
	public static final double AUTO_WALK_PROBABILITY = 0.9; // 자동 이동 확률
	public static final int DOUBLE_JUMP_COUNT = 2; // 연속 점프 횟수
	public static final double DOUBLE_JUMP_VELOCITY = 9.5; // 연속 점프 속도
	public static final double WALK_BOB_MAX = LIMB_SIZE * 0.38; // 걷기 바운스 최대값
	public static final double WALK_BOB_SPEED = 0.07; // 걷기 바운스 속도
	public static final double WALK_BOB_SMOOTHING = 0.28; // 걷기 바운스 보간
	public static final double LIMB_REACT_GAIN = 0.6; // 팔다리 반응 계수
	public static final double LIMB_REACT_MAX_X = LIMB_SIZE * 0.6; // 팔다리 X 반응 한계
	public static final double LIMB_REACT_MAX_Y = LIMB_SIZE * 0.45; // 팔다리 Y 반응 한계
	public static final double LIMB_REACT_SMOOTHING = 0.35; // 팔다리 반응 보간
	public static final double LIMB_SWAY_MAX_X = LIMB_SIZE * 0.225; // 팔다리 흔들림 최대값
	public static final double LIMB_SWAY_SPEED_SCALE = 0.11; // 팔다리 흔들림 속도 스케일
	public static final double LIMB_SWAY_SPEED_MIN = 0.018; // 팔다리 흔들림 최소 속도
	public static final double LIMB_SWAY_SPEED_MAX = 0.09; // 팔다리 흔들림 최대 속도
	public static final double LIMB_SWAY_SMOOTHING = 0.18; // 팔다리 흔들림 보간
	public static final double EYE_X_SHIFT = -2.5; // 눈 X 위치 보정
	public static final double EYE_Y_SHIFT = 0; // 눈 Y 위치 보정
	public static final double EYE_LOOK_MAX = 4.0; // 눈 시선 최대 이동
	public static final double EYE_LOOK_GAIN = 4.2; // 눈 시선 이동 계수
	public static final double EYE_LOOK_SMOOTHING = 0.35; // 눈 시선 보간
	public static final double EYE_REDRAW_THRESHOLD = 0.18; // 눈 리드로잉 임계값
	public static final double EYE_LOOK_MAX_Y = 3.0; // 눈 시선 Y 최대 이동
	public static final double EYE_CURSOR_LOOK_GAIN = 0.09; // 커서 시선 이동 계수
	public static final double EYE_CURSOR_LOOK_GAIN_Y = 0.08; // 커서 시선 Y 이동 계수
	public static final double CURSOR_STOP_RADIUS = 70.0; // 커서 정지 반경
	public static final int CURSOR_RESUME_DELAY_MS = 1000; // 커서 이탈 후 재개 지연
	public static final double PET_HEAD_ZONE_RATIO = 0.48; // 머리 영역 비율
	public static final double PET_MIN_STEP_PX = 1.5; // 쓰다듬기 최소 이동 거리
	public static final double PET_MIN_SPEED_PX_PER_S = 180.0; // 쓰다듬기 최소 속도
	public static final int PET_SHAKE_WINDOW_MS = 900; // 쓰다듬기 판정 시간
	public static final int PET_SHAKE_FLIPS_TO_TRIGGER = 6; // 쓰다듬기 트리거 횟수
	public static final int PET_FACE_HOLD_MS = 480; // 표정 유지 시간
	public static final int PET_HEART_MAX_VISIBLE = 3; // 하트 최대 표시 개수
	public static final int PET_HEART_SPAWN_MIN_MS = 90; // 하트 생성 최소 간격
	public static final int PET_HEART_SPAWN_MAX_MS = 170; // 하트 생성 최대 간격
	public static final int PET_HEART_LIFETIME_MIN_MS = 420; // 하트 최소 수명
	public static final int PET_HEART_LIFETIME_MAX_MS = 760; // 하트 최대 수명
	public static final double PET_HEART_SIZE_MIN = 6.0; // 하트 최소 크기
	public static final double PET_HEART_SIZE_MAX = 10.0; // 하트 최대 크기
	public static final double PET_HEART_RISE_MIN = 0.22; // 하트 상승 최소 속도
	public static final double PET_HEART_RISE_MAX = 0.42; // 하트 상승 최대 속도
	public static final double PET_HEART_DRIFT_X = 0.16; // 하트 좌우 흔들림
	public static final double PET_HEART_BODY_X_MIN = 0.2; // 하트 생성 X 최소 비율
	public static final double PET_HEART_BODY_X_MAX = 0.8; // 하트 생성 X 최대 비율
	public static final double PET_HEART_ABOVE_GAP_MIN = 3.0; // 하트 위 여백 최소값
	public static final double PET_HEART_ABOVE_GAP_MAX = 10.0; // 하트 위 여백 최대값
	public static final List<String> PET_HEART_COLORS = List.of("#ff5f96", "#ff83b3", "#ffb3d1", "#ffe0ed"); // 하트 색상 팔레트
	public static final int WINDOW_PLATFORM_REFRESH_MS = 120; // 창 플랫폼 재탐색 주기
	public static final int WINDOW_PLATFORM_REFRESH_IDLE_MS = 260; // 정지 상태 플랫폼 재탐색 주기
	public static final int WINDOW_PLATFORM_REFRESH_AIR_MS = 200; // 공중 상태 플랫폼 재탐색 주기
	public static final int WINDOW_PLATFORM_REFRESH_MOVE_MS = 150; // 자동 이동 중 플랫폼 재탐색 주기
	public static final int WINDOW_PLATFORM_SAMPLE_STEP = 24; // 플랫폼 가시 구간 샘플 간격
	public static final int WINDOW_PLATFORM_SAMPLE_STEP_IDLE = 36; // 정지 상태 샘플 간격
	public static final int WINDOW_PLATFORM_SAMPLE_STEP_AIR = 32; // 공중 상태 샘플 간격
	public static final int WINDOW_PLATFORM_SAMPLE_STEP_MOVE = 28; // 자동 이동 중 샘플 간격
	public static final double PLATFORM_MIN_OVERLAP = Math.max(10.0, WINDOW_WIDTH * 0.22); // 플랫폼 판정 최소 겹침
	public static final double PLATFORM_SNAP_TOLERANCE = 2.5; // 플랫폼 스냅 허용 오차
	public static final double TASKBAR_PLATFORM_IGNORE_MARGIN = 36.0; // 작업표시줄 무시 여백
	public static final int APP_STATE_CHECK_INTERVAL_MS = 120; // 앱 상태 점검 주기
	public static final int APP_MODE_HOLD_ON_DRAG_MS = 420; // 드래그 후 모드 유지 시간
	public static final int RAINBOW_SPARKLE_CLICK_COUNT = 20; // 무지개 반짝 클릭 횟수
	public static final int RAINBOW_CLICK_CHAIN_TIMEOUT_MS = 1200; // 연속 클릭 허용 간격(ms)
	public static final int RAINBOW_SPARKLE_DURATION_MS = 1600; // 무지개 반짝 지속 시간(ms)
	public static final double RAINBOW_SPARKLE_SPEED = 0.06; // 무지개 색상 변화 속도
	public static final String DISCORD_ICON_BG_COLOR = "#5865F2"; // 디스코드 배경색

	// 앱 감지용 프로세스 이름 (소문자). 윈도우는 exe 이름, macOS 는 앱 이름
	public static final Set<String> DISCORD_PROCESS_NAME_SET = IS_MACOS
		? Set.of("discord", "discord canary", "discord ptb")
		: Set.of("discord.exe", "discordcanary.exe", "discordptb.exe"); // 디스코드 프로세스
	public static final Set<String> BROWSER_PROCESS_NAME_SET = IS_MACOS
		? Set.of("google chrome", "microsoft edge", "safari")
		: Set.of("chrome.exe", "msedge.exe"); // 브라우저 프로세스
	public static final Set<String> STEAM_PROCESS_NAME_SET = IS_MACOS
		? Set.of("steam", "steam helper")
		: Set.of("steam.exe", "steamwebhelper.exe"); // 스팀 프로세스
	public static final Set<String> SPOTIFY_PROCESS_NAME_SET = IS_MACOS
		? Set.of("spotify")
		: Set.of("spotify.exe"); // 스포티파이 프로세스
	public static final List<String> MEDIA_WINDOW_TITLE_KEYWORDS = List.of("youtube", "youtube music", "spotify"); // 미디어 서비스 창 제목 키워드

	public static final String BROWSER_MONOCLE_COLOR = "#000000"; // 돋보기 테두리 색
	public static final String BROWSER_MONOCLE_CHAIN_COLOR = "#723B00"; // 돋보기 손잡이 색
	public static final String BROWSER_MAGNIFIER_FILL_COLOR = "#bfe9ff"; // 돋보기 유리 색
	public static final String BROWSER_IE_ICON_COLOR = "#0078d7"; // IE 아이콘 기본 색
	public static final String BROWSER_IE_ICON_ORBIT_COLOR = "#f6b33d"; // IE 아이콘 궤도 색
	public static final String PIXEL_HEART_COLOR = "#ff4f7a"; // 픽셀 하트 색
	public static final String PIXEL_HEART_SHINE_COLOR = "#ffe6f0"; // 픽셀 하트 반짝임 색
	public static final String HEADSET_COLOR = "#2f2f2f"; // 헤드셋 색

	public static final String EXPR_NEUTRAL = "neutral"; // 기본 표정
	public static final String EXPR_GRAB = "grab"; // 집기 표정
	public static final String EXPR_FALL = "fall"; // 추락 표정
	public static final String EXPR_INVALID = "invalid"; // 오류 표정

	public static final String APP_MODE_DEFAULT = "default";
	public static final String APP_MODE_BROWSER = "browser";
	public static final String APP_MODE_DISCORD = "discord";
	public static final String APP_MODE_STEAM = "steam";
	public static final String APP_MODE_MEDIA = "media";
}
