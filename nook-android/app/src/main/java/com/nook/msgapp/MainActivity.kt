package com.nook.msgapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.nav.DeepLinks
import com.nook.msgapp.ui.NookRoot
import com.nook.msgapp.ui.theme.NookTheme
import com.nook.msgapp.ui.theme.ThemeMode

/** Single activity. FragmentActivity because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        DeepLinks.fromIntent(intent)
        setContent {
            val mode by Prefs.theme.collectAsState()
            val hide by Prefs.hideInSwitcher.collectAsState()
            val dark = when (mode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            LaunchedEffect(dark) {
                // Status and navigation bars follow the NOOK theme (no grey/blue system bars).
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(hide) {
                // Hide chats from the recent-apps switcher and screenshots when enabled.
                if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            NookTheme(mode) {
                NookRoot(this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.fromIntent(intent)
    }
}
