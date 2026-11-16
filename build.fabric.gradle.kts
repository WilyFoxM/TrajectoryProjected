plugins {
    id("net.fabricmc.fabric-loom")
}

fun prop(key: String): String = sc.properties.get<String>(key)

val archives = prop("mod.archives")
// The Minecraft part of the version names the releases the jar covers.
version = "${prop("mod.version")}+${prop("mod.mc_label")}"
group = prop("mod.group")
base.archivesName = archives

repositories {
    mavenCentral()
    maven("https://api.modrinth.com/maven")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    implementation("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    // Optional rendering mods for local runs only: -PwithSodium, -PwithIris (Iris needs Sodium).
    if (project.hasProperty("withSodium") || project.hasProperty("withIris")) {
        runtimeOnly("maven.modrinth:sodium:${prop("deps.sodium")}")
    }
    if (project.hasProperty("withIris")) {
        runtimeOnly("maven.modrinth:iris:${prop("deps.iris")}")
    }
    // Unit tests run in every node: the parity tests compare the core with that version's vanilla.
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val metadata =
    mapOf(
        "version" to version.toString(),
        "minecraft" to prop("mod.mc_compat"),
        "fabric_api" to prop("deps.fabric_api_min"),
        "loader" to prop("deps.fabric_loader"),
    )

tasks.processResources {
    inputs.properties(metadata)
    filesMatching("fabric.mod.json") { expand(metadata) }

    // 26.3 compiles shaders to SPIR-V: `#include` instead of `#moj_import`, and every stage input
    // and output needs an explicit location. Sources keep the older form; this rewrites them.
    if (sc.current.parsed >= "26.3") {
        val locations =
            mapOf(
                // Vertex attributes, in the order of DefaultVertexFormat.POSITION_TEX_COLOR.
                "Position" to 0, "UV0" to 1, "Color" to 2,
                // Vertex outputs and fragment inputs.
                "discCoord" to 0, "vertexColor" to 1,
                // Fragment output.
                "fragColor" to 0,
            )
        val declaration = Regex("""^(in|out) \w+ (\w+);""")
        // Closures are not part of the task's up-to-date check; the table is.
        inputs.property("shaderLocations", locations.toString())
        filesMatching(listOf("assets/*/shaders/**/*.vsh", "assets/*/shaders/**/*.fsh")) {
            val file = path
            filter { line ->
                val variable = declaration.find(line)?.groupValues?.get(2)
                when {
                    line.startsWith("#version") ->
                        "$line\n#extension GL_ARB_separate_shader_objects : require"
                    line.startsWith("#moj_import") -> line.replaceFirst("#moj_import", "#include")
                    variable != null -> {
                        val location =
                            locations[variable] ?: error("No shader location for $variable in $file")
                        "layout(location = $location) $line"
                    }
                    else -> line
                }
            }
        }
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) { rename { "${it}_$archives" } }
}

tasks.named<Jar>("sourcesJar") {
    includeEmptyDirs = false
    from(rootProject.file("LICENSE"))
}

// Development tools live in a separate add-on mod, outside the release jar.
val main = sourceSets.main.get()
val dev =
    sourceSets.create("dev") {
        java.setSrcDirs(listOf(rootProject.file("src/dev/java")))
        resources.setSrcDirs(listOf(rootProject.file("src/dev/resources")))
        compileClasspath += main.output + main.compileClasspath
        runtimeClasspath += main.output + main.runtimeClasspath
    }

tasks.named<ProcessResources>("processDevResources") {
    inputs.property("version", version.toString())
    filesMatching("fabric.mod.json") { expand("version" to version.toString()) }
}

val devJar =
    tasks.register<Jar>("devJar") {
        archiveBaseName = "$archives-dev"
        from(dev.output)
        from(rootProject.file("LICENSE")) { rename { "${it}_$archives-dev" } }
    }

tasks.assemble {
    dependsOn(devJar)
}

loom {
    mods {
        register("trajectory_projected") { sourceSet(main) }
        register("trajectory_projected_dev") { sourceSet(dev) }
    }
    runs {
        named("client") {
            // Development runs record predicted against real arrows (trajectory-reports/).
            setSource(dev)
        }
    }
}

// Starts the built jars from a real mods folder; requires local fixtures in src/releaseSmoke.
// The release jar alone by default, with the development add-on under -PsmokeWithDev.
val releaseSmoke =
    sourceSets.create("releaseSmoke") {
        java.setSrcDirs(listOf(rootProject.file("src/releaseSmoke/java")))
        resources.setSrcDirs(listOf(rootProject.file("src/releaseSmoke/resources")))
        compileClasspath += main.output + main.compileClasspath
    }

val releaseSmokeMods: Configuration by configurations.creating { isTransitive = false }

dependencies {
    releaseSmokeMods("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    releaseSmokeMods(fabricApi.module("fabric-client-gametest-api-v1", prop("deps.fabric_api")))
}

val releaseSmokeJar =
    tasks.register<Jar>("releaseSmokeJar") {
        archiveBaseName = "$archives-release-smoke"
        destinationDirectory = layout.buildDirectory.dir("release-smoke")
        from(releaseSmoke.output)
    }

val syncReleaseSmokeMods =
    tasks.register<Sync>("syncReleaseSmokeMods") {
        from(tasks.jar, releaseSmokeJar, releaseSmokeMods)
        if (project.hasProperty("smokeWithDev")) {
            from(devJar)
        }
        into(layout.buildDirectory.dir("run/releaseSmoke/mods"))
    }

tasks.register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("runReleaseSmoke") {
    group = "verification"
    onlyIf("local fixtures are available") { !releaseSmoke.allJava.isEmpty }
    // A check, not a build step: it runs every time it is asked for.
    outputs.upToDateWhen { false }
    dependsOn(syncReleaseSmokeMods)
    jvmArgs.add("-Dfabric.client.gametest")
    if (project.hasProperty("smokeWithDev")) {
        jvmArgs.add("-Dtrajectory.smoke.dev=true")
    }
    runDir = layout.buildDirectory.dir("run/releaseSmoke")
}

// Optional local build additions (native fixtures), kept outside Git.
rootProject.file("local.fabric.gradle").takeIf { it.exists() }?.let { apply(from = it) }
