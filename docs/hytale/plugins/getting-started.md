---
topic: "Getting Started with Plugins"
category: "Plugins"
updated: 2026-04-16
sources: ["codebase analysis", "hytalemodding.dev", "build.gradle.kts"]
---

# Getting Started with Plugins

## Summary

Hytale server plugins are Java projects that use the `hytale-mod` Gradle plugin. They extend the game by registering commands, handling events, modifying assets, and adding ECS systems.

## Prerequisites

- **Java Development Kit (JDK) 25** or newer
- **Gradle** (wrapper included in projects)
- **IDE**: IntelliJ IDEA or VS Code with Java extensions
- **Hytale Server SDK** (provided via the `hytale-mod` Gradle plugin)

## Project Setup

### 1. Create Project Structure

```
my-plugin/
  ├── build.gradle.kts
  ├── settings.gradle.kts
  ├── gradle.properties
  ├── gradle/
  │   ├── libs.versions.toml
  │   └── wrapper/
  │       └── gradle-wrapper.properties
  └── src/
      └── main/
          ├── java/
          │   └── com/
          │       └── myplugin/
          │           └── MyPlugin.java
          └── resources/
              ├── manifest.json
              └── Server/          ← Optional: asset pack files
```

### 2. Configure build.gradle.kts

```kotlin
plugins {
    id("hytale-mod") version "0.+"
}

group = "MyPluginGroup"
version = "1.0.0"
val javaVersion = 25

repositories {
    mavenCentral()
    maven("https://maven.hytale-modding.info/releases") {
        name = "HytaleModdingReleases"
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

hytale {
    // Uncomment for pre-release server:
    // updateChannel = "pre-release"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
}
```

### 3. Configure settings.gradle.kts

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.hytale-modding.info/releases") {
            name = "HytaleModdingReleases"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "My Plugin Name"
```

### 4. Configure gradle.properties

```properties
org.gradle.jvmargs=-Xmx1G

# Plugin Metadata
plugin_group=MyGroup
plugin_description=My awesome plugin
plugin_author=MyName
plugin_website=
plugin_main_entrypoint=com.myplugin.MyPlugin
server_version=2026.03.26-89796e57b
```

### 5. Create manifest.json

```json
{
  "Group": "${plugin_group}",
  "Name": "${plugin_name}",
  "Version": "${plugin_version}",
  "Description": "${plugin_description}",
  "Authors": [{ "Name": "${plugin_author}" }],
  "Website": "${plugin_website}",
  "ServerVersion": "${server_version}",
  "Dependencies": {},
  "OptionalDependencies": {},
  "DisabledByDefault": false,
  "IncludesAssetPack": false,
  "Main": "${plugin_main_entrypoint}"
}
```

### 6. Create the Plugin Class

```java
package com.myplugin;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jspecify.annotations.NonNull;

public class MyPlugin extends JavaPlugin {

    public MyPlugin(@NonNull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Register commands, events, and systems here
    }
}
```

## Building

```bash
./gradlew build
```

The compiled JAR is output to `build/libs/`.

## Running

```bash
./gradlew runServer             # Normal run
./gradlew runServer --debug-jvm # With remote debugger
```

The server runs from the `run/` directory. Plugin JARs are automatically deployed to `run/mods/`.

## Testing

```bash
./gradlew test
```

Tests use JUnit 5. Server/protocol classes are available on the test classpath via:

```kotlin
configurations {
    testImplementation {
        extendsFrom(configurations.compileOnly.get())
    }
}
```

## See Also

- [Plugin Lifecycle](./lifecycle.md)
- [Manifest](./manifest.md)
- [Commands](./commands.md)
- [Events](./events.md)
