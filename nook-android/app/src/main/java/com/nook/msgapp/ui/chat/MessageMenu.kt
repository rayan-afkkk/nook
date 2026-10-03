package com.nook.msgapp.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nook.core.Message
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val QUICK_REACTIONS = listOf("❤️", "😂", "😮", "😢", "🔥", "👍")

/** The long-pressed message and where its bubble was on screen (window pixels). */
data class MenuTarget(val message: Message, val rect: Rect, val mine: Boolean)

data class MenuAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val onClick: () -> Unit)

private val REACTIONS_H = 48.dp
private val GAP = 8.dp
private val ROW_H = 44.dp
private val MENU_W = 210.dp

/**
 * WhatsApp-style long-press menu: the screen dims, the bubble lifts in place (sliding to fit on screen),
 * reactions pop in one by one above it and the actions fade in below.
 */
@Composable
fun MessageMenu(
    target: MenuTarget?,
    myReaction: String?,
    actions: List<MenuAction>,
    onReact: (String?) -> Unit,
    onMoreReactions: () -> Unit,
    onClose: () -> Unit,
    preview: @Composable (Message) -> Unit,
) {
    val c = Nook.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val open = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<MenuTarget?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(target) {
        if (target != null) {
            shown = target
            open.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 420f))
        } else if (shown != null) {
            open.animateTo(0f, tween(170))
            shown = null
        }
    }
    BackHandler(enabled = target != null, onBack = onClose)

    val s = target ?: shown ?: return
    val mine = s.mine

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(10f)
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val px = { d: androidx.compose.ui.unit.Dp -> with(density) { d.toPx() } }
        val topInset = WindowInsets.statusBars.getTop(density).toFloat()
        val bottomInset = WindowInsets.navigationBars.getBottom(density).toFloat()
        val rectLeft = s.rect.left - origin.x
        val rectTop = s.rect.top - origin.y
        val rectW = max(1f, s.rect.width)
        val previewH = min(s.rect.height, fullH * 0.42f)
        val menuH = actions.size * px(ROW_H) + px(Spacing.xs) * 2
        val minTop = topInset + px(Spacing.sm)
        val maxBottom = fullH - bottomInset - px(Spacing.sm)
        var targetY = rectTop
        if (targetY - px(REACTIONS_H) - px(GAP) < minTop) targetY = minTop + px(REACTIONS_H) + px(GAP)
        if (targetY + previewH + px(GAP) + menuH > maxBottom) targetY = maxBottom - menuH - px(GAP) - previewH
        val shift = targetY - rectTop
        val sidePadStart = with(density) { max(px(Spacing.sm), rectLeft).toDp() }
        val sidePadEnd = with(density) { max(px(Spacing.sm), fullW - rectLeft - rectW).toDp() }
        val sideAlign = if (mine) Alignment.TopEnd else Alignment.TopStart
        val sidePadding = if (mine) Modifier.padding(end = sidePadEnd) else Modifier.padding(start = sidePadStart)

        // Dimmed backdrop (the chat behind is blurred by the screen on Android 12+).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = open.value }
                .background(c.overlay)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )

        // Reactions above the bubble.
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (rectTop - px(REACTIONS_H) - px(GAP) + shift * open.value).roundToInt()) }
                .then(sidePadding),
            contentAlignment = sideAlign,
        ) {
            Row(
                Modifier
                    .graphicsLayer {
                        alpha = open.value
                        val sc = 0.85f + 0.15f * open.value
                        scaleX = sc
                        scaleY = sc
                        translationY = (1f - open.value) * 10.dp.toPx()
                        transformOrigin = TransformOrigin(if (mine) 1f else 0f, 1f)
                    }
                    .shadow(8.dp, RoundedCornerShape(Radius.pill))
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(Radius.pill))
                    .height(REACTIONS_H)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QUICK_REACTIONS.forEachIndexed { i, e ->
                    PopIn(i) {
                        Box(
                            Modifier
                                .size(width = 40.dp, height = 40.dp)
                                .clip(CircleShape)
                                .background(if (myReaction == e) c.surfaceRaised else androidx.compose.ui.graphics.Color.Transparent)
                                .pressScale(scaleTo = 0.8f) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onClose()
                                    onReact(if (myReaction == e) null else e)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(e, fontSize = 23.sp, lineHeight = 30.sp)
                        }
                    }
                }
                PopIn(QUICK_REACTIONS.size) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c.surfaceRaised)
                            .pressScale(scaleTo = 0.85f) { onMoreReactions() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, "More reactions", tint = c.text, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // The bubble, lifted in place.
        Box(
            Modifier
                .offset { IntOffset(rectLeft.roundToInt(), (rectTop + shift * open.value).roundToInt()) }
                .width(with(density) { rectW.toDp() })
                .heightIn(max = with(density) { max(1f, previewH).toDp() })
                .graphicsLayer {
                    val sc = 1f + 0.035f * open.value
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = TransformOrigin(if (mine) 1f else 0f, 0.5f)
                    clip = true
                },
            contentAlignment = if (mine) Alignment.TopEnd else Alignment.TopStart,
        ) {
            preview(s.message)
        }

        // Actions below.
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (rectTop + previewH + px(GAP) + shift * open.value).roundToInt()) }
                .then(sidePadding),
            contentAlignment = sideAlign,
        ) {
            Column(
                Modifier
                    .width(MENU_W)
                    .graphicsLayer {
                        alpha = open.value
                        val sc = 0.9f + 0.1f * open.value
                        scaleX = sc
                        scaleY = sc
                        translationY = -(1f - open.value) * 12.dp.toPx()
                        transformOrigin = TransformOrigin(if (mine) 1f else 0f, 0f)
                    }
                    .shadow(8.dp, RoundedCornerShape(Radius.md))
                    .clip(RoundedCornerShape(Radius.md))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(Radius.md))
                    .padding(vertical = Spacing.xs),
            ) {
                actions.forEachIndexed { i, a ->
                    if (i > 0) NDivider(Modifier.padding(horizontal = Spacing.md))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_H)
                            .pressScale(scaleTo = 0.97f) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onClose()
                                a.onClick()
                            }
                            .padding(horizontal = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        NText(a.label, NookType.label, if (a.destructive) c.danger else c.text, Modifier.weight(1f), maxLines = 1)
                        Icon(a.icon, null, tint = if (a.destructive) c.danger else c.text, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Staggered spring "pop" for each reaction. */
@Composable
private fun PopIn(index: Int, content: @Composable () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L + index * 28L)
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f))
    }
    Box(
        Modifier.graphicsLayer {
            val p = pop.value
            alpha = p.coerceIn(0f, 1f)
            val sc = 0.3f + 0.7f * p
            scaleX = sc
            scaleY = sc
            translationY = (1f - p) * 8.dp.toPx()
        },
    ) {
        content()
    }
}
