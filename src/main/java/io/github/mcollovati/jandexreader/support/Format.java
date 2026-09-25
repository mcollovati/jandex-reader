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

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.stream.Collectors;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.RecordComponentInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeTarget;

/**
 * Renders Jandex structures as Java-like source text.
 */
public final class Format {

    private static final int VISIBILITY_AND_BASIC =
            Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL;

    private static final int ACC_VARARGS = 0x0080;

    private Format() {}

    public static String kind(ClassInfo clazz) {
        if (clazz.isAnnotation()) {
            return "annotation";
        }
        if (clazz.isInterface()) {
            return "interface";
        }
        if (clazz.isEnum()) {
            return "enum";
        }
        if (clazz.isRecord()) {
            return "record";
        }
        if (clazz.isModule()) {
            return "module";
        }
        return "class";
    }

    private static String keyword(ClassInfo clazz) {
        return switch (kind(clazz)) {
            case "annotation" -> "@interface";
            default -> kind(clazz);
        };
    }

    public static String classModifiers(ClassInfo clazz) {
        int flags = clazz.flags() & (VISIBILITY_AND_BASIC | Modifier.ABSTRACT);
        if (clazz.isInterface()) {
            flags &= ~Modifier.ABSTRACT;
        }
        if (clazz.isEnum() || clazz.isRecord()) {
            flags &= ~Modifier.FINAL;
        }
        if (clazz.isEnum() || clazz.isRecord() || clazz.isInterface()) {
            // implicitly static when nested
            flags &= ~Modifier.STATIC;
        }
        return Modifier.toString(flags);
    }

    public static String methodModifiers(MethodInfo method) {
        int flags = method.flags() & Modifier.methodModifiers();
        if (method.declaringClass().isInterface()) {
            flags &= ~Modifier.ABSTRACT;
            if (!Modifier.isStatic(flags) && !Modifier.isPrivate(flags) && !Modifier.isAbstract(method.flags())) {
                return join(Modifier.toString(flags), "default");
            }
        }
        return Modifier.toString(flags);
    }

    public static String fieldModifiers(FieldInfo field) {
        return Modifier.toString(field.flags() & Modifier.fieldModifiers());
    }

