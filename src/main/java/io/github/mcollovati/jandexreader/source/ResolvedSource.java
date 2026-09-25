package io.github.mcollovati.jandexreader.source;

import java.nio.file.Path;

/**
 * A source given on the command line, resolved to a local file or directory.
 *
 * @param label the source as the user wrote it (file path or Maven coordinates)
 * @param path  the local file or directory
 */
public record ResolvedSource(String label, Path path) {
}
