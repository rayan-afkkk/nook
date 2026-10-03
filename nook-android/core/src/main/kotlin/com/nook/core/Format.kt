package com.nook.core

import kotlin.math.abs

object Format {
    fun bytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        val digits = if (value >= 100 || unit == 0) 0 else 1
        return "%.${digits}f %s".format(value, units[unit])
    }

    fun initials(name: String?): String {
        val parts = (name ?: "").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val first = parts.firstOrNull()?.take(1) ?: ""
        val last = if (parts.size > 1) parts.last().take(1) else ""
        return (first + last).uppercase().ifEmpty { "?" }
    }

    /** Stable small hash used to pick a pastel colour for a user. */
    fun hashString(input: String): Int {
        var h = 0
        for (c in input) h = h * 31 + c.code
        return abs(h % Int.MAX_VALUE)
    }
}
