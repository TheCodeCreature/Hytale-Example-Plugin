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

val deployCommonAssets = tasks.register<Copy>("deployCommonAssets") {
    group = "hytale"
    description = "Copies Common/ assets (UI files, etc.) to the deployed mod directory so the client can load them."

    dependsOn("processResources")

    from(layout.buildDirectory.dir("resources/main/Common"))
    into("run/mods/CodeCreature.Development/Common")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Common assets deployed to run/mods/CodeCreature.Development/Common/")
    }
}

val killExistingServers = tasks.register("killExistingServers") {
    group = "hytale"
    description = "Kills any already-running HytaleServer processes to free the port."

    doLast {
        val serverPort = "5520"
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            // Strategy 1: Find by command line (original approach)
            val cmdLineResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("powershell", "-NoProfile", "-Command",
                    """
                    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
                        Where-Object { ${'$'}_.CommandLine -like '*com.hypixel.hytale.Main*' } |
                        ForEach-Object { ${'$'}_.ProcessId }
                    """.trimIndent()
                )
            }.standardOutput.asText.get()

            // Strategy 2: Find by port (catches processes the command-line filter misses)
            val portResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("cmd", "/c", "netstat -ano | findstr :$serverPort")
            }.standardOutput.asText.get()

            val portPids = portResult.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line -> line.split("\\s+".toRegex()).lastOrNull()?.trim() }
                .filter { it.all(Char::isDigit) && it != "0" }
                .toSet()

            val cmdLinePids = cmdLineResult.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()

            val allPids = (cmdLinePids + portPids).toSet()

            if (allPids.isEmpty()) {
                logger.lifecycle("✅ No existing HytaleServer processes found (checked command line and port $serverPort).")
            } else {
                if (cmdLinePids.isNotEmpty()) {
                    logger.lifecycle("Found PIDs by command line match: $cmdLinePids")
                }
                if (portPids.isNotEmpty()) {
                    logger.lifecycle("Found PIDs on port $serverPort: $portPids")
                }
                allPids.forEach { pid ->
                    logger.lifecycle("⚠️ Killing existing HytaleServer process (PID $pid)...")
                    providers.exec {
                        commandLine("taskkill", "/F", "/PID", pid)
                        isIgnoreExitValue = true
                    }
                }
                Thread.sleep(1000)
                logger.lifecycle("✅ Killed ${allPids.size} existing server process(es).")
            }
        } else {
            // Strategy 1: Find by command line
            val cmdLineResult = providers.exec {
                commandLine("sh", "-c", "pgrep -f 'com.hypixel.hytale.Main' || true")
            }.standardOutput.asText.get()

            // Strategy 2: Find by port
            val portResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("sh", "-c", "ss -ulnp sport = :$serverPort 2>/dev/null | awk 'NR>1{print \$NF}' | grep -oP 'pid=\\K[0-9]+' || true")
            }.standardOutput.asText.get()

            val cmdLinePids = cmdLineResult.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val portPids = portResult.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }.toSet()
            val allPids = (cmdLinePids + portPids).toSet()

            if (allPids.isEmpty()) {
                logger.lifecycle("✅ No existing HytaleServer processes found (checked command line and port $serverPort).")
            } else {
                allPids.forEach { pid ->
                    logger.lifecycle("⚠️ Killing existing HytaleServer process (PID $pid)...")
                    providers.exec { commandLine("kill", "-9", pid) }
                }
                Thread.sleep(1000)
                logger.lifecycle("✅ Killed ${allPids.size} existing server process(es).")
            }
        }
    }
}

val checkNoExistingServers = tasks.register("checkNoExistingServers") {
    group = "hytale"
    description = "Fails the build if a HytaleServer is already running. Run 'killExistingServers' to stop it."

    doLast {
        val serverPort = "5520"
        val os = System.getProperty("os.name").lowercase()
        val pids = mutableSetOf<String>()

        if (os.contains("win")) {
            val cmdLineResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("powershell", "-NoProfile", "-Command",
                    """
                    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
                        Where-Object { ${'$'}_.CommandLine -like '*com.hypixel.hytale.Main*' } |
                        ForEach-Object { ${'$'}_.ProcessId }
                    """.trimIndent()
                )
            }.standardOutput.asText.get()

            val portResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("cmd", "/c", "netstat -ano | findstr :$serverPort")
            }.standardOutput.asText.get()

            pids += cmdLineResult.lines().map { it.trim() }.filter { it.isNotEmpty() }
            pids += portResult.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line -> line.split("\\s+".toRegex()).lastOrNull()?.trim() }
                .filter { it.all(Char::isDigit) && it != "0" }
        } else {
            val cmdLineResult = providers.exec {
                commandLine("sh", "-c", "pgrep -f 'com.hypixel.hytale.Main' || true")
            }.standardOutput.asText.get()

            val portResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("sh", "-c", "ss -ulnp sport = :$serverPort 2>/dev/null | awk 'NR>1{print \$NF}' | grep -oP 'pid=\\K[0-9]+' || true")
            }.standardOutput.asText.get()

            pids += cmdLineResult.lines().map { it.trim() }.filter { it.isNotEmpty() }
            pids += portResult.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }
        }

        if (pids.isNotEmpty()) {
            throw GradleException(
                """
                |
                |⚠️  Existing HytaleServer detected! (PIDs: ${pids.joinToString(", ")})
                |    Cannot start a new server while one is already running.
                |
                |    Run:  ./gradlew killExistingServers
                |    Then retry your command.
                """.trimMargin()
            )
        } else {
            logger.lifecycle("✅ No existing HytaleServer processes found — safe to start.")
        }
    }
}

afterEvaluate {
    // Now Gradle will find it, because the plugin has finished working
    val targetTask = tasks.findByName("runServer") ?: tasks.findByName("server")

    if (targetTask != null) {
        targetTask.dependsOn(checkNoExistingServers)
        targetTask.dependsOn(deployCommonAssets)

        // Only sync assets back on successful server shutdown, NOT on kill/failure.
        // Using finalizedBy would run syncAssets even when the task is cancelled or
        // the terminal is killed, overwriting source files with stale build output.
        targetTask.doLast {
            syncAssets.get().actions.forEach { it.execute(syncAssets.get()) }
        }

        // Forward stdin so interactive server commands like /auth login work
        (targetTask as? JavaExec)?.standardInput = System.`in`
        logger.lifecycle("✅ specific task '${targetTask.name}' hooked for auto-sync.")
    } else {
        logger.warn("⚠️ Could not find 'runServer' or 'server' task to hook auto-sync into.")
    }
}
