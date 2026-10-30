package com.useleess.carpet.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the launcher says, read from {@code assets/useleess_carpet/lang/<language>.json} (English fallback),
 * so it can be translated without touching code. Values use {@code %s} for their arguments.
 */
public final class Messages {
    public static final String LANG_DIR = "/assets/useleess_carpet/lang/";
    private final Map<String, String> english;
    private final Map<String, String> local;

    private Messages(Map<String, String> english, Map<String, String> local) {
        this.english = english;
        this.local = local;
    }

    public static Messages load() {
        Map<String, String> english = read("en_us");
        Locale l = Locale.getDefault();
        String code = (l.getLanguage() + "_" + l.getCountry()).toLowerCase(Locale.ROOT);
        return new Messages(english, code.equals("en_us") ? english : read(code));
    }

    public String get(String key, Object... args) {
        String text = local.get(key);
        if (text == null) text = english.get(key);
        if (text == null) return key;
        return args.length == 0 ? text : String.format(text, args);
    }

    public String get(Problem p) {
        return get(p.key, p.args);
    }

    public static Map<String, String> read(String language) {
        try (InputStream in = Messages.class.getResourceAsStream(LANG_DIR + language + ".json")) {
            if (in == null) return new LinkedHashMap<>();
            return parse(new String(Patch.readFully(in), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    /** A flat JSON object of strings (the language file format). */
    static Map<String, String> parse(String json) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        int[] at = {skip(json, 0)};
        expect(json, at, '{');
        if (peek(json, at) == '}') return map;
        while (true) {
            String key = string(json, at);
            expect(json, at, ':');
            map.put(key, string(json, at));
            char c = peek(json, at);
            at[0]++;
            if (c == '}') return map;
            if (c != ',') throw new IOException("bad language file");
        }
    }

    private static String string(String s, int[] at) throws IOException {
        expect(s, at, '"');
        StringBuilder b = new StringBuilder();
        while (at[0] < s.length()) {
            char c = s.charAt(at[0]++);
            if (c == '"') return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            char e = s.charAt(at[0]++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'u': b.append((char) Integer.parseInt(s.substring(at[0], at[0] + 4), 16)); at[0] += 4; break;
                default: b.append(e);
            }
        }
        throw new IOException("bad language file");
    }

    private static void expect(String s, int[] at, char c) throws IOException {
        if (peek(s, at) != c) throw new IOException("bad language file");
        at[0]++;
    }

    private static char peek(String s, int[] at) throws IOException {
        at[0] = skip(s, at[0]);
        if (at[0] >= s.length()) throw new IOException("bad language file");
        return s.charAt(at[0]);
    }

    private static int skip(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }
}
