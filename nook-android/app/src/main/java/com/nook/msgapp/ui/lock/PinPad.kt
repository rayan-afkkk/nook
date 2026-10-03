package com.nook.msgapp.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing

/** The bottom-left key of the pad: biometrics on the lock screen, a submit tick for old PIN records. */
class PinKey(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Row of PIN dots. Shakes whenever [errorKey] changes (pass an increasing counter),
 * and pulses while [busy] (the PIN is being checked).
 */
@Composable
fun PinDots(length: Int, filled: Int, errorKey: Int, modifier: Modifier = Modifier, busy: Boolean = false) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorKey) {
        if (errorKey != 0) {
            for (x in floatArrayOf(-12f, 12f, -8f, 8f)) shake.animateTo(x, tween(50))
            shake.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 600f))
        }
    }
    val pulse by rememberInfiniteTransition(label = "pin-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "pin-pulse",
    )
    val count = maxOf(length, filled)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp)
            .graphicsLayer {
                translationX = shake.value * density
                alpha = if (busy) pulse else 1f
            }
            .semantics {
                contentDescription = "$filled digits entered"
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            PinDot(on = i < filled)
        }
    }
}

@Composable
private fun PinDot(on: Boolean) {
    val c = Nook.colors
    val fill by animateColorAsState(if (on) c.text else Color.Transparent, tween(120), label = "dot")
    val edge by animateColorAsState(if (on) c.text else c.textMuted, tween(120), label = "dot-edge")
    val scale by animateFloatAsState(if (on) 1.15f else 1f, spring(dampingRatio = 0.45f, stiffness = 900f), label = "dot-pop")
    Box(
        Modifier
            .size(13.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, edge, CircleShape),
    )
}

private val KEY_ROWS = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))

/** Numeric keypad: 1-9, then [leftKey] / 0 / delete. Keys spring down when pressed. */
@Composable
fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    leftKey: PinKey? = null,
    disabled: Boolean = false,
    keySize: Dp = 68.dp,
) {
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        KEY_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                row.forEach { d -> DigitKey(d, keySize, disabled) { onDigit(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            if (leftKey != null) {
                IconKey(leftKey.icon, leftKey.label, keySize, disabled, leftKey.onClick)
            } else {
                Box(Modifier.size(keySize))
            }
            DigitKey("0", keySize, disabled) { onDigit("0") }
            IconKey(Icons.AutoMirrored.Outlined.Backspace, "Delete", keySize, disabled, onDelete)
        }
    }
}

@Composable
private fun DigitKey(digit: String, size: Dp, disabled: Boolean, onClick: () -> Unit) {
    val c = Nook.colors
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .graphicsLayer { alpha = if (disabled) 0.5f else 1f }
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.border, CircleShape)
            .pressScale(enabled = !disabled, scaleTo = 0.88f) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = digit },
        contentAlignment = Alignment.Center,
    ) {
        NText(digit, NookType.headline)
    }
}

@Composable
private fun IconKey(icon: ImageVector, label: String, size: Dp, disabled: Boolean, onClick: () -> Unit) {
    val c = Nook.colors
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .graphicsLayer { alpha = if (disabled) 0.5f else 1f }
            .clip(CircleShape)
            .pressScale(enabled = !disabled, scaleTo = 0.88f) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = c.text, modifier = Modifier.size(26.dp))
    }
}
