package com.nook.msgapp.ui.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.nook.core.ChatLogic
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.calls.CallPhase
import com.nook.msgapp.calls.VideoView
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Palette
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay

/** The call: black, big avatar or remote video, serif name, timer, controls. Always dark. */
@Composable
fun CallScreen(onOpenChat: (String) -> Unit) {
    val s by CallController.state.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    val context = LocalContext.current
    val me = Session.myUid
    val otherId = s.call?.let { if (it.callerId == me) it.calleeId else it.callerId }
    val other = otherId?.let { profiles[it] }
    val name = other?.displayName ?: if (s.outgoing) "Calling…" else "NOOK call"

    LaunchedEffect(otherId) { otherId?.let { Profiles.ensure(listOf(it)) } }

    // Keep the screen on during a call; swallow back so a call can't be dismissed by accident.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = s.phase != CallPhase.Ended) {}

    // Mic (and camera for video) permission.
    val permissions = remember(s.video) {
        if (s.video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) CallController.permissionsGranted()
    }
    fun missing() = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
    LaunchedEffect(s.phase) {
        if ((s.phase == CallPhase.Calling || s.phase == CallPhase.Connecting) && missing().isNotEmpty()) {
            launcher.launch(missing().toTypedArray())
        }
    }

    // Ended: show the reason briefly, then close.
    LaunchedEffect(s.phase) {
        if (s.phase == CallPhase.Ended) {
            delay(1400)
            CallController.clear()
        }
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val statusLine = when (s.phase) {
        CallPhase.Incoming -> if (s.video) "Incoming video call" else "Incoming voice call"
        CallPhase.Calling -> "Ringing…"
        CallPhase.Connecting -> "Connecting…"
        CallPhase.Reconnecting -> "Reconnecting…"
        CallPhase.Active -> s.connectedAt?.let { ChatLogic.durationLabel(now - it) } ?: "Connecting…"
        CallPhase.Ended -> s.endReason ?: "Call ended"
        CallPhase.Idle -> ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Black)
            // Consume touches so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val remote = s.remoteVideo
        if (remote != null) {
            VideoView(remote, Modifier.fillMaxSize(), fill = true)
        }
        val local = s.localVideo
        AnimatedVisibility(
            visible = local != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Spacing.md),
        ) {
            if (local != null) {
                Box(Modifier.size(width = 104.dp, height = 148.dp).clip(RoundedCornerShape(16.dp)).background(Palette.CharcoalRaised)) {
                    VideoView(local, Modifier.fillMaxSize(), mirror = s.frontCamera, fill = true)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = Spacing.xxxl, start = Spacing.lg, end = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (remote == null) {
                PulsingAvatar(name, other?.photoURL, otherId, pulsing = s.phase == CallPhase.Incoming || s.phase == CallPhase.Calling)
                Spacer(Modifier.height(Spacing.xl))
            }
            NText(name, NookType.display.copy(fontSize = 34.sp, lineHeight = 38.sp), Palette.Cream, maxLines = 1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            NText(statusLine, NookType.body, Palette.Muted, textAlign = TextAlign.Center)
            if (s.remoteMuted && s.phase == CallPhase.Active) {
                Spacer(Modifier.height(4.dp))
                NText("$name is muted", NookType.caption, Palette.Muted)
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.xl)) {
            if (s.phase == CallPhase.Incoming) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xxl), horizontalArrangement = Arrangement.SpaceBetween) {
                    Control(Icons.Rounded.CallEnd, "Decline", danger = true) { CallController.decline() }
                    Control(if (s.video) Icons.Rounded.Videocam else Icons.Rounded.Call, "Accept", accept = true) {
                        val need = missing()
                        if (need.isNotEmpty()) launcher.launch(need.toTypedArray())
                        CallController.accept()
                    }
                }
            } else if (s.phase != CallPhase.Ended) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Control(if (s.micOn) Icons.Rounded.Mic else Icons.Rounded.MicOff, if (s.micOn) "Mute" else "Unmute", active = !s.micOn) { CallController.toggleMic() }
                    Control(Icons.AutoMirrored.Rounded.VolumeUp, "Speaker", active = s.speakerOn) { CallController.toggleSpeaker() }
                    Control(if (s.cameraOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, "Camera", active = s.cameraOn) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            launcher.launch(arrayOf(Manifest.permission.CAMERA))
                        }
                        CallController.toggleCamera()
                    }
                    if (s.cameraOn) Control(Icons.Rounded.Cameraswitch, "Flip") { CallController.flipCamera() }
                    Control(Icons.Rounded.CallEnd, "End", danger = true) { CallController.hangUp() }
                }
            }
        }
    }
}

@Composable
private fun PulsingAvatar(name: String, url: String?, seed: String?, pulsing: Boolean) {
    val t = rememberInfiniteTransition(label = "ring")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "pulse")
    Box(contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(148.dp)
                    .graphicsLayer {
                        val sc = 1f + pulse * 0.35f
                        scaleX = sc
                        scaleY = sc
                        alpha = (1f - pulse) * 0.5f
                    }
                    .clip(CircleShape)
                    .background(Palette.Orange),
            )
        }
        Avatar(name, url, size = 140.dp, seed = seed)
    }
}

@Composable
private fun Control(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    danger: Boolean = false,
    accept: Boolean = false,
    onClick: () -> Unit,
) {
    val bg = when {
        danger -> Palette.Coral
        accept -> Palette.Online
        active -> Palette.Cream
        else -> Palette.CharcoalRaised
    }
    val fg = when {
        danger || accept -> Color.White
        active -> Color.Black
        else -> Palette.Cream
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(62.dp).clip(CircleShape).background(bg).pressScale(scaleTo = 0.88f, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(6.dp))
        NText(label, NookType.micro, Palette.Muted)
        Spacer(Modifier.width(1.dp))
    }
}
