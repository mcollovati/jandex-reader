package io.github.mcollovati.jandexreader.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.mcollovati.jandexreader.ToolException;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.Indexer;

/**
 * Finds and reads the Jandex index of a source, optionally indexing its classes when no index exists.
 */
public class IndexLoader {

    /** Locations where build tools put the index, in lookup order. */
    public static final List<String> INDEX_LOCATIONS = List.of(
            "META-INF/jandex.idx",
            "WEB-INF/classes/META-INF/jandex.idx");

    private final boolean buildIfMissing;

    public IndexLoader(boolean buildIfMissing) {
        this.buildIfMissing = buildIfMissing;
    }

    public LoadedIndex load(ResolvedSource source) {
        Path path = source.path();
        try {
            if (Files.isDirectory(path)) {
                return loadDirectory(source);
            }
            if (path.getFileName().toString().endsWith(".idx")) {
                try (InputStream in = Files.newInputStream(path)) {
                    return read(source, in, path.toString(), -1);
                }
            }
            return loadArchive(source);
        } catch (IOException e) {
            throw new ToolException("Cannot read " + source.label() + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ToolException("Invalid Jandex index in " + source.label() + ": " + e.getMessage(), e);
        }
    }

    private LoadedIndex loadArchive(ResolvedSource source) throws IOException {
        try (ZipFile zip = new ZipFile(source.path().toFile())) {
            int classes = 0;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                if (isIndexableClass(entries.nextElement().getName())) {
                    classes++;
                }
            }
            for (String location : INDEX_LOCATIONS) {
                ZipEntry entry = zip.getEntry(location);
                if (entry != null) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        return read(source, in, location, classes);
                    }
                }
            }
            if (!buildIfMissing) {
                return new LoadedIndex(source, null, null, null, false, classes);
            }
            Indexer indexer = new Indexer();
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (isIndexableClass(entry.getName())) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        indexer.index(in);
                    }
                }
            }
            return new LoadedIndex(source, indexer.complete(), null, null, true, classes);
        } catch (java.util.zip.ZipException e) {
            throw new ToolException("Not a JAR/ZIP file or index file: " + source.label());
        }
    }

    private LoadedIndex loadDirectory(ResolvedSource source) throws IOException {
        Path root = source.path();
        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(root)) {
            classFiles = files.filter(Files::isRegularFile)
                    .filter(p -> isIndexableClass(root.relativize(p).toString().replace('\\', '/')))
                    .toList();
        }
        for (String location : INDEX_LOCATIONS) {
            Path index = root.resolve(location);
            if (Files.isRegularFile(index)) {
                try (InputStream in = Files.newInputStream(index)) {
                    return read(source, in, location, classFiles.size());
                }
            }
        }
        if (!buildIfMissing) {
            return new LoadedIndex(source, null, null, null, false, classFiles.size());
        }
        Indexer indexer = new Indexer();
        for (Path classFile : classFiles) {
            try (InputStream in = Files.newInputStream(classFile)) {
                indexer.index(in);
            }
        }
        return new LoadedIndex(source, indexer.complete(), null, null, true, classFiles.size());
    }

    private static LoadedIndex read(ResolvedSource source, InputStream in, String location, int archiveClasses)
            throws IOException {
        IndexReader reader = new IndexReader(in);
        Index index = reader.read();
        return new LoadedIndex(source, index, location, reader.getIndexVersion(), false, archiveClasses);
    }

    private static boolean isIndexableClass(String name) {
        return name.endsWith(".class")
                && !name.startsWith("META-INF/versions/")
                && !name.endsWith("module-info.class");
    }
}
