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
	void createsWorkingShortcutInKoreanFolderAndKeepsExistingOne(@TempDir Path dir) throws Exception {
		assumeTrue(Constants.IS_WINDOWS, "윈도우에서만");
		// 한글·작은따옴표가 든 폴더에 가짜 설치본과 바로가기 폴더
		Path home = Files.createDirectories(dir.resolve("설치 폴더'1"));
		Path exe = Files.writeString(home.resolve("MyPet.exe"), "x");
		Path desktop = Files.createDirectories(dir.resolve("바탕 화면"));
		List<String> folders = List.of(Shortcuts.quote(desktop.toString()));
		assertEquals(0, Shortcuts.run(Shortcuts.script(exe, home, folders, true)));
		Path link = desktop.resolve(Shortcuts.NAME + ".lnk");
		assertTrue(Files.isRegularFile(link), "바로가기 파일");
		// 바로가기를 다시 읽어서 대상 확인 (결과를 파일로 받아 한글이 깨지지 않게)
		Path out = dir.resolve("target.txt");
		assertEquals(0, Shortcuts.run("$l = (New-Object -ComObject WScript.Shell).CreateShortcut(" + Shortcuts.quote(link.toString()) + ")\n"
			+ "[IO.File]::WriteAllText(" + Shortcuts.quote(out.toString()) + ", $l.TargetPath + '|' + $l.WorkingDirectory, [Text.Encoding]::UTF8)\n"));
		String read = Files.readString(out, StandardCharsets.UTF_8).replace("\uFEFF", "");
		assertEquals(exe + "|" + home, read);
		// 설정의 [바로가기 만들기]: 이미 있으면 그대로 두고 "이미 있어요"
		assertEquals(Shortcuts.EXIT_EXISTS, Shortcuts.run(Shortcuts.script(exe, home, folders, true)));
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
