package io.github.kroune.common.logging

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Factory for creating loggers for different modules
 */
object Loggers {
    val database = KotlinLogging.logger("database")
    val scraping = KotlinLogging.logger("scraping")
    val bot = KotlinLogging.logger("bot")
    val scheduler = KotlinLogging.logger("scheduler")
    val search = KotlinLogging.logger("search")
    val command = KotlinLogging.logger("command")
}
