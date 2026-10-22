package com.mypet;

import com.mypet.ui.Ui;

import javax.swing.SwingUtilities;
import java.util.Arrays;

/** 앱 본체 진입점 ({@link Launcher} 가 설치본 또는 업데이트 jar 에서 호출). */
public final class MyPet {
	/** 업데이트 후 재시작될 때 붙는 인자. 이전 프로세스가 끝날 때까지 단일 실행 잠금을 기다린다. */
	public static final String AFTER_UPDATE_ARG = "--after-update";

	private MyPet() {
	}

	public static void main(String[] args) {
		System.setProperty("apple.awt.UIElement", "true");
		boolean afterUpdate = Arrays.asList(args).contains(AFTER_UPDATE_ARG);
		if (!SingleInstance.acquire(afterUpdate ? 20_000 : 0)) {
			return;
		}
		Ui.installTheme();
		SwingUtilities.invokeLater(() -> new PetApp().start());
		// 윈도우: 처음 한 번 바탕 화면·시작 메뉴에 바로가기 (exe 를 찾아 실행하지 않아도 되게)
		Shortcuts.createOnce(Constants.appDataDir());
	}
}
