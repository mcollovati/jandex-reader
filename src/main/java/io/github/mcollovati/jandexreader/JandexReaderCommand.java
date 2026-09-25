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
package io.github.mcollovati.jandexreader;

import io.github.mcollovati.jandexreader.commands.AnnotatedCommand;
import io.github.mcollovati.jandexreader.commands.AnnotationsCommand;
import io.github.mcollovati.jandexreader.commands.CheckCommand;
import io.github.mcollovati.jandexreader.commands.ClassCommand;
import io.github.mcollovati.jandexreader.commands.ClassesCommand;
import io.github.mcollovati.jandexreader.commands.DumpCommand;
import io.github.mcollovati.jandexreader.commands.FieldsCommand;
import io.github.mcollovati.jandexreader.commands.ImplementorsCommand;
import io.github.mcollovati.jandexreader.commands.MethodsCommand;
import io.github.mcollovati.jandexreader.commands.SubclassesCommand;
import io.github.mcollovati.jandexreader.support.JandexInfo;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import java.util.List;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;

@TopCommand
@Command(
        name = "jandex-reader",
        mixinStandardHelpOptions = true,
        versionProvider = JandexReaderCommand.VersionProvider.class,
        description =
                "Inspects Jandex indexes (META-INF/jandex.idx) in JAR files, directories, .idx files and Maven artifacts.",
        footer = {
            "",
            "Sources can be:",
            "  - a JAR/WAR/ZIP file or an exploded directory",
            "  - a Jandex index file (*.idx)",
            "  - Maven coordinates: groupId:artifactId[:extension[:classifier]]:version",
            "    (version can be omitted or set to LATEST to use the latest release)",
            "",
            "Exit codes: 0 = success, 1 = 'check' found sources without an index, 2 = error"
        },
        subcommands = {
            CheckCommand.class,
            ClassesCommand.class,
            ClassCommand.class,
            MethodsCommand.class,
            FieldsCommand.class,
            AnnotatedCommand.class,
            AnnotationsCommand.class,
            SubclassesCommand.class,
            ImplementorsCommand.class,
            DumpCommand.class
        })
public class JandexReaderCommand {

    public static class VersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() {
            Config config = ConfigProvider.getConfig();
            String version = config.getOptionalValue("quarkus.application.version", String.class)
                    .orElse("unknown");
            String jandexVersion = config.getOptionalValue("jandex-reader.jandex-version", String.class)
                    .orElse("unknown");
            List<Integer> formats = JandexInfo.supportedFormatVersions();
            return new String[] {
                "jandex-reader " + version,
                "Jandex " + jandexVersion,
                "Supported index format versions: " + JandexInfo.ranges(formats)
                        + (formats.isEmpty() ? "" : " (latest: " + formats.get(formats.size() - 1) + ")")
            };
        }
    }
}
