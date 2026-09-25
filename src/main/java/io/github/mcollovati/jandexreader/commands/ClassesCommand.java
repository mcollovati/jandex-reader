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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jboss.jandex.ClassInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "classes", mixinStandardHelpOptions = true, description = "Lists the classes in the index.")
public class ClassesCommand extends IndexCommand {

    public enum Kind {
        CLASS,
        INTERFACE,
        ENUM,
        RECORD,
        ANNOTATION
    }

    @Parameters(
            paramLabel = "<source>",
            arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(
            names = {"-f", "--filter"},
            paramLabel = "<pattern>",
            description = "Only classes whose name contains the text, or matches the glob (e.g. 'com.acme.*Service').")
    String filter;

    @Option(
            names = {"-k", "--kind"},
            paramLabel = "<kind>",
            split = ",",
            description = "Only these kinds: class, interface, enum, record, annotation.")
    List<Kind> kinds;

    @Option(
            names = {"-l", "--long"},
            description = "Show modifiers and kind, not just the name.")
    boolean longFormat;

    @Option(names = "--no-nested", description = "Exclude nested, local and anonymous classes.")
    boolean noNested;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        Pattern pattern = Names.filter(filter);
        List<Map<String, Object>> result = new ArrayList<>();
        for (LoadedIndex loaded : indexes) {
            List<ClassInfo> classes = loaded.index().getKnownClasses().stream()
                    .filter(c -> Names.matches(pattern, c.name().toString()))
                    .filter(c -> kinds == null
                            || kinds.stream().anyMatch(k -> k.name().equalsIgnoreCase(Format.kind(c))))
                    .filter(c -> !noNested || c.nestingType() == ClassInfo.NestingType.TOP_LEVEL)
                    .sorted(Comparator.comparing(c -> c.name().toString()))
                    .toList();
            if (json) {
                for (ClassInfo clazz : classes) {
                    Map<String, Object> map = Model.classSummary(clazz);
                    map.put("source", loaded.source().label());
                    result.add(map);
                }
                continue;
            }
            if (indexes.size() > 1 && !classes.isEmpty()) {
                out().println("# " + loaded.source().label());
            }
            for (ClassInfo clazz : classes) {
                out().println(
                                longFormat
                                        ? Format.classDeclaration(clazz)
                                        : clazz.name().toString());
            }
        }
        if (json) {
            printJson(result);
        }
        return EXIT_OK;
    }
}
