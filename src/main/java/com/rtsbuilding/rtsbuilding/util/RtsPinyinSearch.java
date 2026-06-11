package com.rtsbuilding.rtsbuilding.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class RtsPinyinSearch {
    private static final String DICT_PATH = "/assets/rtsbuilding/pinyin/data.txt";
    private static final Map<Character, String[]> PINYIN_BY_CHAR = loadDictionary();

    private RtsPinyinSearch() {
    }

    public static boolean contains(String text, String query) {
        if (text == null || text.isBlank() || query == null || query.isBlank()) {
            return false;
        }
        if (!containsCjk(text)) {
            return false;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        if (normalizedQuery.isEmpty()) {
            return false;
        }
        if (text.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
            return true;
        }
        if (matchesInitials(text, normalizedQuery)) {
            return true;
        }
        for (int i = 0; i < text.length(); i++) {
            if (matchesFrom(text, i, normalizedQuery, 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFrom(String text, int textIndex, String query, int queryIndex) {
        if (queryIndex >= query.length()) {
            return true;
        }
        if (textIndex >= text.length()) {
            return false;
        }
        char ch = text.charAt(textIndex);
        String[] pinyins = PINYIN_BY_CHAR.get(ch);
        if (pinyins != null) {
            for (String pinyin : pinyins) {
                if (matchesTokenOption(text, textIndex, query, queryIndex, pinyin)) {
                    return true;
                }
            }
        }
        String literal = normalizeLiteral(ch);
        return !literal.isEmpty() && matchesTokenOption(text, textIndex, query, queryIndex, literal);
    }

    private static boolean matchesTokenOption(String text, int textIndex, String query, int queryIndex, String option) {
        if (option == null || option.isEmpty()) {
            return false;
        }
        String remaining = query.substring(queryIndex);
        if (option.startsWith(remaining)) {
            return true;
        }
        return query.startsWith(option, queryIndex)
                && matchesFrom(text, textIndex + 1, query, queryIndex + option.length());
    }

    private static boolean matchesInitials(String text, String query) {
        if (query.length() > text.length()) {
            return false;
        }
        StringBuilder initials = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String[] pinyins = PINYIN_BY_CHAR.get(ch);
            if (pinyins != null && pinyins.length > 0 && !pinyins[0].isEmpty()) {
                initials.append(pinyins[0].charAt(0));
                continue;
            }
            String literal = normalizeLiteral(ch);
            if (!literal.isEmpty()) {
                initials.append(literal.charAt(0));
            }
        }
        return initials.indexOf(query) >= 0;
    }

    private static String normalizeLiteral(char ch) {
        if (Character.isLetterOrDigit(ch)) {
            return String.valueOf(Character.toLowerCase(ch));
        }
        return "";
    }

    private static Map<Character, String[]> loadDictionary() {
        Map<Character, String[]> result = new HashMap<>();
        try (InputStream in = RtsPinyinSearch.class.getResourceAsStream(DICT_PATH)) {
            if (in == null) {
                return result;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseDictionaryLine(line, result);
                }
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return result;
    }

    private static void parseDictionaryLine(String line, Map<Character, String[]> into) {
        if (line == null || line.length() < 4) {
            return;
        }
        int colon = line.indexOf(':');
        if (colon <= 0 || colon >= line.length() - 1) {
            return;
        }
        char ch = line.charAt(0);
        String[] rawPinyins = line.substring(colon + 1).trim().split("\\s+");
        String[] normalized = new String[rawPinyins.length];
        int count = 0;
        for (String raw : rawPinyins) {
            String clean = normalizePinyin(raw);
            if (clean.isEmpty()) {
                continue;
            }
            boolean duplicate = false;
            for (int i = 0; i < count; i++) {
                if (normalized[i].equals(clean)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                normalized[count++] = clean;
            }
        }
        if (count > 0) {
            String[] pinyins = new String[count];
            System.arraycopy(normalized, 0, pinyins, 0, count);
            into.put(ch, pinyins);
        }
    }

    private static String normalizePinyin(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if (ch >= 'a' && ch <= 'z') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
