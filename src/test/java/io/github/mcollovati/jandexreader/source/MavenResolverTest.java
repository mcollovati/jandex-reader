package io.github.mcollovati.jandexreader.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.mcollovati.jandexreader.ToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenResolverTest {

    @Test
    void isCoordinates() {
        assertTrue(MavenResolver.isCoordinates("io.smallrye:jandex:3.6.0"));
        assertTrue(MavenResolver.isCoordinates("io.smallrye:jandex"));
        assertTrue(MavenResolver.isCoordinates("g:a:jar:sources:1.0"));
        assertFalse(MavenResolver.isCoordinates("target/app.jar"));
        assertFalse(MavenResolver.isCoordinates("app.jar"));
    }

    @Test
    void parseCoordinates() {
        MavenResolver.Artifact artifact = MavenResolver.Artifact.parse("g.h:a:war:tests:1.0");
        assertEquals("g/h/a/1.0/a-1.0-tests.war", artifact.relativePath("1.0"));

        MavenResolver.Artifact simple = MavenResolver.Artifact.parse("g:a:1.0");
        assertEquals("jar", simple.extension());
        assertNull(simple.classifier());
        assertEquals("g/a/1.0/a-1.0.jar", simple.relativePath("1.0"));

        assertThrows(ToolException.class, () -> MavenResolver.Artifact.parse("g:a:b:c:d:e"));
    }

    @Test
    void resolvesFromLocalRepository(@TempDir Path localRepo, @TempDir Path cache) throws Exception {
        Path jar = localRepo.resolve("com/acme/lib/1.2/lib-1.2.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "fake");
        MavenResolver resolver = new MavenResolver(localRepo, cache, List.of(), true);

        ResolvedSource source = resolver.resolve("com.acme:lib:1.2");
        assertEquals(jar, source.path());
        assertEquals("com.acme:lib:1.2", source.label());
    }

    @Test
    void offlineMissingArtifact_fails(@TempDir Path localRepo, @TempDir Path cache) {
        MavenResolver resolver = new MavenResolver(localRepo, cache, List.of(), true);
        ToolException e = assertThrows(ToolException.class, () -> resolver.resolve("com.acme:lib:1.2"));
        assertTrue(e.getMessage().contains("offline"), e.getMessage());
    }
}
