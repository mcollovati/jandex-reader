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
package io.github.mcollovati.jandexreader.commands;

import io.github.mcollovati.jandexreader.source.LoadedIndex;
import io.github.mcollovati.jandexreader.support.Format;
import io.github.mcollovati.jandexreader.support.Model;
import io.github.mcollovati.jandexreader.support.Names;
import io.github.mcollovati.jandexreader.support.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "annotated",
        mixinStandardHelpOptions = true,
        description = "Lists the elements (classes, methods, fields, parameters, ...) annotated with an annotation.")
public class AnnotatedCommand extends IndexCommand {

    public enum Target {
        CLASS,
        FIELD,
        METHOD,
        PARAMETER,
        RECORD_COMPONENT,
        TYPE_USE
    }

    @Parameters(
            index = "0",
            paramLabel = "<annotation>",
            description =
                    "Annotation name, fully qualified or simple (e.g. 'Singleton' or '@jakarta.inject.Singleton').")
    String annotationName;

    @Parameters(
            index = "1..*",
            paramLabel = "<source>",
            arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(
            names = {"-t", "--target"},
            paramLabel = "<target>",
            split = ",",
            converter = TargetConverter.class,
            description = "Only these target kinds: class, field, method, parameter, record-component, type-use.")
    List<Target> targets;

    @Option(
            names = {"-c", "--classes"},
            description = "Only print the distinct classes that contain an annotated element.")
    boolean classesOnly;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        IndexView index = composite(indexes);
        DotName annotation = Names.resolveAnnotationName(index, annotationName);
        List<AnnotationInstance> instances = index.getAnnotations(annotation).stream()
                .filter(a -> a.target() != null)
                .filter(a -> targets == null || targets.contains(target(a.target())))
                .sorted(Comparator.comparing((AnnotationInstance a) -> Format.target(a.target())))
                .toList();
        if (classesOnly) {
            TreeSet<String> classes = new TreeSet<>();
            instances.stream()
                    .map(a -> declaringClass(a.target()))
                    .filter(Objects::nonNull)
                    .forEach(classes::add);
            if (json) {
                printJson(classes);
            } else {
                classes.forEach(out()::println);
            }
        } else if (json) {
            printJson(instances.stream().map(Model::annotationUsage).toList());
        } else {
            Table table = new Table("KIND", "TARGET", "VALUES");
            for (AnnotationInstance instance : instances) {
                String text = Format.annotation(instance);
                String values = text.substring(1 + instance.name().toString().length());
                table.row(
                        Format.targetKind(instance.target()),
                        Format.target(instance.target()),
                        values.isEmpty() ? "" : values);
            }
            if (instances.isEmpty()) {
                err().println("No elements annotated with @" + annotation);
            } else {
                table.print(out());
            }
        }
        return EXIT_OK;
    }

    static class TargetConverter implements ITypeConverter<Target> {
        @Override
        public Target convert(String value) {
            return Target.valueOf(value.trim().toUpperCase().replace('-', '_'));
        }
    }

    private static Target target(AnnotationTarget target) {
        return Target.valueOf(Format.targetKind(target).toUpperCase().replace('-', '_'));
    }

    private static String declaringClass(AnnotationTarget target) {
        if (target == null) {
            return null;
        }
        return switch (target.kind()) {
            case CLASS -> target.asClass().name().toString();
            case FIELD -> target.asField().declaringClass().name().toString();
            case METHOD -> target.asMethod().declaringClass().name().toString();
            case METHOD_PARAMETER ->
                target.asMethodParameter().method().declaringClass().name().toString();
            case RECORD_COMPONENT ->
                target.asRecordComponent().declaringClass().name().toString();
            case TYPE -> declaringClass(target.asType().enclosingTarget());
        };
    }
}
