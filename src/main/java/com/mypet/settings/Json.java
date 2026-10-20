package com.mypet.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 설정 파일용 작은 JSON 도구 (외부 라이브러리 없이).
 * 읽기 결과: 객체 → Map, 배열 → List, 문자열 → String, 숫자 → Double, true/false → Boolean, null → null
 */
public final class Json {
	private Json() {
	}

	// ---------------------------------------------------------------- 쓰기

	/** 사람이 읽기 좋게 들여쓰기 해서 쓴다. Map / List / String / Number / Boolean / null 만 지원. */
	public static String write(Object value) {
		StringBuilder out = new StringBuilder();
		write(out, value, 0);
		return out.append('\n').toString();
	}

	private static void write(StringBuilder out, Object value, int depth) {
		if (value == null) {
			out.append("null");
		} else if (value instanceof String text) {
			writeString(out, text);
		} else if (value instanceof Number || value instanceof Boolean) {
			out.append(value);
		} else if (value instanceof Map<?, ?> map) {
			if (map.isEmpty()) {
				out.append("{}");
				return;
			}
			out.append("{\n");
			int i = 0;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				indent(out, depth + 1);
				writeString(out, String.valueOf(entry.getKey()));
				out.append(": ");
				write(out, entry.getValue(), depth + 1);
				out.append(++i < map.size() ? ",\n" : "\n");
			}
			indent(out, depth);
			out.append('}');
		} else if (value instanceof List<?> list) {
			// 짧은 값 목록(색 16칸 등)은 한 줄에 쓴다
			out.append('[');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					out.append(", ");
				}
				write(out, list.get(i), depth + 1);
			}
			out.append(']');
		} else {
			throw new IllegalArgumentException("JSON 으로 쓸 수 없는 값: " + value.getClass());
		}
	}

	private static void indent(StringBuilder out, int depth) {
		out.append("  ".repeat(depth));
	}

	private static void writeString(StringBuilder out, String text) {
		out.append('"');
		for (char ch : text.toCharArray()) {
			switch (ch) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (ch < 0x20) {
						out.append(String.format("\\u%04x", (int) ch));
					} else {
						out.append(ch);
					}
				}
			}
		}
		out.append('"');
	}

	// ---------------------------------------------------------------- 읽기

	/** JSON 문자열을 읽는다. 형식이 틀리면 IllegalArgumentException. */
	public static Object read(String text) {
		Parser parser = new Parser(text);
		parser.skipSpace();
		Object value = parser.value();
		parser.skipSpace();
		if (!parser.atEnd()) {
			throw parser.error("뒤에 남는 글자가 있습니다");
		}
		return value;
	}

	private static final class Parser {
		private final String text;
		private int pos;

		Parser(String text) {
			// UTF-8 BOM 이 붙은 파일도 읽을 수 있게
			this.text = text.startsWith("﻿") ? text.substring(1) : text;
		}

		boolean atEnd() {
			return pos >= text.length();
		}

		IllegalArgumentException error(String message) {
			return new IllegalArgumentException("JSON 형식 오류(" + pos + "번째 글자): " + message);
		}

		void skipSpace() {
			while (!atEnd() && Character.isWhitespace(text.charAt(pos))) {
				pos++;
			}
		}

		char peek() {
			if (atEnd()) {
				throw error("갑자기 끝났습니다");
			}
			return text.charAt(pos);
		}

		void expect(char ch) {
			if (peek() != ch) {
				throw error("'" + ch + "' 가 필요합니다");
			}
			pos++;
		}

		Object value() {
			char ch = peek();
			return switch (ch) {
				case '{' -> object();
				case '[' -> array();
				case '"' -> string();
				case 't' -> literal("true", Boolean.TRUE);
				case 'f' -> literal("false", Boolean.FALSE);
				case 'n' -> literal("null", null);
				default -> {
					if (ch == '-' || (ch >= '0' && ch <= '9')) {
						yield number();
					}
					throw error("알 수 없는 값");
				}
			};
		}

		Object literal(String word, Object value) {
			if (!text.startsWith(word, pos)) {
				throw error(word + " 가 필요합니다");
			}
			pos += word.length();
			return value;
		}

		Map<String, Object> object() {
			expect('{');
			Map<String, Object> map = new LinkedHashMap<>();
			skipSpace();
			if (peek() == '}') {
				pos++;
				return map;
			}
			while (true) {
				skipSpace();
				String key = string();
				skipSpace();
				expect(':');
				skipSpace();
				map.put(key, value());
				skipSpace();
				if (peek() == ',') {
					pos++;
					continue;
				}
				expect('}');
				return map;
			}
		}

		List<Object> array() {
			expect('[');
			List<Object> list = new ArrayList<>();
			skipSpace();
			if (peek() == ']') {
				pos++;
				return list;
			}
			while (true) {
				skipSpace();
				list.add(value());
				skipSpace();
				if (peek() == ',') {
					pos++;
					continue;
				}
				expect(']');
				return list;
			}
		}

		String string() {
			expect('"');
			StringBuilder out = new StringBuilder();
			while (true) {
				char ch = peek();
				pos++;
				if (ch == '"') {
					return out.toString();
				}
				if (ch != '\\') {
					out.append(ch);
					continue;
				}
				char escape = peek();
				pos++;
				switch (escape) {
					case '"', '\\', '/' -> out.append(escape);
					case 'b' -> out.append('\b');
					case 'f' -> out.append('\f');
					case 'n' -> out.append('\n');
					case 'r' -> out.append('\r');
					case 't' -> out.append('\t');
					case 'u' -> {
						if (pos + 4 > text.length()) {
							throw error("\\u 뒤에 16진수 4자리가 필요합니다");
						}
						out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
						pos += 4;
					}
					default -> throw error("알 수 없는 이스케이프 \\" + escape);
				}
			}
		}

		Double number() {
			int start = pos;
			while (!atEnd() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
				pos++;
			}
			try {
				return Double.parseDouble(text.substring(start, pos));
			} catch (NumberFormatException e) {
				throw error("숫자가 아닙니다");
			}
		}
	}
}
