plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "vibecode-3000"

include("hse-scraping")
include("users-database")
include("telegram-bot")
include("common")