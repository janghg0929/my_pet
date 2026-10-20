package com.mypet.update;

import com.mypet.Constants;
import com.mypet.Launcher;
import com.mypet.MyPet;
import com.mypet.SingleInstance;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * GitHub Releases 기반 자동 업데이트 (my_pet_update_service.py 대체).
 *
 * 1. https://github.com/{저장소}/releases/latest 의 리다이렉트로 최신 태그(v1.2.3)를 알아낸다 (API 호출 제한 없음)
 * 2. 현재 버전보다 높으면 "새 버전이 있습니다. 업데이트할까요?" 를 묻는다
 * 3. 릴리스의 MyPet.jar 를 앱 데이터 폴더/app/MyPet-1.2.3.jar 로 내려받고 검증한다
 * 4. 앱을 다시 실행하면 {@link Launcher} 가 새 jar 로 실행한다
 */
public final class UpdateService {
	private static final String USER_AGENT = "MY_PET-updater";

	private UpdateService() {
	}

	/** 현재 실행 중인 앱 버전 (개발 실행이면 0.0.0-dev). */
	public static String currentVersion() {
		String version = Versions.normalize(MyPet.class.getPackage().getImplementationVersion());
		return version == null ? "0.0.0-dev" : version;
	}

	/** 패키징된 앱(jpackage)으로 실행 중일 때만 업데이트를 확인한다. 파이썬 버전의 exe 체크와 같은 역할. */
	private static boolean isUpdatableBuild() {
		if (Boolean.getBoolean("mypet.update.force")) {
			return true;
		}
		return System.getProperty("jpackage.app-path") != null && !Versions.isDevVersion(currentVersion());
	}

	/** 릴리스 주소 (테스트용으로 -Dmypet.update.baseUrl 로 바꿀 수 있음). */
	private static String repositoryUrl() {
		String base = System.getProperty("mypet.update.baseUrl", "https://github.com/" + Constants.GITHUB_REPOSITORY);
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	private static HttpClient client(HttpClient.Redirect redirect) {
		return HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(Constants.UPDATE_CHECK_TIMEOUT_SEC))
			.followRedirects(redirect)
			.build();
	}

