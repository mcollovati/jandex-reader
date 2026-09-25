package io.github.mcollovati.jandexreader.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.sun.net.httpserver.HttpServer;
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
    void firstText() {
        String xml = "<metadata><versioning>\n  <latest>2.0.0-rc1</latest>\n  <release> 1.9.0 </release>\n"
                + "  <empty></empty></versioning></metadata>";
        assertEquals(Optional.of("1.9.0"), MavenResolver.firstText(xml, "release"));
        assertEquals(Optional.of("2.0.0-rc1"), MavenResolver.firstText(xml, "latest"));
        assertEquals(Optional.empty(), MavenResolver.firstText(xml, "empty"));
        assertEquals(Optional.empty(), MavenResolver.firstText(xml, "missing"));
    }

    @Test
    void downloadsLatestReleaseAndSnapshots(@TempDir Path localRepo, @TempDir Path cache) throws Exception {
        Map<String, String> files = Map.of(
                "/com/acme/lib/maven-metadata.xml",
                "<metadata><versioning><latest>2.0-SNAPSHOT</latest><release>1.2</release></versioning></metadata>",
                "/com/acme/lib/1.2/lib-1.2.jar", "release jar",
                "/com/acme/lib/2.0-SNAPSHOT/maven-metadata.xml", """
                        <metadata><versioning>
                          <snapshot><timestamp>20260101.101010</timestamp><buildNumber>7</buildNumber></snapshot>
                          <snapshotVersions>
                            <snapshotVersion><classifier>sources</classifier><extension>jar</extension>
                              <value>2.0-20260101.101010-6</value></snapshotVersion>
                            <snapshotVersion><extension>jar</extension><value>2.0-20260101.101010-7</value></snapshotVersion>
                          </snapshotVersions>
                        </versioning></metadata>""",
                "/com/acme/lib/2.0-SNAPSHOT/lib-2.0-20260101.101010-7.jar", "snapshot jar");
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = files.get(exchange.getRequestURI().getPath());
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            String repository = "http://localhost:" + server.getAddress().getPort() + "/";
            MavenResolver resolver = new MavenResolver(localRepo, cache, List.of(repository), false);

            ResolvedSource latest = resolver.resolve("com.acme:lib");
            assertEquals("com.acme:lib (1.2)", latest.label());
            assertEquals(cache.resolve("com/acme/lib/1.2/lib-1.2.jar"), latest.path());
            assertEquals("release jar", Files.readString(latest.path()));

            ResolvedSource snapshot = resolver.resolve("com.acme:lib:2.0-SNAPSHOT");
            assertEquals("snapshot jar", Files.readString(snapshot.path()));

            ToolException missing = assertThrows(ToolException.class, () -> resolver.resolve("com.acme:other:1.0"));
            assertTrue(missing.getMessage().contains("not found"), missing.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void offlineMissingArtifact_fails(@TempDir Path localRepo, @TempDir Path cache) {
        MavenResolver resolver = new MavenResolver(localRepo, cache, List.of(), true);
        ToolException e = assertThrows(ToolException.class, () -> resolver.resolve("com.acme:lib:1.2"));
        assertTrue(e.getMessage().contains("offline"), e.getMessage());
    }
}
