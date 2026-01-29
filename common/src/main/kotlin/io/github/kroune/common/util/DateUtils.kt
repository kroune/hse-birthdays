package io.github.kroune.common.util

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/**
 * Utility functions for date operations
 */
object DateUtils {

    /**
     * Get current date in system timezone
     */
    fun today(): LocalDate {
        return LocalDate.now()
    }

    /**
     * Parse birth date from string format (yyyy-MM-dd)
     * Handles both normal dates and 0000-MM-dd format
     */
    fun parseBirthDate(birthDate: String?): LocalDate? {
        if (birthDate.isNullOrBlank()) return null

        return runCatching {
            if (birthDate.length >= 10) {
                val parts = birthDate.split("-")
                if (parts.size == 3) {
                    val year = parts[0].toIntOrNull() ?: return null
                    val month = parts[1].toIntOrNull() ?: return null
                    val day = parts[2].toIntOrNull() ?: return null

                    if (month in 1..12 && day in 1..31) {
                        // Use 2000 as default year for 0000 format
                        return LocalDate.of(if (year == 0) 2000 else year, month, day)
                    }
                }
            }
            null
        }.getOrNull()
    }

    /**
     * Format birth date for display, handling the case when year is not set (0000-MM-DD)
     * Returns formatted string in DD.MM or DD.MM.YYYY format depending on whether year is known
     */
    fun formatBirthDate(birthDateStr: String?): String? {
        if (birthDateStr == null) return null

        return runCatching {
            val date = parseBirthDate(birthDateStr) ?: return null
            val month = date.monthValue.toString().padStart(2, '0')
            val day = date.dayOfMonth.toString().padStart(2, '0')

            // If original string starts with "0000-", don't include year in display
            if (birthDateStr.startsWith("0000-")) {
                "$day.$month"
            } else {
                "$day.$month.${date.year}"
            }
        }.getOrNull()
    }

    /**
     * Check if a birth date matches today's date (month-day comparison)
     */
    fun isBirthdayToday(birthDate: String?): Boolean {
        if (birthDate.isNullOrBlank()) return false

        return runCatching {
            if (birthDate.length >= 10) {
                val birthMonthDay = birthDate.substring(5, 10) // MM-dd
                val today = today()
                val todayMonthDay = "${today.monthValue.toString().padStart(2, '0')}-${today.dayOfMonth.toString().padStart(2, '0')}"
                birthMonthDay == todayMonthDay
            } else {
                false
            }
        }.getOrElse { false }
    }

    /**
     * Calculate age from birth date string
     * Returns null if the year is 0000 (malformed date) or if parsing fails
     */
    fun calculateAge(birthDateStr: String?): Int? {
        if (birthDateStr.isNullOrBlank()) return null

        return runCatching {
            // Check if year is 0000 (malformed date)
            if (birthDateStr.startsWith("0000-")) {
                return null
            }

            val birthDate = parseBirthDate(birthDateStr) ?: return null
            val today = today()
            Period.between(birthDate, today).years
        }.getOrNull()
    }

    /**
     * Calculate days until next birthday
     */
    fun daysUntilBirthday(birthDate: String?): Long? {
        val parsed = parseBirthDate(birthDate) ?: return null
        val today = today()

        var nextBirthday = LocalDate.of(today.year, parsed.monthValue, parsed.dayOfMonth)
        if (nextBirthday.isBefore(today) || nextBirthday.isEqual(today)) {
            nextBirthday = LocalDate.of(today.year + 1, parsed.monthValue, parsed.dayOfMonth)
        }

        return ChronoUnit.DAYS.between(today, nextBirthday)
    }
}
