package io.github.mcollovati.jandexreader.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.mcollovati.jandexreader.source.LoadedIndex;
import io.github.mcollovati.jandexreader.support.Table;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "check", mixinStandardHelpOptions = true,
        description = "Tells whether each source contains a Jandex index. Exits with 1 if any source has none.")
public class CheckCommand extends IndexCommand {

    @Parameters(paramLabel = "<source>", arity = "0..*", description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(names = "--missing", description = "Only show sources without an index.")
    boolean onlyMissing;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected boolean skipMissingIndexes() {
        return false;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        List<LoadedIndex> shown = indexes.stream().filter(l -> !onlyMissing || !l.hasIndex()).toList();
        if (json) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (LoadedIndex loaded : shown) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("source", loaded.source().label());
                map.put("path", loaded.source().path().toAbsolutePath().toString());
                map.put("indexed", loaded.hasIndex() && !loaded.generated());
                map.put("generated", loaded.generated());
                map.put("location", loaded.location());
                map.put("version", loaded.version());
                map.put("indexedClasses", loaded.hasIndex() ? loaded.indexedClasses() : null);
                map.put("archiveClasses", loaded.archiveClasses() >= 0 ? loaded.archiveClasses() : null);
                result.add(map);
            }
            printJson(result);
        } else {
            Table table = new Table("SOURCE", "INDEX", "VERSION", "CLASSES", "LOCATION");
            for (LoadedIndex loaded : shown) {
                table.row(loaded.source().label(), status(loaded), loaded.version(), classes(loaded),
                        loaded.location());
            }
            table.print(out());
            long withIndex = indexes.stream().filter(l -> l.hasIndex() && !l.generated()).count();
            out().println();
            out().println(withIndex + " of " + indexes.size() + " source(s) have a Jandex index");
        }
        boolean anyMissing = indexes.stream().anyMatch(l -> !l.hasIndex() || l.generated());
        return anyMissing ? EXIT_MISSING_INDEX : EXIT_OK;
    }

    private static String status(LoadedIndex loaded) {
        if (loaded.generated()) {
            return "built";
        }
        return loaded.hasIndex() ? "yes" : "no";
    }

    /** Indexed classes over class files in the source, to spot stale or partial indexes. */
    private static String classes(LoadedIndex loaded) {
        String indexed = loaded.hasIndex() ? String.valueOf(loaded.indexedClasses()) : "-";
        return loaded.archiveClasses() >= 0 ? indexed + "/" + loaded.archiveClasses() : indexed;
    }
}
