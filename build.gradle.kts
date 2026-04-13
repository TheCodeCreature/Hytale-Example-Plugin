plugins {
    `maven-publish`
    id("hytale-mod") version "0.+"
}

group = "Camera"
version = "13.0.5"
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

// Make server/protocol classes available on the test classpath so unit tests
// can reference types like PlacementResult from InteractionPositionFixer
configurations {
    testImplementation {
        extendsFrom(configurations.compileOnly.get())
    }
}

hytale {
//     uncomment if you want to add the Assets.zip file to your external libraries;
//     ⚠️ CAUTION, this file is very big and might make your IDE unresponsive for some time!

//     addAssetsDependency = true

    // uncomment if you want to develop your mod against the pre-release version of the game.
    //
    // updateChannel = "pre-release"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }

    withSourcesJar()
}

tasks.named<ProcessResources>("processResources") {
    var replaceProperties = mapOf(
        "plugin_group" to findProperty("plugin_group"),
        "plugin_maven_group" to project.group,
        "plugin_name" to project.name,
        "plugin_version" to project.version,
        "server_version" to findProperty("server_version"),

        "plugin_description" to findProperty("plugin_description"),
        "plugin_website" to findProperty("plugin_website"),

        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author")
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)
}

tasks.withType<Jar> {
    manifest {
        attributes["Specification-Title"] = rootProject.name
        attributes["Specification-Version"] = version
        attributes["Implementation-Title"] = project.name
        attributes["Implementation-Version"] =
            providers.environmentVariable("COMMIT_SHA_SHORT")
                .map { "${version}-${it}" }
                .getOrElse(version.toString())
    }
}

tasks.test {
    useJUnitPlatform()

    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")

    // Allow reflective access for resource collection unit tests
    jvmArgs(
        "--add-opens", "java.base/sun.misc=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED"
    )
}

publishing {
    repositories {
        // This is where you put repositories that you want to publish to.
        // Do NOT put repositories for your dependencies here.
    }

    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

val syncAssets = tasks.register<Copy>("syncAssets") {
    group = "hytale"
    description = "Automatically syncs assets from Build back to Source after server stops."

    // Take from the temporary build folder (Where the game saved changes)
    from(layout.buildDirectory.dir("resources/main"))

    // Copy into your actual project source (Where your code lives)
    into("src/main/resources")

    // IMPORTANT: Protect the manifest template from being overwritten
    exclude("manifest.json")

    // If a file exists, overwrite it with the new version from the game
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Assets successfully synced from Game to Source Code!")
    }
}

val killExistingServers = tasks.register("killExistingServers") {
    group = "hytale"
    description = "Kills any already-running HytaleServer processes to free the port."

    doLast {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            val result = providers.exec {
                isIgnoreExitValue = true
                commandLine("powershell", "-NoProfile", "-Command",
                    """
                    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
                        Where-Object { ${'$'}_.CommandLine -like '*com.hypixel.hytale.Main*' } |
                        ForEach-Object { ${'$'}_.ProcessId }
                    """.trimIndent()
                )
            }.standardOutput.asText.get()

            val pids = result.lines().map { it.trim() }.filter { it.isNotEmpty() }

            if (pids.isEmpty()) {
                logger.lifecycle("✅ No existing HytaleServer processes found.")
            } else {
                pids.forEach { pid ->
                    logger.lifecycle("⚠️ Killing existing HytaleServer process (PID $pid)...")
                    providers.exec {
                        commandLine("taskkill", "/F", "/PID", pid)
                        isIgnoreExitValue = true
                    }
                }
                Thread.sleep(1000)
                logger.lifecycle("✅ Killed ${pids.size} existing server process(es).")
            }
        } else {
            val result = providers.exec {
                commandLine("sh", "-c", "pgrep -f 'com.hypixel.hytale.Main' || true")
            }.standardOutput.asText.get()

            val pids = result.lines().map { it.trim() }.filter { it.isNotEmpty() }

            if (pids.isEmpty()) {
                logger.lifecycle("✅ No existing HytaleServer processes found.")
            } else {
                pids.forEach { pid ->
                    logger.lifecycle("⚠️ Killing existing HytaleServer process (PID $pid)...")
                    providers.exec { commandLine("kill", "-9", pid) }
                }
                Thread.sleep(1000)
                logger.lifecycle("✅ Killed ${pids.size} existing server process(es).")
            }
        }
    }
}

afterEvaluate {
    // Now Gradle will find it, because the plugin has finished working
    val targetTask = tasks.findByName("runServer") ?: tasks.findByName("server")

    if (targetTask != null) {
        targetTask.dependsOn(killExistingServers)
        targetTask.finalizedBy(syncAssets)

        // Forward stdin so interactive server commands like /auth login work
        (targetTask as? JavaExec)?.standardInput = System.`in`
        logger.lifecycle("✅ specific task '${targetTask.name}' hooked for auto-sync.")
    } else {
        logger.warn("⚠️ Could not find 'runServer' or 'server' task to hook auto-sync into.")
    }
}
