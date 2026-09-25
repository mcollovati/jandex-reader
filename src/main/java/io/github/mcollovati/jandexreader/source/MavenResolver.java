package io.github.mcollovati.jandexreader.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import io.github.mcollovati.jandexreader.ToolException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Minimal Maven artifact resolver: looks into the local repository first, then downloads the single
 * artifact (no transitive dependencies) from the configured remote repositories into a cache directory.
 */
public class MavenResolver {

    public static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

    private static final Pattern COORDINATES = Pattern.compile("[\\w.\\-]+(:[\\w.\\-]*){1,4}");

    private final Path localRepository;
    private final Path cacheDirectory;
    private final List<String> remoteRepositories;
    private final boolean offline;
    private HttpClient httpClient;

    public MavenResolver(Path localRepository, Path cacheDirectory, List<String> remoteRepositories, boolean offline) {
        this.localRepository = localRepository;
        this.cacheDirectory = cacheDirectory;
        this.remoteRepositories = remoteRepositories.stream().map(MavenResolver::stripTrailingSlash).toList();
        this.offline = offline;
    }

    /**
     * Tells if the argument looks like Maven coordinates rather than a file path.
     */
    public static boolean isCoordinates(String value) {
        return COORDINATES.matcher(value).matches() && !value.contains("/") && !value.contains("\\");
    }

    /**
     * Resolves the artifact to a local file; the returned label carries the actual version.
     */
    public ResolvedSource resolve(String coordinates) {
        Artifact requested = Artifact.parse(coordinates);
        String version = requested.version();
        Artifact artifact = version == null || version.isEmpty() || "LATEST".equals(version) || "RELEASE".equals(version)
                ? requested.withVersion(latestRelease(requested))
                : requested;
        String label = artifact.version().equals(version) ? coordinates : coordinates + " (" + artifact.version() + ")";
        return new ResolvedSource(label, resolveArtifact(artifact));
    }

    private Path resolveArtifact(Artifact artifact) {
        Path local = localRepository.resolve(artifact.relativePath(artifact.version()));
        if (Files.isRegularFile(local)) {
            return local;
        }
        Path cached = cacheDirectory.resolve(artifact.relativePath(artifact.version()));
        if (Files.isRegularFile(cached)) {
            return cached;
        }
        if (offline) {
            throw new ToolException("Artifact " + artifact + " not found in " + localRepository + " (offline mode)");
        }
        List<String> tried = new ArrayList<>();
        for (String repository : remoteRepositories) {
            String fileVersion = artifact.version();
            if (artifact.version().endsWith("-SNAPSHOT")) {
                Optional<String> snapshot = snapshotVersion(repository, artifact);
                if (snapshot.isEmpty()) {
                    tried.add(repository + " (no snapshot metadata)");
                    continue;
                }
                fileVersion = snapshot.get();
            }
            String url = repository + "/" + artifact.directory() + "/" + artifact.fileName(fileVersion);
            if (download(url, cached)) {
                return cached;
            }
            tried.add(url);
        }
        throw new ToolException("Artifact " + artifact + " not found. Tried: " + localRepository + ", "
                + String.join(", ", tried));
    }

