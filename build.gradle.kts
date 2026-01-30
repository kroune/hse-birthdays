plugins {
    kotlin("jvm") version "2.3.0" apply true
    kotlin("plugin.serialization") version "2.3.0" apply false
    id("com.google.devtools.ksp") version "2.3.4" apply false
    id("io.ktor.plugin") version "3.4.0" apply false
    id("eu.vendeli.telegram-bot") version "9.1.1" apply false
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}
