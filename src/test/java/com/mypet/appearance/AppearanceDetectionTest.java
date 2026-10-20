package com.mypet.appearance;

import com.mypet.Constants;
import com.mypet.appearance.AppearanceDetection.WindowState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppearanceDetectionTest {
	private static final String BROWSER = Constants.BROWSER_PROCESS_NAME_SET.iterator().next();
	private static final String DISCORD = Constants.DISCORD_PROCESS_NAME_SET.iterator().next();
	private static final long OWN_PID = ProcessHandle.current().pid();

	private static WindowState state(Long pid, String process, String title, boolean browser, boolean discord) {
		return new WindowState(pid, process, title, browser, discord, false, false);
	}

	@Test
	void browserActiveGivesBrowserMode() {
		assertEquals(Constants.APP_MODE_BROWSER,
			AppearanceDetection.resolveAppMode(state(1L, BROWSER, "Google", true, false), null));
	}

	@Test
	void youtubeTitleGivesMediaMode() {
		assertEquals(Constants.APP_MODE_MEDIA,
			AppearanceDetection.resolveAppMode(state(1L, BROWSER, "Song - YouTube", true, false), null));
	}

	@Test
	void discordOverlayIsIgnored() {
		assertEquals(Constants.APP_MODE_DEFAULT,
			AppearanceDetection.resolveAppMode(state(1L, DISCORD, "Discord Overlay", false, true), null));
		assertEquals(Constants.APP_MODE_DISCORD,
			AppearanceDetection.resolveAppMode(state(1L, DISCORD, "#general", false, true), null));
	}

	@Test
	void clickingPetKeepsModeWhileTargetVisible() {
		assertEquals(Constants.APP_MODE_BROWSER,
			AppearanceDetection.resolveAppMode(state(OWN_PID, "java.exe", "", true, false), Constants.APP_MODE_BROWSER));
		assertEquals(Constants.APP_MODE_DEFAULT,
			AppearanceDetection.resolveAppMode(state(OWN_PID, "java.exe", "", false, false), Constants.APP_MODE_BROWSER));
	}

	@Test
	void otherAppReturnsDefault() {
		assertEquals(Constants.APP_MODE_DEFAULT,
			AppearanceDetection.resolveAppMode(state(1L, "explorer.exe", "", true, true), Constants.APP_MODE_BROWSER));
	}

	@Test
	void appearanceModes() {
		assertEquals(false, AppearanceMode.OFF.isEnabled(true));
		assertEquals(true, AppearanceMode.ALWAYS.isEnabled(false));
		assertEquals(true, AppearanceMode.AUTO.isEnabled(true));
	}
}