	/** 최신 릴리스 태그를 알아낸다. 실패하면 null. */
	static String fetchLatestTag() {
		try {
			HttpRequest request = HttpRequest.newBuilder(new URI(repositoryUrl() + "/releases/latest"))
				.timeout(Duration.ofSeconds(Constants.UPDATE_CHECK_TIMEOUT_SEC))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
			HttpResponse<Void> response = client(HttpClient.Redirect.NEVER).send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() / 100 != 3) {
				return null;
			}
			Optional<String> location = response.headers().firstValue("Location");
			if (location.isEmpty()) {
				return null;
			}
			return extractTag(location.get());
		} catch (IOException | URISyntaxException | IllegalArgumentException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	/** .../releases/tag/v1.2.3 형태의 주소에서 태그를 꺼낸다. */
	static String extractTag(String location) {
		int index = location.lastIndexOf("/tag/");
		if (index < 0) {
			return null;
		}
		String tag = location.substring(index + "/tag/".length());
		int end = tag.indexOf('?');
		if (end >= 0) {
			tag = tag.substring(0, end);
		}
		tag = URLDecoder.decode(tag, StandardCharsets.UTF_8).strip();
		return tag.isEmpty() ? null : tag;
	}

	private static URI assetUri(String tag) throws URISyntaxException {
		return new URI(repositoryUrl() + "/releases/download/" + tag + "/" + Constants.UPDATE_JAR_ASSET_NAME);
	}

	/** 릴리스에 업데이트 jar 가 실제로 올라와 있는지 (빌드가 끝나기 전이면 묻지 않기 위함). */
	static boolean isAssetAvailable(String tag) {
		try {
			HttpRequest request = HttpRequest.newBuilder(assetUri(tag))
				.timeout(Duration.ofSeconds(Constants.UPDATE_CHECK_TIMEOUT_SEC))
				.header("User-Agent", USER_AGENT)
				.header("Range", "bytes=0-0")
				.GET()
				.build();
			HttpResponse<InputStream> response = client(HttpClient.Redirect.NORMAL).send(request, HttpResponse.BodyHandlers.ofInputStream());
			response.body().close();
			return response.statusCode() == 200 || response.statusCode() == 206;
		} catch (IOException | URISyntaxException | IllegalArgumentException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/** 업데이트 jar 를 내려받아 검증 후 app 폴더에 설치. 성공하면 설치된 경로, 실패하면 null. */
	static Path downloadAndInstall(String tag, String expectedVersion) {
		Path appDir = Constants.appDataDir().resolve("app");
		Path target = appDir.resolve("MyPet-" + expectedVersion + ".jar");
		Path part = appDir.resolve("MyPet-" + expectedVersion + ".jar.part");
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				Files.createDirectories(appDir);
				HttpRequest request = HttpRequest.newBuilder(assetUri(tag))
					.timeout(Duration.ofSeconds(120))
					.header("User-Agent", USER_AGENT)
					.GET()
					.build();
				HttpResponse<Path> response = client(HttpClient.Redirect.NORMAL).send(request, HttpResponse.BodyHandlers.ofFile(part));
				if (response.statusCode() != 200 || !isValidUpdateJar(part, expectedVersion)) {
					Files.deleteIfExists(part);
					continue;
				}
				try {
					Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException atomicFailed) {
					Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
				}
				return target;
			} catch (IOException | URISyntaxException | IllegalArgumentException e) {
				try {
					Files.deleteIfExists(part);
				} catch (IOException ignored) {
					// 무시
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	/** 내려받은 파일이 올바른 MY_PET jar 인지 (파이썬 버전의 MZ 헤더 검사에 해당). */
	static boolean isValidUpdateJar(Path jarPath, String expectedVersion) {
		String version = Versions.readJarVersion(jarPath);
		if (version == null || Versions.compare(version, expectedVersion) != 0) {
			return false;
		}
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			return jar.getEntry("com/mypet/MyPet.class") != null && jar.getEntry("com/mypet/Launcher.class") != null;
		} catch (IOException e) {
			return false;
		}
	}

	/** 시작 시 백그라운드에서 업데이트를 확인하고, 새 버전이 있으면 사용자에게 묻는다. */
	public static void checkForUpdatesAsync(Window parent, Runnable exitApplication) {
		if (!isUpdatableBuild()) {
			return;
		}
		Thread thread = new Thread(() -> {
			String installedVersion = currentVersion();
			String tag = fetchLatestTag();
			if (tag == null) {
				return;
			}
			String remoteVersion = Versions.normalize(tag);
			if (remoteVersion == null || !Versions.isNewer(remoteVersion, installedVersion)) {
				return;
			}
			if (!isAssetAvailable(tag)) {
				return;
			}
			SwingUtilities.invokeLater(() -> promptAndUpdate(parent, tag, remoteVersion, exitApplication));
		}, "mypet-update-check");
		thread.setDaemon(true);
		thread.start();
	}

	private static void promptAndUpdate(Window parent, String tag, String remoteVersion, Runnable exitApplication) {
		if (!askYesNo(parent, "업데이트", "새 버전(" + remoteVersion + ")이 있습니다. 업데이트할까요?")) {
			return;
		}

		JDialog progress = createProgressDialog(parent);
		SwingWorker<Path, Void> worker = new SwingWorker<>() {
			@Override
			protected Path doInBackground() {
				return downloadAndInstall(tag, remoteVersion);
			}

			@Override
			protected void done() {
				progress.dispose();
				Path installed = null;
				try {
					installed = get();
				} catch (Exception ignored) {
					// 실패 처리
				}
				if (installed == null) {
					showMessage(parent, "업데이트 실패", "업데이트 파일을 다운로드하지 못했습니다.", JOptionPane.ERROR_MESSAGE);
					return;
				}
				showMessage(parent, "업데이트", "업데이트를 적용하기 위해 MY_PET을 다시 시작합니다.", JOptionPane.INFORMATION_MESSAGE);
				if (!restartApplication()) {
					showMessage(parent, "업데이트", "업데이트가 설치되었습니다. MY_PET을 다시 실행해 주세요.", JOptionPane.INFORMATION_MESSAGE);
				}
				exitApplication.run();
			}
		};
		worker.execute();
		progress.setVisible(true);
	}

	/** 앱을 새 프로세스로 다시 실행. 새 프로세스는 이전 프로세스가 끝날 때까지 단일 실행 잠금을 기다린다. */
	static boolean restartApplication() {
		List<String> command = new ArrayList<>();
		String appPath = System.getProperty("jpackage.app-path");
		if (appPath != null && Files.isRegularFile(Path.of(appPath))) {
			command.add(appPath);
		} else {
			try {
				Path javaBin = Path.of(System.getProperty("java.home"), "bin", Constants.IS_WINDOWS ? "javaw.exe" : "java");
				Path launcherJar = Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
				command.add(javaBin.toString());
				command.add("-cp");
				command.add(launcherJar.toString());
				command.add(Launcher.class.getName());
			} catch (URISyntaxException | RuntimeException e) {
				return false;
			}
		}
		command.add(MyPet.AFTER_UPDATE_ARG);
		SingleInstance.release();
		try {
			new ProcessBuilder(command).start();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean askYesNo(Component parent, String title, String message) {
		JOptionPane pane = new JOptionPane(message, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);
		JDialog dialog = pane.createDialog(parent, title);
		dialog.setAlwaysOnTop(true);
		dialog.setVisible(true);
		dialog.dispose();
		return Integer.valueOf(JOptionPane.YES_OPTION).equals(pane.getValue());
	}

	private static void showMessage(Component parent, String title, String message, int type) {
		JOptionPane pane = new JOptionPane(message, type);
		JDialog dialog = pane.createDialog(parent, title);
		dialog.setAlwaysOnTop(true);
		dialog.setVisible(true);
		dialog.dispose();
	}

	private static JDialog createProgressDialog(Window parent) {
		JDialog dialog = new JDialog(parent, "업데이트", JDialog.ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.setAlwaysOnTop(true);
		JProgressBar bar = new JProgressBar();
		bar.setIndeterminate(true);
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
		panel.add(new JLabel("새 버전을 내려받는 중입니다..."), BorderLayout.NORTH);
		panel.add(bar, BorderLayout.CENTER);
		dialog.setContentPane(panel);
		dialog.pack();
		dialog.setSize(Math.max(dialog.getWidth(), 300), dialog.getHeight());
		dialog.setLocationRelativeTo(parent);
		return dialog;
	}
}
