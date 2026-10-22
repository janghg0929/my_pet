package com.mypet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ShortcutsTest {
	@Test
	void quotesPathsForPowerShell() {
		assertEquals("'C:\\Users\\it''s me'", Shortcuts.quote("C:\\Users\\it's me"));
	}

	@Test
	void writesIconFileFromApp(@TempDir Path dir) throws Exception {
		Path icon = Shortcuts.writeIcon(dir);
		assertEquals(dir.resolve(Shortcuts.ICON_FILE), icon);
		byte[] head = Files.readAllBytes(icon);
		assertTrue(head.length > 1000 && head[0] == 0 && head[1] == 0 && head[2] == 1 && head[3] == 0, ".ico 머리말");
	}

	@Test
	void createsWorkingShortcutInKoreanFolderAndKeepsExistingOne(@TempDir Path dir) throws Exception {
		assumeTrue(Constants.IS_WINDOWS, "윈도우에서만");
		// 한글·작은따옴표가 든 폴더에 가짜 설치본과 바로가기 폴더
		Path home = Files.createDirectories(dir.resolve("설치 폴더'1"));
		Path exe = Files.writeString(home.resolve("MyPet.exe"), "x");
		Path icon = Shortcuts.writeIcon(home);
		Path desktop = Files.createDirectories(dir.resolve("바탕 화면"));
		List<String> folders = List.of(Shortcuts.quote(desktop.toString()));
		assertEquals(0, Shortcuts.run(Shortcuts.script(exe, home, icon, folders, true)));
		Path link = desktop.resolve(Shortcuts.NAME + ".lnk");
		assertTrue(Files.isRegularFile(link), "바로가기 파일");
		assertEquals(exe + "|" + MyPet.SHORTCUT_ARG + "|" + home + "|" + icon + ",0", readLink(link, dir));
		// 설정의 [바로가기]: 이미 있으면 그대로 두고 "이미 있어요"
		assertEquals(Shortcuts.EXIT_EXISTS, Shortcuts.run(Shortcuts.script(exe, home, icon, folders, true)));
		// --shortcut 이 없는 예전 바로가기는 고쳐 쓴다
		assertEquals(0, Shortcuts.run("$s = New-Object -ComObject WScript.Shell; $l = $s.CreateShortcut(" + Shortcuts.quote(link.toString())
			+ "); $l.Arguments = ''; $l.Save()\n"));
		assertEquals(0, Shortcuts.run(Shortcuts.script(exe, home, icon, folders, true)));
		assertEquals(Shortcuts.EXIT_EXISTS, Shortcuts.run(Shortcuts.script(exe, home, icon, folders, true)));
	}

	@Test
	void repairsOldDesktopShortcutIcon(@TempDir Path dir) throws Exception {
		assumeTrue(Constants.IS_WINDOWS, "윈도우에서만");
		Path home = Files.createDirectories(dir.resolve("설치"));
		Path exe = Files.writeString(home.resolve("MyPet.exe"), "x");
		Path icon = Shortcuts.writeIcon(home);
		Path desktop = Files.createDirectories(dir.resolve("바탕 화면"));
		Path link = desktop.resolve(Shortcuts.NAME + ".lnk");
		// 예전 바로가기: exe 아이콘(윈도우가 자바 아이콘으로 기억), 표시 없음
		assertEquals(0, Shortcuts.run("$l = (New-Object -ComObject WScript.Shell).CreateShortcut(" + Shortcuts.quote(link.toString()) + ")\n"
			+ "$l.TargetPath = " + Shortcuts.quote(exe.toString()) + "; $l.IconLocation = " + Shortcuts.quote(exe + ",0") + "; $l.Save()\n"));
		assertEquals(0, Shortcuts.run(Shortcuts.repairScript(exe, home, icon, Shortcuts.quote(desktop.toString()))));
		assertEquals(exe + "|" + MyPet.SHORTCUT_ARG + "|" + home + "|" + icon + ",0", readLink(link, dir));
		// 바로가기가 없으면 만들지 않는다
		Path empty = Files.createDirectories(dir.resolve("빈 바탕 화면"));
		assertEquals(0, Shortcuts.run(Shortcuts.repairScript(exe, home, icon, Shortcuts.quote(empty.toString()))));
		assertFalse(Files.exists(empty.resolve(Shortcuts.NAME + ".lnk")));
	}

	/** 바로가기를 다시 읽는다: 대상|인자|시작 위치|아이콘 (결과를 파일로 받아 한글이 깨지지 않게). */
	private static String readLink(Path link, Path dir) throws Exception {
		Path out = dir.resolve("link.txt");
		assertEquals(0, Shortcuts.run("$l = (New-Object -ComObject WScript.Shell).CreateShortcut(" + Shortcuts.quote(link.toString()) + ")\n"
			+ "[IO.File]::WriteAllText(" + Shortcuts.quote(out.toString())
			+ ", $l.TargetPath + '|' + $l.Arguments + '|' + $l.WorkingDirectory + '|' + $l.IconLocation, [Text.Encoding]::UTF8)\n"));
		return Files.readString(out, StandardCharsets.UTF_8).replace("\uFEFF", "");
	}

	@Test
	void skipsWhenNotInstalled(@TempDir Path dir) throws Exception {
		// 실행 파일이 없으면 아무것도 하지 않는다 (표시 파일도 안 생김)
		Shortcuts.createOnce(dir);
		Thread.sleep(200);
		assertFalse(Files.exists(dir.resolve(Shortcuts.MARKER)));
		assertEquals(Shortcuts.Result.NOT_INSTALLED, Shortcuts.createDesktop(dir));
	}
}
