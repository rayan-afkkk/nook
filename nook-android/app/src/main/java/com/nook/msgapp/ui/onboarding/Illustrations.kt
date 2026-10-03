package com.nook.msgapp.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nook.msgapp.ui.theme.Fonts
import com.nook.msgapp.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/* ================================================================== motion helpers */

private val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

/** Clamped piecewise-linear interpolation (Reanimated's `interpolate` with CLAMP). */
internal fun interp(x: Float, inputs: FloatArray, outputs: FloatArray): Float {
    if (x <= inputs[0]) return outputs[0]
    val last = inputs.size - 1
    if (x >= inputs[last]) return outputs[last]
    for (i in 0 until last) {
        val a = inputs[i]
        val b = inputs[i + 1]
        if (x <= b) {
            val f = if (b - a == 0f) 1f else (x - a) / (b - a)
            return outputs[i] + (outputs[i + 1] - outputs[i]) * f
        }
    }
    return outputs[last]
}

internal fun interp(x: Float, a: Float, b: Float, from: Float, to: Float): Float =
    interp(x, floatArrayOf(a, b), floatArrayOf(from, to))

/**
 * Pops its child in with a springy scale when [active] turns on and resets when it turns off,
 * so the entrance replays every time the slide is revisited. Static under reduce motion.
 */
@Composable
internal fun PopIn(
    active: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    from: Float = 0.4f,
    rotate: Float = 0f,
    content: @Composable () -> Unit,
) {
    val p = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(active, reduceMotion) {
        if (reduceMotion) {
            p.snapTo(1f)
        } else if (active) {
            delay(delayMs.toLong())
            p.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 257f))
        } else {
            p.snapTo(0f)
        }
    }
    Box(
        modifier.graphicsLayer {
            val v = p.value
            alpha = (v * 1.6f).coerceIn(0f, 1f)
            translationY = (1f - v) * 18.dp.toPx()
            val s = from + (1f - from) * v
            scaleX = s
            scaleY = s
            rotationZ = rotate * v
        },
    ) { content() }
}

/** Gentle endless bob, phase-shifted by [delayMs]. */
@Composable
internal fun FloatBob(
    active: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    amplitude: Float = 5f,
    durationMs: Int = 1800,
    content: @Composable () -> Unit,
) {
    val y = remember { Animatable(0f) }
    LaunchedEffect(active, reduceMotion) {
        if (!active || reduceMotion) {
            y.snapTo(0f)
        } else {
            delay(delayMs.toLong())
            val half = (durationMs / 2).coerceAtLeast(1)
            while (true) {
                y.animateTo(-amplitude, tween(half, easing = EaseInOutSine))
                y.animateTo(amplitude, tween(half, easing = EaseInOutSine))
            }
        }
    }
    Box(modifier.graphicsLayer { translationY = y.value * density }) { content() }
}

/** A 0..1 clock that repeats while active. Looping scenes derive everything from it. */
@Composable
internal fun rememberLoop(active: Boolean, reduceMotion: Boolean, durationMs: Int, restValue: Float = 1f): Animatable<Float, AnimationVector1D> {
    val t = remember { Animatable(if (reduceMotion) restValue else 0f) }
    LaunchedEffect(active, reduceMotion, durationMs) {
        if (reduceMotion) {
            t.snapTo(restValue)
        } else if (!active) {
            t.snapTo(0f)
        } else {
            t.snapTo(0f)
            t.animateTo(1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart))
        }
    }
    return t
}

private val Ink = Palette.Ink

/* ================================================================== slide 1: bubbles */

private class BubbleSpec(val left: Boolean, val width: Float, val lines: Int, val color: Color, val lineColor: Color)

private val BUBBLES = listOf(
    BubbleSpec(true, 0.62f, 2, Palette.Charcoal, Color(0xFF4A433D)),
    BubbleSpec(false, 0.5f, 1, Palette.Cream, Color(0xFFCFC6B8)),
    BubbleSpec(true, 0.44f, 1, Palette.Lavender, Color(0xFFB9A8DC)),
    BubbleSpec(false, 0.66f, 2, Palette.Cream, Color(0xFFCFC6B8)),
)

