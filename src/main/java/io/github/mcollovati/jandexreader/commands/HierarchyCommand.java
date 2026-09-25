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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Base class for commands walking the type hierarchy of a class or interface.
 */
abstract class HierarchyCommand extends IndexCommand {

    @Parameters(
            index = "0",
            paramLabel = "<type>",
            description = "Class or interface name, fully qualified or simple. It does not need to be in the index.")
    String typeName;

    @Parameters(
            index = "1..*",
            paramLabel = "<source>",
            arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(
            names = {"-d", "--direct"},
            description = "Only direct descendants.")
    boolean direct;

    @Option(
            names = {"-l", "--long"},
            description = "Show the full declaration, not just the name.")
    boolean longFormat;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    protected abstract Collection<ClassInfo> find(IndexView index, DotName name);

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        IndexView index = composite(indexes);
        DotName name = Names.resolveClassName(index, typeName);
        Set<ClassInfo> found = new LinkedHashSet<>(find(index, name));
        List<ClassInfo> sorted = found.stream()
                .sorted(Comparator.comparing(c -> c.name().toString()))
                .toList();
        if (json) {
            printJson(sorted.stream().map(Model::classSummary).toList());
        } else {
            if (sorted.isEmpty()) {
                err().println("Nothing found for " + name);
            }
            sorted.forEach(c -> out().println(
                            longFormat ? Format.classDeclaration(c) : c.name().toString()));
        }
        return EXIT_OK;
    }
}
