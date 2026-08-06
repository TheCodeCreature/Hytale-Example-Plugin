import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.util.jar.JarFile

plugins {
    `maven-publish`
    id("hytale-mod") version "0.+"
}

group = "Plugin"
version = "13.0.7"
val javaVersion = 25
val pluginName = findProperty("plugin_name")?.toString() ?: project.name
val pluginGroup = findProperty("plugin_group")?.toString() ?: "Plugin"
val pluginAuthor = findProperty("plugin_author")?.toString()?.trim().orEmpty()
val pluginArchiveName = if (pluginAuthor.isNotEmpty()) "[$pluginAuthor] $pluginName" else pluginName
val runModDirectoryName = "${pluginGroup}_${pluginName}"

repositories {
    mavenCentral()
    maven("https://maven.hytale-modding.info/releases") {
        name = "HytaleModdingReleases"
    }
}

val hytaleInstall = file("${System.getenv("APPDATA")}/Hytale/install/release/package/game/latest")
val serverJar = file("$hytaleInstall/Server/HytaleServer.jar")

// Auto-detect server version from the local HytaleServer.jar manifest.
// Falls back to the gradle.properties value if the JAR doesn't exist (e.g. CI).
val detectedServerVersion: String by lazy {
    if (serverJar.exists()) {
        JarFile(serverJar).use { jar ->
            jar.manifest.mainAttributes.getValue("Implementation-Version")
                ?: findProperty("server_version")?.toString()
                ?: error("No Implementation-Version in HytaleServer.jar and no server_version in gradle.properties")
        }
    } else {
        findProperty("server_version")?.toString()
            ?: error("HytaleServer.jar not found and no server_version fallback in gradle.properties")
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.jspecify)

    compileOnly(files("$hytaleInstall/Server/HytaleServer.jar"))
    compileOnly(files("$hytaleInstall/Assets.zip"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testImplementation("net.bytebuddy:byte-buddy:1.17.6")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.17.6")
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
    // uncomment if you want to add the Assets.zip file to your external libraries;
    // ⚠️ CAUTION, this file is very big and might make your IDE unresponsive for some time!

    addAssetsDependency = true

    // uncomment if you want to develop your mod against the pre-release version of the game.
    
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
        "plugin_name" to (findProperty("plugin_name")?.toString() ?: project.name),
        "plugin_version" to project.version,
        "server_version" to if (detectedServerVersion.matches(Regex("""\d+\.\d+\.\d+"""))) "^$detectedServerVersion" else detectedServerVersion,

        "plugin_description" to findProperty("plugin_description"),
        "plugin_website" to findProperty("plugin_website"),

        "plugin_main_entrypoint" to findProperty("plugin_main_entrypoint"),
        "plugin_author" to findProperty("plugin_author")
    )

    filesMatching("manifest.json") {
        expand(replaceProperties)
    }

    inputs.properties(replaceProperties)

    val resourceTypeIconsDir = project.layout.projectDirectory.dir("src/main/resources/Common/UI/Custom/Common/Icons/ResourceTypes")
    val mirroredItemIconsDir = layout.buildDirectory.dir("resources/main/Common/Icons/ItemsGenerated")
    val mirroredItemTexturesDir = layout.buildDirectory.dir("resources/main/Common/Items/GeneratedProxyTextures")

    doLast {
        val sourceDir = resourceTypeIconsDir.asFile
        if (!sourceDir.exists()) {
            return@doLast
        }

        val iconOutDir = mirroredItemIconsDir.get().asFile
        val textureOutDir = mirroredItemTexturesDir.get().asFile
        iconOutDir.mkdirs()
        textureOutDir.mkdirs()

        sourceDir.listFiles { file -> file.isFile && file.extension.equals("png", ignoreCase = true) }
            ?.forEach { file ->
                val sourceImage = ImageIO.read(file)
                if (sourceImage != null) {
                    val resizedIcon = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
                    val iconGraphics = resizedIcon.createGraphics()
                    iconGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    iconGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    iconGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    iconGraphics.drawImage(sourceImage, 0, 0, 64, 64, null)
                    iconGraphics.dispose()
                    ImageIO.write(resizedIcon, "png", iconOutDir.resolve(file.name))

                    val resizedTexture = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
                    val textureGraphics = resizedTexture.createGraphics()
                    textureGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                    textureGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                    textureGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    textureGraphics.drawImage(sourceImage, 0, 0, 32, 32, null)
                    textureGraphics.dispose()
                    ImageIO.write(resizedTexture, "png", textureOutDir.resolve(file.name))
                } else {
                    file.copyTo(iconOutDir.resolve(file.name), overwrite = true)
                    file.copyTo(textureOutDir.resolve(file.name), overwrite = true)
                }
            }
    }
}

tasks.withType<Jar> {
    archiveBaseName.set(pluginArchiveName)
    archiveFileName.set("$pluginArchiveName-${project.version}.jar")
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
    systemProperty("net.bytebuddy.experimental", "true")

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

tasks.javadoc {
    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    javadocTool.set(javaToolchains.javadocToolFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    })

    setDestinationDir(layout.buildDirectory.dir("docs/javadoc").get().asFile)
    source = sourceSets.main.get().allJava
    classpath = sourceSets.main.get().compileClasspath

    // --- ADD THESE THREE LINES TO FIX THE ERRORS ---
    isFailOnError = false // Prevents warnings from crashing the Gradle build
    val options = options as StandardJavadocDocletOptions
    options.addStringOption("Xdoclint:none", "-quiet") // Silences the "no comment" warnings completely
}

val deployCommonAssets = tasks.register<Copy>("deployCommonAssets") {
    group = "hytale"
    description = "Copies Common/ assets (UI files, etc.) to the deployed mod directory so the client can load them."

    dependsOn("processResources")

    from(layout.buildDirectory.dir("resources/main/Common"))
    into("run/mods/$runModDirectoryName/Common")

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    doLast {
        println("✅ Common assets deployed to run/mods/$runModDirectoryName/Common/")
    }
}

// ── Server process detection ─────────────────────────────────────────
// Configurable values — change these if the server binary or port ever changes.
val hytaleServerPort = "5520"
// Command-line filters that ONLY match the dedicated server, not the game client.
// The server launches via com.hypixel.hytale.Main with a --server flag or similar
// distinguishing argument. Adjust these if the server launch command changes.
val hytaleServerProcessFilters = listOf(
    "*com.hypixel.hytale.Main*--server*",  // server with --server flag
    "*com.hypixel.hytale.Main*-server*",   // alternate flag style
    "*hytale_server*",                      // native launcher (lowercase)
)
// Fallback filter: matches the Main class broadly. Only used in port-intersection
// strategy (process must ALSO be LISTENING on the server port to qualify).
val hytaleMainClassFilter = "*com.hypixel.hytale.Main*"

/**
 * Returns the set of PIDs for the Hytale **dedicated server** only.
 *
 * Strategy 1: Processes whose command line matches a server-specific filter.
 * Strategy 2: Processes LISTENING on [port] whose command line contains the
 *             Hytale main class — this catches the server even if the exact
 *             flags change, without false-positiving on the game client
 *             (which connects to the port but doesn't listen on it).
 */
fun Project.findHytaleServerPids(
    port: String = hytaleServerPort,
    serverFilters: List<String> = hytaleServerProcessFilters,
): Set<String> {
    val os = System.getProperty("os.name").lowercase()
    val pids = mutableSetOf<String>()

    if (os.contains("win")) {
        // Strategy 1: match command line against server-specific filters
        for (filter in serverFilters) {
            val result = providers.exec {
                isIgnoreExitValue = true
                commandLine("powershell", "-NoProfile", "-Command",
                    """
                    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
                        Where-Object { ${'$'}_.CommandLine -like '$filter' } |
                        ForEach-Object { ${'$'}_.ProcessId }
                    """.trimIndent()
                )
            }.standardOutput.asText.get()
            pids += result.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }
        }

        // Strategy 2: find PIDs LISTENING on the server port, then intersect
        // with processes whose command line contains the Hytale main class.
        // LISTENING = server; ESTABLISHED = client. We only want the server.
        val portResult = providers.exec {
            isIgnoreExitValue = true
            commandLine("cmd", "/c", "netstat -ano | findstr LISTENING | findstr :$port")
        }.standardOutput.asText.get()

        val listeningPids = portResult.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> line.split("\\s+".toRegex()).lastOrNull()?.trim() }
            .filter { it.all(Char::isDigit) && it != "0" }
            .toSet()

        if (listeningPids.isNotEmpty()) {
            // Verify each listening PID is actually a Hytale process
            val hytaleResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("powershell", "-NoProfile", "-Command",
                    """
                    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
                        Where-Object { ${'$'}_.CommandLine -like '$hytaleMainClassFilter' } |
                        ForEach-Object { ${'$'}_.ProcessId }
                    """.trimIndent()
                )
            }.standardOutput.asText.get()
            val hytalePids = hytaleResult.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }.toSet()

            // Only add PIDs that are BOTH listening on the port AND are Hytale processes
            pids += listeningPids.intersect(hytalePids)
        }
    } else {
        // Strategy 1: match command line against server-specific filters
        for (filter in serverFilters) {
            val pattern = filter.replace("*", ".*")
            val result = providers.exec {
                isIgnoreExitValue = true
                commandLine("sh", "-c", "pgrep -f '$pattern' || true")
            }.standardOutput.asText.get()
            pids += result.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }
        }

        // Strategy 2: find PIDs listening on the server port, intersect with Hytale processes
        val portResult = providers.exec {
            isIgnoreExitValue = true
            commandLine("sh", "-c",
                "ss -tlnp sport = :$port 2>/dev/null | awk 'NR>1{print \$NF}' | grep -oP 'pid=\\K[0-9]+' || true")
        }.standardOutput.asText.get()

        val listeningPids = portResult.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }.toSet()

        if (listeningPids.isNotEmpty()) {
            val mainPattern = hytaleMainClassFilter.replace("*", "")
            val hytaleResult = providers.exec {
                isIgnoreExitValue = true
                commandLine("sh", "-c", "pgrep -f '$mainPattern' || true")
            }.standardOutput.asText.get()
            val hytalePids = hytaleResult.lines().map { it.trim() }.filter { it.isNotEmpty() && it.all(Char::isDigit) }.toSet()

            pids += listeningPids.intersect(hytalePids)
        }
    }
    return pids
}

