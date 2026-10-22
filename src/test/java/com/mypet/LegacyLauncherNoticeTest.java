package com.mypet;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyLauncherNoticeTest {
	static final Path HOME = Path.of("C:\\Users\\me\\AppData\\Local\\MyPet");
	static final Optional<String> INSTALLED = Optional.of("C:\\Users\\me\\AppData\\Local\\MyPet\\MyPet.exe");

	@Test
	void showsOnlyWhenInstalledAppStartsWithoutShortcutMark() {
		// 예전 MyPet.exe: 설치된 앱을 아무 표시 없이 켠다 (대소문자 달라도 같은 파일)
		assertTrue(LegacyLauncherNotice.shouldShow(new String[0], INSTALLED, HOME, true));
		assertTrue(LegacyLauncherNotice.shouldShow(new String[0], Optional.of("c:\\users\\ME\\appdata\\local\\mypet\\MYPET.EXE"), HOME, true));
		// 바로가기·설치 프로그램, 업데이트 뒤 다시 켜기
		assertFalse(LegacyLauncherNotice.shouldShow(new String[] {MyPet.SHORTCUT_ARG}, INSTALLED, HOME, true));
		assertFalse(LegacyLauncherNotice.shouldShow(new String[] {MyPet.AFTER_UPDATE_ARG}, INSTALLED, HOME, true));
		// 개발 중(java.exe), 미리보기 폴더, 맥, 알 수 없음
		assertFalse(LegacyLauncherNotice.shouldShow(new String[0], Optional.of("C:\\jdk\\bin\\java.exe"), HOME, true));
		assertFalse(LegacyLauncherNotice.shouldShow(new String[0], Optional.of("C:\\Users\\me\\Downloads\\MyPet\\MyPet.exe"), HOME, true));
		assertFalse(LegacyLauncherNotice.shouldShow(new String[0], INSTALLED, HOME, false));
		assertFalse(LegacyLauncherNotice.shouldShow(new String[0], Optional.empty(), HOME, true));
	}
}
