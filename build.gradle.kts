import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application

    kotlin("jvm")

    id("com.github.johnrengelman.shadow")
}

group = "io.github.smalauncher"
version = "0.2.0"

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots") // for Kord snapshots
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.5.6")
    compileOnly("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.kohsuke:github-api:1.314")
    implementation("org.slf4j:slf4j-simple:2.0.5")
}

configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(10, "seconds")
    resolutionStrategy.cacheChangingModulesFor(10, "seconds")
}

application {
    @Suppress("DEPRECATION") // Shadow plugin requires this
    mainClassName = "io.github.smalauncher.smars.MainKt"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"

    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.github.smalauncher.smars.MainKt"
        )
    }
}
