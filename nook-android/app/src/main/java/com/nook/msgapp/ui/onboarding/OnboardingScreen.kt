@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.nook.msgapp.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NookLogo
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Fonts
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Palette
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.tan

private enum class SlideId { Private, Voice, Calls, Lock }

private class Slide(val id: SlideId, val title: String, val body: String, val tint: Color)

private val SLIDES = listOf(
    Slide(SlideId.Private, "Private by design", "A quiet corner for you and your crew. Only the people in a chat can open it.", Palette.Lavender),
    Slide(SlideId.Voice, "Say it your way", "Voice notes, photos, GIFs and stickers your group makes together.", Palette.Peach),
    Slide(SlideId.Calls, "Calls with your crew", "Jump on a voice or video call in a tap. Friends join right from the notification.", Palette.Mint),
    Slide(SlideId.Lock, "Yours to lock", "Lock NOOK with your own PIN or fingerprint, and let messages disappear when you want.", Palette.Sky),
)

/** Illustration travels at this fraction of the page speed (text moves at full speed). */
private const val PARALLAX = 0.5f

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInOutQuad = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** Onboarding is always drawn on the dark editorial palette, whatever the theme setting. */
private val Bg = Palette.Black
private val TextColor = Palette.Cream
private val MutedColor = Palette.Muted

/** True when the person turned animations off (Developer options / accessibility). */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Pager position as a float: 1.5 means halfway between slide 2 and 3. */
private fun PagerState.position(): Float = currentPage + currentPageOffsetFraction

/** The animated four-slide intro shown on first launch, preceded by the logo splash. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { SLIDES.size })
    val last = SLIDES.size - 1

    var splashDone by remember { mutableStateOf(false) }
    var splashGone by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }
    val finished = remember { booleanArrayOf(false) }
    val intro = remember { Animatable(0f) }

    fun finish() {
        if (finished[0]) return
        finished[0] = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onDone()
    }

    fun next() {
        val current = pager.currentPage
        if (current >= last) {
            finish()
        } else {
            scope.launch {
                if (reduceMotion) pager.scrollToPage(current + 1) else pager.animateScrollToPage(current + 1)
            }
        }
    }

    fun revealContent() {
        if (splashDone) return
        scope.launch {
            delay(if (reduceMotion) 150L else 380L)
            splashDone = true
            intro.animateTo(1f, tween(if (reduceMotion) 200 else 520, easing = EaseOutCubic))
            splashGone = true
        }
    }

    // Reduce motion: the logo is shown static, so move on after a short beat.
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            delay(400)
            revealContent()
        }
    }

    // A tick on every page change; remember once the last slide has been seen.
    LaunchedEffect(pager) {
        var previous = pager.currentPage
        snapshotFlow { pager.currentPage }.collect { page ->
            if (page != previous) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                previous = page
            }
            if (page == last) reachedEnd = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
        val screenW = maxWidth
        val artSize: Dp = minOf(screenW - Spacing.xl * 2, maxHeight * 0.42f, 320.dp)

        // Content: fades and rises in once the splash has drawn.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = intro.value
                    translationY = 24.dp.toPx() * (1f - intro.value)
                },
        ) {
            Glow(pager = pager, width = screenW, centerY = artSize * 0.5f + 90.dp)

            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NookLogo(size = 30.dp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .heightIn(min = 44.dp)
                            .widthIn(min = 48.dp)
                            .pressScale(onClick = { finish() })
                            .semantics { contentDescription = "Skip introduction" },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        NText("Skip", NookType.label, MutedColor)
                    }
                }

                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    SlidePage(
                        slide = SLIDES[page],
                        page = page,
                        pager = pager,
                        artSize = artSize,
                        active = splashDone && pager.currentPage == page,
                        reduceMotion = reduceMotion,
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xs, bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    ProgressPill(count = SLIDES.size, pager = pager)
                    ShimmerButton(
                        title = if (pager.currentPage >= last) "Get started" else "Continue",
                        shimmer = reachedEnd && !reduceMotion,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            next()
                        },
                    )
                }
            }
        }

        // Splash: the logo draws itself, then lifts away. It also swallows taps until the content is ready.
        if (!splashGone) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val v = intro.value
                        alpha = 1f - v
                        scaleX = 1f - 0.12f * v
                        scaleY = 1f - 0.12f * v
                        translationY = -40.dp.toPx() * v
                    }
                    .background(Bg)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            ) {
                NookLogo(size = 112.dp, animated = !reduceMotion, onDrawn = { revealContent() })
                SplashWordmark(reduceMotion)
            }
        }
    }
}

@Composable
private fun SplashWordmark(reduceMotion: Boolean) {
    val p = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(350)
            p.animateTo(1f, tween(500))
        }
    }
    NText(
        "NOOK",
        style = TextStyle(fontFamily = Fonts.Serif, fontSize = 32.sp, letterSpacing = 6.sp),
        color = TextColor,
        modifier = Modifier.graphicsLayer {
            alpha = p.value
            translationY = 8.dp.toPx() * (1f - p.value)
        },
    )
}

@Composable
private fun SlidePage(slide: Slide, page: Int, pager: PagerState, artSize: Dp, active: Boolean, reduceMotion: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xl)
            .graphicsLayer {
                // Reduce motion: hold each page still and crossfade instead of sliding.
                if (reduceMotion) {
                    val offset = pager.position() - page
                    alpha = 1f - minOf(1f, abs(offset))
                    translationX = offset * size.width
                }
            },
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    if (!reduceMotion) {
                        val offset = pager.position() - page
                        val d = minOf(1f, abs(offset))
                        // The page itself moves at full speed; pushing the art back by half makes it trail.
                        translationX = offset * (size.width + Spacing.xl.toPx() * 2) * PARALLAX
                        scaleX = 1f - 0.08f * d
                        scaleY = 1f - 0.08f * d
                        alpha = 1f - 0.6f * d
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when (slide.id) {
                SlideId.Private -> BubblesIllustration(active, reduceMotion, artSize)
                SlideId.Voice -> VoiceIllustration(active, reduceMotion, artSize)
                SlideId.Calls -> CallsIllustration(active, reduceMotion, artSize)
                SlideId.Lock -> LockIllustration(active, reduceMotion, artSize)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.Bottom),
        ) {
            Headline(slide.title, active, reduceMotion)
            NText(slide.body, NookType.bodyLarge, MutedColor, modifier = Modifier.widthIn(max = 340.dp))
        }
    }
}

/** Headline whose words rise in one after another. */
@Composable
private fun Headline(text: String, active: Boolean, reduceMotion: Boolean) {
    FlowRow(
        Modifier.semantics(mergeDescendants = true) {
            heading()
            contentDescription = text
        },
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        text.split(" ").forEachIndexed { i, word ->
            HeadlineWord(word, i, active, reduceMotion)
        }
    }
}

