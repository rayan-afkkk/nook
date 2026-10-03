package com.nook.core

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class LockKind(val wire: String) {
    Pin("pin"), Password("password");

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.wire == s }
    }
}

/** What is kept on the device. The secret itself is never stored. */
data class LockRecord(
    val kind: LockKind,
    /** hex, 16 random bytes */
    val salt: String,
    /** hex, PBKDF2-HMAC-SHA256 output */
    val hash: String,
    val iterations: Int,
    val biometric: Boolean,
    /** PIN only: lets the lock screen submit as soon as enough digits are typed. */
    val pinLength: Int? = null,
) {
    /** Simple line format: no JSON library needed. */
    fun serialize(): String = listOf("1", kind.wire, salt, hash, iterations, if (biometric) 1 else 0, pinLength ?: "").joinToString("|")

    companion object {
        fun parse(raw: String?): LockRecord? {
            if (raw.isNullOrEmpty()) return null
            val p = raw.split("|")
            if (p.size != 7 || p[0] != "1") return null
            val kind = LockKind.from(p[1]) ?: return null
            val iterations = p[4].toIntOrNull() ?: return null
            if (p[2].isEmpty() || p[3].isEmpty()) return null
            return LockRecord(kind, p[2], p[3], iterations, p[5] == "1", p[6].toIntOrNull())
        }
    }
}

object LockCrypto {
    /** Native PBKDF2 is fast, so the work factor can be much higher than the old JS one. */
    const val ITERATIONS = 210_000
    const val FREE_ATTEMPTS = 5

    fun validateSecret(kind: LockKind, secret: String): String? = when {
        secret.length < Limits.LOCK_MIN_LENGTH ->
            "Use at least ${Limits.LOCK_MIN_LENGTH} ${if (kind == LockKind.Pin) "digits" else "characters"}."
        kind == LockKind.Pin && !secret.all { it in '0'..'9' } -> "A PIN can only contain numbers."
        kind == LockKind.Pin && secret.all { it == secret[0] } -> "Avoid repeating the same digit."
        kind == LockKind.Pin && ("0123456789".contains(secret) || "9876543210".contains(secret)) ->
            "Avoid simple sequences like 123456."
        secret.length > 64 -> "Keep it under 64 characters."
        else -> null
    }

    fun deriveHash(secret: String, saltHex: String, iterations: Int): String {
        val spec = PBEKeySpec(secret.toCharArray(), hexToBytes(saltHex), iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return bytesToHex(key)
    }

    fun createRecord(kind: LockKind, secret: String, biometric: Boolean, iterations: Int = ITERATIONS): LockRecord {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = bytesToHex(salt)
        return LockRecord(
            kind = kind,
            salt = saltHex,
            hash = deriveHash(secret, saltHex, iterations),
            iterations = iterations,
            biometric = biometric,
            pinLength = if (kind == LockKind.Pin) secret.length else null,
        )
    }

    /** Constant-time comparison of two hex digests. */
    fun safeEqual(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    fun verify(record: LockRecord, secret: String): Boolean =
        safeEqual(deriveHash(secret, record.salt, record.iterations), record.hash)

    /** Wrong-attempt throttling: free tries, then a growing cooldown (30s, 60s, 120s ... max 15 min). */
    fun cooldownMs(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0
        val steps = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(10)
        return minOf(30_000L shl steps, 15 * 60_000L)
    }

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
