package com.nook.msgapp.ui.chat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Full-screen photo: pinch to zoom, drag to pan, double-tap to zoom, swipe down to close. */
@Composable
fun ImageViewer(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val tx = remember { Animatable(0f) }
    val ty = remember { Animatable(0f) }
    BackHandler(onBack = onClose)

    fun reset() {
        scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
        scope.launch { tx.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
        scope.launch { ty.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 400f)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Fades out while it's dragged down to close.
                alpha = if (scale.value <= 1.01f) 1f - (abs(ty.value) / (size.height.coerceAtLeast(1f))).coerceIn(0f, 0.7f) else 1f
            }
            .background(Color.Black),
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale.value > 1.01f) {
                                reset()
                            } else {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val target = 2.5f
                                scope.launch { scale.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 400f)) }
                                scope.launch { tx.animateTo((center.x - tap.x) * (target - 1f), spring(dampingRatio = 0.8f, stiffness = 400f)) }
                                scope.launch { ty.animateTo((center.y - tap.y) * (target - 1f), spring(dampingRatio = 0.8f, stiffness = 400f)) }
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var multiTouch = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) multiTouch = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val zoomed = scale.value > 1.01f
                            if (multiTouch || zoomed) {
                                val newScale = (scale.value * zoom).coerceIn(1f, 5f)
                                val maxX = (size.width * (newScale - 1f)) / 2f
                                val maxY = (size.height * (newScale - 1f)) / 2f
                                scope.launch {
                                    scale.snapTo(newScale)
                                    tx.snapTo((tx.value + pan.x).coerceIn(-maxX, maxX))
                                    ty.snapTo((ty.value + pan.y).coerceIn(-maxY, maxY))
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (pan.y != 0f) {
                                scope.launch { ty.snapTo(ty.value + pan.y) }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        if (scale.value <= 1.01f) {
                            if (!multiTouch && abs(ty.value) > 120.dp.toPx()) onClose() else reset()
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = tx.value
                    translationY = ty.value
                },
        )
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            NIconButton(
                Icons.Rounded.Share,
                "Share photo",
                onClick = {
                    LockStore.skipNextBackgroundLock()
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
                    context.startActivity(Intent.createChooser(send, "Share photo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                tint = Color.White,
                background = Color.White.copy(alpha = 0.14f),
            )
            NIconButton(Icons.Rounded.Close, "Close photo", onClose, tint = Color.White, background = Color.White.copy(alpha = 0.14f))
        }
    }
}
