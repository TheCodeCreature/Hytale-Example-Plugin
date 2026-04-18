---
topic: "Gradle Plugin Mechanics"
category: "Plugins"
updated: 2026-04-18
sources: ["build.gradle.kts", "HytaleServerPlatform.kt (ScaffoldIt source)", "hytalemodding.dev/en/docs/guides/plugin/setting-up-env", "hytalemodding.dev/en/docs/guides/plugin/browsing-serverjar", "plugin-template README"]
---

# Gradle Plugin Mechanics

## Summary

The `hytale-mod` Gradle plugin (and its successor/sibling ScaffoldIt `dev.scaffoldit`) automates the setup of the Hytale server SDK for plugin development. It adds the official Hytale Maven repository, resolves the server JAR as a compile dependency, and provides tasks for decompiling and running the server. Understanding how these pieces interact is essential for diagnosing classpath issues.

## How the Server Dependency Gets on the Compile Classpath

### The Maven Artifact

The plugin automatically adds two things to your project:

1. **The Hytale Maven repository**: `https://maven.hytale.com/release` (or `https://maven.hytale.com/pre-release` if `updateChannel = "pre-release"`)
2. **The server dependency**: `com.hypixel.hytale:Server:<version>`

This is equivalent to writing manually in `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.hytale.com/release")
    }
}
dependencies {
    implementation("com.hypixel.hytale:Server:+")  // "+" = latest available
}
```

The actual compiled `.class` files used during compilation come from this Maven artifact JAR, which Gradle downloads and caches in:

```
~/.gradle/caches/modules-2/files-2.1/com.hypixel.hytale/Server/<version>/
```

**Source:** Confirmed via the ScaffoldIt source code (`HytaleServerPlatform.configureHytaleMaven()`), which does exactly this. The `hytale-mod` plugin works the same way.

### What the Hytale Maven Publishes

Per the official docs: Hytale provides JARs for the **last five releases**. If the game updates and Hypixel hasn't yet published the new server JAR to Maven, the latest classes won't be available as a compile dependency.

## The `.tmp_hytale_src/` Folder

### What It Is

`.tmp_hytale_src/` contains **decompiled Java source files** produced by the `decompileServer` Gradle task. It decompiles the **locally installed HytaleServer.jar** from your Hytale game installation using Vineflower (or similar decompiler).

### NOT on the Compile Classpath

`.tmp_hytale_src/` is **purely for IDE browsing and reference**. It is NOT on the compile classpath. You cannot import classes from `.tmp_hytale_src/` in your plugin code — your plugin compiles against the **Maven artifact JAR**, not the decompiled sources.

### Source vs. Compiled Dependency

| Aspect | `.tmp_hytale_src/` (Decompiled Sources) | Maven Artifact (Compile Classpath) |
|--------|----------------------------------------|-----------------------------------|
| **Source** | Local HytaleServer.jar from game install | `com.hypixel.hytale:Server` from Maven |
| **Purpose** | IDE browsing, searching, reference | Actual compilation dependency |
| **On classpath?** | No | Yes |
| **Updated by** | `decompileServer` task + game updates | `--refresh-dependencies` + Maven publish |
| **Format** | `.java` source files | `.class` files in JAR |

## Version Mismatch Problem

### The Core Issue

**YES, the decompiled sources and the compile dependency can be different versions.** This is a common source of confusion:

1. **Your game auto-updates** → local HytaleServer.jar is now version N+1
2. **You run `decompileServer`** → `.tmp_hytale_src/` reflects version N+1, including new classes like `LivingEntityInventoryChangeEvent`
3. **Gradle's cached dependency** is still version N (or Maven hasn't published N+1 yet)
4. **Result**: You can SEE the class in `.tmp_hytale_src/`, but the compiler doesn't recognize it because the Maven JAR doesn't contain it

### Why It Happens

- **Gradle dependency caching**: Gradle aggressively caches dynamic version resolutions (like `+`). Even with `version = "+"`, Gradle may not re-check the Maven repo every build.
- **Maven publish lag**: Hypixel may not publish a new server JAR to Maven immediately after a game update.
- **Game auto-update**: The locally installed HytaleServer.jar updates with the Hytale launcher, independently of the Maven artifact.

## Diagnosing and Fixing

### Check Current Compile Dependency Version

```bash
# Show the resolved version of the server dependency
./gradlew dependencies --configuration compileClasspath

# Or for more detail
./gradlew dependencies --configuration compileClasspath | findstr /i "hytale"
```

This will show something like:
```
\--- com.hypixel.hytale:Server:+ -> 2026.03.26-89796e57b
```

### Force Dependency Refresh

```bash
# Force Gradle to re-resolve all dependencies from remote repositories
./gradlew build --refresh-dependencies
```

This clears Gradle's dynamic version cache and re-checks the Maven repository for the latest available version.

### Verify the Class Exists in the JAR

```bash
# Find the cached server JAR and check if the class exists in it
# On Windows:
dir /s /b "%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.hypixel.hytale\Server"

# Then inspect it:
jar tf <path-to-server.jar> | findstr /i "LivingEntityInventoryChangeEvent"
```

### Alternative: Direct Maven Dependency

If the `hytale-mod` plugin is using an outdated server version, you can add the dependency explicitly in `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.hytale.com/release") {
        name = "HytaleRelease"
    }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:+")
}
```

### Nuclear Option: Clean Everything

```bash
# Delete Gradle caches for hytale artifacts
./gradlew clean

# Delete the Gradle dependency cache (forces full re-download)
# WARNING: This re-downloads ALL dependencies, not just Hytale
# On Windows:
rmdir /s /q "%USERPROFILE%\.gradle\caches\modules-2\files-2.1\com.hypixel.hytale"

# Then rebuild
./gradlew build --refresh-dependencies
```

## Plugin Configuration Reference

```kotlin
hytale {
    // Include Assets.zip on classpath (WARNING: very large file)
    // addAssetsDependency = true
    
    // Use pre-release server version (from maven.hytale.com/pre-release)
    // updateChannel = "pre-release"
}
```

| Option | Default | Effect |
|--------|---------|--------|
| `addAssetsDependency` | `false` | Adds the game's Assets.zip to the classpath |
| `updateChannel` | `"release"` | Which Maven patchline to use: `"release"` or `"pre-release"` |

## Relevant Gradle Tasks

| Task | Purpose |
|------|---------|
| `decompileServer` | Decompiles the local HytaleServer.jar to `.tmp_hytale_src/` |
| `runServer` | Starts the Hytale server with the plugin loaded |
| `build` | Compiles and packages the plugin JAR |
| `dependencies` | Shows resolved dependency versions |
| `build --refresh-dependencies` | Forces re-resolution of all Maven dependencies |

## `server_version` in gradle.properties

The `server_version=2026.03.26-89796e57b` property in `gradle.properties` is **metadata only** — it's substituted into `manifest.json` via `processResources` but does NOT control which version of the server JAR is used for compilation. The compile version is determined by the Maven resolution (typically `+` = latest available).

## The `hytale.home_path` Property

If the build fails with "Failed to find Hytale at the expected location", set this in `gradle.properties`:

```properties
hytale.home_path=C:\Path\To\Hytale\Data
```

This tells the plugin where to find the local Hytale installation (needed for `decompileServer` and `runServer`).

## See Also

- [Build & Run](./build-and-run.md)
- [Getting Started](./getting-started.md)
- [Community Resources](../community/resources.md)
- [Browsing the server.jar code](https://hytalemodding.dev/en/docs/guides/plugin/browsing-serverjar) — Alternative manual decompilation approach
