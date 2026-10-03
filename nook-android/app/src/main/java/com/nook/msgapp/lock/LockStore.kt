package com.nook.msgapp.lock

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nook.core.Limits
import com.nook.core.LockCrypto
import com.nook.core.LockKind
import com.nook.core.LockRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class Attempts(val failed: Int = 0, val until: Long = 0)

/**
 * The on-device app lock. Only a salted PBKDF2 hash is stored (app-private storage);
 * the PIN/password itself never leaves memory.
 */
object LockStore {
    private lateinit var sp: SharedPreferences

    private val _record = MutableStateFlow<LockRecord?>(null)
    val record: StateFlow<LockRecord?> = _record.asStateFlow()

    /** null until storage has been read. */
    private val _configured = MutableStateFlow<Boolean?>(null)
    val configured: StateFlow<Boolean?> = _configured.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _attempts = MutableStateFlow(Attempts())
    val attempts: StateFlow<Attempts> = _attempts.asStateFlow()

    private var backgroundedAt = 0L

    fun init(context: Context) {
        sp = context.getSharedPreferences("nook.lock.v1", Context.MODE_PRIVATE)
        val r = LockRecord.parse(sp.getString("record", null))
        _record.value = r
        _attempts.value = Attempts(sp.getInt("failed", 0), sp.getLong("until", 0))
        _configured.value = r != null
        // Cold start: always locked when a lock exists.
        _locked.value = r != null
    }

    private fun saveAttempts(a: Attempts) {
        _attempts.value = a
        sp.edit().putInt("failed", a.failed).putLong("until", a.until).apply()
    }

    suspend fun setLock(kind: LockKind, secret: String, biometric: Boolean) {
        val r = withContext(Dispatchers.Default) { LockCrypto.createRecord(kind, secret, biometric) }
        sp.edit().putString("record", r.serialize()).commit()
        saveAttempts(Attempts())
        _record.value = r
        _configured.value = true
        _locked.value = false
    }

    fun setBiometric(enabled: Boolean) {
        val r = _record.value?.copy(biometric = enabled) ?: return
        sp.edit().putString("record", r.serialize()).apply()
        _record.value = r
    }

    fun cooldownRemaining(now: Long = System.currentTimeMillis()): Long = (_attempts.value.until - now).coerceAtLeast(0)

    /** Returns true on success; wrong secrets are counted and throttled. */
    suspend fun unlockWithSecret(secret: String): Boolean {
        val r = _record.value ?: return true
        if (cooldownRemaining() > 0) return false
        val ok = withContext(Dispatchers.Default) { LockCrypto.verify(r, secret) }
        if (ok) {
            saveAttempts(Attempts())
            _locked.value = false
        } else {
            val failed = _attempts.value.failed + 1
            saveAttempts(Attempts(failed, System.currentTimeMillis() + LockCrypto.cooldownMs(failed)))
        }
        return ok
    }

    /** Checks the secret without changing lock state (e.g. before turning the lock off). */
    suspend fun verify(secret: String): Boolean {
        val r = _record.value ?: return false
        return withContext(Dispatchers.Default) { LockCrypto.verify(r, secret) }
    }

    fun unlockedByBiometrics() {
        saveAttempts(Attempts())
        _locked.value = false
    }

    fun lock() {
        if (_record.value != null) _locked.value = true
    }

    /** Used by "Forgot PIN" and sign-out: removes the local lock entirely. */
    fun clear() {
        sp.edit().clear().commit()
        _record.value = null
        _attempts.value = Attempts()
        _configured.value = false
        _locked.value = false
    }

    private var skipNext = false

    fun onBackground() {
        backgroundedAt = System.currentTimeMillis()
    }

    fun onForeground() {
        val away = if (backgroundedAt > 0) System.currentTimeMillis() - backgroundedAt else 0
        if (!skipNext && away > Limits.LOCK_BACKGROUND_TIMEOUT_MS) lock()
        skipNext = false
        backgroundedAt = 0
    }

    /** Don't re-lock on the next return (the system photo/file picker or a call screen was open). */
    fun skipNextBackgroundLock() {
        skipNext = true
    }
}

object Biometrics {
    private const val AUTH = BIOMETRIC_STRONG or BIOMETRIC_WEAK

    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, title: String = "Unlock NOOK", onResult: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText("Use PIN or password")
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}
