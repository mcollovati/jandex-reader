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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

/**
 * Builds JARs from the compiled fixture classes, with or without a Jandex index.
 */
final class FixtureJars {

    private static final String FIXTURES_PACKAGE = "io/github/mcollovati/jandexreader/fixtures";

    private FixtureJars() {}

    static Path create(Path directory, String name, boolean withIndex) throws IOException {
        Path classesRoot = Path.of("target", "test-classes");
        List<Path> classes;
        try (Stream<Path> files = Files.list(classesRoot.resolve(FIXTURES_PACKAGE))) {
            classes =
                    files.filter(p -> p.toString().endsWith(".class")).sorted().toList();
        }
        Path jar = directory.resolve(name);
        Indexer indexer = new Indexer();
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Path clazz : classes) {
                out.putNextEntry(
                        new JarEntry(classesRoot.relativize(clazz).toString().replace('\\', '/')));
                Files.copy(clazz, out);
                out.closeEntry();
                try (InputStream in = Files.newInputStream(clazz)) {
                    indexer.index(in);
                }
            }
            if (withIndex) {
                out.putNextEntry(new JarEntry("META-INF/jandex.idx"));
                new IndexWriter(new NonClosing(out)).write(indexer.complete());
                out.closeEntry();
            }
        }
        return jar;
    }

    /** IndexWriter does not close the stream, but keep the JAR stream safe regardless. */
    private static final class NonClosing extends OutputStream {
        private final OutputStream delegate;

        NonClosing(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void close() {}
    }
}