/** "Private by design": chat bubbles pop in one by one, then float. */
@Composable
internal fun BubblesIllustration(active: Boolean, reduceMotion: Boolean, size: Dp) {
    Column(
        Modifier.size(size).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        BUBBLES.forEachIndexed { i, b ->
            PopIn(
                active = active,
                reduceMotion = reduceMotion,
                delayMs = 180 + i * 220,
                rotate = if (b.left) -1.5f else 1.5f,
                modifier = Modifier.align(if (b.left) Alignment.Start else Alignment.End),
            ) {
                FloatBob(active, reduceMotion, delayMs = i * 260, amplitude = 4f + (i % 2), durationMs = 2200 + i * 200) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (b.left) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(if (i == 0) Palette.Peach else Palette.Mint))
                        }
                        val shape = RoundedCornerShape(
                            topStart = 22.dp,
                            topEnd = 22.dp,
                            bottomEnd = if (b.left) 22.dp else 6.dp,
                            bottomStart = if (b.left) 6.dp else 22.dp,
                        )
                        Column(
                            Modifier
                                .width(size * b.width)
                                .clip(shape)
                                .background(b.color)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (l in 0 until b.lines) {
                                val fraction = if (l == b.lines - 1 && b.lines > 1) 0.6f else 0.88f
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(b.lineColor),
                                )
                            }
                        }
                    }
                }
            }
        }
        PopIn(
            active = active,
            reduceMotion = reduceMotion,
            delayMs = 1150,
            modifier = Modifier.align(Alignment.Start).padding(start = 36.dp),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Palette.Charcoal)
                    .border(1.dp, Palette.Border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (d in 0 until 3) {
                    FloatBob(active, reduceMotion, delayMs = d * 150, amplitude = 2.5f, durationMs = 700) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Palette.Muted))
                    }
                }
            }
        }
    }
}

/* ================================================================== slide 2: voice */

private const val BARS = 26

/** Fixed "recorded" envelope so the waveform looks like speech, not noise. */
private val ENVELOPE = FloatArray(BARS) { i -> 0.35f + 0.65f * abs(sin(i * 0.9f) * cos(i * 0.37f)) }

