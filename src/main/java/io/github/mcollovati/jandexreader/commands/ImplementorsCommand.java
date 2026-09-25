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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "implementors",
        mixinStandardHelpOptions = true,
        description = "Lists the indexed classes implementing an interface.")
public class ImplementorsCommand extends HierarchyCommand {

    @Option(
            names = {"-i", "--interfaces"},
            description = "Also list sub-interfaces.")
    boolean includeInterfaces;

    @Override
    protected Collection<ClassInfo> find(IndexView index, DotName name) {
        List<ClassInfo> result = new ArrayList<>(
                direct ? index.getKnownDirectImplementations(name) : index.getAllKnownImplementations(name));
        if (includeInterfaces) {
            result.addAll(direct ? index.getKnownDirectSubinterfaces(name) : index.getAllKnownSubinterfaces(name));
        }
        return result;
    }
}
