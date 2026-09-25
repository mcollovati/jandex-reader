# jandex-reader

[![Build](https://github.com/mcollovati/jandex-reader/actions/workflows/build.yml/badge.svg)](https://github.com/mcollovati/jandex-reader/actions/workflows/build.yml)

Command line tool that checks whether JAR files, directories or Maven artifacts contain a
[Jandex](https://github.com/smallrye/jandex) index (`META-INF/jandex.idx`) and lets you browse its
content: classes, members, annotations and type hierarchy.

Built with Quarkus + Picocli, runs on the JVM or as a GraalVM/Mandrel native executable.

## Why

Jandex ships a small CLI (`java -jar jandex.jar -d file.idx`), but it only creates indexes or dumps a
standalone `.idx` file in one fixed format. It can't query an index, doesn't look inside JARs, and knows
nothing about Maven. This tool fills that gap.

## Download

Each [release](https://github.com/mcollovati/jandex-reader/releases) has native executables for
Linux (amd64, arm64), macOS (arm64, amd64) and Windows (amd64), plus a JVM uber-jar (Java 21+) and
`checksums_sha256.txt`:

```shell
curl -sL https://github.com/mcollovati/jandex-reader/releases/latest/download/jandex-reader-linux-amd64.tar.gz \
  | tar xz jandex-reader
./jandex-reader --version
```

The macOS binaries aren't signed. If Gatekeeper blocks one, run `xattr -d com.apple.quarantine jandex-reader`.

Pushes and pull requests only run the JVM build and tests. To get binaries without releasing, start
the *Build* workflow manually from the Actions tab and download them from the run's artifacts.

## Release

Start the *Release* workflow from the Actions tab, or run
`gh workflow run release.yml -f version=1.0.0`, with the version to release. The version is also the
tag name, with no `v` prefix. The workflow:

1. checks the version format (`1.0.0`, or with a suffix such as `1.1.0-rc1`; no SNAPSHOT) and fails if
   the tag already exists
2. sets the version in `pom.xml`, then builds and tests the JVM jar and the native executables
3. checks the tag again and publishes the release with [JReleaser](https://jreleaser.org)
   (`mvn jreleaser:full-release`, configured in the `jreleaser-maven-plugin` section of `pom.xml`).
   JReleaser creates the tag on the built commit and writes a changelog of the commits since the
   previous tag. Versions with a suffix are marked as pre-releases.

Tick *Dry run* (or add `-f dry-run=true`) to go through all of the above without creating the tag or
the release. The run summary then shows the files and the changelog the release would have.

The version in `pom.xml` on `main` stays a `-SNAPSHOT`; the release sets its version only in the build.

## Build

```shell
# JVM (uber-jar)
mvn package
java -jar target/jandex-reader-1.0.0-SNAPSHOT-runner.jar --help

# Native executable (needs GraalVM/Mandrel for JDK 21+ in GRAALVM_HOME)
mvn package -Dnative
./target/jandex-reader-1.0.0-SNAPSHOT-runner --help

# Native executable built in a container (only Docker/Podman needed)
mvn package -Dnative -Dquarkus.native.container-build=true
```

`mvn verify -Dnative` also runs the test suite against the native executable.

## Sources

Every command accepts one or more sources:

| Source                | Example                                    |
|-----------------------|--------------------------------------------|
| JAR / WAR / ZIP       | `target/app.jar`                           |
| Exploded directory    | `target/classes`                           |
| Jandex index file     | `jandex.idx`                               |
| Maven coordinates     | `io.smallrye.reactive:mutiny:3.3.0`        |
| Latest release        | `io.smallrye.reactive:mutiny` (or `:LATEST`) |
| Extension, classifier | `g:a:war:1.0`, `g:a:jar:tests:1.0`         |
| Class path            | `--cp "a.jar:b.jar"`                       |
| Maven project deps    | `--pom path/to/pom.xml [--scope runtime]`  |

The index is looked up at `META-INF/jandex.idx` (and `WEB-INF/classes/META-INF/jandex.idx` for WARs).

Maven artifacts come from the local repository (`~/.m2/repository`, or the `localRepository` in
`~/.m2/settings.xml`, or `--local-repo`). If an artifact isn't there, it's downloaded from Maven Central
(or the `--repo` URLs) into `~/.cache/jandex-reader/repository`. Only the artifact itself is fetched, not
its dependencies. To inspect a full dependency tree, use `--pom`, which runs
`mvn dependency:build-classpath` (through `mvnw` when the project has one) so your Maven settings,
mirrors and credentials apply.

Query commands skip sources without an index and print a warning. Use `-b/--build-index` to index their
classes on the fly instead.

## Commands

| Command                         | Description                                                    |
|---------------------------------|----------------------------------------------------------------|
| `check <src>...`                | Index present? Format version, indexed/total classes, location |
| `classes <src>...`              | List classes (`-f` filter, `-k` kind, `-l` declarations)       |
| `class <name> <src>...`         | Class declaration, annotations, fields, methods                |
| `methods <class> <src>...`      | Methods of a class (`-f` filter, `-a` annotated only, `-q`)    |
| `fields <class> <src>...`       | Fields of a class (`-f` filter, `-a` annotated only, `-q`)     |
| `annotated <annotation> <src>...` | Elements carrying an annotation (`-t` target kinds, `-c` classes only) |
| `annotations <src>...`          | Annotation types used, with counts per target kind (`-s` sort by count) |
| `subclasses <class> <src>...`   | Known subclasses (`-d` direct only)                            |
| `implementors <iface> <src>...` | Known implementations (`-d` direct only, `-i` include sub-interfaces) |
| `dump <src>...`                 | Every class with annotations and members                       |

Common options: `--json`, `-b/--build-index`, `--cp`, `--pom`, `--scope`, `--local-repo`, `-r/--repo`,
`--offline`. Run `jandex-reader <command> --help` for details.

Names can be fully qualified (`com.acme.Outer$Inner`) or simple (`Inner`, `Outer.Inner`,
`@Singleton`). Ambiguous simple names list the candidates. Filters (`-f`) are substrings, or globs when
they contain `*` or `?` (e.g. `com.acme.*Service`).

`jandex-reader --version` also prints the bundled Jandex version and the index format versions it can
read. An index whose format is newer than these can't be read until Jandex is upgraded (`jandex.version`
in `pom.xml`).

Exit codes: `0` success, `1` `check` found at least one source without an index, `2` error.

## Examples

```shell
# Which dependencies of my project ship a Jandex index?
jandex-reader check --pom . --scope runtime
jandex-reader check --pom . --missing          # only the ones without

# Browse an artifact
jandex-reader classes -k interface io.smallrye.stork:stork-api:2.7.7
jandex-reader class PanacheEntity io.quarkus:quarkus-hibernate-orm-panache:3.39.4
jandex-reader methods -q -f '*Result*' PanacheQuery io.quarkus:quarkus-hibernate-orm-panache:3.39.4

# Annotations
jandex-reader annotations -s target/app.jar
jandex-reader annotated -c ApplicationScoped target/app.jar       # annotated classes
jandex-reader annotated -t method,parameter Deprecated target/app.jar

# Hierarchy
jandex-reader implementors -i jakarta.ws.rs.core.Feature --pom .

# JSON for scripting
jandex-reader check --json --pom . | jq -r '.[] | select(.indexed | not) | .source'
```

## License

[Apache License 2.0](LICENSE)