@Composable
private fun HeadlineWord(word: String, index: Int, active: Boolean, reduceMotion: Boolean) {
    val p = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(active, reduceMotion) {
        if (reduceMotion) {
            p.snapTo(1f)
        } else if (active) {
            delay(120L + index * 80L)
            p.animateTo(1f, tween(420, easing = EaseOutCubic))
        } else {
            p.animateTo(0f, tween(120))
        }
    }
    NText(
        word,
        NookType.display,
        TextColor,
        modifier = Modifier.graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 18.dp.toPx()
        },
    )
}

/** Dots that stretch into a pill for the current slide, following the swipe. */
@Composable
private fun ProgressPill(count: Int, pager: PagerState) {
    val pos = pager.position()
    Row(
        Modifier.height(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            val d = minOf(1f, abs(pos - i))
            Box(
                Modifier
                    .width((28f - 20f * d).dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(lerp(TextColor, Color(0xFF4A443E), d)),
            )
        }
    }
}

/** Soft radial light behind the art that shifts tint with the slide. */
@Composable
private fun Glow(pager: PagerState, width: Dp, centerY: Dp) {
    val tints = remember { SLIDES.map { it.tint } }
    Canvas(Modifier.fillMaxSize()) {
        val pos = pager.position()
        val radius = width.toPx() * 0.75f
        val center = Offset(this.size.width / 2f, centerY.toPx())
        tints.forEachIndexed { i, tint ->
            val a = (1f - abs(pos - i)).coerceIn(0f, 1f)
            if (a > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to tint.copy(alpha = 0.42f * a),
                        0.55f to tint.copy(alpha = 0.12f * a),
                        1f to tint.copy(alpha = 0f),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}

/** Cream pill button whose highlight sweeps across once the last slide is reached. */
@Composable
private fun ShimmerButton(title: String, shimmer: Boolean, onClick: () -> Unit) {
    val sweep = remember { Animatable(-1f) }
    LaunchedEffect(shimmer) {
        if (shimmer) {
            sweep.snapTo(-1f)
            delay(350)
            sweep.animateTo(1.4f, tween(1100, easing = EaseInOutQuad))
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(Palette.Cream)
            .pressScale(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        NText(title, TextStyle(fontFamily = Fonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp), Palette.Black)
        Canvas(Modifier.fillMaxSize()) {
            val s = sweep.value
            if (s > -1f && s < 1.4f) {
                val band = 90.dp.toPx()
                val h = this.size.height
                val skew = h / 2f * tan(Math.toRadians(20.0)).toFloat()
                val x0 = s * this.size.width - band
                val path = Path().apply {
                    moveTo(x0 + skew, 0f)
                    lineTo(x0 + band + skew, 0f)
                    lineTo(x0 + band - skew, h)
                    lineTo(x0 - skew, h)
                    close()
                }
                drawPath(
                    path,
                    Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White.copy(alpha = 0.85f),
                        1f to Color.White.copy(alpha = 0f),
                        startX = x0 - skew,
                        endX = x0 + band + skew,
                    ),
                )
            }
        }
    }
}
