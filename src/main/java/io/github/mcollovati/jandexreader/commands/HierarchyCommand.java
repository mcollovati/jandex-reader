package io.github.mcollovati.jandexreader.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.mcollovati.jandexreader.source.LoadedIndex;
import io.github.mcollovati.jandexreader.support.Format;
import io.github.mcollovati.jandexreader.support.Model;
import io.github.mcollovati.jandexreader.support.Names;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Base class for commands walking the type hierarchy of a class or interface.
 */
abstract class HierarchyCommand extends IndexCommand {

    @Parameters(index = "0", paramLabel = "<type>",
            description = "Class or interface name, fully qualified or simple. It does not need to be in the index.")
    String typeName;

    @Parameters(index = "1..*", paramLabel = "<source>", arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(names = {"-d", "--direct"}, description = "Only direct descendants.")
    boolean direct;

    @Option(names = {"-l", "--long"}, description = "Show the full declaration, not just the name.")
    boolean longFormat;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    protected abstract Collection<ClassInfo> find(IndexView index, DotName name);

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        IndexView index = composite(indexes);
        DotName name = Names.resolveClassName(index, typeName);
        Set<ClassInfo> found = new LinkedHashSet<>(find(index, name));
        List<ClassInfo> sorted = found.stream().sorted(Comparator.comparing(c -> c.name().toString())).toList();
        if (json) {
            printJson(sorted.stream().map(Model::classSummary).toList());
        } else {
            if (sorted.isEmpty()) {
                err().println("Nothing found for " + name);
            }
            sorted.forEach(c -> out().println(longFormat ? Format.classDeclaration(c) : c.name().toString()));
        }
        return EXIT_OK;
    }
}
