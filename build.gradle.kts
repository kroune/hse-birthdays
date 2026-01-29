plugins {
    kotlin("jvm") version "2.2.20" apply true
    kotlin("plugin.serialization") version "2.2.20" apply false
    id("com.google.devtools.ksp") version "2.2.0-2.0.2" apply false
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}
