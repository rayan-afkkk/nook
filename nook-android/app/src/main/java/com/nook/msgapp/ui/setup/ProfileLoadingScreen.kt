package com.nook.msgapp.ui.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nook.msgapp.data.Auth
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NookLogo
import com.nook.msgapp.ui.components.Skeleton
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown while we wait for the server to confirm the profile (first sign-in on a device). */
@Composable
fun ProfileLoadingScreen() {
    val c = Nook.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slow by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(6000)
        slow = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
    ) {
        NookLogo(size = 60.dp)
        Skeleton(Modifier.size(width = 180.dp, height = 16.dp))
        Skeleton(Modifier.size(width = 120.dp, height = 12.dp))
        AnimatedVisibility(
            visible = slow,
            enter = fadeIn(tween(260)) + slideInVertically(tween(300)) { it / 4 },
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Spacer(Modifier.height(Spacing.md))
                NText(
                    "Still connecting… NOOK needs a connection the first time you sign in on this phone. " +
                        "If it doesn't load, check your connection, or sign out and try again.",
                    NookType.body,
                    c.textMuted,
                    textAlign = TextAlign.Center,
                )
                NButton(
                    title = "Sign out",
                    variant = ButtonVariant.Secondary,
                    loading = signingOut,
                    onClick = {
                        if (!signingOut) {
                            signingOut = true
                            scope.launch {
                                try {
                                    // Finish even if this screen goes away mid-way (the stage changes as state is cleared).
                                    withContext(NonCancellable) { Auth.signOut(context.applicationContext) }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Throwable) {
                                    Toasts.error(e)
                                    signingOut = false
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
