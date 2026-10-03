package com.nook.msgapp.data

import android.content.Context
import android.content.SharedPreferences
import com.nook.core.Disappearing
import com.nook.msgapp.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-sensitive device preferences. Never synced to Firebase, so changing them costs no writes.
 */
object Prefs {
    private lateinit var sp: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.Dark)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** The intro slides have been completed or skipped at least once on this device. */
    private val _onboardingSeen = MutableStateFlow(false)
    val onboardingSeen: StateFlow<Boolean> = _onboardingSeen.asStateFlow()

    /** The permission primer has been shown (each permission may still be denied). */
    private val _permissionsPrimed = MutableStateFlow(false)
    val permissionsPrimed: StateFlow<Boolean> = _permissionsPrimed.asStateFlow()

    /** Hide app content in the recent-apps switcher (FLAG_SECURE). */
    private val _hideInSwitcher = MutableStateFlow(true)
    val hideInSwitcher: StateFlow<Boolean> = _hideInSwitcher.asStateFlow()

    /** Timer applied to new chats you create. */
    private val _disappearingDefault = MutableStateFlow(Disappearing.Off)
    val disappearingDefault: StateFlow<Disappearing> = _disappearingDefault.asStateFlow()

    /** Show who sent a message in notifications. */
    private val _messageNotifications = MutableStateFlow(true)
    val messageNotifications: StateFlow<Boolean> = _messageNotifications.asStateFlow()

    fun init(context: Context) {
        sp = context.getSharedPreferences("nook.prefs.v1", Context.MODE_PRIVATE)
        _theme.value = ThemeMode.from(sp.getString("theme", "dark"))
        _onboardingSeen.value = sp.getBoolean("onboardingSeen", false)
        _permissionsPrimed.value = sp.getBoolean("permissionsPrimed", false)
        _hideInSwitcher.value = sp.getBoolean("hideInSwitcher", true)
        _disappearingDefault.value = Disappearing.from(sp.getString("disappearingDefault", "off"))
        _messageNotifications.value = sp.getBoolean("messageNotifications", true)
    }

    fun setTheme(v: ThemeMode) { _theme.value = v; sp.edit().putString("theme", v.wire).apply() }
    fun setOnboardingSeen(v: Boolean) { _onboardingSeen.value = v; sp.edit().putBoolean("onboardingSeen", v).apply() }
    fun setPermissionsPrimed(v: Boolean) { _permissionsPrimed.value = v; sp.edit().putBoolean("permissionsPrimed", v).apply() }
    fun setHideInSwitcher(v: Boolean) { _hideInSwitcher.value = v; sp.edit().putBoolean("hideInSwitcher", v).apply() }
    fun setDisappearingDefault(v: Disappearing) { _disappearingDefault.value = v; sp.edit().putString("disappearingDefault", v.wire).apply() }
    fun setMessageNotifications(v: Boolean) { _messageNotifications.value = v; sp.edit().putBoolean("messageNotifications", v).apply() }

    /** Called on sign-out: keeps device-level choices but resets the per-account flow. */
    fun resetForSignOut() = setPermissionsPrimed(false)
}
