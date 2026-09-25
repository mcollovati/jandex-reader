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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.jandex.ClassInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
        name = "dump",
        mixinStandardHelpOptions = true,
        description = "Prints every indexed class with its annotations, fields and methods.")
public class DumpCommand extends IndexCommand {

    @Parameters(
            paramLabel = "<source>",
            arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(
            names = {"-f", "--filter"},
            paramLabel = "<pattern>",
            description = "Only classes whose name contains the text or matches the glob.")
    String filter;

    @Option(names = "--synthetic", description = "Include synthetic and bridge members.")
    boolean includeSynthetic;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        Pattern pattern = Names.filter(filter);
        List<ClassInfo> classes = composite(indexes).getKnownClasses().stream()
                .filter(c -> Names.matches(pattern, c.name().toString()))
                .sorted(Comparator.comparing(c -> c.name().toString()))
                .toList();
        if (json) {
            printJson(classes.stream()
                    .map(c -> Model.classDetail(c, includeSynthetic))
                    .toList());
            return EXIT_OK;
        }
        ClassPrinter printer = new ClassPrinter(out(), includeSynthetic);
        for (int i = 0; i < classes.size(); i++) {
            if (i > 0) {
                out().println();
            }
            printer.printClass(classes.get(i));
        }
        return EXIT_OK;
    }
}
