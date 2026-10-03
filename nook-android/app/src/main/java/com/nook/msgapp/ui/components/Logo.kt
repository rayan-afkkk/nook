package com.nook.msgapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.nook.msgapp.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* The NOOK mark: an arched alcove that doubles as a speech bubble, with a warm light inside.
 * Drawn in the original SVG coordinates (viewBox 18 16 84 104):  M30 112 V56 A30 30 0 0 1 90 56 V100 H48 Z */
private const val VIEW_X = 18f
private const val VIEW_Y = 16f
private const val VIEW_W = 84f
private const val VIEW_H = 104f
private const val STROKE = 8f
private const val DOT_X = 60f
private const val DOT_Y = 66f
private const val DOT_R = 8f

private fun logoPath(): Path = Path().apply {
    moveTo(30f, 112f)
    lineTo(30f, 56f)
    // Half circle over the top, centre (60, 56), radius 30, clockwise from the left point to the right point.
    arcTo(Rect(30f, 26f, 90f, 86f), startAngleDegrees = 180f, sweepAngleDegrees = 180f, forceMoveTo = false)
    lineTo(90f, 100f)
    lineTo(48f, 100f)
    close()
}

/**
 * The NOOK logo. With [animated] the outline draws itself in, then the orange light pops
 * and [onDrawn] is called (used by the onboarding splash).
 */
@Composable
fun NookLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    stroke: Color = Palette.Cream,
    onDrawn: (() -> Unit)? = null,
) {
    val path = remember { logoPath() }
    val measure = remember(path) { PathMeasure().apply { setPath(path, false) } }
    val length = remember(measure) { measure.length }
    val segment = remember { Path() }
    val draw = remember { Animatable(if (animated) 0f else 1f) }
    val dot = remember { Animatable(if (animated) 0f else 1f) }
    val drawnCallback by rememberUpdatedState(onDrawn)

    LaunchedEffect(animated) {
        if (animated) {
            draw.snapTo(0f)
            dot.snapTo(0f)
            launch { draw.animateTo(1f, tween(900, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f))) }
            delay(750)
            dot.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 200f))
            drawnCallback?.invoke()
        }
    }

    Canvas(modifier.size(size).semantics { contentDescription = "NOOK" }) {
        val k = minOf(this.size.width / VIEW_W, this.size.height / VIEW_H)
        val left = (this.size.width - VIEW_W * k) / 2f
        val top = (this.size.height - VIEW_H * k) / 2f
        withTransform({
            translate(left, top)
            scale(k, k, pivot = Offset.Zero)
            translate(-VIEW_X, -VIEW_Y)
        }) {
            val progress = draw.value.coerceIn(0f, 1f)
            val style = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (progress >= 1f) {
                drawPath(path, stroke, style = style)
            } else if (progress > 0f) {
                segment.reset()
                measure.getSegment(0f, length * progress, segment, true)
                drawPath(segment, stroke, style = style)
            }
            val r = DOT_R * dot.value
            if (r > 0f) drawCircle(Palette.Orange, radius = r, center = Offset(DOT_X, DOT_Y))
        }
    }
}
