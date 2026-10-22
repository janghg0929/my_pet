package com.mypet;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 윈도우 바로가기 "MY_PET" (설치된 %LOCALAPPDATA%\MyPet\MyPet.exe --shortcut 을 실행).
 * - 시작 메뉴: 자동 업데이트(jar)로 이 버전이 된 사람도 켤 수 있게, 처음 실행 때 한 번만 만든다 (표시 파일).
 *   그때 바탕 화면에 이미 있는 MY_PET 바로가기도 아이콘·표시를 고쳐 쓴다.
 * - 바탕 화면: 설치 프로그램(MyPetSetup.cs)에서 고르거나, 설정의 [바로가기] 를 누른 사람만.
 * 바로가기 아이콘은 설치 폴더의 MyPet-icon.ico 를 쓴다: exe 를 가리키면 윈도우가 예전(자바) 아이콘을
 * 같은 경로로 기억해 두고 계속 보여 줘서, 새 경로의 아이콘 파일을 따로 둔다.
 */
public final class Shortcuts {
	public static final String NAME = "MY_PET";
	/** 시작 메뉴 바로가기를 만든 표시 (예전 바로가기의 아이콘·표시를 한 번 고쳐 쓰게 이름을 바꿔 왔다). */
	static final String MARKER = "shortcut-icons";
	/** 바로가기 아이콘 파일 (설치 폴더, MyPetSetup.cs 의 IconFile 과 같다). */
	static final String ICON_FILE = "MyPet-icon.ico";
	static final String ICON_RESOURCE = "/com/mypet/icons/MyPet.ico";
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

	/**
	 * 아직 안 했고 설치된 실행 파일이 있으면 뒤에서 (시작을 늦추지 않게):
	 * 시작 메뉴 바로가기를 만들고, 바탕 화면에 이미 있는 MY_PET 바로가기는 아이콘·표시를 고쳐 쓴다.
	 */
	public static void createOnce(Path home) {
		Path exe = installedExe(home);
		Path marker = home.resolve(MARKER);
		if (exe == null || Files.exists(marker)) {
			return;
		}
		Thread thread = new Thread(() -> {
			Path icon = writeIcon(home);
			if (run(script(exe, home, icon, List.of(PROGRAMS), false) + repairScript(exe, home, icon, DESKTOP)) == 0) {
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

	/** 바탕 화면에 바로가기를 만든다 (설정의 [바로가기]). 이미 있으면 그대로 둔다. 몇 초 걸릴 수 있다. */
	public static Result createDesktop(Path home) {
		Path exe = installedExe(home);
		if (exe == null) {
			return Result.NOT_INSTALLED;
		}
		int code = run(script(exe, home, writeIcon(home), List.of(DESKTOP), true));
		return code == 0 ? Result.CREATED : (code == EXIT_EXISTS ? Result.EXISTS : Result.FAILED);
	}

	/** 앱에 든 아이콘을 설치 폴더에 꺼내 둔다 (못 하면 null: 그때는 exe 아이콘). */
	static Path writeIcon(Path home) {
		Path icon = home.resolve(ICON_FILE);
		try (InputStream in = Shortcuts.class.getResourceAsStream(ICON_RESOURCE)) {
			if (in == null) {
				return null;
			}
			Files.copy(in, icon, StandardCopyOption.REPLACE_EXISTING);
			return icon;
		} catch (java.io.IOException | RuntimeException e) {
			return Files.isRegularFile(icon) ? icon : null;
		}
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
	 * keepExisting 이면 이미 있는 (--shortcut 이 붙은) 바로가기는 건드리지 않고 {@link #EXIT_EXISTS} 로 끝난다.
	 * --shortcut 이 없는 예전 바로가기는 고쳐 쓴다.
	 */
	static String script(Path exe, Path home, Path icon, List<String> folders, boolean keepExisting) {
		return "$ErrorActionPreference = 'Stop'\n"
			+ "$shell = New-Object -ComObject WScript.Shell\n"
			+ "foreach ($dir in @(" + String.join(", ", folders) + ")) {\n"
			+ "  if (-not $dir) { continue }\n"
			+ "  $path = Join-Path $dir " + quote(NAME + ".lnk") + "\n"
			+ (keepExisting ? "  if ((Test-Path -LiteralPath $path) -and ($shell.CreateShortcut($path).Arguments -eq "
				+ quote(MyPet.SHORTCUT_ARG) + ")) { exit " + EXIT_EXISTS + " }\n" : "")
			+ "  $link = $shell.CreateShortcut($path)\n"
			+ fill("$link", exe, home, icon)
			+ "  $link.Save()\n"
			+ "}\n";
	}

	/** folder 에 이미 있는 MY_PET 바로가기가 이 설치본을 가리키면 아이콘·표시를 고쳐 쓴다 (없으면 그대로). */
	static String repairScript(Path exe, Path home, Path icon, String folder) {
		return "$dir = " + folder + "\n"
			+ "if ($dir) {\n"
			+ "  $path = Join-Path $dir " + quote(NAME + ".lnk") + "\n"
			+ "  if (Test-Path -LiteralPath $path) {\n"
			+ "    $link = (New-Object -ComObject WScript.Shell).CreateShortcut($path)\n"
			+ "    if ($link.TargetPath -eq " + quote(exe.toString()) + ") {\n"
			+ fill("$link", exe, home, icon)
			+ "      $link.Save()\n"
			+ "    }\n"
			+ "  }\n"
			+ "}\n";
	}

	private static String fill(String link, Path exe, Path home, Path icon) {
		return "  " + link + ".TargetPath = " + quote(exe.toString()) + "\n"
			+ "  " + link + ".Arguments = " + quote(MyPet.SHORTCUT_ARG) + "\n"
			+ "  " + link + ".WorkingDirectory = " + quote(home.toString()) + "\n"
			+ "  " + link + ".IconLocation = " + quote((icon != null ? icon : exe) + ",0") + "\n"
			+ "  " + link + ".Description = " + quote("데스크톱 펫 비트") + "\n";
	}

	/** PowerShell 작은따옴표 문자열 (안의 ' 는 '' 로). */
	static String quote(String text) {
		return "'" + text.replace("'", "''") + "'";
	}
}
