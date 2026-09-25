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
public record LoadedIndex(ResolvedSource source, IndexView index, String location, Integer version,
                          boolean generated, int archiveClasses) {

    public boolean hasIndex() {
        return index != null;
    }

    public int indexedClasses() {
        return index == null ? 0 : index.getKnownClasses().size();
    }
}
