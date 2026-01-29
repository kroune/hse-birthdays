plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.2.20"
    id("com.google.devtools.ksp") version "2.2.0-2.0.2"
}

group = "io.github.kroune"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("eu.vendeli:telegram-bot:8.4.1")
    ksp("eu.vendeli:ksp:8.4.1")

    implementation(project(":common"))
    implementation(project(":users-database"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}