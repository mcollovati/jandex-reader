package io.github.mcollovati.jandexreader.commands;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.mcollovati.jandexreader.ToolException;
import io.github.mcollovati.jandexreader.source.IndexLoader;
import io.github.mcollovati.jandexreader.source.LoadedIndex;
import io.github.mcollovati.jandexreader.source.MavenResolver;
import io.github.mcollovati.jandexreader.source.ResolvedSource;
import io.github.mcollovati.jandexreader.source.SourceResolver;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Base class for commands working on the indexes of one or more sources.
 */
public abstract class IndexCommand implements Callable<Integer> {

    public static final int EXIT_OK = 0;
    public static final int EXIT_MISSING_INDEX = 1;
    public static final int EXIT_ERROR = 2;

    @Spec
    CommandSpec spec;

    @Option(names = {"--cp", "--classpath"}, paramLabel = "<path>",
            description = "Class path whose entries are added as sources (e.g. output of mvn dependency:build-classpath).")
    String classPath;

    @Option(names = "--pom", paramLabel = "<pom.xml>",
            description = "Adds the dependencies of a Maven project as sources (runs mvn dependency:build-classpath).")
    Path pom;

    @Option(names = "--scope", defaultValue = "runtime", paramLabel = "<scope>",
            description = "Dependency scope used with --pom (compile, runtime, test, provided, system). Default: ${DEFAULT-VALUE}.")
    String scope;

    @Option(names = {"-b", "--build-index"},
            description = "Index the classes of sources that have no Jandex index, instead of skipping them.")
    boolean buildIndex;

    @Option(names = "--local-repo", paramLabel = "<dir>",
            description = "Maven local repository. Default: from ~/.m2/settings.xml or ~/.m2/repository.")
    Path localRepository;

    @Option(names = {"-r", "--repo"}, paramLabel = "<url>",
            description = "Remote Maven repository used to download artifacts (repeatable). Default: Maven Central.")
    List<String> repositories;

    @Option(names = "--offline", description = "Do not download artifacts.")
    boolean offline;

    @Option(names = "--json", description = "Print the result as JSON.")
    boolean json;

    /** Sources given as positional parameters. */
    protected abstract List<String> sourceArguments();

    /** Runs the command on the loaded indexes. */
    protected abstract int execute(List<LoadedIndex> indexes) throws Exception;

    /** Commands that report on missing indexes themselves return {@code false}. */
    protected boolean skipMissingIndexes() {
        return true;
    }

    @Override
    public final Integer call() {
        try {
            List<LoadedIndex> loaded = loadIndexes();
            if (skipMissingIndexes()) {
                List<LoadedIndex> missing = loaded.stream().filter(l -> !l.hasIndex()).toList();
                loaded = loaded.stream().filter(LoadedIndex::hasIndex).toList();
                if (loaded.isEmpty()) {
                    String which = missing.size() == 1 ? missing.get(0).source().label() : "any of the sources";
                    throw new ToolException("No Jandex index found in " + which
                            + " (use --build-index to index the classes anyway)");
                }
                if (!missing.isEmpty()) {
                    err().println("warning: skipped " + missing.size() + " source(s) without a Jandex index"
                            + " (use --build-index to include them, or 'check' to list them)");
                }
            }
            int exitCode = execute(loaded);
            out().flush();
            return exitCode;
        } catch (ToolException e) {
            out().flush();
            err().println("error: " + e.getMessage());
            return EXIT_ERROR;
        } catch (Exception e) {
            out().flush();
            err().println("error: " + e);
            return EXIT_ERROR;
        }
    }

    private List<LoadedIndex> loadIndexes() {
        MavenResolver mavenResolver = new MavenResolver(
                localRepository != null ? localRepository : MavenResolver.defaultLocalRepository(),
                MavenResolver.defaultCacheDirectory(),
                repositories != null && !repositories.isEmpty() ? repositories : List.of(MavenResolver.MAVEN_CENTRAL),
                offline);
        SourceResolver sourceResolver = new SourceResolver(mavenResolver);
        List<ResolvedSource> sources = new ArrayList<>();
        for (String argument : sourceArguments()) {
            sources.add(sourceResolver.resolve(argument));
        }
        if (classPath != null) {
            sources.addAll(sourceResolver.resolveClassPath(classPath));
        }
        if (pom != null) {
            sources.addAll(sourceResolver.resolvePomDependencies(pom, scope));
        }
        if (sources.isEmpty()) {
            throw new ToolException("No sources given");
        }
        IndexLoader loader = new IndexLoader(buildIndex);
        return sources.stream().map(loader::load).toList();
    }

    protected static IndexView composite(List<LoadedIndex> indexes) {
        if (indexes.size() == 1) {
            return indexes.get(0).index();
        }
        return CompositeIndex.create(indexes.stream().map(LoadedIndex::index).toList());
    }

    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    protected void printJson(Object value) throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        out().println(mapper.writeValueAsString(value));
    }
}
