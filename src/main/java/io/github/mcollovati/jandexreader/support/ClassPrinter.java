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

import java.io.PrintWriter;
import java.util.List;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.RecordComponentInfo;

/**
 * Prints classes and members as indented Java-like text, annotations above the annotated element.
 */
public class ClassPrinter {

    private final PrintWriter out;
    private final boolean includeSynthetic;

    public ClassPrinter(PrintWriter out, boolean includeSynthetic) {
        this.out = out;
        this.includeSynthetic = includeSynthetic;
    }

    public void printClass(ClassInfo clazz) {
        printAnnotations(clazz.declaredAnnotations(), "");
        out.println(Format.classDeclaration(clazz) + " {");
        if (clazz.nestingType() != ClassInfo.NestingType.TOP_LEVEL) {
            out.println("    // " + clazz.nestingType().name().toLowerCase().replace('_', '-') + " class"
                    + (clazz.enclosingClass() != null ? " of " + clazz.enclosingClass() : ""));
        }
        if (clazz.isRecord()) {
            List<RecordComponentInfo> components = clazz.recordComponentsInDeclarationOrder();
            if (!components.isEmpty()) {
                out.println("    // record components");
                for (RecordComponentInfo component : components) {
                    printAnnotations(component.declaredAnnotations(), "    ");
                    out.println("    " + Format.recordComponent(component));
                }
            }
        }
        List<FieldInfo> fields = Members.fields(clazz, includeSynthetic);
        if (!fields.isEmpty()) {
            out.println();
            fields.forEach(f -> printField(f, "    "));
        }
        List<MethodInfo> methods = Members.methods(clazz, includeSynthetic);
        if (!methods.isEmpty()) {
            out.println();
            methods.forEach(m -> printMethod(m, "    "));
        }
        out.println("}");
    }

    public void printField(FieldInfo field, String indent) {
        printAnnotations(field.declaredAnnotations(), indent);
        out.println(indent + Format.fieldDeclaration(field));
    }

    public void printMethod(MethodInfo method, String indent) {
        printAnnotations(method.declaredAnnotations(), indent);
        out.println(indent + Format.methodDeclaration(method));
        for (AnnotationInstance annotation : Members.parameterAnnotations(method)) {
            int position = annotation.target().asMethodParameter().position();
            String name = method.parameterName(position);
            out.println(indent + "    // parameter " + position + (name != null ? " " + name : "") + ": "
                    + Format.annotation(annotation));
        }
    }

    private void printAnnotations(java.util.Collection<AnnotationInstance> annotations, String indent) {
        for (AnnotationInstance annotation : annotations) {
            out.println(indent + Format.annotation(annotation));
        }
    }
}
