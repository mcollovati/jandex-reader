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

import java.util.Collection;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Command;

@Command(name = "subclasses", mixinStandardHelpOptions = true, description = "Lists the indexed subclasses of a class.")
public class SubclassesCommand extends HierarchyCommand {

    @Override
    protected Collection<ClassInfo> find(IndexView index, DotName name) {
        return direct ? index.getKnownDirectSubclasses(name) : index.getAllKnownSubclasses(name);
    }
}
