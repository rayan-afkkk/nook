package com.nook.msgapp.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nook.core.ChatLogic
import com.nook.core.Media
import com.nook.msgapp.media.VoicePlayer
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing

private val FLAT = List(40) { 0.2f }
private val SPEEDS = listOf(1f, 1.5f, 2f)

/** Voice note: play/pause, waveform that fills as it plays (tap it to seek), duration and speed. */
@Composable
fun VoiceBubble(messageId: String, media: Media, mine: Boolean, interactive: Boolean = true) {
    val c = Nook.colors
    val haptics = LocalHapticFeedback.current
    val currentId by VoicePlayer.currentId.collectAsState()
    val playingNow by VoicePlayer.playing.collectAsState()
    val loadingNow by VoicePlayer.loading.collectAsState()
    val position by VoicePlayer.positionMs.collectAsState()
    val playerDuration by VoicePlayer.durationMs.collectAsState()
    val speed by VoicePlayer.speed.collectAsState()

    val active = currentId == messageId
    val playing = active && playingNow
    val loading = active && loadingNow
    val duration = if (active && playerDuration > 0) playerDuration else (media.durationMs ?: 0L)
    val progress = if (active && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val label = ChatLogic.durationLabel(if (active && (playing || position > 0)) position else duration)
    val fg = if (mine) c.onPrimary else c.text
    val played = if (mine) c.onPrimary else c.accent
    val rest = if (mine) Color.Black.copy(alpha = 0.25f) else c.border
    val samples = media.waveform?.takeIf { it.isNotEmpty() } ?: FLAT
    val latestDuration by rememberUpdatedState(duration)
    val latestActive by rememberUpdatedState(active)

    Row(
        Modifier.width(232.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (mine) c.onPrimary else c.primary)
                .then(
                    if (interactive) {
                        Modifier.pressScale(scaleTo = 0.88f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            VoicePlayer.toggle(messageId, media.url)
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(color = if (mine) c.primary else c.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause voice note" else "Play voice note",
                    tint = if (mine) c.primary else c.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .then(
                        if (interactive) {
                            Modifier.pointerInput(messageId) {
                                detectTapGestures { offset ->
                                    if (latestActive && latestDuration > 0 && size.width > 0) {
                                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                                        VoicePlayer.seek(messageId, (f * latestDuration).toLong())
                                    }
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val n = samples.size
                if (n == 0) return@Canvas
                val gap = 1.5.dp.toPx()
                val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
                val minH = 3.dp.toPx()
                samples.forEachIndexed { i, v ->
                    val h = (v.coerceIn(0f, 1f) * size.height).coerceAtLeast(minH)
                    val x = i * (barW + gap)
                    drawRoundRect(
                        color = if (i.toFloat() / n < progress) played else rest,
                        topLeft = Offset(x, (size.height - h) / 2f),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                    )
                }
            }
            NText(label, NookType.micro, fg.copy(alpha = if (loading) 0.5f else 0.8f))
        }
        Box(
            Modifier
                .heightIn(min = 26.dp)
                .widthIn(min = 36.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .border(1.dp, if (mine) Color.Black.copy(alpha = 0.25f) else c.border, RoundedCornerShape(Radius.pill))
                .then(
                    if (interactive) {
                        Modifier.pressScale(scaleTo = 0.9f) {
                            val i = SPEEDS.indexOf(speed).let { if (it < 0) 0 else it }
                            VoicePlayer.setSpeed(SPEEDS[(i + 1) % SPEEDS.size])
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val s = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
            NText("${s}×", NookType.micro, fg)
        }
    }
}
