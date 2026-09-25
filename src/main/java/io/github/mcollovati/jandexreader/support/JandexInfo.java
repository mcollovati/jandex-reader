package io.github.mcollovati.jandexreader.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.Indexer;

/**
 * Information about the bundled Jandex library.
 */
public final class JandexInfo {

    /** Upper bound for probing; well above any index format version released so far. */
    private static final int MAX_PROBED_VERSION = 64;

    private JandexInfo() {
    }

    /**
     * Index format versions the bundled Jandex can read. Jandex keeps the supported range in
     * package-private constants, so it is detected by writing and reading back an empty index in
     * every candidate version.
     */
    public static List<Integer> supportedFormatVersions() {
        Index empty = new Indexer().complete();
        List<Integer> versions = new ArrayList<>();
        for (int version = 1; version <= MAX_PROBED_VERSION; version++) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                new IndexWriter(out).write(empty, version);
                IndexReader reader = new IndexReader(new ByteArrayInputStream(out.toByteArray()));
                reader.read();
                if (reader.getIndexVersion() == version) {
                    versions.add(version);
                }
            } catch (Exception e) {
                // version not supported
            }
        }
        return versions;
    }

    /**
     * Formats versions as compact ranges, e.g. {@code 2-3, 6-13}.
     */
    public static String ranges(List<Integer> versions) {
        List<String> ranges = new ArrayList<>();
        int i = 0;
        while (i < versions.size()) {
            int start = versions.get(i);
            int end = start;
            while (i + 1 < versions.size() && versions.get(i + 1) == end + 1) {
                end = versions.get(++i);
            }
            ranges.add(start == end ? String.valueOf(start) : start + "-" + end);
            i++;
        }
        return String.join(", ", ranges);
    }
}
