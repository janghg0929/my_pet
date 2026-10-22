package com.mypet;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 윈도우 바로가기: 바탕 화면과 시작 메뉴에 "MY_PET" 바로가기를 만든다 (설치된 %LOCALAPPDATA%\MyPet\MyPet.exe 를 실행).
 * 한 번만 만든다 (표시 파일): 사용자가 바로가기를 지우면 다시 만들지 않는다.
 * 새로 설치한 사람도, 자동 업데이트(jar)로 이 버전이 된 사람도 처음 실행 때 생긴다.
 */
public final class Shortcuts {
	public static final String NAME = "MY_PET";
	static final String MARKER = "shortcuts-created";
	/** 바탕 화면(OneDrive 로 옮긴 곳 포함)과 시작 메뉴 프로그램 폴더 (PowerShell 식). */
	static final List<String> DEFAULT_FOLDERS =
		List.of("[Environment]::GetFolderPath('Desktop')", "[Environment]::GetFolderPath('Programs')");

	private Shortcuts() {
	}

	/** 아직 안 만들었고 설치된 실행 파일이 있으면 뒤에서 만든다 (시작을 늦추지 않게). */
	public static void createOnce(Path home) {
		if (!Constants.IS_WINDOWS) {
			return;
		}
		Path exe = home.resolve("MyPet.exe");
		Path marker = home.resolve(MARKER);
		if (Files.exists(marker) || !Files.isRegularFile(exe)) {
			return;
		}
		Thread thread = new Thread(() -> {
			if (run(script(exe, home, DEFAULT_FOLDERS))) {
				try {
					Files.writeString(marker, "바탕 화면·시작 메뉴 바로가기를 만들었어요. 지워도 다시 만들지 않아요.\n", StandardCharsets.UTF_8);
				} catch (java.io.IOException e) {
					System.err.println("[MY_PET] 바로가기 표시 파일 저장 실패: " + e);
				}
			}
		}, "mypet-shortcuts");
		thread.setDaemon(true);
		thread.start();
	}

	/** PowerShell 스크립트를 실행한다. 성공하면 true. */
	static boolean run(String script) {
		try {
			// 한글 경로(OneDrive 바탕 화면 등)도 깨지지 않게 스크립트를 UTF-16 base64 로 넘긴다
			String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
			Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-EncodedCommand", encoded).redirectErrorStream(true).start();
			process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
			if (!process.waitFor(30, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (java.io.IOException | RuntimeException e) {
			System.err.println("[MY_PET] 바로가기를 만들지 못했어요: " + e);
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** folders(PowerShell 식) 마다 WScript.Shell 로 "MY_PET.lnk" 를 만드는 스크립트. */
	static String script(Path exe, Path home, List<String> folders) {
		return "$ErrorActionPreference = 'Stop'\n"
			+ "$shell = New-Object -ComObject WScript.Shell\n"
			+ "foreach ($dir in @(" + String.join(", ", folders) + ")) {\n"
			+ "  if (-not $dir) { continue }\n"
			+ "  $link = $shell.CreateShortcut((Join-Path $dir " + quote(NAME + ".lnk") + "))\n"
			+ "  $link.TargetPath = " + quote(exe.toString()) + "\n"
			+ "  $link.WorkingDirectory = " + quote(home.toString()) + "\n"
			+ "  $link.IconLocation = " + quote(exe + ",0") + "\n"
			+ "  $link.Description = " + quote("데스크톱 펫 비트") + "\n"
			+ "  $link.Save()\n"
			+ "}\n";
	}

	/** PowerShell 작은따옴표 문자열 (안의 ' 는 '' 로). */
	static String quote(String text) {
		return "'" + text.replace("'", "''") + "'";
	}
}
