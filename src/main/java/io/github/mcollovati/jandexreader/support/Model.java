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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.RecordComponentInfo;
import org.jboss.jandex.Type;

/**
 * Converts Jandex structures to plain maps and lists for JSON output.
 */
public final class Model {

    private Model() {}

    public static Map<String, Object> classSummary(ClassInfo clazz) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", clazz.name().toString());
        map.put("kind", Format.kind(clazz));
        map.put("modifiers", Format.classModifiers(clazz));
        map.put("nesting", clazz.nestingType().name().toLowerCase().replace('_', '-'));
        return map;
    }

    public static Map<String, Object> classDetail(ClassInfo clazz, boolean includeSynthetic) {
        Map<String, Object> map = classSummary(clazz);
        map.put("declaration", Format.classDeclaration(clazz));
        map.put(
                "superclass",
                clazz.superClassType() == null ? null : clazz.superClassType().toString());
        map.put(
                "interfaces",
                clazz.interfaceTypes().stream().map(Type::toString).toList());
        map.put(
                "typeParameters",
                clazz.typeParameters().stream().map(Type::toString).toList());
        map.put("annotations", annotations(clazz.declaredAnnotations()));
        if (clazz.isRecord()) {
            map.put(
                    "recordComponents",
                    clazz.recordComponentsInDeclarationOrder().stream()
                            .map(Model::recordComponent)
                            .toList());
        }
        map.put(
                "fields",
                Members.fields(clazz, includeSynthetic).stream()
                        .map(Model::field)
                        .toList());
        map.put(
                "methods",
                Members.methods(clazz, includeSynthetic).stream()
                        .map(Model::method)
                        .toList());
        return map;
    }

    public static Map<String, Object> field(FieldInfo field) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", field.name());
        map.put("type", Format.type(field.type()));
        map.put("modifiers", Format.fieldModifiers(field));
        map.put("enumConstant", field.isEnumConstant());
        map.put("declaration", Format.fieldDeclaration(field));
        map.put("annotations", annotations(field.declaredAnnotations()));
        return map;
    }

    public static Map<String, Object> method(MethodInfo method) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", method.name());
        map.put("declaration", Format.methodDeclaration(method));
        map.put("modifiers", Format.methodModifiers(method));
        map.put("constructor", method.isConstructor());
        map.put("returnType", Format.type(method.returnType()));
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (int i = 0; i < method.parametersCount(); i++) {
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("name", method.parameterName(i));
            parameter.put("type", Format.type(method.parameterType(i)));
            int position = i;
            parameter.put(
                    "annotations",
                    annotations(Members.parameterAnnotations(method).stream()
                            .filter(a -> a.target().asMethodParameter().position() == position)
                            .toList()));
            parameters.add(parameter);
        }
        map.put("parameters", parameters);
        map.put("exceptions", method.exceptions().stream().map(Format::type).toList());
        map.put("annotations", annotations(method.declaredAnnotations()));
        return map;
    }

    public static Map<String, Object> recordComponent(RecordComponentInfo component) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", component.name());
        map.put("type", Format.type(component.type()));
        map.put("annotations", annotations(component.declaredAnnotations()));
        return map;
    }

    public static Map<String, Object> annotationUsage(AnnotationInstance annotation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("annotation", annotation.name().toString());
        map.put("targetKind", annotation.target() == null ? null : Format.targetKind(annotation.target()));
        map.put("target", Format.target(annotation.target()));
        Map<String, Object> values = new LinkedHashMap<>();
        for (AnnotationValue value : annotation.values()) {
            values.put(value.name(), Format.value(value));
        }
        map.put("values", values);
        map.put("text", Format.annotation(annotation));
        return map;
    }

    private static List<String> annotations(java.util.Collection<AnnotationInstance> annotations) {
        return annotations.stream().map(Format::annotation).toList();
    }
}
