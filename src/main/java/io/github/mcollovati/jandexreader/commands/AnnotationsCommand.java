package io.github.mcollovati.jandexreader.commands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import io.github.mcollovati.jandexreader.source.LoadedIndex;
import io.github.mcollovati.jandexreader.support.Names;
import io.github.mcollovati.jandexreader.support.Table;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "annotations", mixinStandardHelpOptions = true,
        description = "Lists the annotation types used in the index, with usage counts per target kind.")
public class AnnotationsCommand extends IndexCommand {

    @Parameters(paramLabel = "<source>", arity = "0..*", description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(names = {"-f", "--filter"}, paramLabel = "<pattern>",
            description = "Only annotations whose name contains the text or matches the glob.")
    String filter;

    @Option(names = {"-s", "--sort-by-count"}, description = "Sort by usage count, most used first.")
    boolean sortByCount;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        IndexView index = composite(indexes);
        Pattern pattern = Names.filter(filter);
        Map<String, Map<AnnotationTarget.Kind, Integer>> usages = new TreeMap<>();
        for (ClassInfo clazz : index.getKnownClasses()) {
            for (List<AnnotationInstance> instances : clazz.annotationsMap().values()) {
                for (AnnotationInstance instance : instances) {
                    String name = instance.name().toString();
                    if (instance.target() == null || !Names.matches(pattern, name)) {
                        continue;
                    }
                    usages.computeIfAbsent(name, n -> new EnumMap<>(AnnotationTarget.Kind.class))
                            .merge(instance.target().kind(), 1, Integer::sum);
                }
            }
        }
        List<Map.Entry<String, Map<AnnotationTarget.Kind, Integer>>> entries = new ArrayList<>(usages.entrySet());
        if (sortByCount) {
            entries.sort(Comparator.comparing((Map.Entry<String, Map<AnnotationTarget.Kind, Integer>> e) -> total(e.getValue()))
                    .reversed());
        }
        if (json) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (var entry : entries) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("name", entry.getKey());
                map.put("count", total(entry.getValue()));
                Map<String, Integer> byTarget = new LinkedHashMap<>();
                entry.getValue().forEach((kind, count) -> byTarget.put(kindName(kind), count));
                map.put("targets", byTarget);
                result.add(map);
            }
            printJson(result);
        } else {
            Table table = new Table("COUNT", "ANNOTATION", "TARGETS");
            for (var entry : entries) {
                List<String> byTarget = new ArrayList<>();
                entry.getValue().forEach((kind, count) -> byTarget.add(kindName(kind) + "=" + count));
                table.row(total(entry.getValue()), entry.getKey(), String.join(" ", byTarget));
            }
            table.print(out());
        }
        return EXIT_OK;
    }

    private static int total(Map<AnnotationTarget.Kind, Integer> counts) {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static String kindName(AnnotationTarget.Kind kind) {
        return switch (kind) {
            case METHOD_PARAMETER -> "parameter";
            case TYPE -> "type-use";
            case RECORD_COMPONENT -> "record-component";
            default -> kind.name().toLowerCase();
        };
    }
}
