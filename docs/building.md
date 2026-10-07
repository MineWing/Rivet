# Building from source

## Requirements

- Java Development Kit 21
- Maven
- Git

## Build

```bash
git clone https://github.com/byalex33/Rivet.git
cd Rivet
mvn package
```

Do not use Maven's `clean` goal in this workspace.

The package build compiles the plugin, runs the complete test suite, creates the normal project JAR under `target/`, and writes the deployable shaded JAR to:

```text
/Users/alex/Documents/MinecraftServers/250k/plugins/rivet-1.0-SNAPSHOT.jar
```

The output path is configured by the Maven Shade plugin in `pom.xml` and must remain intact.

## Supported target

Rivet currently compiles for Paper 1.21.11 and Java 21. Runtime dependencies included by the project are shaded into the deployable JAR where required.

Before deploying from an isolated checkout, compare its history with the working repository. Preserve previously delivered features, including verified local commits that have not reached `main`; excluding unrelated uncommitted edits must not remove those features from the server build.
