package io.github.mcollovati.jandexreader.commands;

import java.util.Collection;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import picocli.CommandLine.Command;

@Command(name = "subclasses", mixinStandardHelpOptions = true,
        description = "Lists the indexed subclasses of a class.")
public class SubclassesCommand extends HierarchyCommand {

    @Override
    protected Collection<ClassInfo> find(IndexView index, DotName name) {
        return direct ? index.getKnownDirectSubclasses(name) : index.getAllKnownSubclasses(name);
    }
}
