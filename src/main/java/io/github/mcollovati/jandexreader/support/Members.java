/*
 * Copyright 2026 Marco Collovati
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    private Members() {}

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
