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
import io.github.mcollovati.jandexreader.support.ClassPrinter;
import io.github.mcollovati.jandexreader.support.Model;
import io.github.mcollovati.jandexreader.support.Names;
import java.util.List;
import java.util.Map;
import org.jboss.jandex.ClassInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "class",
        mixinStandardHelpOptions = true,
        description = "Shows a class: declaration, annotations, fields and methods.")
public class ClassCommand extends IndexCommand {

    @Parameters(
            index = "0",
            paramLabel = "<class>",
            description = "Class name, fully qualified or simple (e.g. 'Outer.Inner').")
    String className;

    @Parameters(
            index = "1..*",
            paramLabel = "<source>",
            arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new java.util.ArrayList<>();

    @Option(names = "--synthetic", description = "Include synthetic and bridge members.")
    boolean includeSynthetic;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        ClassInfo clazz = Names.requireClass(composite(indexes), className);
        String foundIn = indexes.stream()
                .filter(l -> l.index().getClassByName(clazz.name()) != null)
                .map(l -> l.source().label())
                .findFirst()
                .orElse(null);
        if (json) {
            Map<String, Object> map = Model.classDetail(clazz, includeSynthetic);
            map.put("source", foundIn);
            printJson(map);
        } else {
            if (indexes.size() > 1) {
                out().println("// from " + foundIn);
            }
            new ClassPrinter(out(), includeSynthetic).printClass(clazz);
        }
        return EXIT_OK;
    }
}
