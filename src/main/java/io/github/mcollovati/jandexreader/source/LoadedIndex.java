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

import org.jboss.jandex.IndexView;

/**
 * The outcome of looking for a Jandex index in a source.
 *
 * @param source          the source
 * @param index           the index, or {@code null} when the source has none
 * @param location        where the index was found (entry name inside an archive, or file path)
 * @param version         the index format version, or {@code null} when not read from a file
 * @param generated       {@code true} when the index was built on the fly from the source classes
 * @param archiveClasses  number of class files in the source, or -1 when not counted
 */
public record LoadedIndex(
        ResolvedSource source,
        IndexView index,
        String location,
        Integer version,
        boolean generated,
        int archiveClasses) {

    public boolean hasIndex() {
        return index != null;
    }

    public int indexedClasses() {
        return index == null ? 0 : index.getKnownClasses().size();
    }
}
