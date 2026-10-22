package com.mypet;

import com.mypet.ui.Ui;

import javax.swing.SwingUtilities;
import java.util.Arrays;

/** 앱 본체 진입점 ({@link Launcher} 가 설치본 또는 업데이트 jar 에서 호출). */
public final class MyPet {
	/** 업데이트 후 재시작될 때 붙는 인자. 이전 프로세스가 끝날 때까지 단일 실행 잠금을 기다린다. */
	public static final String AFTER_UPDATE_ARG = "--after-update";
	/** MY_PET 바로가기와 설치 프로그램(MyPet-Setup.exe)이 켤 때 붙이는 표시. 없으면 예전 MyPet.exe 로 켠 것. */
	public static final String SHORTCUT_ARG = "--shortcut";

	private MyPet() {
	}

	public static void main(String[] args) {
		System.setProperty("apple.awt.UIElement", "true");
		boolean afterUpdate = Arrays.asList(args).contains(AFTER_UPDATE_ARG);
		if (!SingleInstance.acquire(afterUpdate ? 20_000 : 0)) {
			return;
		}
		Ui.installTheme();
		SwingUtilities.invokeLater(() -> {
			new PetApp().start();
			// 예전에 받은 MyPet.exe 로 켰으면 "설치 파일을 다시 받아 주세요"
			LegacyLauncherNotice.showIfNeeded(args);
		});
		// 윈도우: 처음 한 번 시작 메뉴에 바로가기 (바탕 화면은 설치 창에서 고른 사람만)
		Shortcuts.createOnce(Constants.appDataDir());
	}
}
