package com.mypet;

import com.mypet.update.Versions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;

/**
 * 진입점. 자동 업데이트로 내려받은 최신 jar(앱 데이터 폴더/app/MyPet-x.y.z.jar)가
 * 설치본보다 새 버전이면 그 jar 의 코드로 실행하고, 아니면 설치본을 그대로 실행한다.
 *
 * 이 클래스는 JDK 와 {@link Versions} 만 사용해야 한다 (앱 코드를 미리 로드하면 업데이트 jar 가 적용되지 않음).
 */
public final class Launcher {
	static final String MAIN_CLASS = "com.mypet.MyPet";

	private Launcher() {
	}

	public static void main(String[] args) throws Exception {
		// macOS: 독(Dock) 아이콘/메뉴바 없이 실행 (AWT 초기화 전에 설정해야 함)
		System.setProperty("apple.awt.UIElement", "true");

		String bundledVersion = Launcher.class.getPackage().getImplementationVersion();
		Path updatedJar = findNewestUpdateJar(Constants.appDataDir().resolve("app"), bundledVersion);
		if (updatedJar != null) {
			try {
				runFromJar(updatedJar, args);
				return;
			} catch (ReflectiveOperationException | IOException | LinkageError e) {
				System.err.println("[MY_PET] 업데이트 jar 실행 실패, 설치본으로 실행: " + e);
			}
		}
		MyPet.main(args);
	}

	/** 설치본보다 새 버전인 업데이트 jar 중 가장 최신 것을 찾고, 쓸모없는 jar 는 정리한다. */
	static Path findNewestUpdateJar(Path appDir, String bundledVersion) {
		if (!Files.isDirectory(appDir)) {
			return null;
		}
		Path newest = null;
		String newestVersion = bundledVersion;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(appDir, "MyPet-*.jar")) {
			for (Path jar : stream) {
				String version = Versions.readJarVersion(jar);
				if (version != null && (newestVersion == null || Versions.isNewer(version, newestVersion))) {
					newest = jar;
					newestVersion = version;
				}
			}
		} catch (IOException e) {
			return null;
		}
		cleanupOldJars(appDir, newest);
		return newest;
	}

	/** 사용하지 않을 업데이트 jar 를 지운다 (실행 중이라 잠겨 있으면 다음 기회에). */
	private static void cleanupOldJars(Path appDir, Path keep) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(appDir, "MyPet-*.jar")) {
			for (Path jar : stream) {
				if (!jar.equals(keep)) {
					try {
						Files.deleteIfExists(jar);
					} catch (IOException ignored) {
						// 다음 실행 때 다시 시도
					}
				}
			}
		} catch (IOException ignored) {
			// 무시
		}
	}

	private static void runFromJar(Path jar, String[] args) throws ReflectiveOperationException, IOException {
		ClassLoader loader = new ChildFirstClassLoader(new URL[] {jar.toUri().toURL()}, Launcher.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(loader);
		Class<?> mainClass = Class.forName(MAIN_CLASS, true, loader);
		mainClass.getMethod("main", String[].class).invoke(null, (Object) args);
	}

	/** 업데이트 jar 의 클래스/리소스를 설치본보다 먼저 찾는 클래스로더. */
	static final class ChildFirstClassLoader extends URLClassLoader {
		static {
			registerAsParallelCapable();
		}

		ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				Class<?> loaded = findLoadedClass(name);
				if (loaded == null && !isPlatformClass(name)) {
					try {
						loaded = findClass(name);
					} catch (ClassNotFoundException ignored) {
						// 부모에서 찾는다
					}
				}
				if (loaded == null) {
					loaded = super.loadClass(name, false);
				}
				if (resolve) {
					resolveClass(loaded);
				}
				return loaded;
			}
		}

		private static boolean isPlatformClass(String name) {
			return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
				|| name.startsWith("sun.") || name.startsWith("com.sun.") && !name.startsWith("com.sun.jna.");
		}

		@Override
		public URL getResource(String name) {
			URL url = findResource(name);
			return url != null ? url : super.getResource(name);
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			Enumeration<URL> own = findResources(name);
			if (own.hasMoreElements()) {
				return own;
			}
			return super.getResources(name);
		}

		@Override
		public InputStream getResourceAsStream(String name) {
			URL url = getResource(name);
			try {
				return url == null ? null : url.openStream();
			} catch (IOException e) {
				return null;
			}
		}
	}
}
