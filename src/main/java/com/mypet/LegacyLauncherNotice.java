package com.mypet;

import com.mypet.ui.Ui;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 예전 MyPet.exe(2.1.2 까지: 받은 파일이 곧 실행기)로 켰을 때 "설치 파일을 다시 받아 주세요" 공지.
 * 새 설치 프로그램(MyPet-Setup.exe)과 MY_PET 바로가기는 {@link MyPet#SHORTCUT_ARG} 를 붙여서 켜고,
 * 예전 실행기는 아무것도 붙이지 않고 설치된 %LOCALAPPDATA%\MyPet\MyPet.exe 를 켠다.
 */
public final class LegacyLauncherNotice {
	static final String DOWNLOAD_URL = "https://github.com/janghg0929/my_pet/releases/latest";

	private LegacyLauncherNotice() {
	}

	/**
	 * 공지를 띄울지: 윈도우 설치본(설치 폴더의 MyPet.exe)이 표시 없이 켜졌을 때.
	 * 개발 중 실행(java.exe)이나 업데이트 뒤 다시 켜질 때는 띄우지 않는다.
	 */
	static boolean shouldShow(String[] args, Optional<String> command, Path home, boolean windows) {
		if (!windows || command.isEmpty()) {
			return false;
		}
		if (Arrays.asList(args).contains(MyPet.SHORTCUT_ARG) || Arrays.asList(args).contains(MyPet.AFTER_UPDATE_ARG)) {
			return false;
		}
		String running = normalize(command.get());
		String installed = normalize(home.resolve("MyPet.exe").toString());
		return running.equals(installed);
	}

	private static String normalize(String path) {
		try {
			return Path.of(path).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
		} catch (RuntimeException e) {
			return path.toLowerCase(Locale.ROOT);
		}
	}

	/** 필요하면 공지를 띄운다 (Swing 스레드에서 부를 것). 비트는 그대로 돌아다닌다 (모달 아님). */
	public static void showIfNeeded(String[] args) {
		if (!shouldShow(args, ProcessHandle.current().info().command(), Constants.appDataDir(), Constants.IS_WINDOWS)) {
			return;
		}
		String download = "설치 파일 받으러 가기";
		String later = "나중에";
		JOptionPane pane = new JOptionPane(
			"MY_PET 설치 방식이 바뀌었어요!\n\n"
				+ "이제는 설치 파일(MyPet-Setup.exe)로 한 번 설치하고,\n"
				+ "바탕 화면이나 시작 메뉴의 MY_PET 바로가기로 켜요.\n\n"
				+ "예전에 받아 둔 MyPet.exe 는 지우고, 새 설치 파일을 받아서 실행해 주세요.\n"
				+ "(설정과 꾸민 모습은 그대로 남아요)",
			JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[] {download, later}, download);
		JDialog dialog = pane.createDialog(null, "MY_PET 안내");
		dialog.setIconImages(Ui.appIcons());
		dialog.setModal(false);
		dialog.setAlwaysOnTop(true);
		pane.addPropertyChangeListener(JOptionPane.VALUE_PROPERTY, event -> {
			if (download.equals(event.getNewValue())) {
				try {
					java.awt.Desktop.getDesktop().browse(java.net.URI.create(DOWNLOAD_URL));
				} catch (java.io.IOException | RuntimeException e) {
					System.err.println("[MY_PET] 다운로드 페이지를 열지 못했어요: " + e);
				}
			}
			dialog.dispose();
		});
		dialog.setVisible(true);
	}
}
