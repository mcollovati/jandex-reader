package io.github.mcollovati.jandexreader.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.github.mcollovati.jandexreader.source.LoadedIndex;
import io.github.mcollovati.jandexreader.support.ClassPrinter;
import io.github.mcollovati.jandexreader.support.Format;
import io.github.mcollovati.jandexreader.support.Members;
import io.github.mcollovati.jandexreader.support.Model;
import io.github.mcollovati.jandexreader.support.Names;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.FieldInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "fields", mixinStandardHelpOptions = true, description = "Lists the fields declared by a class.")
public class FieldsCommand extends IndexCommand {

    @Parameters(index = "0", paramLabel = "<class>", description = "Class name, fully qualified or simple.")
    String className;

    @Parameters(index = "1..*", paramLabel = "<source>", arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(names = {"-f", "--filter"}, paramLabel = "<pattern>",
            description = "Only fields whose name contains the text or matches the glob.")
    String filter;

    @Option(names = {"-a", "--annotated"}, description = "Only annotated fields.")
    boolean onlyAnnotated;

    @Option(names = {"-q", "--quiet"}, description = "Print one declaration per line, without annotations.")
    boolean quiet;

    @Option(names = "--synthetic", description = "Include synthetic fields.")
    boolean includeSynthetic;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        ClassInfo clazz = Names.requireClass(composite(indexes), className);
        Pattern pattern = Names.filter(filter);
        List<FieldInfo> fields = Members.fields(clazz, includeSynthetic).stream()
                .filter(f -> Names.matches(pattern, f.name()))
                .filter(f -> !onlyAnnotated || !f.annotations().isEmpty())
                .toList();
        if (json) {
            printJson(fields.stream().map(Model::field).toList());
        } else if (quiet) {
            fields.forEach(f -> out().println(Format.fieldDeclaration(f)));
        } else {
            ClassPrinter printer = new ClassPrinter(out(), includeSynthetic);
            fields.forEach(f -> printer.printField(f, ""));
        }
        return EXIT_OK;
    }
}
