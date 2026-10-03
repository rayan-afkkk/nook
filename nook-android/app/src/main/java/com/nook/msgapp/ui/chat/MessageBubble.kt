package com.nook.msgapp.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nook.core.ChatLogic
import com.nook.core.Format
import com.nook.core.Media
import com.nook.core.Message
import com.nook.core.MessageKind
import com.nook.core.ReplyRef
import com.nook.msgapp.data.OutboxItem
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.ProgressBar
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private val REPLY_TRIGGER = 56.dp
private val GROUP_AVATAR = 26.dp

/** Max media size inside a bubble (dp). */
@Composable
fun maxMediaWidth(): Float = min(260f, LocalConfiguration.current.screenWidthDp * 0.66f)

/** Scales media to fit [max] wide and 1.3 × [max] tall without upscaling. */
fun mediaSize(media: Media?, max: Float): Pair<Dp, Dp> {
    val w = (media?.width?.takeIf { it > 0 } ?: max.toInt()).toFloat()
    val h = (media?.height?.takeIf { it > 0 } ?: max.toInt()).toFloat()
    val scale = minOf(max / w, (max * 1.3f) / h, 1f)
    return (w * scale).roundToInt().dp to (h * scale).roundToInt().dp
}

/** Opens a remote file in whatever app handles it (falls back to the browser). */
fun openRemoteFile(context: Context, url: String, mime: String?) {
    val uri = Uri.parse(url)
    LockStore.skipNextBackgroundLock()
    val typed = Intent(Intent.ACTION_VIEW).apply {
        if (!mime.isNullOrEmpty()) setDataAndType(uri, mime) else data = uri
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(typed)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
            Toasts.show("No app on this phone can open that file.")
        }
    }
}

/* ------------------------------------------------------------------ date separator */

@Composable
fun DateSeparator(label: String) {
    val c = Nook.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.weight(1f).heightIn(min = 0.5.dp, max = 0.5.dp).background(c.border))
        NText(label.uppercase(), NookType.micro, c.textMuted)
        Box(Modifier.weight(1f).heightIn(min = 0.5.dp, max = 0.5.dp).background(c.border))
    }
}

/* ------------------------------------------------------------------ message row */

/**
 * One message in the list: optional sender name (groups), avatar, the bubble (long-press for the menu,
 * swipe right to reply), reaction pills and the "Seen" footer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(
    message: Message,
    me: String,
    mine: Boolean,
    isGroup: Boolean,
    senderName: String?,
    senderPhoto: String?,
    showAvatar: Boolean,
    replyAuthor: String?,
    chainedAbove: Boolean,
    chainedBelow: Boolean,
    footer: String?,
    onReply: (Message) -> Unit,
    onLongPress: (Message, Rect) -> Unit,
    onOpenImage: (Message) -> Unit,
    onToggleReaction: (Message, String) -> Unit,
) {
    val c = Nook.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxMedia = maxMediaWidth()
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.78f).dp
    val triggerPx = with(density) { REPLY_TRIGGER.toPx() }
    val maxDragPx = with(density) { (REPLY_TRIGGER + 16.dp).toPx() }
    val dragX = remember { Animatable(0f) }
    val bounds = remember { arrayOf(Rect.Zero) }
    val latestMessage by rememberUpdatedState(message)
    val latestReply by rememberUpdatedState(onReply)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "squeeze")
    val showGroupAvatar = isGroup && !mine

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm)
            .padding(top = if (chainedAbove) 2.dp else Spacing.xs),
    ) {
        if (senderName != null && !mine) {
            NText(
                senderName,
                NookType.captionBold,
                c.textMuted,
                Modifier.padding(start = (if (showGroupAvatar) GROUP_AVATAR + 6.dp else 0.dp) + 10.dp, bottom = 2.dp),
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var x = 0f
                    var armed = false
                    detectHorizontalDragGestures(
                        onDragStart = { x = dragX.value },
                        onDragEnd = {
                            if (armed) latestReply(latestMessage)
                            armed = false
                            x = 0f
                            scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                        },
                        onDragCancel = {
                            armed = false
                            x = 0f
                            scope.launch { dragX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                        },
                    ) { change, amount ->
                        val next = (x + amount * 0.6f).coerceIn(0f, maxDragPx)
                        if (next != x || next > 0f) change.consume()
                        x = next
                        scope.launch { dragX.snapTo(next) }
                        if (!armed && next >= triggerPx) {
                            armed = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else if (armed && next < triggerPx) {
                            armed = false
                        }
                    }
                },
        ) {
            // Reply arrow revealed behind the bubble while swiping.
            Icon(
                Icons.AutoMirrored.Rounded.Reply,
                contentDescription = null,
                tint = c.textMuted,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(18.dp)
                    .graphicsLayer {
                        val p = (dragX.value / triggerPx).coerceIn(0f, 1f)
                        alpha = p
                        scaleX = 0.6f + 0.4f * p
                        scaleY = 0.6f + 0.4f * p
                    },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(dragX.value.roundToInt(), 0) },
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (showGroupAvatar) {
                    if (showAvatar) {
                        Avatar(senderName ?: "", senderPhoto, size = GROUP_AVATAR, seed = message.senderId)
                    } else {
                        Spacer(Modifier.width(GROUP_AVATAR))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Box(
                    Modifier
                        .widthIn(max = maxBubble)
                        .onGloballyPositioned { bounds[0] = it.boundsInWindow() }
                        .graphicsLayer {
                            scaleX = squeeze
                            scaleY = squeeze
                        }
                        .combinedClickable(
                            interactionSource = source,
                            indication = null,
                            onClick = {
                                val m = latestMessage
                                when (m.kind) {
                                    MessageKind.Image -> onOpenImage(m)
                                    MessageKind.File -> m.media?.let { openRemoteFile(context, it.url, it.mime) }
                                    else -> Unit
                                }
                            },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress(latestMessage, bounds[0])
                            },
                        ),
                ) {
                    BubbleBody(message, mine, replyAuthor, chainedBelow, maxMedia)
                }
            }
        }
        ReactionPills(message.reactions, me, mine, showGroupAvatar) { onToggleReaction(message, it) }
        if (footer != null) {
            NText(footer, NookType.micro, c.textMuted, Modifier.align(Alignment.End).padding(end = Spacing.xs, top = 2.dp))
        }
    }
}

/* ------------------------------------------------------------------ bubble body */

