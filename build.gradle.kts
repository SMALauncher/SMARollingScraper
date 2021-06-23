plugins {
    kotlin("jvm") version "1.5.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("dev.kord:kord-core:0.7.0")
    implementation("io.fusionauth:fusionauth-jwt:4.2.0")
    implementation("org.kohsuke:github-api:1.131")
}
