plugins {
    kotlin("jvm")
}

group = "io.github.kroune"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.21")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}