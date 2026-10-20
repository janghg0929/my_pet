package com.mypet.appearance.rendering;

import com.mypet.Util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.HashMap;
import java.util.Map;

/** Tk Canvas 도형(create_rectangle/oval/arc/line/polygon/text)을 Java2D 로 그리는 보조 함수. */
public final class Draw {
	private static final Map<String, Color> COLOR_CACHE = new HashMap<>();

	private Draw() {
	}

	public static Color color(String hex) {
		return COLOR_CACHE.computeIfAbsent(hex.toLowerCase(), Util::toColor);
	}

	private static void antialias(Graphics2D g, boolean on) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			on ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
	}

	/** create_rectangle. outline 이 null 이면 테두리 없음. 픽셀 느낌을 위해 정수 좌표/안티앨리어싱 없이 그린다. */
	public static void rect(Graphics2D g, double x1, double y1, double x2, double y2, String fill, String outline, int width) {
		antialias(g, false);
		int ix1 = Util.pyRound(Math.min(x1, x2));
		int iy1 = Util.pyRound(Math.min(y1, y2));
		int ix2 = Util.pyRound(Math.max(x1, x2));
		int iy2 = Util.pyRound(Math.max(y1, y2));
		int w = ix2 - ix1;
		int h = iy2 - iy1;
		if (w <= 0 || h <= 0) {
			return;
		}
		// 화면 배율(125%, 150% 등)에서도 픽셀이 고르게 맞도록 실제 화면 픽셀 좌표로 바꿔서 그린다
		AffineTransform transform = g.getTransform();
		if (transform.getShearX() != 0 || transform.getShearY() != 0) {
			drawRectInUserSpace(g, ix1, iy1, w, h, fill, outline, width);
			return;
		}
		double sx = transform.getScaleX();
		double sy = transform.getScaleY();
		int dx1 = (int) Math.round(transform.getTranslateX() + ix1 * sx);
		int dy1 = (int) Math.round(transform.getTranslateY() + iy1 * sy);
		int dx2 = (int) Math.round(transform.getTranslateX() + (ix1 + w) * sx);
		int dy2 = (int) Math.round(transform.getTranslateY() + (iy1 + h) * sy);
		Graphics2D device = (Graphics2D) g.create();
		try {
			device.setTransform(new AffineTransform());
			int border = outline != null && width > 0 ? Math.max(1, (int) Math.round(width * Math.min(sx, sy))) : 0;
			drawRectInUserSpace(device, dx1, dy1, dx2 - dx1, dy2 - dy1, fill, outline, border);
		} finally {
			device.dispose();
		}
	}

	/**
	 * 테두리 색으로 바깥을 꽉 채우고 안쪽만 채움 색으로 칠한다.
	 * 선을 덧그리는 방식은 화면 배율에서 채움 색이 테두리 밖으로 삐져나오기 때문.
	 */
	private static void drawRectInUserSpace(Graphics2D g, int x, int y, int w, int h, String fill, String outline, int border) {
		if (outline != null && border > 0) {
			g.setColor(color(outline));
			g.fillRect(x, y, w, h);
			if (fill != null && w > border * 2 && h > border * 2) {
				g.setColor(color(fill));
				g.fillRect(x + border, y + border, w - border * 2, h - border * 2);
			}
			return;
		}
		if (fill != null) {
			g.setColor(color(fill));
			g.fillRect(x, y, w, h);
		}
	}

	/** create_oval. */
	public static void oval(Graphics2D g, double x1, double y1, double x2, double y2, String fill, String outline, int width) {
		antialias(g, true);
		Ellipse2D shape = new Ellipse2D.Double(x1, y1, x2 - x1, y2 - y1);
		if (fill != null) {
			g.setColor(color(fill));
			g.fill(shape);
		}
		if (outline != null && width > 0) {
			g.setColor(color(outline));
			g.setStroke(new BasicStroke(width));
			g.draw(shape);
		}
	}

	/** create_arc(style=ARC). 각도는 Tk 와 같이 3시 방향 기준 반시계(도). */
	public static void arc(Graphics2D g, double x1, double y1, double x2, double y2, double start, double extent, String outline, int width) {
		antialias(g, true);
		g.setColor(color(outline));
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		g.draw(new Arc2D.Double(x1, y1, x2 - x1, y2 - y1, start, extent, Arc2D.OPEN));
	}

	/** create_line. */
	public static void line(Graphics2D g, double x1, double y1, double x2, double y2, String fill, int width, boolean roundCap) {
		antialias(g, true);
		g.setColor(color(fill));
		g.setStroke(new BasicStroke(width, roundCap ? BasicStroke.CAP_ROUND : BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
		g.draw(new Line2D.Double(x1, y1, x2, y2));
	}

	/** create_polygon(outline=""). */
	public static void polygon(Graphics2D g, double[] coords, String fill) {
		antialias(g, true);
		Path2D.Double path = new Path2D.Double();
		path.moveTo(coords[0], coords[1]);
		for (int i = 2; i + 1 < coords.length; i += 2) {
			path.lineTo(coords[i], coords[i + 1]);
		}
		path.closePath();
		g.setColor(color(fill));
		g.fill(path);
	}

	/** create_text(anchor="center"). */
	public static void textCentered(Graphics2D g, double cx, double cy, String text, Font font, String fill) {
		antialias(g, true);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(font);
		g.setColor(color(fill));
		FontMetrics metrics = g.getFontMetrics();
		double x = cx - metrics.stringWidth(text) / 2.0;
		double baseline = cy - (metrics.getAscent() + metrics.getDescent()) / 2.0 + metrics.getAscent();
		g.drawString(text, (float) x, (float) baseline);
	}
}
