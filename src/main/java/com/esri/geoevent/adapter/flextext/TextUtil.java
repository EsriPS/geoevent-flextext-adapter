package com.esri.geoevent.adapter.flextext;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {
	private TextUtil() {
	}

	public static String unescape(String s) {
		s = replace(s, "\\r", "\r");
		s = replace(s, "\\n", "\n");
		s = replace(s, "\\t", "\t");
		return unescapeUnicode(s);
	}

	public static String unescapeUnicode(String s) {
		String patternString = "\\Q\\u\\E[\\da-fA-F]{4}";
		Pattern pattern = Pattern.compile(patternString); // This is \ u followed by four hexadecimal characters (0-F)
		Matcher matcher = pattern.matcher(s);
		String translated = "";
		int end = 0;
		while (matcher.find()) {
			String group = matcher.group();
			int start = matcher.start();
			translated += s.substring(end, start);
			byte[] bytes = hexStringToByteArray(group.substring(2));
			try {
				translated += new String(bytes, "UTF-16");
			} catch (UnsupportedEncodingException e) {
				translated += group;
			}
			end = matcher.end();
		}
		translated += s.substring(end);
		return translated;
	}

	public static byte[] hexStringToByteArray(String s) {
		int len = s.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
		}
		return data;
	}

	public static String replace(String s, String search, String replace) {
		if (s == null)
			return null;
		if (s.equals(search))
			return replace;
		while (s.indexOf(search) > -1) {
			int index = s.indexOf(search);
			String part1 = "";
			if (index > 0)
				part1 = s.substring(0, index);
			s = s.substring(index);
			String part2 = "";
			if ((s.length() - search.length()) > 0)
				part2 = s.substring(search.length());
			s = part1 + replace + part2;
		}
		return s;
	}
}
