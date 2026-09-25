package io.github.mcollovati.jandexreader.support;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import io.github.mcollovati.jandexreader.ToolException;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

/**
 * Resolves user-provided class and annotation names, allowing simple names such as {@code Singleton}
 * or nested names written with a dot ({@code Outer.Inner}).
 */
public final class Names {

    private Names() {
    }

    /**
     * Finds a class that must be present in the index.
     */
    public static ClassInfo requireClass(IndexView index, String name) {
        ClassInfo found = index.getClassByName(DotName.createSimple(name));
        if (found != null) {
            return found;
        }
        List<String> candidates = matching(index.getKnownClasses().stream().map(c -> c.name().toString()).toList(),
                name);
        if (candidates.size() == 1) {
            return index.getClassByName(DotName.createSimple(candidates.get(0)));
        }
        throw notFoundOrAmbiguous("Class", name, candidates);
    }

    /**
     * Resolves a class name that may be outside the index (e.g. a JDK superclass or an interface from
     * another library); an unknown fully qualified name is returned as is.
     */
    public static DotName resolveClassName(IndexView index, String name) {
        if (index.getClassByName(DotName.createSimple(name)) != null) {
            return DotName.createSimple(name);
        }
        Set<String> known = new TreeSet<>();
        for (ClassInfo clazz : index.getKnownClasses()) {
            known.add(clazz.name().toString());
            if (clazz.superName() != null) {
                known.add(clazz.superName().toString());
            }
            clazz.interfaceNames().forEach(i -> known.add(i.toString()));
        }
        return resolveAmong(known, name, "Class");
    }

    public static DotName resolveAnnotationName(IndexView index, String name) {
        String normalized = name.startsWith("@") ? name.substring(1) : name;
        if (!index.getAnnotations(DotName.createSimple(normalized)).isEmpty()) {
            return DotName.createSimple(normalized);
        }
        Set<String> known = annotationNames(index);
        index.getKnownClasses().stream().filter(ClassInfo::isAnnotation).forEach(c -> known.add(c.name().toString()));
        return resolveAmong(known, normalized, "Annotation");
    }

    public static Set<String> annotationNames(IndexView index) {
        Set<String> names = new TreeSet<>();
        for (ClassInfo clazz : index.getKnownClasses()) {
            clazz.annotationsMap().keySet().forEach(n -> names.add(n.toString()));
        }
        return names;
    }

    private static DotName resolveAmong(Collection<String> known, String name, String what) {
        if (known.contains(name)) {
            return DotName.createSimple(name);
        }
        List<String> candidates = matching(known, name);
        if (candidates.size() == 1) {
            return DotName.createSimple(candidates.get(0));
        }
        if (candidates.isEmpty() && name.contains(".")) {
            // fully qualified name not referenced by the index: queries will simply return nothing
            return DotName.createSimple(name);
        }
        throw notFoundOrAmbiguous(what, name, candidates);
    }

    /**
     * Names equal to the query ignoring '$' vs '.', or ending with it after a package or nesting separator.
     */
    static List<String> matching(Collection<String> names, String query) {
        String normalizedQuery = query.replace('$', '.');
        return names.stream()
                .filter(n -> {
                    String normalized = n.replace('$', '.');
                    return normalized.equals(normalizedQuery) || normalized.endsWith("." + normalizedQuery);
                })
                .sorted()
                .toList();
    }

    private static ToolException notFoundOrAmbiguous(String what, String name, List<String> candidates) {
        if (candidates.isEmpty()) {
            return new ToolException(what + " not found in index: " + name);
        }
        return new ToolException(what + " name '" + name + "' is ambiguous, candidates:\n  "
                + String.join("\n  ", candidates));
    }

    /**
     * Compiles a filter: patterns with {@code *} or {@code ?} are globs matched against the whole
     * fully qualified name ({@code *} matches any sequence, dots included), other patterns match as substrings.
     */
    public static Pattern filter(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        if (!pattern.contains("*") && !pattern.contains("?")) {
            return Pattern.compile(Pattern.quote(pattern));
        }
        StringBuilder regex = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }

    public static boolean matches(Pattern filter, String name) {
        return filter == null || filter.matcher(name).find();
    }
}
