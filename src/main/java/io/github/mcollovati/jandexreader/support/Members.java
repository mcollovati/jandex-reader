package io.github.mcollovati.jandexreader.support;

import java.util.Comparator;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;

/**
 * Member listing helpers shared by commands.
 */
public final class Members {

    private Members() {
    }

    public static List<FieldInfo> fields(ClassInfo clazz, boolean includeSynthetic) {
        return clazz.fieldsInDeclarationOrder().stream()
                .filter(f -> includeSynthetic || !f.isSynthetic())
                .toList();
    }

    public static List<MethodInfo> methods(ClassInfo clazz, boolean includeSynthetic) {
        return clazz.methodsInDeclarationOrder().stream()
                .filter(m -> includeSynthetic || !(m.isSynthetic() || m.isBridge()))
                .filter(m -> !m.isStaticInitializer())
                .sorted(Comparator.comparing((MethodInfo m) -> !m.isConstructor()))
                .toList();
    }

    public static List<AnnotationInstance> parameterAnnotations(MethodInfo method) {
        return method.annotations().stream()
                .filter(a -> a.target() != null && a.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER)
                .toList();
    }
}
