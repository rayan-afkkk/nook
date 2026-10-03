package com.nook.msgapp.ui.chat

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nook.core.ChatLogic
import com.nook.core.Limits
import com.nook.core.ReplyRef
import com.nook.core.Sticker
import com.nook.msgapp.data.GiphyItem
import com.nook.msgapp.data.GiphyType
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.media.VoiceClip
import com.nook.msgapp.media.VoiceRecorder
import com.nook.msgapp.ui.components.ListRow
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlin.math.min

enum class AttachSource { Photo, Camera, File }

private val BUTTON = 42.dp
private val CANCEL_DISTANCE = 110.dp
private const val HOLD_MS = 220L

/**
 * Message composer: attach menu, multi-line field, emoji/GIF/sticker panel, send button that swaps
 * with a hold-to-record mic (slide left to cancel), and the reply preview bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Composer(
    replyTo: ReplyRef?,
    replyAuthor: String,
    onCancelReply: () -> Unit,
    onSendText: (String) -> Unit,
    onTyping: (Boolean) -> Unit,
    onAttach: (AttachSource) -> Unit,
    onVoice: (VoiceClip) -> Unit,
    onGiphy: (GiphyItem, GiphyType) -> Unit,
    onSticker: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Nook.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focus = remember { FocusRequester() }
    var text by rememberSaveable { mutableStateOf("") }
    var panel by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }
    var recordingUi by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val recorder = remember { VoiceRecorder(context) }
    val elapsed by recorder.elapsedMs.collectAsState()
    val level by recorder.level.collectAsState()
    val hasText = text.isNotBlank()

    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }
    BackHandler(enabled = panel) { panel = false }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Toasts.show("Hold the mic to record a voice note.")
        } else {
            Toasts.show("Microphone access is off. Turn it on in Android settings.")
        }
    }

    // Reply chosen: bring the keyboard up so they can type right away.
    LaunchedEffect(replyTo?.id) {
        if (replyTo != null && !panel) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSendText(t)
        text = ""
        onTyping(false)
    }

    fun beginRecording(): Boolean {
        if (!recorder.hasPermission()) {
            LockStore.skipNextBackgroundLock()
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        return try {
            recorder.start()
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            panel = false
            keyboard?.hide()
            dragX = 0f
            recordingUi = true
            true
        } catch (e: Exception) {
            Toasts.error(e)
            false
        }
    }

    fun endRecording(cancel: Boolean) {
        recordingUi = false
        dragX = 0f
        if (cancel) {
            recorder.cancel()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            return
        }
        val clip = recorder.stop()
        if (clip != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onVoice(clip)
        } else {
            Toasts.show("Hold the mic to record a voice note.")
        }
    }

    val begin by rememberUpdatedState({ beginRecording() })
    val end by rememberUpdatedState({ cancel: Boolean -> endRecording(cancel) })

    // Keep the last reply around while the bar animates out.
    val lastReply = remember { arrayOf<ReplyRef?>(null) }
    val lastAuthor = remember { arrayOf("") }
    if (replyTo != null) {
        lastReply[0] = replyTo
        lastAuthor[0] = replyAuthor
    }

    Column(modifier.fillMaxWidth().background(c.background)) {
        NDivider()
        AnimatedVisibility(
            visible = replyTo != null,
            enter = expandVertically(tween(180)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(150)),
        ) {
            val r = replyTo ?: lastReply[0]
            if (r != null) {
                Row(
                    Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    ReplyQuote(r, if (replyTo != null) replyAuthor else lastAuthor[0], compact = true, modifier = Modifier.weight(1f))
                    NIconButton(Icons.Rounded.Close, "Cancel reply", onCancelReply, size = 34.dp, iconSize = 18.dp)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (recordingUi) {
                RecordingBar(elapsed, level, dragX, Modifier.weight(1f))
            } else {
                val rotation by animateFloatAsState(if (attachOpen) 45f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "attach")
                NIconButton(
                    Icons.Rounded.Add,
                    "Attach",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        attachOpen = true
                    },
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    background = c.surface,
                    border = true,
                    size = BUTTON,
                    iconSize = 22.dp,
                )
                val shape = RoundedCornerShape(Radius.card)
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = BUTTON)
                        .clip(shape)
                        .background(c.surface)
                        .border(1.dp, c.border, shape)
                        .padding(start = Spacing.md),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = BUTTON - 2.dp)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) NText("Message", NookType.bodyLarge, c.textMuted, maxLines = 1)
                        BasicTextField(
                            value = text,
                            onValueChange = { v ->
                                val next = v.take(Limits.MAX_TEXT)
                                text = next
                                onTyping(next.isNotBlank())
                            },
                            textStyle = NookType.bodyLarge.copy(color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                .onFocusChanged { if (it.isFocused) panel = false },
                        )
                    }
                    Box(
                        Modifier
                            .size(width = 40.dp, height = BUTTON - 2.dp)
                            .pressScale(scaleTo = 0.85f) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (panel) {
                                    panel = false
                                    runCatching { focus.requestFocus() }
                                    keyboard?.show()
                                } else {
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                    panel = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (panel) Icons.Rounded.Keyboard else Icons.Outlined.EmojiEmotions,
                            contentDescription = if (panel) "Show keyboard" else "Emoji, GIFs and stickers",
                            tint = c.textMuted,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            }
            AnimatedContent(
                targetState = hasText,
                transitionSpec = { (scaleIn(tween(160), initialScale = 0.6f) + fadeIn(tween(160))) togetherWith (scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(120))) },
                label = "sendMic",
            ) { showSend ->
                if (showSend) {
                    Box(
                        Modifier
                            .size(BUTTON)
                            .clip(CircleShape)
                            .background(c.primary)
                            .pressScale(scaleTo = 0.88f) { send() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, "Send", tint = c.onPrimary, modifier = Modifier.size(22.dp))
                    }
                } else {
                    MicButton(
                        recording = recordingUi,
                        level = level,
                        onBegin = { begin() },
                        onDrag = { dragX = it },
                        onEnd = { cancel -> end(cancel) },
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = panel && !recordingUi,
            enter = expandVertically(tween(220)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
        ) {
            MediaPanel(
                onEmoji = { e ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    text = (text + e).take(Limits.MAX_TEXT)
                    onTyping(true)
                },
                onGiphy = { item, type ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onGiphy(item, type)
                },
                onSticker = { s ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSticker(s)
                },
                modifier = Modifier.height(MEDIA_PANEL_HEIGHT),
            )
        }
    }

    if (attachOpen) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { attachOpen = false },
            sheetState = sheet,
            containerColor = c.surface,
            contentColor = c.text,
            scrimColor = c.overlay,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)) {
                ListRow("Photo", icon = Icons.Outlined.Image, subtitle = "From your gallery", showChevron = false, onClick = {
                    attachOpen = false
                    onAttach(AttachSource.Photo)
                })
                ListRow("Camera", icon = Icons.Outlined.PhotoCamera, subtitle = "Take a photo now", showChevron = false, onClick = {
                    attachOpen = false
                    onAttach(AttachSource.Camera)
                })
                ListRow("File", icon = Icons.Outlined.Description, subtitle = "Up to 10 MB", showChevron = false, onClick = {
                    attachOpen = false
                    onAttach(AttachSource.File)
                })
            }
        }
    }
}

/** Timer, pulsing dot, live level and "slide to cancel" while a voice note records. */
@Composable
private fun RecordingBar(elapsedMs: Long, level: Float, dragX: Float, modifier: Modifier = Modifier) {
    val c = Nook.colors
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "recDot",
    )
    val shownLevel by animateFloatAsState(level, tween(100), label = "level")
    Row(
        modifier.heightIn(min = BUTTON).padding(start = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { alpha = pulse }
                .clip(CircleShape)
                .background(c.danger),
        )
        NText(ChatLogic.durationLabel(elapsedMs), NookType.label, modifier = Modifier.widthIn(min = 40.dp))
        // Live input level.
        Box(
            Modifier
                .width(28.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(c.surfaceRaised),
        ) {
            Box(
                Modifier
                    .width((28f * shownLevel.coerceIn(0.05f, 1f)).dp)
                    .height(4.dp)
                    .background(c.danger),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row(
                Modifier
                    .padding(end = Spacing.xs)
                    .graphicsLayer {
                        translationX = min(0f, dragX)
                        alpha = (1f + min(0f, dragX) / 160.dp.toPx()).coerceIn(0f, 1f)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                NText("Slide to cancel", NookType.caption, c.textMuted)
            }
        }
    }
}

/**
 * Hold to record (after a short hold), slide left past [CANCEL_DISTANCE] to cancel, release to send.
 * A quick tap shows a hint.
 */
@Composable
private fun MicButton(
    recording: Boolean,
    level: Float,
    onBegin: () -> Boolean,
    onDrag: (Float) -> Unit,
    onEnd: (cancel: Boolean) -> Unit,
) {
    val c = Nook.colors
    val latestBegin by rememberUpdatedState(onBegin)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onEnd)
    val scale by animateFloatAsState(if (recording) 1.15f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "mic")
    val bg by animateColorAsState(if (recording) c.danger else c.primary, tween(160), label = "micBg")
    val halo by animateFloatAsState(if (recording) 1f + level * 0.7f else 1f, tween(100), label = "halo")
    Box(
        Modifier
            .size(BUTTON)
            .pointerInput(Unit) {
                val cancelPx = CANCEL_DISTANCE.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val released = withTimeoutOrNull(HOLD_MS) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (released == true) {
                        Toasts.show("Hold to record, slide left to cancel.")
                        return@awaitEachGesture
                    }
                    if (!latestBegin()) return@awaitEachGesture
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        if (!cancelled) latestDrag(dx)
                        if (!cancelled && dx < -cancelPx) {
                            cancelled = true
                            latestEnd(true)
                        }
                        change.consume()
                    }
                    if (!cancelled) latestEnd(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(
                Modifier
                    .size(BUTTON)
                    .graphicsLayer {
                        scaleX = halo * scale
                        scaleY = halo * scale
                        alpha = 0.25f
                    }
                    .clip(CircleShape)
                    .background(c.danger),
            )
        }
        Box(
            Modifier
                .size(BUTTON)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, "Record voice note", tint = c.onPrimary, modifier = Modifier.size(22.dp))
        }
    }
}
