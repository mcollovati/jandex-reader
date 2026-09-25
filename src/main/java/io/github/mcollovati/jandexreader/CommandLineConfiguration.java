package io.github.mcollovati.jandexreader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import picocli.CommandLine;

@ApplicationScoped
public class CommandLineConfiguration {

    @Produces
    CommandLine commandLine(PicocliCommandLineFactory factory) {
        return factory.create()
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setUsageHelpAutoWidth(true);
    }
}
