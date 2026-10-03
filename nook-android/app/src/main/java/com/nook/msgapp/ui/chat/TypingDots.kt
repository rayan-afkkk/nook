package com.nook.msgapp.ui.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing

/** Three bouncing dots in a bubble ("someone is typing"). */
@Composable
fun TypingDots(modifier: Modifier = Modifier) {
    val c = Nook.colors
    val shape = RoundedCornerShape(Radius.card)
    Row(
        modifier
            .padding(start = Spacing.md, top = Spacing.xs, bottom = 2.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(horizontal = Spacing.md, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(0, c.textMuted)
        Dot(140, c.textMuted)
        Dot(280, c.textMuted)
    }
}

@Composable
private fun Dot(delayMs: Int, color: Color) {
    val transition = rememberInfiniteTransition(label = "typing")
    val y by transition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 260),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMs),
        ),
        label = "dot",
    )
    Box(
        Modifier
            .graphicsLayer { translationY = y * density }
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}
