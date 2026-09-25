# jandex-reader

Command line tool that checks whether JAR files, directories or Maven artifacts contain a
[Jandex](https://github.com/smallrye/jandex) index (`META-INF/jandex.idx`) and lets you browse its
content: classes, members, annotations and type hierarchy.

Built with Quarkus + Picocli, runs on the JVM or as a GraalVM/Mandrel native executable.

## Why

Jandex ships a small CLI (`java -jar jandex.jar -d file.idx`), but it only creates indexes or dumps a
standalone `.idx` file in one fixed format. It can't query an index, doesn't look inside JARs, and knows
nothing about Maven. This tool fills that gap.

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
