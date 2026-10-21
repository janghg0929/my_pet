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
 * 부위 사진 파일 (앱 데이터 폴더의 parts 폴더). 사용자가 고른 사진은 PNG 로 복사해 둔다
 * (긴 쪽이 2048px 를 넘는 아주 큰 사진만 줄인다: 화질은 그대로, 앱이 무거워지지 않게).
 * 도트 그림판으로 그린 그림은 "px_" 로 시작하는 이름으로 저장하고, 픽셀이 뭉개지지 않게 그린다.
 * 그림에서 알파가 {@link #BODY_ALPHA} 인 칸은 "몸 색" 칸이라 지금 몸 색으로 바꿔 그린다.
 */
public final class PartImages {
	public static final int MAX_SIDE = 2048;
	/** 도트 그림의 "몸 색" 칸 표시 (이 알파값인 픽셀은 지금 몸 색으로 칠한다). painter.js 와 같다. */
	public static final int BODY_ALPHA = 254;
	private static final String PIXEL_PREFIX = "px_";

	private final Path dir;
	private final Map<String, BufferedImage> cache = new HashMap<>();
	/** 몸 색을 칠한 도트 그림 ("이름|색" → 그림). */
	private final Map<String, BufferedImage> tintCache = new HashMap<>();

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

	/** 도트 그림판으로 그린 그림을 그대로 (줄이지 않고) 저장. 저장된 파일 이름 ("px_…png"). */
	public String importPixelArt(BufferedImage drawn) throws IOException {
		if (drawn == null || drawn.getWidth() > MAX_SIDE || drawn.getHeight() > MAX_SIDE) {
			throw new IOException("그림 크기가 맞지 않아요");
		}
		BufferedImage copy = new BufferedImage(drawn.getWidth(), drawn.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < drawn.getHeight(); y++) {
			for (int x = 0; x < drawn.getWidth(); x++) {
				copy.setRGB(x, y, drawn.getRGB(x, y));
			}
		}
		Files.createDirectories(dir);
		String name = PIXEL_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 24) + ".png";
		ImageIO.write(copy, "png", dir.resolve(name).toFile());
		synchronized (this) {
			cache.put(name, copy);
		}
		return name;
	}

	/** 도트 그림판으로 그린 그림인지 (픽셀이 뭉개지지 않게 그린다). */
	public static boolean isPixelArt(String name) {
		return name != null && name.startsWith(PIXEL_PREFIX);
	}

	/** 그릴 그림: 도트 그림이면 "몸 색" 칸을 color 로 칠한 그림, 아니면 그대로. */
	public synchronized BufferedImage tinted(String name, String color) {
		BufferedImage image = get(name);
		if (image == null || !isPixelArt(name) || color == null) {
			return image;
		}
		String key = name + "|" + color;
		BufferedImage done = tintCache.get(key);
		if (done != null) {
			return done;
		}
		int rgb;
		try {
			rgb = Integer.parseInt(color.replace("#", ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return image;
		}
		BufferedImage result = null;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				if ((argb >>> 24) == BODY_ALPHA) {
					if (result == null) {
						result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
						for (int yy = 0; yy < image.getHeight(); yy++) {
							for (int xx = 0; xx < image.getWidth(); xx++) {
								result.setRGB(xx, yy, image.getRGB(xx, yy));
							}
						}
					}
					result.setRGB(x, y, 0xFF000000 | rgb);
				}
			}
		}
		done = result == null ? image : result;
		if (tintCache.size() > 200) {
			tintCache.clear();
		}
		tintCache.put(key, done);
		return done;
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

	/**
	 * 도트 그림의 픽셀 그대로 ("가로,세로,RGBA base64"). 설정 화면의 웹 엔진은 그림을 캔버스로 읽으면
	 * 이웃 칸 색이 조금씩 섞여서, 도트 그림은 이걸로 칸 색을 받는다. 도트 그림이 아니거나 못 읽으면 null.
	 */
	public String pixelData(String name) {
		BufferedImage image = isPixelArt(name) ? get(name) : null;
		if (image == null) {
			return null;
		}
		int w = image.getWidth();
		int h = image.getHeight();
		byte[] rgba = new byte[w * h * 4];
		int i = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = image.getRGB(x, y);
				rgba[i++] = (byte) (argb >> 16);
				rgba[i++] = (byte) (argb >> 8);
				rgba[i++] = (byte) argb;
				rgba[i++] = (byte) (argb >>> 24);
			}
		}
		return w + "," + h + "," + Base64.getEncoder().encodeToString(rgba);
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
						tintCache.clear();
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
