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
import org.jboss.jandex.MethodInfo;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "methods", mixinStandardHelpOptions = true, description = "Lists the methods declared by a class.")
public class MethodsCommand extends IndexCommand {

    @Parameters(index = "0", paramLabel = "<class>", description = "Class name, fully qualified or simple.")
    String className;

    @Parameters(index = "1..*", paramLabel = "<source>", arity = "0..*",
            description = "JAR, directory, .idx file or Maven coordinates.")
    List<String> sources = new ArrayList<>();

    @Option(names = {"-f", "--filter"}, paramLabel = "<pattern>",
            description = "Only methods whose name contains the text or matches the glob.")
    String filter;

    @Option(names = {"-a", "--annotated"}, description = "Only methods with annotations (on the method or its parameters).")
    boolean onlyAnnotated;

    @Option(names = {"-q", "--quiet"}, description = "Print one declaration per line, without annotations.")
    boolean quiet;

    @Option(names = "--synthetic", description = "Include synthetic and bridge methods.")
    boolean includeSynthetic;

    @Override
    protected List<String> sourceArguments() {
        return sources;
    }

    @Override
    protected int execute(List<LoadedIndex> indexes) throws Exception {
        ClassInfo clazz = Names.requireClass(composite(indexes), className);
        Pattern pattern = Names.filter(filter);
        List<MethodInfo> methods = Members.methods(clazz, includeSynthetic).stream()
                .filter(m -> Names.matches(pattern, m.name()))
                .filter(m -> !onlyAnnotated || !m.annotations().isEmpty())
                .toList();
        if (json) {
            printJson(methods.stream().map(Model::method).toList());
        } else if (quiet) {
            methods.forEach(m -> out().println(Format.methodDeclaration(m)));
        } else {
            ClassPrinter printer = new ClassPrinter(out(), includeSynthetic);
            methods.forEach(m -> printer.printMethod(m, ""));
        }
        return EXIT_OK;
    }
}
