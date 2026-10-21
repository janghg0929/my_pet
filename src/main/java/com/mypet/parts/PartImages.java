package com.mypet.parts;

import com.mypet.Constants;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 부위 사진 파일 (앱 데이터 폴더의 parts 폴더). 사용자가 고른 사진은 최대 256px PNG 로 줄여서 복사해 둔다.
 */
public final class PartImages {
	public static final int MAX_SIDE = 256;

	private final Path dir;
	private final Map<String, BufferedImage> cache = new HashMap<>();

	public PartImages(Path dir) {
		this.dir = dir;
	}

	/** 앱 기본 위치: %LOCALAPPDATA%\MyPet\parts (맥 ~/.my_pet/parts) */
	public static PartImages standard() {
		return new PartImages(Constants.appDataDir().resolve("parts"));
	}

	public Path dir() {
		return dir;
	}

	/** 사진을 읽어서 줄이고 PNG 로 저장. 저장된 파일 이름을 돌려준다. 읽을 수 없는 파일이면 IOException. */
	public String importImage(File source) throws IOException {
		BufferedImage original = ImageIO.read(source);
		if (original == null) {
			throw new IOException("읽을 수 없는 그림 파일이에요 (PNG, JPG, GIF, BMP 를 쓸 수 있어요)");
		}
		BufferedImage scaled = scaleDown(original);
		Files.createDirectories(dir);
		String name = UUID.randomUUID().toString().replace("-", "") + ".png";
		ImageIO.write(scaled, "png", dir.resolve(name).toFile());
		cache.put(name, scaled);
		return name;
	}

	/** 긴 쪽이 MAX_SIDE 를 넘으면 비율을 지켜 줄인다 (투명도 유지). */
	static BufferedImage scaleDown(BufferedImage original) {
		int w = original.getWidth();
		int h = original.getHeight();
		double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(w, h));
		int nw = Math.max(1, (int) Math.round(w * scale));
		int nh = Math.max(1, (int) Math.round(h * scale));
		BufferedImage result = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = result.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(original, 0, 0, nw, nh, null);
		g.dispose();
		return result;
	}

	/** 저장된 사진 (없거나 못 읽으면 null). */
	public synchronized BufferedImage get(String name) {
		if (name == null || PartLayout.safeFileName(name) == null) {
			return null;
		}
		if (cache.containsKey(name)) {
			return cache.get(name);
		}
		BufferedImage image = null;
		try {
			Path file = dir.resolve(name);
			if (Files.isRegularFile(file)) {
				image = ImageIO.read(file.toFile());
			}
		} catch (IOException | RuntimeException e) {
			image = null;
		}
		cache.put(name, image);
		return image;
	}

	/** 설정 화면(HTML)에 보낼 data URL. */
	public String dataUrl(String name) {
		if (name == null || PartLayout.safeFileName(name) == null) {
			return null;
		}
		try {
			byte[] bytes = Files.readAllBytes(dir.resolve(name));
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** 쓰지 않는 사진 파일을 지운다. */
	public synchronized void deleteUnused(Set<String> keep) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
			for (Path file : stream) {
				String name = file.getFileName().toString();
				if (!keep.contains(name)) {
					try {
						Files.deleteIfExists(file);
						cache.remove(name);
					} catch (IOException ignored) {
						// 다음에 다시
					}
				}
			}
		} catch (IOException ignored) {
			// 무시
		}
	}
}
