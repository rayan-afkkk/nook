package com.nook.core

object Username {
    /** Names nobody can claim (impersonation or confusion risk). */
    private val RESERVED = setOf(
        "admin", "administrator", "nook", "nookapp", "support", "help", "official", "system", "root",
        "moderator", "mod", "staff", "team", "security", "null", "undefined", "me", "you", "everyone",
    )

    fun normalize(input: String): String = input.trim().trimStart('@').lowercase()

    /** Returns an error sentence, or null when the (already normalised) name is acceptable. */
    fun validate(name: String): String? = when {
        name.length < Limits.USERNAME_MIN -> "At least ${Limits.USERNAME_MIN} characters."
        name.length > Limits.USERNAME_MAX -> "At most ${Limits.USERNAME_MAX} characters."
        name.first() !in 'a'..'z' -> "Start with a letter."
        !Regex("^[a-z0-9._]+$").matches(name) -> "Only letters, numbers, dots and underscores."
        name.endsWith(".") || name.endsWith("_") -> "Can't end with a dot or underscore."
        name.contains("..") -> "Can't have two dots in a row."
        name in RESERVED -> "That one's reserved."
        else -> null
    }

    /** Must stay in sync with the regex in firestore.rules. */
    const val PATTERN = "^[a-z][a-z0-9._]{1,18}[a-z0-9]$"
}
