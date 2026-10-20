package com.mypet.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 버전 문자열 파싱/비교. 런처에서도 쓰이므로 JDK 클래스만 사용한다. */
public final class Versions {
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	private Versions() {
	}

	/** 버전 문자열을 비교 가능한 [major, minor, patch] 로 변환. */
	public static int[] parse(String versionText) {
		int[] numbers = new int[3];
		if (versionText == null) {
			return numbers;
		}
		Matcher matcher = NUMBER.matcher(versionText);
		int index = 0;
		while (index < 3 && matcher.find()) {
			try {
				numbers[index] = Integer.parseInt(matcher.group());
			} catch (NumberFormatException e) {
				numbers[index] = Integer.MAX_VALUE;
			}
			index++;
		}
		return numbers;
	}

	public static int compare(String a, String b) {
		int[] pa = parse(a);
		int[] pb = parse(b);
		for (int i = 0; i < 3; i++) {
			int c = Integer.compare(pa[i], pb[i]);
			if (c != 0) {
				return c;
			}
		}
		return 0;
	}

	/** 원격 버전이 로컬보다 높은지 판단. */
	public static boolean isNewer(String remoteVersion, String localVersion) {
		return compare(remoteVersion, localVersion) > 0;
	}

	/** 태그(v1.2.3)나 BOM/공백이 섞인 문자열을 1.2.3 형태로 정규화. */
	public static String normalize(String versionText) {
		if (versionText == null) {
			return null;
		}
		String text = versionText.replace("﻿", "").strip();
		if (text.startsWith("v") || text.startsWith("V")) {
			text = text.substring(1);
		}
		return text.isEmpty() ? null : text;
	}

	/** 개발용 실행(버전 정보 없음)인지 여부. */
	public static boolean isDevVersion(String version) {
		return version == null || version.contains("dev");
	}

	/** jar 매니페스트의 Implementation-Version 을 읽는다. 없거나 실패하면 null. */
	public static String readJarVersion(Path jarPath) {
		if (jarPath == null || !Files.isRegularFile(jarPath)) {
			return null;
		}
		try (JarFile jar = new JarFile(jarPath.toFile())) {
			Manifest manifest = jar.getManifest();
			if (manifest == null) {
				return null;
			}
			return normalize(manifest.getMainAttributes().getValue("Implementation-Version"));
		} catch (IOException e) {
			return null;
		}
	}
}
