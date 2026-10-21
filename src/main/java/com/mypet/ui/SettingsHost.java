package com.mypet.ui;

import javax.swing.JDialog;

/** HTML 설정 화면이 앱에 요청하는 기능. 모든 메서드는 Swing 이벤트 스레드(EDT)에서 호출된다. */
public interface SettingsHost {
	/** 화면에 보낼 현재 상태 (JSON 객체 문자열). */
	String settingsStateJson();

	/** 외형 모드 변경 (key: browserMonocle 등, mode: auto/off/always). */
	void setAppearanceMode(String key, String mode);

	/** 기본 색으로 되돌리기. */
	void applyDefaultColor();

	/** 색 선택 창 열기. */
	void pickCustomColor();

	/** 모습 꾸미기: 부위 배치 적용 (PartLayout JSON 문자열). */
	void setParts(String layoutJson);

	/** 모습 꾸미기: 부위 사진 고르기 (눈이면 expression = neutral/grab/fall). */
	void pickPartImage(String part, String expression);

	/** 고급 시간 막대: 사진 파일을 골라 parts 폴더로 가져오고 파일 이름을 돌려준다 (취소하면 null). */
	String importPartImage();

	/** 도트 그림판 그림(PNG data URL)을 parts 폴더에 저장하고 파일 이름을 돌려준다 (실패하면 null). */
	String saveDrawing(String dataUrl);

	/** 글자 입력 창 (Swing: 한글이 바로 입력된다). 취소하면 null. */
	String askText(String title, String initial);

	/** 모습 꾸미기: 부위 사진 빼기. */
	void removePartImage(String part, String expression);

	/** 모습 꾸미기: 기본 비트로 되돌리기. */
	void resetParts();

	/** 부위 사진을 설정 화면에 보낼 data URL (없으면 null). */
	String partImageDataUrl(String fileName);

	/** 도트 그림의 픽셀 그대로 ("가로,세로,RGBA base64"), 도트 그림이 아니면 null. */
	default String partImagePixels(String fileName) {
		return null;
	}

	/** 저장한 모습: 지금 모습(배치·사진·몸 색)을 이름을 물어서 저장. */
	void saveLook();

	/** 저장한 모습: 불러와서 지금 모습으로. */
	void applyLook(String id);

	/** 저장한 모습: 이름 바꾸기 (이름을 묻는다). */
	void renameLook(String id);

	/** 저장한 모습: 지우기. */
	void deleteLook(String id);

	/** 설정 창 닫기. */
	void closeSettings();

	/** 앱 종료. */
	void quit();

	/** 창 크기가 바뀌었을 때 캐릭터 근처로 다시 배치. */
	void placeSettingsDialog(JDialog dialog);

	/** 설정 창 하나 (HTML 화면 또는 대체 화면). */
	interface View {
		JDialog dialog();

		/** 앱 상태가 바뀌었으니 화면을 다시 그린다 (EDT 에서 호출). */
		void refresh();
	}

	/**
	 * JavaFX(WebView)가 있으면 HTML 설정 창을 만든다. 없으면 null.
	 * WebSettings 는 JavaFX 클래스를 쓰므로 JavaFX 가 없는 런타임에서 로드되지 않게 리플렉션으로만 만든다.
	 */
	static View createWebView(java.awt.Window owner, SettingsHost host) {
		if (Boolean.getBoolean("mypet.swingSettings")) {
			return null;
		}
		try {
			ClassLoader loader = SettingsHost.class.getClassLoader();
			Class.forName("javafx.embed.swing.JFXPanel", false, loader);
			Class.forName("javafx.scene.web.WebView", false, loader);
			Class<?> type = Class.forName("com.mypet.ui.WebSettings", true, loader);
			return (View) type.getConstructor(java.awt.Window.class, SettingsHost.class).newInstance(owner, host);
		} catch (Throwable t) {
			return null;
		}
	}
}
