package io.github.mcollovati.jandexreader.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "implementors", mixinStandardHelpOptions = true,
        description = "Lists the indexed classes implementing an interface.")
public class ImplementorsCommand extends HierarchyCommand {

    @Option(names = {"-i", "--interfaces"}, description = "Also list sub-interfaces.")
    boolean includeInterfaces;

    @Override
    protected Collection<ClassInfo> find(IndexView index, DotName name) {
        List<ClassInfo> result = new ArrayList<>(direct
                ? index.getKnownDirectImplementations(name)
                : index.getAllKnownImplementations(name));
        if (includeInterfaces) {
            result.addAll(direct ? index.getKnownDirectSubinterfaces(name) : index.getAllKnownSubinterfaces(name));
        }
        return result;
    }
}
