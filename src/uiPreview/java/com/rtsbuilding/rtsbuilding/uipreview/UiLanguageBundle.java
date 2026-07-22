package com.rtsbuilding.rtsbuilding.uipreview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/** 读取主线扁平语言 JSON 的小型 Java 8 适配器，不维护预览专用翻译副本。 */
public final class UiLanguageBundle {
    private final Map<String, String> values;

    private UiLanguageBundle(Map<String, String> values) {
        this.values = values;
    }

    public static UiLanguageBundle load(File file) throws IOException {
        StringBuilder json = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), "UTF-8"));
        try {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) json.append(buffer, 0, read);
        } finally {
            reader.close();
        }
        return new UiLanguageBundle(parseFlatObject(json.toString()));
    }

    public String text(String key) {
        String value = values.get(key);
        return value == null ? key : value;
    }

    public String format(String key, Object... args) {
        String value = text(key);
        for (Object arg : args) {
            value = value.replaceFirst("%[0-9$]*s", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        }
        return value;
    }

    public int size() {
        return values.size();
    }

    private static Map<String, String> parseFlatObject(String json) throws IOException {
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        int[] index = new int[] {skip(json, 0)};
        if (index[0] < json.length() && json.charAt(index[0]) == '\uFEFF') index[0]++;
        index[0] = skip(json, index[0]);
        require(json, index, '{');
        while (true) {
            index[0] = skip(json, index[0]);
            if (peek(json, index) == '}') {
                index[0]++;
                break;
            }
            String key = string(json, index);
            index[0] = skip(json, index[0]);
            require(json, index, ':');
            index[0] = skip(json, index[0]);
            String value = string(json, index);
            result.put(key, value);
            index[0] = skip(json, index[0]);
            char next = peek(json, index);
            if (next == ',') index[0]++;
            else if (next != '}') throw new IOException("Invalid language JSON near " + index[0]);
        }
        return result;
    }

    private static String string(String json, int[] index) throws IOException {
        require(json, index, '"');
        StringBuilder value = new StringBuilder();
        while (index[0] < json.length()) {
            char c = json.charAt(index[0]++);
            if (c == '"') return value.toString();
            if (c != '\\') {
                value.append(c);
                continue;
            }
            if (index[0] >= json.length()) throw new IOException("Unterminated escape");
            char escaped = json.charAt(index[0]++);
            switch (escaped) {
                case '"': value.append('"'); break;
                case '\\': value.append('\\'); break;
                case '/': value.append('/'); break;
                case 'b': value.append('\b'); break;
                case 'f': value.append('\f'); break;
                case 'n': value.append('\n'); break;
                case 'r': value.append('\r'); break;
                case 't': value.append('\t'); break;
                case 'u':
                    if (index[0] + 4 > json.length()) throw new IOException("Short unicode escape");
                    value.append((char) Integer.parseInt(json.substring(index[0], index[0] + 4), 16));
                    index[0] += 4;
                    break;
                default: throw new IOException("Unsupported escape: " + escaped);
            }
        }
        throw new IOException("Unterminated string");
    }

    private static void require(String json, int[] index, char expected) throws IOException {
        if (index[0] >= json.length() || json.charAt(index[0]) != expected) {
            throw new IOException("Expected '" + expected + "' near " + index[0]);
        }
        index[0]++;
    }

    private static char peek(String json, int[] index) throws IOException {
        if (index[0] >= json.length()) throw new IOException("Unexpected end of JSON");
        return json.charAt(index[0]);
    }

    private static int skip(String json, int index) {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) index++;
        return index;
    }
}