/** "Say it your way": a live voice-note waveform with GIF and sticker tiles bouncing in. */
@Composable
internal fun VoiceIllustration(active: Boolean, reduceMotion: Boolean, size: Dp) {
    val t = rememberLoop(active, reduceMotion, 4200, 0.45f)
    Column(
        Modifier.size(size),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
    ) {
        PopIn(active, reduceMotion, delayMs = 120, from = 0.7f) {
            Row(
                Modifier
                    .width(size * 0.92f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Palette.Charcoal)
                    .border(1.dp, Palette.Border, RoundedCornerShape(28.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Palette.Cream), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Palette.Black, modifier = Modifier.size(20.dp))
                }
                Canvas(Modifier.weight(1f).height(40.dp)) {
                    val barW = 3.dp.toPx()
                    val gap = (this.size.width - BARS * barW) / (BARS - 1)
                    val maxH = 34.dp.toPx()
                    val minH = 4.dp.toPx()
                    val clock = t.value
                    for (i in 0 until BARS) {
                        val wobble = 0.55f + 0.45f * abs(sin(clock * PI.toFloat() * 4f + i * 0.55f))
                        val h = maxOf(minH, maxH * ENVELOPE[i] * wobble)
                        val played = i.toFloat() / BARS < clock
                        drawRoundRect(
                            color = if (played) Palette.Orange else Color(0xFF5A524B),
                            topLeft = Offset(i * (barW + gap), (this.size.height - h) / 2f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                        )
                    }
                }
                Text("1.5×", style = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Palette.Muted))
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PopIn(active, reduceMotion, delayMs = 520, rotate = -8f) {
                FloatBob(active, reduceMotion, amplitude = 6f, durationMs = 2000) {
                    Box(
                        Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(Palette.Lavender),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "GIF",
                            style = TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.Bold, fontSize = 25.sp, letterSpacing = 1.sp, color = Ink),
                        )
                    }
                }
            }
            PopIn(active, reduceMotion, delayMs = 720, rotate = 7f) {
                FloatBob(active, reduceMotion, delayMs = 300, amplitude = 7f, durationMs = 2300) {
                    Box(
                        Modifier.size(100.dp).clip(RoundedCornerShape(28.dp)).background(Palette.Peach),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.EmojiEmotions, contentDescription = null, tint = Ink, modifier = Modifier.size(44.dp))
                    }
                }
            }
            PopIn(active, reduceMotion, delayMs = 920, rotate = -4f) {
                FloatBob(active, reduceMotion, delayMs = 600, amplitude = 5f, durationMs = 1900) {
                    Box(
                        Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Mint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = Ink, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

/* ================================================================== slide 3: calls */

private class Friend(val initial: String, val color: Color, val angle: Float)

private val FRIENDS = listOf(
    Friend("A", Palette.Peach, -150f),
    Friend("S", Palette.Mint, -30f),
    Friend("M", Palette.Sky, 40f),
    Friend("J", Palette.Rose, 140f),
)

/** "Calls with your crew": pulsing call rings while friends join one by one. */
@Composable
internal fun CallsIllustration(active: Boolean, reduceMotion: Boolean, size: Dp) {
    val t = rememberLoop(active, reduceMotion, 2600, 0.3f)
    val center = 96.dp
    val orbit = size * 0.36f
    val friendSize = 58.dp
    Box(Modifier.size(size)) {
        if (active && !reduceMotion) {
            Canvas(Modifier.fillMaxSize()) {
                val base = center.toPx() / 2f
                val stroke = Stroke(width = 1.5.dp.toPx())
                for (offset in floatArrayOf(0f, 0.33f, 0.66f)) {
                    val p = (t.value + offset) % 1f
                    val alpha = interp(p, floatArrayOf(0f, 0.15f, 1f), floatArrayOf(0f, 0.55f, 0f))
                    val scale = interp(p, 0f, 1f, 1f, 2.3f)
                    drawCircle(Palette.Cream.copy(alpha = alpha.coerceIn(0f, 1f)), radius = base * scale, style = stroke)
                }
            }
        }
        PopIn(active, reduceMotion, modifier = Modifier.align(Alignment.Center), delayMs = 80, from = 0.6f) {
            Box(
                Modifier
                    .size(center)
                    .clip(CircleShape)
                    .background(Palette.Lavender)
                    .border(3.dp, Palette.Black, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = Ink, modifier = Modifier.size(34.dp))
            }
        }
        FRIENDS.forEachIndexed { i, f ->
            val rad = f.angle * PI.toFloat() / 180f
            val x = size / 2 + orbit * cos(rad) - friendSize / 2
            val y = size / 2 + orbit * sin(rad) - friendSize / 2
            Box(Modifier.offset(x = x, y = y)) {
                PopIn(active, reduceMotion, delayMs = 500 + i * 420, from = 0.2f) {
                    FloatBob(active, reduceMotion, delayMs = i * 200, amplitude = 4f, durationMs = 2000 + i * 150) {
                        Box(Modifier.size(friendSize)) {
                            Box(
                                Modifier
                                    .size(friendSize)
                                    .clip(CircleShape)
                                    .background(f.color)
                                    .border(3.dp, Palette.Black, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(f.initial, style = TextStyle(fontFamily = Fonts.Serif, fontSize = 26.sp, color = Ink))
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = (-2).dp, y = (-2).dp)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Palette.Online)
                                    .border(2.dp, Palette.Black, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ================================================================== slide 4: lock */

private val SNAP = floatArrayOf(0.24f, 0.31f, 0.35f)
private const val REOPEN_START = 0.9f
private const val DISSOLVE_LEN = 0.16f
private fun dissolveStart(i: Int) = 0.44f + i * 0.12f

private class LockMessage(val left: Float, val top: Float, val width: Float, val color: Color)

private val MESSAGES = listOf(
    LockMessage(0.02f, 0.08f, 0.4f, Palette.Sky),
    LockMessage(0.6f, 0.22f, 0.36f, Palette.Rose),
    LockMessage(0.04f, 0.7f, 0.34f, Palette.Mint),
)

private class Particle(val dx: Float, val dy: Float, val s: Float)

private val PARTICLES = listOf(
    Particle(-14f, -26f, 6f),
    Particle(8f, -34f, 5f),
    Particle(22f, -20f, 4f),
    Particle(-4f, -42f, 4f),
    Particle(30f, -36f, 3f),
    Particle(-24f, -14f, 3f),
)

/** "Yours to lock": the lock snaps shut, then messages dissolve like disappearing messages. */
@Composable
internal fun LockIllustration(active: Boolean, reduceMotion: Boolean, size: Dp) {
    val t = rememberLoop(active, reduceMotion, 4400, 0.4f)
    Box(Modifier.size(size)) {
        MESSAGES.forEachIndexed { i, m ->
            val start = dissolveStart(i)
            Box(Modifier.offset(x = size * m.left, y = size * m.top).width(size * m.width)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val clock = t.value
                            val fade = interp(clock, 0f, 0.12f, 0f, 1f)
                            val p = interp(clock, start, start + DISSOLVE_LEN, 0f, 1f)
                            alpha = fade * (1f - p)
                            translationY = -12f * p * density
                            scaleX = 1f - 0.18f * p
                            scaleY = 1f - 0.18f * p
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(m.color)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(Modifier.fillMaxWidth(0.85f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x381A1714)))
                    Box(Modifier.fillMaxWidth(0.55f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x381A1714)))
                }
                Canvas(Modifier.matchParentSize()) {
                    val clock = t.value
                    val p = interp(clock, start + 0.02f, start + DISSOLVE_LEN + 0.08f, 0f, 1f)
                    if (p > 0f && p < 1f) {
                        val a = sin(p * PI.toFloat()).coerceIn(0f, 1f)
                        PARTICLES.forEach { pt ->
                            val r = pt.s / 2f * (1f - 0.5f * p) * density
                            val cx = this.size.width / 2f + pt.dx * p * density
                            val cy = (18f + pt.s / 2f + pt.dy * p) * density
                            drawCircle(m.color.copy(alpha = a), radius = r, center = Offset(cx, cy))
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PopIn(active, reduceMotion, delayMs = 60, from = 0.7f) {
                Box(Modifier.size(width = 120.dp, height = 156.dp), contentAlignment = Alignment.BottomCenter) {
                    // Shackle: an arch drawn as a thick stroke, sliding down when the lock snaps shut.
                    Canvas(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .size(width = 76.dp, height = 88.dp)
                            .graphicsLayer {
                                val clock = t.value
                                val closing = interp(clock, floatArrayOf(0f, SNAP[0], SNAP[1], SNAP[2]), floatArrayOf(-24f, -24f, 4f, 0f))
                                val reopen = interp(clock, REOPEN_START, 1f, 0f, -24f)
                                translationY = (if (clock >= REOPEN_START) reopen else closing) * density
                            },
                    ) {
                        val sw = 12.dp.toPx()
                        val half = sw / 2f
                        val w = this.size.width
                        val h = this.size.height
                        val path = Path().apply {
                            moveTo(half, h)
                            lineTo(half, w / 2f)
                            arcTo(Rect(half, half, w - half, w - half), 180f, 180f, false)
                            lineTo(w - half, h)
                        }
                        drawPath(path, Palette.Cream, style = Stroke(width = sw, cap = StrokeCap.Butt))
                    }
                    Box(
                        Modifier
                            .size(width = 120.dp, height = 96.dp)
                            .graphicsLayer {
                                val clock = t.value
                                val bump = interp(clock, floatArrayOf(SNAP[1], SNAP[1] + 0.03f, SNAP[2] + 0.03f), floatArrayOf(1f, 0.95f, 1f))
                                scaleX = bump
                                scaleY = bump
                            }
                            .clip(RoundedCornerShape(24.dp))
                            .background(Palette.Cream),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier.graphicsLayer { alpha = interp(t.value, SNAP[1], SNAP[2], 0.45f, 1f) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(Palette.Orange))
                            Box(
                                Modifier
                                    .offset(y = (-4).dp)
                                    .size(width = 8.dp, height = 18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Palette.Orange),
                            )
                        }
                    }
                }
            }
        }
    }
}
