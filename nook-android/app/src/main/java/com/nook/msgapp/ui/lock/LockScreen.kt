package com.nook.msgapp.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nook.core.LockCrypto
import com.nook.core.LockKind
import com.nook.msgapp.data.Auth
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.lock.Biometrics
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.ui.LocalActivity
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.NookLogo
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen overlay shown while NOOK is locked. Nothing underneath is visible or reachable. */
@Composable
fun LockScreen() {
    val c = Nook.colors
    val activity = LocalActivity.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val record by LockStore.record.collectAsState()
    val attempts by LockStore.attempts.collectAsState()

    var value by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorKey by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var forgotOpen by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    val prompted = remember { booleanArrayOf(false) }

    // Cooldown countdown after too many wrong tries.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(attempts.until) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= attempts.until) break
            delay(250)
        }
    }
    val waitSeconds = ((LockStore.cooldownRemaining(now) + 999) / 1000).toInt()

    val isPin = record?.kind != LockKind.Password
    val bioAvailable = remember { Biometrics.available(context) }
    val biometricOn = record?.biometric == true && bioAvailable

    // The system back button must not dismiss the lock.
    BackHandler(enabled = true) {}

    fun tryBiometrics() {
        runCatching {
            Biometrics.prompt(activity) { ok ->
                if (ok) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    LockStore.unlockedByBiometrics()
                }
            }
        }
    }

    // Offer biometrics straight away the first time the lock shows.
    LaunchedEffect(biometricOn) {
        if (biometricOn && !prompted[0]) {
            prompted[0] = true
            delay(300) // let the activity finish resuming before the system sheet opens
            tryBiometrics()
        }
    }

    fun submit(secret: String) {
        if (busy || secret.isEmpty() || waitSeconds > 0) return
        busy = true
        scope.launch {
            val ok = try {
                LockStore.unlockWithSecret(secret)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                false
            }
            busy = false
            if (ok) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                errorKey += 1
                value = ""
                now = System.currentTimeMillis()
                val left = LockCrypto.FREE_ATTEMPTS - LockStore.attempts.value.failed
                message = if (left > 0) {
                    "That's not it. $left ${if (left == 1) "try" else "tries"} left before a short wait."
                } else {
                    "Too many tries. Take a breath and try again shortly."
                }
            }
        }
    }

    fun onDigit(d: String) {
        if (waitSeconds > 0 || busy) return
        val next = (value + d).take(32)
        value = next
        message = null
        val length = record?.pinLength
        if (length != null && next.length == length) submit(next)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            // Swallow every touch so nothing behind the lock can be reached.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                NookLogo(size = 52.dp)
                NText("Welcome back", NookType.title, textAlign = TextAlign.Center)
                val line = when {
                    waitSeconds > 0 -> "Try again in ${waitSeconds}s"
                    message != null -> message.orEmpty()
                    isPin -> "Enter your PIN to unlock NOOK."
                    else -> "Enter your password to unlock NOOK."
                }
                AnimatedContent(
                    targetState = line,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "lock-line",
                ) { text ->
                    NText(
                        text,
                        NookType.body,
                        if (message != null || waitSeconds > 0) c.danger else c.textMuted,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (isPin) {
                Column(
                    Modifier.fillMaxWidth().padding(top = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    PinDots(
                        length = record?.pinLength ?: 6,
                        filled = value.length,
                        errorKey = errorKey,
                        busy = busy,
                    )
                    val leftKey = when {
                        biometricOn -> PinKey(Icons.Rounded.Fingerprint, "Unlock with fingerprint") { tryBiometrics() }
                        record?.pinLength == null && value.isNotEmpty() -> PinKey(Icons.Rounded.Check, "Unlock") { submit(value) }
                        else -> null
                    }
                    PinPad(
                        onDigit = { d -> onDigit(d) },
                        onDelete = { value = value.dropLast(1) },
                        leftKey = leftKey,
                        disabled = waitSeconds > 0 || busy,
                    )
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(biometricOn) {
                    if (!biometricOn) {
                        delay(300)
                        runCatching { focus.requestFocus() }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(top = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    NTextField(
                        value = value,
                        onValueChange = {
                            if (waitSeconds == 0 && !busy) {
                                value = it
                                message = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        label = "Password",
                        password = true,
                        imeAction = ImeAction.Go,
                        onSubmit = { submit(value) },
                        maxLength = 64,
                    )
                    NButton(
                        title = "Unlock",
                        onClick = { submit(value) },
                        icon = Icons.Rounded.LockOpen,
                        loading = busy,
                        enabled = waitSeconds == 0 && value.isNotEmpty(),
                    )
                    if (biometricOn) {
                        NButton(
                            title = "Use fingerprint",
                            onClick = { tryBiometrics() },
                            icon = Icons.Rounded.Fingerprint,
                            variant = ButtonVariant.Secondary,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .pressScale { forgotOpen = true }
                    .padding(horizontal = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                NText(if (isPin) "Forgot PIN?" else "Forgot password?", NookType.label, c.textMuted)
            }
        }
    }

    if (forgotOpen) {
        AlertDialog(
            onDismissRequest = { if (!signingOut) forgotOpen = false },
            containerColor = c.surface,
            titleContentColor = c.text,
            textContentColor = c.textMuted,
            title = { NText("Reset your lock", NookType.headline) },
            text = {
                NText(
                    "You'll be signed out on this phone. Sign back in with Google and set a new PIN or password. " +
                        "Your chats stay in your account.",
                    NookType.body,
                    c.textMuted,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !signingOut,
                    onClick = {
                        signingOut = true
                        scope.launch {
                            try {
                                // Clearing the lock removes this screen mid-way, so finish regardless.
                                withContext(NonCancellable) { Auth.signOut(context.applicationContext) }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                Toasts.error(e)
                                signingOut = false
                            }
                        }
                    },
                ) {
                    if (signingOut) {
                        CircularProgressIndicator(color = c.danger, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        NText("Sign out and reset", NookType.label, c.danger)
                    }
                }
            },
            dismissButton = {
                TextButton(enabled = !signingOut, onClick = { forgotOpen = false }) {
                    NText("Cancel", NookType.label, c.text)
                }
            },
        )
    }
}