    /**
     * Class declaration line, e.g. {@code public final class a.B<T> extends a.C implements a.D}.
     */
    public static String classDeclaration(ClassInfo clazz) {
        StringBuilder sb = new StringBuilder(join(classModifiers(clazz), keyword(clazz)));
        sb.append(' ').append(clazz.name());
        sb.append(typeParameters(clazz.typeParameters()));
        Type superType = clazz.superClassType();
        if (superType != null && !clazz.isInterface() && !isImplicitSuperclass(clazz, superType)) {
            sb.append(" extends ").append(superType);
        }
        List<Type> interfaces = clazz.interfaceTypes();
        if (clazz.isAnnotation()) {
            interfaces = interfaces.stream()
                    .filter(t -> !t.name().toString().equals("java.lang.annotation.Annotation"))
                    .toList();
        }
        if (!interfaces.isEmpty()) {
            sb.append(clazz.isInterface() ? " extends " : " implements ")
                    .append(interfaces.stream().map(Type::toString).collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    private static boolean isImplicitSuperclass(ClassInfo clazz, Type superType) {
        String name = superType.name().toString();
        return name.equals("java.lang.Object")
                || (clazz.isEnum() && name.equals("java.lang.Enum"))
                || (clazz.isRecord() && name.equals("java.lang.Record"));
    }

    /**
     * Type usage text; unlike {@link Type#toString()}, type variables are printed without their bounds.
     */
    public static String type(Type type) {
        return switch (type.kind()) {
            case TYPE_VARIABLE -> type.asTypeVariable().identifier();
            case ARRAY ->
                type(type.asArrayType().elementType())
                        + "[]".repeat(type.asArrayType().deepDimensions());
            default -> type.toString();
        };
    }

    /**
     * Erased type name, e.g. {@code java.lang.String[]} or {@code java.util.List}.
     */
    public static String erasure(Type type) {
        if (type.kind() == Type.Kind.ARRAY) {
            return erasure(type.asArrayType().elementType())
                    + "[]".repeat(type.asArrayType().deepDimensions());
        }
        return type.name().toString();
    }

    public static String typeParameters(List<? extends Type> typeParameters) {
        if (typeParameters.isEmpty()) {
            return "";
        }
        return typeParameters.stream().map(Type::toString).collect(Collectors.joining(", ", "<", ">"));
    }

    public static String methodName(MethodInfo method) {
        if (method.isConstructor()) {
            return method.declaringClass().simpleName();
        }
        return method.name();
    }

    /**
     * Full method declaration, e.g. {@code public static <T> java.util.List<T> of(T... values) throws X}.
     */
    public static String methodDeclaration(MethodInfo method) {
        StringBuilder sb = new StringBuilder(methodModifiers(method));
        String typeParams = typeParameters(method.typeParameters());
        if (!typeParams.isEmpty()) {
            appendSpaced(sb, typeParams);
        }
        if (!method.isConstructor() && !method.isStaticInitializer()) {
            appendSpaced(sb, type(method.returnType()));
        }
        appendSpaced(sb, methodName(method));
        sb.append('(');
        for (int i = 0; i < method.parametersCount(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterType(method, i));
            String name = method.parameterName(i);
            if (name != null) {
                sb.append(' ').append(name);
            }
        }
        sb.append(')');
        if (method.defaultValue() != null) {
            sb.append(" default ").append(value(method.defaultValue()));
        }
        if (!method.exceptions().isEmpty()) {
            sb.append(" throws ")
                    .append(method.exceptions().stream().map(Format::type).collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }

    private static String parameterType(MethodInfo method, int position) {
        String type = type(method.parameterType(position));
        if ((method.flags() & ACC_VARARGS) != 0 && position == method.parametersCount() - 1 && type.endsWith("[]")) {
            return type.substring(0, type.length() - 2) + "...";
        }
        return type;
    }

    /**
     * Short method reference, e.g. {@code of(java.lang.Object[])}.
     */
    public static String methodReference(MethodInfo method) {
        return methodName(method)
                + method.parameterTypes().stream().map(Format::erasure).collect(Collectors.joining(",", "(", ")"));
    }

    public static String fieldDeclaration(FieldInfo field) {
        return join(fieldModifiers(field), type(field.type())) + " " + field.name();
    }

    public static String recordComponent(RecordComponentInfo component) {
        return type(component.type()) + " " + component.name();
    }

    public static String annotation(AnnotationInstance annotation) {
        StringBuilder sb = new StringBuilder("@").append(annotation.name());
        List<AnnotationValue> values = annotation.values();
        if (!values.isEmpty()) {
            sb.append('(');
            if (values.size() == 1 && values.get(0).name().equals("value")) {
                sb.append(value(values.get(0)));
            } else {
                sb.append(values.stream().map(v -> v.name() + " = " + value(v)).collect(Collectors.joining(", ")));
            }
            sb.append(')');
        }
        return sb.toString();
    }

    public static String value(AnnotationValue value) {
        return switch (value.kind()) {
            case STRING -> quote(value.asString(), '"');
            case CHARACTER -> quote(String.valueOf(value.asChar()), '\'');
            case LONG -> value.asLong() + "L";
            case FLOAT -> value.asFloat() + "f";
            case CLASS -> value.asClass().toString() + ".class";
            case ENUM -> value.asEnumType().local().replace('$', '.') + "." + value.asEnum();
            case NESTED -> annotation(value.asNested());
            case ARRAY -> value.asArrayList().stream().map(Format::value).collect(Collectors.joining(", ", "{", "}"));
            default -> String.valueOf(value.value());
        };
    }

    private static String quote(String text, char quote) {
        StringBuilder sb = new StringBuilder().append(quote);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c == quote) {
                        sb.append('\\');
                    }
                    sb.append(c);
                }
            }
        }
        return sb.append(quote).toString();
    }

    public static String targetKind(AnnotationTarget target) {
        return switch (target.kind()) {
            case CLASS -> "class";
            case FIELD -> "field";
            case METHOD -> "method";
            case METHOD_PARAMETER -> "parameter";
            case RECORD_COMPONENT -> "record-component";
            case TYPE -> "type-use";
        };
    }

    /**
     * Human-readable location of an annotation target, e.g. {@code a.B#foo(int)}.
     */
    public static String target(AnnotationTarget target) {
        if (target == null) {
            return "?";
        }
        return switch (target.kind()) {
            case CLASS -> target.asClass().name().toString();
            case FIELD ->
                target.asField().declaringClass().name() + "."
                        + target.asField().name();
            case METHOD -> target.asMethod().declaringClass().name() + "#" + methodReference(target.asMethod());
            case METHOD_PARAMETER -> {
                MethodParameterInfo parameter = target.asMethodParameter();
                String name = parameter.name() != null ? " " + parameter.name() : "";
                yield target(parameter.method()) + " parameter " + parameter.position() + name;
            }
            case RECORD_COMPONENT ->
                target.asRecordComponent().declaringClass().name() + "."
                        + target.asRecordComponent().name();
            case TYPE -> {
                TypeTarget typeTarget = target.asType();
                String where = switch (typeTarget.usage()) {
                    case EMPTY -> "in";
                    case CLASS_EXTENDS -> "in supertype of";
                    case METHOD_PARAMETER -> "in parameter of";
                    case TYPE_PARAMETER -> "in type parameter of";
                    case TYPE_PARAMETER_BOUND -> "in type parameter bound of";
                    case THROWS -> "in throws clause of";
                };
                String type = typeTarget.target() != null ? typeTarget.target() + " " : "";
                yield type + where + " " + target(typeTarget.enclosingTarget());
            }
        };
    }

    private static String join(String first, String second) {
        return first.isEmpty() ? second : first + " " + second;
    }

    private static void appendSpaced(StringBuilder sb, String text) {
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(text);
    }
}
