package com.nook.msgapp.ui.setup

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nook.msgapp.push.Notifications
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Palette
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay

private enum class PermKey { Notifications, Camera, Microphone, FullScreen }

private enum class PermState { Granted, Undetermined, Denied, Blocked }

private class PermSpec(
    val key: PermKey,
    val icon: ImageVector,
    val tile: Color,
    val title: String,
    val reason: String,
)

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** The runtime permission behind a card, or null when nothing needs asking at runtime. */
private fun manifestPermission(key: PermKey): String? = when (key) {
    PermKey.Notifications -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
    PermKey.Camera -> Manifest.permission.CAMERA
    PermKey.Microphone -> Manifest.permission.RECORD_AUDIO
    PermKey.FullScreen -> null
}

private fun isGranted(context: Context, key: PermKey): Boolean = when (key) {
    PermKey.Notifications -> NotificationManagerCompat.from(context).areNotificationsEnabled()
    PermKey.FullScreen -> Notifications.canUseFullScreenIntent(context)
    else -> {
        val perm = manifestPermission(key)
        perm == null || ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppSettings(context)
    }
}

private fun openFullScreenSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            return
        } catch (_: ActivityNotFoundException) {
            // Fall through to the app's settings page.
        }
    }
    openAppSettings(context)
}

/** Step 2 of 3: explain each permission before Android asks for it. */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val c = Nook.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val specs = remember {
        buildList {
            add(
                PermSpec(
                    PermKey.Notifications, Icons.Outlined.Notifications, Palette.Peach, "Notifications",
                    "Know when someone messages or calls. We only ever say who it's from, never what they wrote.",
                ),
            )
            add(
                PermSpec(
                    PermKey.Camera, Icons.Outlined.PhotoCamera, Palette.Sky, "Camera",
                    "Snap photos for your chats and turn your camera on in video calls.",
                ),
            )
            add(
                PermSpec(
                    PermKey.Microphone, Icons.Outlined.Mic, Palette.Mint, "Microphone",
                    "Record voice notes and talk on calls. NOOK never listens in the background.",
                ),
            )
            if (Build.VERSION.SDK_INT >= 34) {
                add(
                    PermSpec(
                        PermKey.FullScreen, Icons.Outlined.PhoneInTalk, Palette.Lavender, "Full-screen call alerts",
                        "Lets an incoming call fill your screen, even when your phone is locked. Turn it on in the next screen.",
                    ),
                )
            }
        }
    }

    // Bumped whenever the app comes back to the front (e.g. from system Settings) to re-check everything.
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }

    // Permissions the person has denied at least once in this session.
    var denied by remember { mutableStateOf(setOf<PermKey>()) }
    var pending by remember { mutableStateOf<PermKey?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val key = pending
        pending = null
        if (key != null) {
            if (granted) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                denied = denied - key
            } else {
                denied = denied + key
            }
        }
        refreshTick++
    }

    fun stateOf(key: PermKey): PermState {
        if (isGranted(context, key)) return PermState.Granted
        if (key == PermKey.FullScreen) return PermState.Undetermined
        val perm = manifestPermission(key) ?: return PermState.Blocked // pre-13 notifications switched off in Settings
        if (key !in denied) return PermState.Undetermined
        val activity = context.findActivity()
        val canAskAgain = activity != null && activity.shouldShowRequestPermissionRationale(perm)
        return if (canAskAgain) PermState.Denied else PermState.Blocked
    }

    fun allow(key: PermKey, state: PermState) {
        when {
            key == PermKey.FullScreen -> openFullScreenSettings(context)
            state == PermState.Blocked -> if (key == PermKey.Notifications) openNotificationSettings(context) else openAppSettings(context)
            else -> {
                val perm = manifestPermission(key) ?: return
                if (key == PermKey.Notifications) Notifications.ensureChannels(context)
                pending = key
                launcher.launch(perm)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StepHeader(
                step = 2,
                total = 3,
                title = "A few permissions",
                subtitle = "We explain each one before Android asks. Skip any of them; you can change your mind in Settings.",
            )
            specs.forEachIndexed { i, spec ->
                // Reading refreshTick ties the check to resumes and permission results.
                val state = remember(refreshTick, denied, pending) { stateOf(spec.key) }
                EnterUp(delayMs = 60 + i * 80) {
                    PermissionCard(spec, state, busy = pending == spec.key) { allow(spec.key, state) }
                }
            }
            NText(
                "Photos and files you pick are shared only when you send them.",
                NookType.caption,
                c.textMuted,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg),
            )
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            NButton(title = "Continue", onClick = onDone, icon = Icons.AutoMirrored.Rounded.ArrowForward)
        }
    }
}

@Composable
private fun EnterUp(delayMs: Int, content: @Composable () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(300))
    }
    Box(
        Modifier.graphicsLayer {
            alpha = p.value
            translationY = (1f - p.value) * 16.dp.toPx()
        },
    ) { content() }
}

@Composable
private fun PermissionCard(spec: PermSpec, state: PermState, busy: Boolean, onAllow: () -> Unit) {
    val c = Nook.colors
    NCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(Radius.sm)).background(spec.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(spec.icon, contentDescription = null, tint = c.onPastel, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NText(spec.title, NookType.bodyBold)
                NText(spec.reason, NookType.caption, c.textMuted)
            }
            AnimatedContent(
                targetState = state == PermState.Granted,
                transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.7f)) togetherWith fadeOut(tween(120)) },
                label = "perm",
            ) { granted ->
                if (granted) {
                    Box(
                        Modifier.size(44.dp).semantics { contentDescription = "${spec.title} allowed" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.online, modifier = Modifier.size(22.dp))
                    }
                } else {
                    NButton(
                        title = if (state == PermState.Blocked) "Settings" else "Allow",
                        onClick = onAllow,
                        variant = if (state == PermState.Blocked) ButtonVariant.Secondary else ButtonVariant.Primary,
                        loading = busy,
                        block = false,
                    )
                }
            }
        }
    }
}
