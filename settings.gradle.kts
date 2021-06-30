pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
    }

    plugins {
        kotlin("jvm") version "1.5.10"

        id("com.github.johnrengelman.shadow") version "5.2.0"
    }
}

rootProject.name = "SMARollingScraper"

