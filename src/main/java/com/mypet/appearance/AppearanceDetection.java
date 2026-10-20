package com.mypet.appearance;

import com.mypet.Constants;
import com.mypet.platform.PlatformSupport;
import com.mypet.platform.PlatformSupport.ForegroundInfo;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/** 활성 창/보이는 창 감지 및 외형용 app_mode 결정 (my_pet_appearance_detection.py 포팅). */
public final class AppearanceDetection {
	private AppearanceDetection() {
	}

	public record WindowState(
		Long activePid,
		String activeProcessName,
		String activeWindowTitle,
		boolean browserVisible,
		boolean discordVisible,
		boolean steamVisible,
		boolean spotifyVisible
	) {
	}

	private static final long OWN_PID = ProcessHandle.current().pid();

	public static WindowState getWindowState(PlatformSupport platform) {
		ForegroundInfo foreground = platform.foreground();
		Set<String> visible = platform.visibleProcessNames();
		return new WindowState(
			foreground == null ? null : foreground.pid(),
			foreground == null ? null : foreground.processName(),
			foreground == null || foreground.windowTitle() == null ? "" : foreground.windowTitle(),
			!Collections.disjoint(visible, Constants.BROWSER_PROCESS_NAME_SET),
			!Collections.disjoint(visible, Constants.DISCORD_PROCESS_NAME_SET),
			!Collections.disjoint(visible, Constants.STEAM_PROCESS_NAME_SET),
			!Collections.disjoint(visible, Constants.SPOTIFY_PROCESS_NAME_SET)
		);
	}

	static boolean isMediaAppActive(String processName, String windowTitle) {
		if (Constants.SPOTIFY_PROCESS_NAME_SET.contains(processName)) {
			return true;
		}
		if (!Constants.BROWSER_PROCESS_NAME_SET.contains(processName)) {
			return false;
		}
		String titleLower = windowTitle.toLowerCase(Locale.ROOT);
		for (String keyword : Constants.MEDIA_WINDOW_TITLE_KEYWORDS) {
			if (titleLower.contains(keyword)) {
				return true;
			}
		}
		return false;
	}

	/** 포그라운드가 펫 자신(또는 알 수 없음)인지. */
	static boolean isPetForeground(WindowState state) {
		if (state.activeProcessName() == null) {
			return true;
		}
		return state.activePid() != null && state.activePid() == OWN_PID;
	}

	static boolean isAppModeVisible(String appMode, WindowState state) {
		return switch (appMode) {
			case Constants.APP_MODE_MEDIA -> state.browserVisible() || state.spotifyVisible();
			case Constants.APP_MODE_STEAM -> state.steamVisible();
			case Constants.APP_MODE_BROWSER -> state.browserVisible();
			case Constants.APP_MODE_DISCORD -> state.discordVisible();
			default -> false;
		};
	}

	public static String resolveAppMode(WindowState state, String currentAppMode) {
		String processName = state.activeProcessName();
		if (isPetForeground(state)) {
			if (currentAppMode != null && isAppModeVisible(currentAppMode, state)) {
				return currentAppMode;
			}
			return Constants.APP_MODE_DEFAULT;
		}
		if (isMediaAppActive(processName, state.activeWindowTitle())) {
			return Constants.APP_MODE_MEDIA;
		}
		if (Constants.STEAM_PROCESS_NAME_SET.contains(processName) && state.steamVisible()) {
			return Constants.APP_MODE_STEAM;
		}
		if (Constants.BROWSER_PROCESS_NAME_SET.contains(processName) && state.browserVisible()) {
			return Constants.APP_MODE_BROWSER;
		}
		if (Constants.DISCORD_PROCESS_NAME_SET.contains(processName)
			&& state.discordVisible()
			&& !state.activeWindowTitle().toLowerCase(Locale.ROOT).contains("overlay")) {
			return Constants.APP_MODE_DISCORD;
		}
		return Constants.APP_MODE_DEFAULT;
	}
}
