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
package io.github.mcollovati.jandexreader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusMainTest
class JandexReaderTest {

    private static final String PKG = "io.github.mcollovati.jandexreader.fixtures.Fixtures$";

    static Path indexedJar;
    static Path plainJar;

    /** Standard output with line endings normalized, so assertions also hold on Windows. */
    private static String stdout(LaunchResult result) {
        return result.getOutput().replace("\r\n", "\n");
    }

    @BeforeAll
    static void createJars() throws Exception {
        Path dir = Files.createTempDirectory("jandex-reader-test");
        indexedJar = FixtureJars.create(dir, "indexed.jar", true);
        plainJar = FixtureJars.create(dir, "plain.jar", false);
    }

    @Test
    void check_reportsIndexPresence(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("check", indexedJar.toString(), plainJar.toString());
        assertEquals(1, result.exitCode(), result.getErrorOutput());
        String output = stdout(result);
        assertTrue(output.matches("(?s).*indexed\\.jar\\s+yes\\s+\\d+\\s+8/8\\s+META-INF/jandex\\.idx.*"), output);
        assertTrue(output.matches("(?s).*plain\\.jar\\s+no\\s+-\\s+-/8.*"), output);
        assertTrue(output.contains("1 of 2 source(s) have a Jandex index"), output);
    }

    @Test
    void check_allIndexed_exitsZero(QuarkusMainLauncher launcher) {
        assertEquals(0, launcher.launch("check", indexedJar.toString()).exitCode());
    }

    @Test
    void classes_filteredByKind(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("classes", "-k", "record,enum", indexedJar.toString());
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        assertEquals(PKG + "Color\n" + PKG + "Point", stdout(result).strip());
    }

    @Test
    void classes_globFilter(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("classes", "-f", "*Service", indexedJar.toString());
        assertEquals(PKG + "Service\n" + PKG + "SpecialService", stdout(result).strip());
    }

    @Test
    void class_showsDeclarationAndMembers(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("class", "Service", indexedJar.toString());
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        String output = stdout(result);
        assertTrue(
                output.contains(
                        "@io.github.mcollovati.jandexreader.fixtures.Fixtures$Marker(codes = {1, 2}, value = \"svc\")"),
                output);
        assertTrue(output.contains("public static class " + PKG + "Service implements " + PKG + "Greeter {"), output);
        assertTrue(output.contains("private java.util.List<java.lang.@NotNull String> names"), output);
        assertTrue(
                output.contains(
                        "protected <T extends java.lang.Number> T first(java.util.List<T> items, java.lang.String... rest)"),
                output);
    }

    @Test
    void class_unknownName_fails(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("class", "Missing", indexedJar.toString());
        assertEquals(2, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Class not found in index: Missing"), result.getErrorOutput());
    }

    @Test
    void methods_quiet(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("methods", "-q", "Greeter", indexedJar.toString());
        assertEquals(
                "public java.lang.String greet(java.lang.String name)",
                stdout(result).strip());
    }

    @Test
    void fields_ofEnum(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("fields", "-q", "-f", "RED", "Fixtures.Color", indexedJar.toString());
        assertEquals("public static final " + PKG + "Color RED", stdout(result).strip());
    }

    @Test
    void annotated_listsAllTargetKinds(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("annotated", "Marker", indexedJar.toString());
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        String output = stdout(result);
        assertTrue(
                output.contains("parameter         " + PKG + "Greeter#greet(java.lang.String) parameter 0 name"),
                output);
        assertTrue(output.contains("record-component  " + PKG + "Point.x"), output);
        assertTrue(
                output.contains("method            " + PKG + "Service#first(java.util.List,java.lang.String[])"),
                output);
    }

    @Test
    void annotated_classesOnly_withTargetFilter(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("annotated", "-c", "-t", "class,field", "@Marker", indexedJar.toString());
        assertEquals(PKG + "Point\n" + PKG + "Service", stdout(result).strip());
    }

    @Test
    void annotated_typeUse(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("annotated", "NotNull", indexedJar.toString());
        assertTrue(
                stdout(result).contains("type-use  java.lang.@NotNull String in " + PKG + "Service.names"),
                stdout(result));
    }

    @Test
    void annotations_countsPerTarget(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("annotations", "-f", "*Marker", indexedJar.toString());
        assertTrue(
                stdout(result)
                        .matches("(?s).*8\\s+" + PKG.replace("$", "\\$")
                                + "Marker\\s+class=1 field=2 method=2 parameter=2 record-component=1.*"),
                stdout(result));
    }

    @Test
    void subclassesAndImplementors(QuarkusMainLauncher launcher) {
        assertEquals(
                PKG + "SpecialService",
                stdout(launcher.launch("subclasses", "Service", indexedJar.toString()))
                        .strip());
        assertEquals(
                PKG + "Service",
                stdout(launcher.launch("implementors", "--direct", "Greeter", indexedJar.toString()))
                        .strip());
        assertEquals(
                PKG + "Service\n" + PKG + "SpecialService",
                stdout(launcher.launch("implementors", "Greeter", indexedJar.toString()))
                        .strip());
    }

    @Test
    void missingIndex_failsUnlessBuildRequested(QuarkusMainLauncher launcher) {
        LaunchResult failed = launcher.launch("classes", plainJar.toString());
        assertEquals(2, failed.exitCode());
        assertTrue(failed.getErrorOutput().contains("No Jandex index found"), failed.getErrorOutput());

        LaunchResult built = launcher.launch("classes", "--build-index", "-f", "Point", plainJar.toString());
        assertEquals(0, built.exitCode(), built.getErrorOutput());
        assertEquals(PKG + "Point", stdout(built).strip());
    }

    @Test
    void multipleSources_skipMissingWithWarning(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("subclasses", "Service", indexedJar.toString(), plainJar.toString());
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        assertTrue(
                result.getErrorOutput().contains("skipped 1 source(s) without a Jandex index"),
                result.getErrorOutput());
    }

    @Test
    void json_classDetail(QuarkusMainLauncher launcher) throws Exception {
        LaunchResult result = launcher.launch("class", "--json", "Point", indexedJar.toString());
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        JsonNode json = new ObjectMapper().readTree(stdout(result));
        assertEquals("record", json.get("kind").asText());
        assertEquals("x", json.get("recordComponents").get(0).get("name").asText());
        assertFalse(json.get("methods").isEmpty());
    }

    @Test
    void json_check(QuarkusMainLauncher launcher) throws Exception {
        LaunchResult result = launcher.launch("check", "--json", indexedJar.toString());
        JsonNode json = new ObjectMapper().readTree(stdout(result));
        assertTrue(json.get(0).get("indexed").asBoolean());
        assertEquals(8, json.get(0).get("indexedClasses").asInt());
    }

    @Test
    void classPathOption(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("check", "--cp", indexedJar + java.io.File.pathSeparator + plainJar);
        assertTrue(stdout(result).contains("1 of 2 source(s)"), stdout(result));
    }

    @Test
    void version_showsJandexAndFormatVersions(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("--version");
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        String output = stdout(result);
        assertTrue(output.matches("(?s)jandex-reader \\S+\nJandex \\d+\\.\\d+\\.\\d+.*"), output);
        assertTrue(output.matches("(?s).*Supported index format versions: 2-3, 6-\\d+ \\(latest: \\d+\\).*"), output);
    }

    @Test
    void unknownSource_fails(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("check", "does-not-exist.jar");
        assertEquals(2, result.exitCode());
        assertTrue(result.getErrorOutput().contains("Source not found: does-not-exist.jar"), result.getErrorOutput());
    }
}