val killExistingServers = tasks.register("killExistingServers") {
    group = "hytale"
    description = "Kills any already-running HytaleServer processes to free the port."

    doLast {
        val pids = project.findHytaleServerPids()
        val os = System.getProperty("os.name").lowercase()

        if (pids.isEmpty()) {
            logger.lifecycle("✅ No existing HytaleServer processes found (checked command line and port $hytaleServerPort).")
        } else {
            logger.lifecycle("Found HytaleServer PIDs: ${pids.joinToString(", ")}")
            pids.forEach { pid ->
                logger.lifecycle("⚠️ Killing existing HytaleServer process (PID $pid)...")
                if (os.contains("win")) {
                    providers.exec {
                        commandLine("taskkill", "/F", "/PID", pid)
                        isIgnoreExitValue = true
                    }.result.get() // .get() forces the lazy provider to execute
                } else {
                    providers.exec {
                        commandLine("kill", "-9", pid)
                        isIgnoreExitValue = true
                    }.result.get()
                }
            }
            // Wait for OS to fully release the process and port
            Thread.sleep(2000)

            // Verify the processes are actually gone
            val remaining = project.findHytaleServerPids()
            if (remaining.isNotEmpty()) {
                logger.warn("⚠️ PIDs still alive after kill: ${remaining.joinToString(", ")} — retrying...")
                remaining.forEach { pid ->
                    if (os.contains("win")) {
                        providers.exec {
                            commandLine("taskkill", "/F", "/PID", pid)
                            isIgnoreExitValue = true
                        }.result.get()
                    } else {
                        providers.exec {
                            commandLine("kill", "-9", pid)
                            isIgnoreExitValue = true
                        }.result.get()
                    }
                }
                Thread.sleep(2000)
            }
            logger.lifecycle("✅ Killed ${pids.size} existing server process(es).")
        }
    }
}

val checkNoExistingServers = tasks.register("checkNoExistingServers") {
    group = "hytale"
    description = "Fails the build if a HytaleServer is already running. Run 'killExistingServers' to stop it."

    doLast {
        val pids = project.findHytaleServerPids()

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

        (targetTask as? JavaExec)?.let { runTask ->
            runTask.doFirst {
                // Defensive cleanup: some plugin/toolchain combinations can inject
                // empty JVM/program args, which Java interprets as an empty main class.
                runTask.setJvmArgs(runTask.jvmArgs.filter { it.isNotBlank() })
                runTask.setArgs(runTask.args.filter { it.isNotBlank() })
            }
        }

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
