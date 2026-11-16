pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Fabric Loom for Minecraft 26.x: official names, no remapping.
        id("net.fabricmc.fabric-loom") version "1.17.21"
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // One node per Minecraft version, all built by build.fabric.gradle.kts.
        version("26.1.2", "26.1.2").buildscript("build.fabric.gradle.kts")
        version("26.2", "26.2").buildscript("build.fabric.gradle.kts")
        version("26.3", "26.3").buildscript("build.fabric.gradle.kts")
        vcsVersion = "26.2"
    }
}

rootProject.name = "TrajectoryProjected"
