package io.github.kroune

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Env {
    private val dotenv: Map<String, String> by lazy { loadDotenv() }

    fun get(name: String): String? {
        val envValue = System.getenv(name)
        if (!envValue.isNullOrBlank()) {
            return envValue
        }
        return dotenv[name]
    }

    fun require(name: String): String {
        val value = get(name)
        require(!value.isNullOrBlank()) { "Missing required environment variable: $name" }
        return value!!
    }

    private fun loadDotenv(): Map<String, String> {
        val path = findDotenvPath() ?: return emptyMap()
        return parseDotenv(path)
    }

    private fun findDotenvPath(): Path? {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (true) {
            val candidate = current.resolve(".env")
            if (Files.isRegularFile(candidate)) {
                return candidate
            }
            val parent = current.parent ?: return null
            if (parent == current) {
                return null
            }
            current = parent
        }
    }

    private fun parseDotenv(path: Path): Map<String, String> {
        val result = mutableMapOf<String, String>()
        Files.readAllLines(path, StandardCharsets.UTF_8).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@forEach
            }
            val withoutExport = if (trimmed.startsWith("export ")) {
                trimmed.removePrefix("export ").trim()
            } else {
                trimmed
            }
            val idx = withoutExport.indexOf('=')
            if (idx <= 0) {
                return@forEach
            }
            val key = withoutExport.substring(0, idx).trim()
            if (key.isEmpty()) {
                return@forEach
            }
            val rawValue = withoutExport.substring(idx + 1).trim()
            val value = unquote(rawValue)
            result.putIfAbsent(key, value)
        }
        return result
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                val inner = value.substring(1, value.length - 1)
                return inner
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\'", "'")
            }
        }
        return value
    }
}