    private String latestRelease(Artifact artifact) {
        String metadataPath = artifact.groupId().replace('.', '/') + "/" + artifact.artifactId() + "/maven-metadata";
        List<Path> candidates = List.of(
                localRepository.resolve(metadataPath + "-local.xml"),
                localRepository.resolve(metadataPath + "-central.xml"));
        if (!offline) {
            for (String repository : remoteRepositories) {
                Optional<Document> document = fetchXml(repository + "/" + metadataPath + ".xml");
                Optional<String> release = document.flatMap(d -> firstText(d.getDocumentElement(), "release"))
                        .or(() -> document.flatMap(d -> firstText(d.getDocumentElement(), "latest")));
                if (release.isPresent()) {
                    return release.get();
                }
            }
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try (InputStream in = Files.newInputStream(candidate)) {
                    Optional<String> release = firstText(parse(in).getDocumentElement(), "release");
                    if (release.isPresent()) {
                        return release.get();
                    }
                } catch (Exception e) {
                    // ignore unreadable local metadata
                }
            }
        }
        throw new ToolException("Cannot determine the latest version of " + artifact.groupId() + ":"
                + artifact.artifactId() + "; please specify a version");
    }

    private Optional<String> snapshotVersion(String repository, Artifact artifact) {
        Optional<Document> document = fetchXml(repository + "/" + artifact.directory() + "/maven-metadata.xml");
        if (document.isEmpty()) {
            return Optional.empty();
        }
        NodeList versions = document.get().getElementsByTagName("snapshotVersion");
        for (int i = 0; i < versions.getLength(); i++) {
            Element element = (Element) versions.item(i);
            String extension = firstText(element, "extension").orElse("");
            String classifier = firstText(element, "classifier").orElse("");
            if (extension.equals(artifact.extension()) && classifier.equals(nullToEmpty(artifact.classifier()))) {
                return firstText(element, "value");
            }
        }
        Element root = document.get().getDocumentElement();
        Optional<String> timestamp = firstText(root, "timestamp");
        Optional<String> buildNumber = firstText(root, "buildNumber");
        if (timestamp.isPresent() && buildNumber.isPresent()) {
            return Optional.of(artifact.version().replace("SNAPSHOT", timestamp.get() + "-" + buildNumber.get()));
        }
        return Optional.empty();
    }

    private boolean download(String url, Path target) {
        try {
            HttpResponse<InputStream> response = client().send(request(url), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    return false;
                }
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
                try {
                    Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temp);
                }
                return true;
            }
        } catch (IOException e) {
            throw new ToolException("Failed to download " + url + ": " + e, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Interrupted while downloading " + url, e);
        }
    }

    private Optional<Document> fetchXml(String url) {
        try {
            HttpResponse<InputStream> response = client().send(request(url), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    return Optional.empty();
                }
                return Optional.of(parse(body));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Interrupted while downloading " + url, e);
        } catch (Exception e) {
            throw new ToolException("Failed to download " + url + ": " + e, e);
        }
    }

    private static Document parse(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(in);
    }

    private static Optional<String> firstText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return Optional.empty();
        }
        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.trim());
    }

    private HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "jandex-reader")
                .GET()
                .build();
    }

    private HttpClient client() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20))
                    .build();
        }
        return httpClient;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Returns the local repository configured in {@code ~/.m2/settings.xml}, falling back to
     * {@code ~/.m2/repository}. The {@code maven.repo.local} system property wins over both.
     */
    public static Path defaultLocalRepository() {
        String property = System.getProperty("maven.repo.local");
        if (property != null && !property.isBlank()) {
            return Path.of(property);
        }
        Path m2 = Path.of(System.getProperty("user.home"), ".m2");
        Path settings = m2.resolve("settings.xml");
        if (Files.isRegularFile(settings)) {
            try {
                Matcher matcher = Pattern.compile("<localRepository>\\s*([^<]+?)\\s*</localRepository>")
                        .matcher(Files.readString(settings));
                if (matcher.find()) {
                    String value = matcher.group(1).replace("${user.home}", System.getProperty("user.home"));
                    return Path.of(value);
                }
            } catch (IOException e) {
                // fall back to the default location
            }
        }
        return m2.resolve("repository");
    }

    public static Path defaultCacheDirectory() {
        String xdg = System.getenv("XDG_CACHE_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(System.getProperty("user.home"), ".cache");
        return base.resolve("jandex-reader").resolve("repository");
    }

    record Artifact(String groupId, String artifactId, String extension, String classifier, String version) {

        /**
         * Parses {@code groupId:artifactId[:extension[:classifier]]:version} or {@code groupId:artifactId}.
         */
        static Artifact parse(String coordinates) {
            String[] parts = coordinates.split(":", -1);
            return switch (parts.length) {
                case 2 -> new Artifact(parts[0], parts[1], "jar", null, null);
                case 3 -> new Artifact(parts[0], parts[1], "jar", null, parts[2]);
                case 4 -> new Artifact(parts[0], parts[1], parts[2], null, parts[3]);
                case 5 -> new Artifact(parts[0], parts[1], parts[2], parts[3].isEmpty() ? null : parts[3], parts[4]);
                default -> throw new ToolException("Invalid Maven coordinates: " + coordinates
                        + " (expected groupId:artifactId[:extension[:classifier]]:version)");
            };
        }

        Artifact withVersion(String newVersion) {
            return new Artifact(groupId, artifactId, extension, classifier, newVersion);
        }

        String directory() {
            return groupId.replace('.', '/') + "/" + artifactId + "/" + version;
        }

        String fileName(String fileVersion) {
            return artifactId + "-" + fileVersion + (classifier == null ? "" : "-" + classifier) + "." + extension;
        }

        String relativePath(String fileVersion) {
            return directory() + "/" + fileName(fileVersion);
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + extension + (classifier == null ? "" : ":" + classifier)
                    + ":" + version;
        }
    }
}
