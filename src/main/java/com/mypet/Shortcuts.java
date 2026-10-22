package com.mypet;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 윈도우 바로가기 "MY_PET" (설치된 %LOCALAPPDATA%\MyPet\MyPet.exe 를 실행).
 * - 시작 메뉴: 자동 업데이트(jar)로 이 버전이 된 사람도 켤 수 있게, 처음 실행 때 한 번만 만든다 (표시 파일).
 * - 바탕 화면: 처음 설치할 때 설치 창(MyPetLauncher.cs)에서 고르거나, 설정의 [바로가기 만들기] 를 누른 사람만.
 */
public final class Shortcuts {
	public static final String NAME = "MY_PET";
	static final String MARKER = "shortcuts-created";
	/** 시작 메뉴 프로그램 폴더 (PowerShell 식). */
	static final String PROGRAMS = "[Environment]::GetFolderPath('Programs')";
	/** 바탕 화면 (OneDrive 로 옮긴 곳 포함, PowerShell 식). */
	static final String DESKTOP = "[Environment]::GetFolderPath('Desktop')";
	/** 바로가기가 이미 있을 때 스크립트가 끝나는 코드. */
	static final int EXIT_EXISTS = 3;

	/** 바탕 화면 바로가기 만들기 결과. */
	public enum Result { CREATED, EXISTS, NOT_INSTALLED, FAILED }

	private Shortcuts() {
	}

	/** 설치된 실행 파일 (윈도우에서 설치본이 있을 때만, 아니면 null). */
	public static Path installedExe(Path home) {
		Path exe = home.resolve("MyPet.exe");
		return Constants.IS_WINDOWS && Files.isRegularFile(exe) ? exe : null;
	}

	/** 아직 안 만들었고 설치된 실행 파일이 있으면 시작 메뉴 바로가기를 뒤에서 만든다 (시작을 늦추지 않게). */
	public static void createOnce(Path home) {
		Path exe = installedExe(home);
		Path marker = home.resolve(MARKER);
		if (exe == null || Files.exists(marker)) {
			return;
		}
		Thread thread = new Thread(() -> {
			if (run(script(exe, home, List.of(PROGRAMS), false)) == 0) {
				try {
					Files.writeString(marker, "시작 메뉴 바로가기를 만들었어요. 지워도 다시 만들지 않아요.\n", StandardCharsets.UTF_8);
				} catch (java.io.IOException e) {
					System.err.println("[MY_PET] 바로가기 표시 파일 저장 실패: " + e);
				}
			}
		}, "mypet-shortcuts");
		thread.setDaemon(true);
		thread.start();
	}

	/** 바탕 화면에 바로가기를 만든다 (설정의 [바로가기 만들기]). 이미 있으면 그대로 둔다. 몇 초 걸릴 수 있다. */
	public static Result createDesktop(Path home) {
		Path exe = installedExe(home);
		if (exe == null) {
			return Result.NOT_INSTALLED;
		}
		int code = run(script(exe, home, List.of(DESKTOP), true));
		return code == 0 ? Result.CREATED : (code == EXIT_EXISTS ? Result.EXISTS : Result.FAILED);
	}

	/** PowerShell 스크립트를 실행하고 끝난 코드를 돌려준다 (실행 못 하면 -1). */
	static int run(String script) {
		try {
			// 한글 경로(OneDrive 바탕 화면 등)도 깨지지 않게 스크립트를 UTF-16 base64 로 넘긴다
			String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
			Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-EncodedCommand", encoded).redirectErrorStream(true).start();
			process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
			if (!process.waitFor(30, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return -1;
			}
			return process.exitValue();
		} catch (java.io.IOException | RuntimeException e) {
			System.err.println("[MY_PET] 바로가기를 만들지 못했어요: " + e);
			return -1;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		}
	}

	/**
	 * folders(PowerShell 식) 마다 WScript.Shell 로 "MY_PET.lnk" 를 만드는 스크립트.
	 * keepExisting 이면 이미 있는 바로가기는 건드리지 않고 {@link #EXIT_EXISTS} 로 끝난다.
	 */
	static String script(Path exe, Path home, List<String> folders, boolean keepExisting) {
		return "$ErrorActionPreference = 'Stop'\n"
			+ "$shell = New-Object -ComObject WScript.Shell\n"
			+ "foreach ($dir in @(" + String.join(", ", folders) + ")) {\n"
			+ "  if (-not $dir) { continue }\n"
			+ "  $path = Join-Path $dir " + quote(NAME + ".lnk") + "\n"
			+ (keepExisting ? "  if (Test-Path -LiteralPath $path) { exit " + EXIT_EXISTS + " }\n" : "")
			+ "  $link = $shell.CreateShortcut($path)\n"
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