private val URL_REGEX = Regex("""(https?://[^\s]+|www\.[^\s]+)""", RegexOption.IGNORE_CASE)

private fun linkify(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in URL_REGEX.findAll(text)) {
        val raw = match.value.trimEnd('.', ',', ')', '!', '?', ';', ':')
        val start = match.range.first
        val end = start + raw.length
        if (start > last) append(text.substring(last, start))
        val url = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
        pushLink(LinkAnnotation.Url(url, TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))))
        append(raw)
        pop()
        last = end
    }
    if (last < text.length) append(text.substring(last))
}

/** The bubble itself (no swipe, reactions or footer). Shared by the list and the long-press menu. */
@Composable
fun BubbleBody(
    message: Message,
    mine: Boolean,
    replyAuthor: String?,
    chainedBelow: Boolean,
    maxMedia: Float,
    interactive: Boolean = true,
) {
    val c = Nook.colors
    val m = message
    val bare = m.kind == MessageKind.Sticker || m.kind == MessageKind.Gif || m.kind == MessageKind.Image
    val fg = if (mine) c.onPrimary else c.text
    val tail = 6.dp
    val round = 20.dp
    val shape = when {
        bare || chainedBelow -> RoundedCornerShape(round)
        mine -> RoundedCornerShape(topStart = round, topEnd = round, bottomEnd = tail, bottomStart = round)
        else -> RoundedCornerShape(topStart = round, topEnd = round, bottomEnd = round, bottomStart = tail)
    }
    val time = m.createdAt?.let { ChatLogic.clockLabel(it) } ?: ""

    Column(
        if (bare) {
            Modifier
        } else {
            Modifier
                .clip(shape)
                .background(if (mine) c.primary else c.surface)
                .border(1.dp, if (mine) c.primary else c.border, shape)
                .padding(start = 11.dp, end = 11.dp, top = 7.dp, bottom = 5.dp)
        },
    ) {
        if (m.forwarded) {
            Row(
                Modifier.padding(bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Forward, null, tint = if (mine) c.onPrimary else c.textMuted, modifier = Modifier.size(12.dp))
                NText("Forwarded", NookType.micro, (if (mine) c.onPrimary else c.textMuted).copy(alpha = 0.8f))
            }
        }
        m.replyTo?.let { ReplyQuote(it, replyAuthor ?: "Message", onMine = mine && !bare) }
        Box {
            when (m.kind) {
                MessageKind.Image -> {
                    val (w, h) = mediaSize(m.media, maxMedia)
                    AsyncImage(
                        model = ChatLogic.thumbnailUrl(m.media?.url.orEmpty(), (w.value * 2).roundToInt()),
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(w, h).clip(RoundedCornerShape(18.dp)).background(c.surfaceRaised),
                    )
                }
                MessageKind.Gif, MessageKind.Sticker -> {
                    val sticker = m.kind == MessageKind.Sticker
                    val (w, h) = if (sticker) 140.dp to 140.dp else mediaSize(m.media, maxMedia)
                    val shapeMedia = RoundedCornerShape(18.dp)
                    Box(
                        if (sticker) Modifier.size(w, h) else Modifier.size(w, h).clip(shapeMedia).background(c.surfaceRaised),
                    ) {
                        val preview = m.media?.previewUrl
                        if (!sticker && !preview.isNullOrEmpty()) {
                            AsyncImage(model = preview, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(w, h))
                        }
                        AsyncImage(
                            model = m.media?.url,
                            contentDescription = if (sticker) "Sticker" else "GIF",
                            contentScale = if (sticker) ContentScale.Fit else ContentScale.Crop,
                            modifier = Modifier.size(w, h),
                        )
                        if (!sticker) {
                            Box(
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(c.overlay)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                NText("GIF", NookType.micro, Color.White)
                            }
                        }
                    }
                }
                MessageKind.File -> m.media?.let { FileCard(it, mine) }
                MessageKind.Voice -> m.media?.let { VoiceBubble(m.id, it, mine, interactive) }
                else -> {
                    val text = m.text.orEmpty()
                    val linkColor = if (mine) c.onPrimary else c.accent
                    val annotated = remember(text, linkColor) { linkify(text, linkColor) }
                    Text(annotated, style = NookType.bodyLarge, color = fg)
                }
            }
            if (bare) {
                MetaLine(m, mine, time, bare = true, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        if (!bare) {
            MetaLine(m, mine, time, bare = false, modifier = Modifier.align(Alignment.End).padding(top = 1.dp))
        }
    }
}

@Composable
private fun MetaLine(m: Message, mine: Boolean, time: String, bare: Boolean, modifier: Modifier = Modifier) {
    val c = Nook.colors
    val tint = when {
        bare -> Color.White
        mine -> c.onPrimary
        else -> c.textMuted
    }
    Row(
        modifier.then(
            if (bare) {
                Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(c.overlay)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            } else {
                Modifier
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (m.expireAt != null) Icon(Icons.Rounded.Timer, "Disappearing", tint = tint.copy(alpha = 0.75f), modifier = Modifier.size(11.dp))
        NText(time, NookType.micro, tint.copy(alpha = 0.75f))
        if (mine) {
            Icon(
                if (m.pending) Icons.Rounded.Schedule else Icons.Rounded.Done,
                contentDescription = if (m.pending) "Sending" else "Sent",
                tint = tint.copy(alpha = 0.75f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------ reply quote */

fun kindIcon(kind: MessageKind): ImageVector? = when (kind) {
    MessageKind.Image -> Icons.Rounded.Image
    MessageKind.File -> Icons.Outlined.Description
    MessageKind.Voice -> Icons.Rounded.Mic
    MessageKind.Gif -> Icons.Rounded.Gif
    MessageKind.Sticker -> Icons.Rounded.EmojiEmotions
    else -> null
}

/** Quote of the message being replied to, inside a bubble or above the composer. */
@Composable
fun ReplyQuote(reply: ReplyRef, author: String, onMine: Boolean = false, compact: Boolean = false, modifier: Modifier = Modifier) {
    val c = Nook.colors
    val fg = if (onMine) c.onPrimary else c.text
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier
            .padding(bottom = if (compact) 0.dp else 5.dp)
            .clip(shape)
            .background(if (onMine) Color.Black.copy(alpha = 0.08f) else c.surfaceRaised),
    ) {
        Box(Modifier.width(3.dp).heightIn(min = 36.dp).background(c.accent))
        Column(Modifier.padding(horizontal = Spacing.xs, vertical = 5.dp)) {
            NText(author, NookType.captionBold, fg, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                kindIcon(reply.kind)?.let { Icon(it, null, tint = fg.copy(alpha = 0.75f), modifier = Modifier.size(13.dp)) }
                Text(
                    reply.text.ifEmpty { "Message" },
                    style = NookType.caption,
                    color = fg.copy(alpha = 0.75f),
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ reactions */

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReactionPills(reactions: Map<String, String>, me: String, mine: Boolean, indent: Boolean, onToggle: (String) -> Unit) {
    if (reactions.isEmpty()) return
    val c = Nook.colors
    val haptics = LocalHapticFeedback.current
    val pills = remember(reactions, me) { ChatLogic.groupReactions(reactions, me) }
    Box(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier
                .align(if (mine) Alignment.TopEnd else Alignment.TopStart)
                .offset(y = (-4).dp)
                .padding(start = if (!mine) (if (indent) GROUP_AVATAR + 6.dp else 0.dp) + 8.dp else 0.dp, end = if (mine) 8.dp else 0.dp)
                .widthIn(max = (LocalConfiguration.current.screenWidthDp * 0.78f).dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            pills.forEach { p ->
                Row(
                    Modifier
                        .heightIn(min = 24.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(c.surfaceRaised)
                        .border(1.dp, if (p.mine) c.accent else c.border, RoundedCornerShape(Radius.pill))
                        .pressScale(scaleTo = 0.9f) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle(p.emoji)
                        }
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    NText(p.emoji, NookType.caption)
                    if (p.count > 1) NText(p.count.toString(), NookType.micro, c.textMuted)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ file card */

private fun fileIcon(mime: String?, name: String?): ImageVector {
    val n = (name ?: "").lowercase()
    return when {
        mime?.contains("pdf") == true || n.endsWith(".pdf") -> Icons.Outlined.PictureAsPdf
        mime?.startsWith("audio") == true || n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".wav") -> Icons.Outlined.MusicNote
        mime?.startsWith("video") == true || n.endsWith(".mp4") || n.endsWith(".mov") -> Icons.Outlined.Movie
        n.endsWith(".zip") || n.endsWith(".rar") || n.endsWith(".7z") -> Icons.Outlined.Archive
        else -> Icons.Outlined.Description
    }
}

@Composable
fun FileCard(media: Media, mine: Boolean) {
    val c = Nook.colors
    val fg = if (mine) c.onPrimary else c.text
    Row(
        Modifier.widthIn(min = 180.dp, max = 250.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (mine) Color.Black.copy(alpha = 0.08f) else c.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(fileIcon(media.mime, media.name), null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NText(media.name ?: "File", NookType.bodyBold, fg, maxLines = 2)
            NText("${Format.bytes(media.size ?: 0L)} · Tap to open", NookType.micro, fg.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

/* ------------------------------------------------------------------ outbox */

/** A media message that is still uploading (or failed), shown at the bottom of the chat. */
@Composable
fun OutboxBubble(item: OutboxItem, onRetry: () -> Unit, onCancel: () -> Unit) {
    val c = Nook.colors
    val failed = item.error != null
    val isImage = item.kind == MessageKind.Image || item.kind == MessageKind.Sticker
    val label = when {
        isImage -> "Photo"
        item.kind == MessageKind.Voice -> "Voice note"
        else -> item.file.name
    }
    val progress by animateFloatAsState(item.progress.coerceAtLeast(0.04f), tween(200), label = "upload")
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm).padding(top = Spacing.xs), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier
                .widthIn(min = 180.dp, max = (LocalConfiguration.current.screenWidthDp * 0.78f).dp)
                .graphicsLayer { alpha = if (failed) 0.9f else 1f }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp))
                .background(c.primary)
                .padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (isImage) {
                AsyncImage(
                    model = item.file.uri,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(15.dp)).background(c.surfaceRaised),
                )
            } else {
                Row(
                    Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        if (item.kind == MessageKind.Voice) Icons.Rounded.Mic else Icons.Outlined.Description,
                        null,
                        tint = c.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    NText(label, NookType.bodyBold, c.onPrimary, Modifier.weight(1f, fill = false), maxLines = 1)
                    if (item.file.size > 0) NText(Format.bytes(item.file.size), NookType.micro, c.onPrimary.copy(alpha = 0.7f))
                }
            }
            if (failed) {
                Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    NText(item.error ?: "Upload failed", NookType.caption, c.danger, Modifier.weight(1f), maxLines = 2)
                    Box(Modifier.size(36.dp).pressScale(scaleTo = 0.88f, onClick = onRetry), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Refresh, "Retry upload", tint = c.onPrimary, modifier = Modifier.size(18.dp))
                    }
                    Box(Modifier.size(36.dp).pressScale(scaleTo = 0.88f, onClick = onCancel), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Discard", tint = c.onPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ProgressBar(progress) }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(24.dp).pressScale(scaleTo = 0.88f, onClick = onCancel), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, "Cancel upload", tint = c.onPrimary.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}
