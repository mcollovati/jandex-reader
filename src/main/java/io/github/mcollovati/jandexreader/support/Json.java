package io.github.mcollovati.jandexreader.support;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Pretty-printing JSON writer for the plain maps, collections, strings, numbers and booleans built by
 * {@link Model} and the commands. Hand-written to keep a JSON library out of the native executable.
 */
public final class Json {

    private static final String INDENT = "  ";

    private Json() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value, int depth) {
        switch (value) {
            case null -> sb.append("null");
            case String text -> string(sb, text);
            case Boolean bool -> sb.append(bool);
            case Double d when d.isNaN() || d.isInfinite() -> sb.append("null");
            case Float f when f.isNaN() || f.isInfinite() -> sb.append("null");
            case Number number -> sb.append(number);
            case Map<?, ?> map -> object(sb, map, depth);
            case Collection<?> collection -> array(sb, collection, depth);
            default -> string(sb, value.toString());
        }
    }

    private static void object(StringBuilder sb, Map<?, ?> map, int depth) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        Iterator<? extends Map.Entry<?, ?>> entries = map.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<?, ?> entry = entries.next();
            sb.append(INDENT.repeat(depth + 1));
            string(sb, String.valueOf(entry.getKey()));
            sb.append(": ");
            write(sb, entry.getValue(), depth + 1);
            sb.append(entries.hasNext() ? ",\n" : "\n");
        }
        sb.append(INDENT.repeat(depth)).append('}');
    }

    private static void array(StringBuilder sb, Collection<?> collection, int depth) {
        if (collection.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        Iterator<?> items = collection.iterator();
        while (items.hasNext()) {
            sb.append(INDENT.repeat(depth + 1));
            write(sb, items.next(), depth + 1);
            sb.append(items.hasNext() ? ",\n" : "\n");
        }
        sb.append(INDENT.repeat(depth)).append(']');
    }

    private static void string(StringBuilder sb, String text) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
