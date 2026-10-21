// 앱 아이콘 만들기: 하얀 타일 위로 살짝 기울어져 떨어지는 비트 (팔을 들고 > < 눈).
// 사용법 (프로젝트 폴더에서): java scripts/MakeIcons.java
// 결과: src/main/resources/com/mypet/icons/icon-<크기>.png (창 아이콘),
//       packaging/MyPet.ico (윈도우 실행 파일), packaging/MyPet.icns (맥 앱)
// 크기마다 벡터로 다시 그려서 작은 아이콘도 또렷하다 (32px 이하는 그림자·속도선을 빼서 단순하게).

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MakeIcons {
	static final Color INK = new Color(0x26221E);
	static final Color BODY = new Color(0x4FC3F7);
	static final Color SPEED = new Color(0xCFD8DC);
	static final Color SHADOW = new Color(0x26, 0x22, 0x1E, 64);
	/** 그림 좌표: 200×200 칸, 타일은 (7,5) 에서 180×180. */
	static final double UNITS = 200;

	public static void main(String[] args) throws IOException {
		Path root = Path.of(args.length > 0 ? args[0] : ".");
		Path icons = root.resolve("src/main/resources/com/mypet/icons");
		Path packaging = root.resolve("packaging");
		Files.createDirectories(icons);
		Files.createDirectories(packaging);

		// 창 아이콘 (Java 가 화면 배율에 맞는 크기를 고른다)
		for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) {
			ImageIO.write(render(size), "png", icons.resolve("icon-" + size + ".png").toFile());
		}

		// 윈도우 .ico: 16~256 PNG 를 넣는다
		Map<Integer, byte[]> ico = new LinkedHashMap<>();
		for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) {
			ico.put(size, png(render(size)));
		}
		Files.write(packaging.resolve("MyPet.ico"), ico(ico));

		// 맥 .icns: 형식 이름 → 크기 (모두 PNG)
		Map<String, Integer> icnsTypes = new LinkedHashMap<>();
		icnsTypes.put("icp4", 16);
		icnsTypes.put("icp5", 32);
		icnsTypes.put("ic11", 32); // 16@2x
		icnsTypes.put("icp6", 64);
		icnsTypes.put("ic12", 64); // 32@2x
		icnsTypes.put("ic07", 128);
		icnsTypes.put("ic08", 256);
		icnsTypes.put("ic13", 256); // 128@2x
		icnsTypes.put("ic09", 512);
		icnsTypes.put("ic14", 512); // 256@2x
		icnsTypes.put("ic10", 1024); // 512@2x
		Files.write(packaging.resolve("MyPet.icns"), icns(icnsTypes));
		System.out.println("아이콘 완료: " + icons + ", " + packaging);
	}

	static BufferedImage render(int size) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		boolean small = size <= 32;
		double k = size / UNITS;
		g.scale(k, k);
		if (small) {
			// 작은 아이콘: 그림자 없이 타일을 꽉 채운다
			g.translate(-5, -3);
			g.scale(200.0 / 188, 200.0 / 188);
		}
		g.translate(7, 5);
		// 타일 (+ 그림자)
		double border = small ? 8 : 4;
		RoundRectangle2D tile = new RoundRectangle2D.Double(0, 0, 180, 180, 60, 60);
		if (!small) {
			g.setColor(SHADOW);
			g.fill(AffineTransform.getTranslateInstance(5, 5).createTransformedShape(tile));
		}
		g.setColor(Color.WHITE);
		g.fill(tile);
		g.setColor(INK);
		g.setStroke(new BasicStroke((float) border));
		g.draw(new RoundRectangle2D.Double(border / 2 - 2, border / 2 - 2, 180 - border + 4, 180 - border + 4, 60, 60));
		// 속도선
		if (!small) {
			g.setColor(SPEED);
			double[][] lines = {{50, 22, 34}, {82, 10, 42}, {114, 26, 30}};
			for (double[] l : lines) {
				g.fill(new Rectangle2D.Double(l[0], l[1], 6, l[2]));
			}
		}
		// 비트: 몸 44×28, 팔을 든 모습, 12도 기울어져 떨어진다
		Graphics2D b = (Graphics2D) g.create();
		if (small) {
			b.translate(90, 92);
			b.rotate(Math.toRadians(-12));
			b.scale(2.5, 2.5);
			b.translate(-22, -18);
		} else {
			b.translate(90, 115);
			b.rotate(Math.toRadians(-12));
			b.translate(-44, -28);
			b.scale(2, 2);
		}
		double outline = small ? 3 : 2;
		block(b, 0, 0, 44, 28, outline);
		block(b, -9, 6, 8, 8, outline);
		block(b, 45, 6, 8, 8, outline);
		block(b, 10, 29, 8, 8, outline);
		block(b, 25, 29, 8, 8, outline);
		Path2D eyes = new Path2D.Double();
		eyes.moveTo(13.4, 12.5);
		eyes.lineTo(19.5, 15.2);
		eyes.lineTo(13.4, 17.8);
		eyes.moveTo(32.6, 12.5);
		eyes.lineTo(26.5, 15.2);
		eyes.lineTo(32.6, 17.8);
		b.setColor(INK);
		b.setStroke(new BasicStroke(small ? 3f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		b.draw(eyes);
		b.dispose();
		g.dispose();
		return image;
	}

	/** 검은 외곽선이 있는 파란 네모. */
	static void block(Graphics2D g, double x, double y, double w, double h, double outline) {
		g.setColor(INK);
		g.fill(new Rectangle2D.Double(x, y, w, h));
		if (w > outline * 2 && h > outline * 2) {
			g.setColor(BODY);
			g.fill(new Rectangle2D.Double(x + outline, y + outline, w - outline * 2, h - outline * 2));
		}
	}

	static byte[] png(BufferedImage image) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/** .ico: 머리말 + 크기별 항목 + PNG 데이터 (Vista 이후 PNG 항목을 읽는다). */
	static byte[] ico(Map<Integer, byte[]> images) {
		int count = images.size();
		int offset = 6 + 16 * count;
		int total = offset + images.values().stream().mapToInt(b -> b.length).sum();
		ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
		buf.putShort((short) 0).putShort((short) 1).putShort((short) count);
		for (Map.Entry<Integer, byte[]> e : images.entrySet()) {
			int size = e.getKey();
			buf.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
			buf.put((byte) 0).put((byte) 0);
			buf.putShort((short) 1).putShort((short) 32);
			buf.putInt(e.getValue().length).putInt(offset);
			offset += e.getValue().length;
		}
		for (byte[] data : images.values()) {
			buf.put(data);
		}
		return buf.array();
	}

	/** .icns: "icns" + 전체 길이, 그다음 (형식 4글자 + 길이 + PNG) 항목들 (빅 엔디언). */
	static byte[] icns(Map<String, Integer> types) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(body);
		for (Map.Entry<String, Integer> e : types.entrySet()) {
			byte[] data = png(render(e.getValue()));
			out.writeBytes(e.getKey());
			out.writeInt(8 + data.length);
			out.write(data);
		}
		ByteArrayOutputStream file = new ByteArrayOutputStream();
		DataOutputStream head = new DataOutputStream(file);
		head.writeBytes("icns");
		head.writeInt(8 + body.size());
		head.write(body.toByteArray());
		return file.toByteArray();
	}
}
