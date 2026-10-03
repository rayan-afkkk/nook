package com.nook.msgapp.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nook.core.Limits
import com.nook.core.LockCrypto
import com.nook.core.LockKind
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.lock.Biometrics
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.NToggle
import com.nook.msgapp.ui.components.Segmented
import com.nook.msgapp.ui.setup.StepHeader
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Phase { Verify, Enter, Confirm, Biometric }

private const val BIO_LABEL = "Fingerprint"
private const val MAX_PIN = 16

private fun LockKind.noun() = if (this == LockKind.Pin) "PIN" else "password"

/**
 * Choose a PIN or password, type it twice, optionally turn on fingerprint unlock, then save it with
 * [LockStore.setLock]. Used by first-run setup ([step] = 3 shows the setup progress header) and by
 * settings to change the lock ([verifyCurrent] = true asks for the current secret first).
 */
@Composable
fun LockSetupFlow(
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null,
    title: String = "Lock NOOK",
    modifier: Modifier = Modifier,
    step: Int? = null,
    verifyCurrent: Boolean = false,
) {
    val c = Nook.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val current by LockStore.record.collectAsState()

    var phase by remember { mutableStateOf(if (verifyCurrent && LockStore.record.value != null) Phase.Verify else Phase.Enter) }
    var kind by remember { mutableStateOf(LockKind.Pin) }
    var first by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var errorKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val bioAvailable = remember { Biometrics.available(context) }
    var useBio by remember { mutableStateOf(bioAvailable && (LockStore.record.value?.biometric ?: true)) }

    val currentKind = current?.kind ?: LockKind.Pin

    fun fail(message: String) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        error = message
        errorKey += 1
        value = ""
    }

    fun save(biometric: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                LockStore.setLock(kind, first, biometric)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (verifyCurrent) Toasts.show("App lock updated.")
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Toasts.error(e)
                busy = false
            }
        }
    }

    fun next(input: String = value) {
        if (busy) return
        error = null
        when (phase) {
            Phase.Verify -> {
                busy = true
                scope.launch {
                    val ok = try {
                        LockStore.verify(input)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        false
                    }
                    busy = false
                    if (!ok) {
                        fail("That's not your current ${currentKind.noun()}.")
                    } else {
                        value = ""
                        phase = Phase.Enter
                    }
                }
            }
            Phase.Enter -> {
                val problem = LockCrypto.validateSecret(kind, input)
                if (problem != null) {
                    fail(problem)
                } else {
                    first = input
                    value = ""
                    phase = Phase.Confirm
                }
            }
            Phase.Confirm -> {
                if (input != first) {
                    first = ""
                    phase = Phase.Enter
                    fail("Those didn't match. Let's start again.")
                } else if (bioAvailable) {
                    phase = Phase.Biometric
                } else {
                    save(false)
                }
            }
            Phase.Biometric -> save(useBio)
        }
    }

    fun back() {
        when (phase) {
            Phase.Confirm, Phase.Biometric -> {
                first = ""
                value = ""
                error = null
                phase = Phase.Enter
            }
            else -> onCancel?.invoke()
        }
    }

    BackHandler(enabled = !busy && (phase == Phase.Confirm || phase == Phase.Biometric || onCancel != null)) { back() }

    Column(modifier.fillMaxSize()) {
        if (onCancel != null) {
            Row(Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                NIconButton(Icons.Rounded.Close, "Cancel", onClick = { onCancel() }, enabled = !busy)
            }
        }

        AnimatedContent(
            targetState = phase,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(260)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(200)) { w -> if (forward) -w / 6 else w / 6 } + fadeOut(tween(160)))
            },
            label = "lock-setup",
        ) { p ->
            val inputKind = if (p == Phase.Verify) currentKind else kind
            val headingText = when (p) {
                Phase.Verify -> "Enter current ${currentKind.noun()}"
                Phase.Enter -> if (verifyCurrent) "New ${kind.noun()}" else title
                Phase.Confirm -> "Confirm your ${kind.noun()}"
                Phase.Biometric -> "Use ${BIO_LABEL.lowercase()}?"
            }
            val subtitle = when (p) {
                Phase.Verify -> "Confirm it's you before changing the lock."
                Phase.Enter -> "Choose a PIN or password of at least ${Limits.LOCK_MIN_LENGTH} characters. It stays on this phone and is never uploaded."
                Phase.Confirm -> "Type it once more so we know it's right."
                Phase.Biometric -> "Unlock in a touch. Your ${kind.noun()} still works as a backup."
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (step != null) {
                    StepHeader(step = step, total = 3, title = headingText, subtitle = subtitle)
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        NText(headingText, NookType.headline, modifier = Modifier.semantics { heading() })
                        NText(subtitle, NookType.body, c.textMuted)
                    }
                }

                when {
                    p == Phase.Biometric -> {
                        NCard(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = c.text, modifier = Modifier.size(26.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    NText("$BIO_LABEL unlock", NookType.bodyBold)
                                    NText("Uses the ${BIO_LABEL.lowercase()} saved on this phone.", NookType.caption, c.textMuted)
                                }
                                NToggle(checked = useBio, onCheckedChange = { useBio = it }, enabled = !busy)
                            }
                        }
                    }
                    inputKind == LockKind.Pin -> {
                        if (p == Phase.Enter) KindPicker(kind) { k ->
                            kind = k
                            value = ""
                            error = null
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            PinDots(
                                length = maxOf(Limits.LOCK_MIN_LENGTH, value.length),
                                filled = value.length,
                                errorKey = errorKey,
                                busy = busy,
                            )
                            val hint = when {
                                error != null -> error.orEmpty()
                                p == Phase.Verify -> " "
                                else -> "${Limits.LOCK_MIN_LENGTH} digits or more"
                            }
                            NText(
                                hint,
                                NookType.caption,
                                if (error != null) c.danger else c.textMuted,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                            PinPad(
                                disabled = busy,
                                onDigit = { d ->
                                    error = null
                                    val v = (value + d).take(MAX_PIN)
                                    value = v
                                    if (p == Phase.Verify && current?.pinLength == v.length) next(v)
                                },
                                onDelete = { value = value.dropLast(1) },
                            )
                        }
                    }
                    else -> {
                        if (p == Phase.Enter) KindPicker(kind) { k ->
                            kind = k
                            value = ""
                            error = null
                        }
                        val focus = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            delay(280)
                            runCatching { focus.requestFocus() }
                        }
                        NTextField(
                            value = value,
                            onValueChange = {
                                value = it
                                error = null
                            },
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            label = when (p) {
                                Phase.Confirm -> "Confirm password"
                                Phase.Verify -> "Current password"
                                else -> "Password"
                            },
                            password = true,
                            error = error,
                            helper = if (p == Phase.Verify) null else "At least ${Limits.LOCK_MIN_LENGTH} characters.",
                            imeAction = ImeAction.Next,
                            onSubmit = { next() },
                            maxLength = 64,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }

        Box(Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.md)) {
            if (phase == Phase.Biometric) {
                NButton(title = "Finish", onClick = { save(useBio) }, icon = Icons.Rounded.Check, loading = busy)
            } else {
                val finishing = phase == Phase.Confirm && !bioAvailable
                val minLength = if (phase == Phase.Verify) 1 else Limits.LOCK_MIN_LENGTH
                NButton(
                    title = if (finishing) "Finish" else "Next",
                    onClick = { next() },
                    icon = if (finishing) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    loading = busy,
                    enabled = value.length >= minLength,
                )
            }
        }
    }
}

@Composable
private fun KindPicker(kind: LockKind, onChange: (LockKind) -> Unit) {
    Segmented(
        options = listOf(LockKind.Pin to "PIN", LockKind.Password to "Password"),
        selected = kind,
        onSelect = onChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
    )
}
