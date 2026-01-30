plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
    id("io.ktor.plugin")
    id("eu.vendeli.telegram-bot")
    application
}

group = "io.github.kroune"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":users-database"))
}

application {
    mainClass.set("io.github.kroune.bot.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}