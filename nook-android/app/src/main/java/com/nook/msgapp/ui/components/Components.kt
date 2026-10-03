package com.nook.msgapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nook.core.Format
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay

/* ------------------------------------------------------------------ press feedback */

/** Scales down slightly while pressed (springy), with no ripple. The NOOK tap feel everywhere. */
fun Modifier.pressScale(
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) scaleTo else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickableNoRipple(source, enabled, role, onClick, {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                })
            } else {
                Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
            },
        )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoRipple(
    source: MutableInteractionSource,
    enabled: Boolean,
    role: Role?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) = this.then(
    Modifier.combinedClickable(
        interactionSource = source,
        indication = null,
        enabled = enabled,
        role = role,
        onClick = onClick,
        onLongClick = onLongClick,
    ),
)

/* ------------------------------------------------------------------ text */

@Composable
fun NText(
    text: String,
    style: TextStyle = NookType.body,
    color: Color = Nook.colors.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
    )
}

/* ------------------------------------------------------------------ buttons */

enum class ButtonVariant { Primary, Secondary, Destructive, Ghost, Accent }

/** Pill button. Full width by default for primary actions. */
@Composable
fun NButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    block: Boolean = true,
) {
    val c = Nook.colors
    val (bg, fg, border) = when (variant) {
        ButtonVariant.Primary -> Triple(c.primary, c.onPrimary, c.primary)
        ButtonVariant.Secondary -> Triple(c.surface, c.text, c.border)
        ButtonVariant.Destructive -> Triple(c.surface, c.danger, c.border)
        ButtonVariant.Ghost -> Triple(Color.Transparent, c.text, Color.Transparent)
        ButtonVariant.Accent -> Triple(c.accent, Color.Black, c.accent)
    }
    val haptics = LocalHapticFeedback.current
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = modifier
            .then(if (block) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(Radius.pill))
            .pressScale(enabled = enabled && !loading) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                NText(title, NookType.label, fg)
            }
        }
    }
}

/** Round outline icon button (header actions, composer). */
@Composable
fun NIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Nook.colors.text,
    background: Color = Color.Transparent,
    border: Boolean = false,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (border) Modifier.border(1.dp, Nook.colors.border, CircleShape) else Modifier)
            .pressScale(enabled = enabled, scaleTo = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else tint.copy(alpha = 0.4f), modifier = Modifier.size(iconSize))
    }
}

/* ------------------------------------------------------------------ surfaces */

enum class CardTone { Surface, Highlight, Mint, Sky, Peach, Lavender, Rose }

@Composable
fun NCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Surface,
    padded: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Nook.colors
    val bg = when (tone) {
        CardTone.Surface -> c.surface
        CardTone.Highlight -> c.highlight
        CardTone.Mint -> c.pastels[0]
        CardTone.Sky -> c.pastels[1]
        CardTone.Peach -> c.pastels[2]
        CardTone.Lavender -> c.pastels[3]
        CardTone.Rose -> c.pastels[4]
    }
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(if (tone == CardTone.Surface) Modifier.border(1.dp, c.border, shape) else Modifier)
            .then(if (onClick != null) Modifier.pressScale(scaleTo = 0.98f, onClick = onClick) else Modifier)
            .then(if (padded) Modifier.padding(Spacing.md) else Modifier),
        content = content,
    )
}

/** Dashed-looking empty-state card (a soft outline). */
@Composable
fun DashedCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Radius.card)
    Column(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, Nook.colors.border), shape)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun NDivider(modifier: Modifier = Modifier) = HorizontalDivider(modifier = modifier, thickness = 0.5.dp, color = Nook.colors.border)

/* ------------------------------------------------------------------ header */

/** Big serif title on the left, icon actions on the right, hairline below. */
@Composable
fun NHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    divider: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        if (onBack != null) {
            Box(Modifier.padding(start = Spacing.xs, top = Spacing.xxs)) {
                NIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = Spacing.lg, end = Spacing.sm, top = if (onBack != null) 0.dp else Spacing.sm, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NText(title, NookType.title, modifier = Modifier.weight(1f), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        if (divider) NDivider()
    }
}

/** Compact top bar: back arrow + small title (used by detail screens). */
@Composable
fun NTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(Spacing.xxs))
            NText(title, NookType.subhead, modifier = Modifier.weight(1f), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        NDivider()
    }
}

/* ------------------------------------------------------------------ rows */

/** Settings-style row: icon tile, bold title, muted subtitle, chevron (or a custom trailing view). */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    destructive: Boolean = false,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Nook.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(scaleTo = 0.98f, onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (destructive) c.danger else c.text, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NText(title, NookType.bodyBold, if (destructive) c.danger else c.text, maxLines = 1)
            if (subtitle != null) NText(subtitle, NookType.caption, c.textMuted, maxLines = 2)
        }
        when {
            trailing != null -> trailing()
            onClick != null && showChevron -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Small caps section label above a group of rows. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    NText(text.uppercase(), NookType.micro, Nook.colors.textMuted, modifier.padding(top = Spacing.lg, bottom = Spacing.xxs))
}

