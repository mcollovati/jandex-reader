/*
 * Copyright 2026 Marco Collovati
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.mcollovati.jandexreader.source;

import io.github.mcollovati.jandexreader.ToolException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Maven artifact resolver: looks into the local repository first, then downloads the single
 * artifact (no transitive dependencies) from the configured remote repositories into a cache directory.
 */
public class MavenResolver {

    public static final String MAVEN_CENTRAL = "https://repo.maven.apache.org/maven2";

    private static final Pattern COORDINATES = Pattern.compile("[\\w.\\-]+(:[\\w.\\-]*){1,4}");
    private static final Pattern SNAPSHOT_VERSION =
            Pattern.compile("<snapshotVersion>(.*?)</snapshotVersion>", Pattern.DOTALL);

    private final Path localRepository;
    private final Path cacheDirectory;
    private final List<String> remoteRepositories;
    private final boolean offline;

    public MavenResolver(Path localRepository, Path cacheDirectory, List<String> remoteRepositories, boolean offline) {
        this.localRepository = localRepository;
        this.cacheDirectory = cacheDirectory;
        this.remoteRepositories = remoteRepositories.stream()
                .map(MavenResolver::stripTrailingSlash)
                .toList();
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
        Artifact artifact =
                version == null || version.isEmpty() || "LATEST".equals(version) || "RELEASE".equals(version)
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
        throw new ToolException(
                "Artifact " + artifact + " not found. Tried: " + localRepository + ", " + String.join(", ", tried));
    }

    private String latestRelease(Artifact artifact) {
        String metadataPath = artifact.groupId().replace('.', '/') + "/" + artifact.artifactId() + "/maven-metadata";
        List<Path> candidates = List.of(
                localRepository.resolve(metadataPath + "-local.xml"),
                localRepository.resolve(metadataPath + "-central.xml"));
        if (!offline) {
            for (String repository : remoteRepositories) {
                Optional<String> metadata = fetch(repository + "/" + metadataPath + ".xml");
                Optional<String> release = metadata.flatMap(xml -> firstText(xml, "release"))
                        .or(() -> metadata.flatMap(xml -> firstText(xml, "latest")));
                if (release.isPresent()) {
                    return release.get();
                }
            }
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    Optional<String> release = firstText(Files.readString(candidate), "release");
                    if (release.isPresent()) {
                        return release.get();
                    }
                } catch (IOException e) {
                    // ignore unreadable local metadata
                }
            }
        }
        throw new ToolException("Cannot determine the latest version of " + artifact.groupId() + ":"
                + artifact.artifactId() + "; please specify a version");
    }

    private Optional<String> snapshotVersion(String repository, Artifact artifact) {
        Optional<String> metadata = fetch(repository + "/" + artifact.directory() + "/maven-metadata.xml");
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        Matcher versions = SNAPSHOT_VERSION.matcher(metadata.get());
        while (versions.find()) {
            String element = versions.group(1);
            String extension = firstText(element, "extension").orElse("");
            String classifier = firstText(element, "classifier").orElse("");
            if (extension.equals(artifact.extension()) && classifier.equals(nullToEmpty(artifact.classifier()))) {
                return firstText(element, "value");
            }
        }
        Optional<String> timestamp = firstText(metadata.get(), "timestamp");
        Optional<String> buildNumber = firstText(metadata.get(), "buildNumber");
        if (timestamp.isPresent() && buildNumber.isPresent()) {
            return Optional.of(artifact.version().replace("SNAPSHOT", timestamp.get() + "-" + buildNumber.get()));
        }
        return Optional.empty();
    }

    private boolean download(String url, Path target) {
        try {
            HttpURLConnection connection = open(url);
            try {
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return false;
                }
                Files.createDirectories(target.getParent());
                Path temp = Files.createTempFile(
                        target.getParent(), target.getFileName().toString(), ".part");
                try (InputStream body = connection.getInputStream()) {
                    Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temp);
                }
                return true;
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new ToolException("Failed to download " + url + ": " + e, e);
        }
    }

    /**
     * Downloads a small text file, such as {@code maven-metadata.xml}; empty when it does not exist.
     */
    private Optional<String> fetch(String url) {
        try {
            HttpURLConnection connection = open(url);
            try {
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return Optional.empty();
                }
                try (InputStream body = connection.getInputStream()) {
                    return Optional.of(new String(body.readAllBytes(), StandardCharsets.UTF_8));
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException e) {
            throw new ToolException("Failed to download " + url + ": " + e, e);
        }
    }

    /**
     * Text of the first {@code <tag>} element. Maven metadata is simple, generated XML (no attributes
     * on these elements, no CDATA or entities in versions), so a full XML parser is not needed; this
     * keeps java.xml out of the native executable.
     */
    static Optional<String> firstText(String xml, String tag) {
        Matcher matcher =
                Pattern.compile("<" + tag + ">\\s*([^<]*?)\\s*</" + tag + ">").matcher(xml);
        return matcher.find() && !matcher.group(1).isEmpty() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    // HttpURLConnection (java.base) instead of java.net.http keeps the native executable smaller
    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "jandex-reader");
        return connection;
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
                default ->
                    throw new ToolException("Invalid Maven coordinates: " + coordinates
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
            return groupId + ":" + artifactId + ":" + extension + (classifier == null ? "" : ":" + classifier) + ":"
                    + version;
        }
    }
}
