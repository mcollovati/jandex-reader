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
package io.github.mcollovati.jandexreader.source;

import io.github.mcollovati.jandexreader.ToolException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Turns command line sources (paths, Maven coordinates, class paths, POM files) into local files.
 */
public class SourceResolver {

    private final MavenResolver mavenResolver;

    public SourceResolver(MavenResolver mavenResolver) {
        this.mavenResolver = mavenResolver;
    }

    public ResolvedSource resolve(String source) {
        Path path = Path.of(source);
        if (Files.exists(path)) {
            return new ResolvedSource(source, path);
        }
        if (MavenResolver.isCoordinates(source)) {
            return mavenResolver.resolve(source);
        }
        throw new ToolException("Source not found: " + source);
    }

    /**
     * Resolves the entries of a class path string, skipping entries that do not exist.
     */
    public List<ResolvedSource> resolveClassPath(String classPath) {
        List<ResolvedSource> sources = new ArrayList<>();
        for (String entry : classPath.split(File.pathSeparator)) {
            entry = entry.trim();
            Path path = entry.isEmpty() ? null : Path.of(entry);
            if (path != null && Files.exists(path)) {
                // file names keep the output readable; JSON output still reports the full path
                String label =
                        Files.isDirectory(path) ? entry : path.getFileName().toString();
                sources.add(new ResolvedSource(label, path));
            }
        }
        return sources;
    }

    /**
     * Runs {@code mvn dependency:build-classpath} on the given POM and resolves the resulting class path.
     */
    public List<ResolvedSource> resolvePomDependencies(Path pom, String scope) {
        if (Files.isDirectory(pom)) {
            pom = pom.resolve("pom.xml");
        }
        if (!Files.isRegularFile(pom)) {
            throw new ToolException("POM file not found: " + pom);
        }
        pom = pom.toAbsolutePath();
        Path output = null;
        try {
            output = Files.createTempFile("jandex-reader-classpath", ".txt");
            List<String> command = new ArrayList<>();
            command.add(mavenExecutable(pom.getParent()));
            command.addAll(List.of(
                    "-q",
                    "-B",
                    "-f",
                    pom.toString(),
                    "dependency:build-classpath",
                    "-Dmdep.outputFile=" + output,
                    "-DincludeScope=" + scope));
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(10, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new ToolException("Timed out resolving dependencies of " + pom);
            }
            if (process.exitValue() != 0) {
                throw new ToolException("Maven failed to resolve dependencies of " + pom + ":\n" + log.strip());
            }
            return resolveClassPath(Files.readString(output).strip());
        } catch (IOException e) {
            throw new ToolException("Cannot run Maven to resolve dependencies of " + pom + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Interrupted while resolving dependencies of " + pom, e);
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private static String mavenExecutable(Path projectDir) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (Path dir = projectDir; dir != null; dir = dir.getParent()) {
            Path wrapper = dir.resolve(windows ? "mvnw.cmd" : "mvnw");
            if (Files.isExecutable(wrapper)) {
                return wrapper.toString();
            }
        }
        return windows ? "mvn.cmd" : "mvn";
    }
}