/* ------------------------------------------------------------------ avatar */

@Composable
fun Avatar(
    name: String,
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    online: Boolean = false,
    seed: String? = null,
) {
    val c = Nook.colors
    Box(modifier.size(size)) {
        if (!url.isNullOrEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape).background(c.surface),
            )
        } else {
            Box(
                Modifier.size(size).clip(CircleShape).background(c.pastelFor(seed ?: name)),
                contentAlignment = Alignment.Center,
            ) {
                NText(Format.initials(name), if (size >= 64.dp) NookType.headline else NookType.label, c.onPastel)
            }
        }
        if (online) {
            val dot = maxOf(10.dp, size * 0.26f)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dot)
                    .clip(CircleShape)
                    .background(c.background)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(c.online),
            )
        }
    }
}

/* ------------------------------------------------------------------ inputs */

/** Rounded text field with a label, helper/error line and optional leading text (e.g. "@"). */
@Composable
fun NTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    helper: String? = null,
    error: String? = null,
    prefix: String? = null,
    singleLine: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Done,
    onSubmit: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    maxLength: Int = 200,
) {
    val c = Nook.colors
    val borderColor by animateColorAsState(if (error != null) c.danger else c.border, tween(150), label = "border")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) NText(label, NookType.captionBold, c.textMuted)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(c.surface)
                .border(1.dp, borderColor, RoundedCornerShape(Radius.md))
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) NText(prefix, NookType.bodyLarge, c.textMuted)
            Box(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                if (value.isEmpty()) NText(placeholder, NookType.bodyLarge, c.textMuted, maxLines = 1)
                BasicTextField(
                    value = value,
                    onValueChange = { onValueChange(it.take(maxLength)) },
                    singleLine = singleLine,
                    textStyle = NookType.bodyLarge.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = !password && keyboardType == KeyboardType.Text),
                    keyboardActions = KeyboardActions(onAny = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke()
        }
        when {
            error != null -> NText(error, NookType.caption, c.danger)
            helper != null -> NText(helper, NookType.caption, c.textMuted)
        }
    }
}

/** NOOK switch: cream track when on, raised surface when off. */
@Composable
fun NToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Nook.colors
    val track by animateColorAsState(if (checked) c.accent else c.surfaceRaised, tween(160), label = "track")
    val offset by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "thumb")
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(track)
            .border(1.dp, if (checked) c.accent else c.border, RoundedCornerShape(Radius.pill))
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }, role = Role.Switch) {
                onCheckedChange(!checked)
            }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = 18.dp * offset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) Color.Black else c.textMuted),
        )
    }
}

/** Segmented pill control (Off / 24h / 7d, theme picker...). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Nook.colors
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(Radius.pill))
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val bg by animateColorAsState(if (active) c.primary else Color.Transparent, tween(180), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(bg)
                    .pressScale(scaleTo = 0.95f) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                NText(label, NookType.label, if (active) c.onPrimary else c.textMuted)
            }
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val c = Nook.colors
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) c.primary else c.surface)
            .border(1.dp, if (selected) c.primary else c.border, RoundedCornerShape(Radius.pill))
            .then(if (onClick != null) Modifier.pressScale(scaleTo = 0.94f, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) c.onPrimary else c.text, modifier = Modifier.size(14.dp))
        NText(text, NookType.captionBold, if (selected) c.onPrimary else c.text)
    }
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(Radius.pill)),
        color = Nook.colors.accent,
        trackColor = Nook.colors.surfaceRaised,
        drawStopIndicator = {},
    )
}

/* ------------------------------------------------------------------ states */

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Nook.colors
    Column(
        modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.text, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        NText(title, NookType.headline, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        NText(body, NookType.body, c.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(Spacing.sm))
            NButton(action, onAction, block = false)
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Nook.colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

/** Shimmer-free skeleton block (a soft pulsing placeholder). */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "sk").animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "sk",
    )
    Box(modifier.graphicsLayer { alpha = pulse }.clip(RoundedCornerShape(Radius.sm)).background(Nook.colors.surfaceRaised))
}

/* ------------------------------------------------------------------ toast */

/** Bottom toast fed by [Toasts]. Put one in the root of the app. */
@Composable
fun BoxScope.ToastHost() {
    var message by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Toasts.events.collect {
            message = it
            visible = true
        }
    }
    LaunchedEffect(message, visible) {
        if (visible) {
            delay(2600)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(220)) { it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = Spacing.lg, end = Spacing.lg),
    ) {
        Box(
            Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(Nook.colors.primary)
                .padding(PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)),
        ) {
            NText(message ?: "", NookType.label, Nook.colors.onPrimary, maxLines = 3)
        }
    }
}

@Composable
fun OfflineBanner(offline: Boolean) {
    AnimatedVisibility(offline, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
        Box(Modifier.fillMaxWidth().background(Nook.colors.surfaceRaised).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            NText("You're offline — messages will send when you're back", NookType.captionBold, Nook.colors.textMuted)
        }
    }
}

