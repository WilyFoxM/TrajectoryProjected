plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2"

stonecutter parameters {
    // Version-specific values from `stonecutter.properties.toml`.
    properties {
        tags(current.version)
    }

    // 26.3 moved the GPU API from com.mojang.blaze3d into com.mojang.renderpearl.api. Sources keep
    // the 26.2 names; the swap covers imports and mixin descriptors alike.
    val renderpearl = current.parsed >= "26.3"
    for ((old, new) in listOf(
        "com.mojang.blaze3d.buffers.GpuBuffer" to "com.mojang.renderpearl.api.buffers.GpuBuffer",
        "com.mojang.blaze3d.systems.RenderPass" to "com.mojang.renderpearl.api.commands.RenderPass",
        "com.mojang.blaze3d.pipeline.RenderPipeline" to "com.mojang.renderpearl.api.pipeline.RenderPipeline",
        "com.mojang.blaze3d.pipeline.DepthStencilState" to "com.mojang.renderpearl.api.pipeline.DepthStencilState",
        "com.mojang.blaze3d.platform.CompareOp" to "com.mojang.renderpearl.api.pipeline.CompareOp",
    )) {
        replacements {
            string(renderpearl) { replace(old, new) }
            string(renderpearl) { replace(old.replace('.', '/'), new.replace('.', '/')) }
        }
    }
}

// Formatting: google-java-format over every Java source. Spotless is not used, because it
// makes the Stonecutter root buildable and only formats files inside one project's directory.
repositories {
    mavenCentral()
}

val googleJavaFormat: Configuration by configurations.creating {
    // The root has no Java plugin, so ask for the standard JVM runtime variants explicitly.
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}

dependencies {
    googleJavaFormat("com.google.googlejavaformat:google-java-format:1.31.0")
}

val javaSources = fileTree(rootDir) {
    include("src/**/*.java")
    // Version seams hold Stonecutter conditions (`//? if`), which google-java-format would turn
    // into plain comments (`// ? if`).
    exclude("src/main/java/ru/wilyfox/trajectory/**/compat/**")
}

fun registerFormat(name: String, description: String, vararg mode: String) =
    tasks.register<JavaExec>(name) {
        group = "formatting"
        this.description = description
        classpath = googleJavaFormat
        mainClass = "com.google.googlejavaformat.java.Main"
        jvmArgs(
            listOf("api", "code", "file", "parser", "tree", "util").map {
                "--add-exports=jdk.compiler/com.sun.tools.javac.$it=ALL-UNNAMED"
            },
        )
        inputs.files(javaSources)
        argumentProviders.add(CommandLineArgumentProvider { mode.toList() + javaSources.files.map { it.path } })
    }

registerFormat("formatCheck", "Fails if any Java source is not google-java-format clean.", "--dry-run", "--set-exit-if-changed")
registerFormat("format", "Rewrites Java sources with google-java-format.", "--replace")
