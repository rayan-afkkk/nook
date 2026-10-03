package com.nook.msgapp.ui.home

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.Disappearing
import com.nook.core.LockKind
import com.nook.core.Profile
import com.nook.core.ProfileState
import com.nook.msgapp.BuildConfig
import com.nook.msgapp.data.Auth
import com.nook.msgapp.data.Cloudinary
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.ui.LocalActivity
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.Chip
import com.nook.msgapp.ui.components.ListRow
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NHeader
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.ProgressBar
import com.nook.msgapp.ui.components.SectionLabel
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.components.rememberCameraCapture
import com.nook.msgapp.ui.components.rememberImagePicker
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import com.nook.msgapp.ui.theme.ThemeMode
import kotlinx.coroutines.launch

internal fun disappearingLabel(d: Disappearing) = when (d) {
    Disappearing.Off -> "Off"
    Disappearing.Day -> "24 hours"
    Disappearing.Week -> "7 days"
}

internal fun themeLabel(t: ThemeMode) = when (t) {
    ThemeMode.System -> "Match the phone"
    ThemeMode.Dark -> "Dark"
    ThemeMode.Light -> "Light"
}

private enum class AccountConfirm { SignOut, Delete }

/**
 * Profile photo flow shared by the Account tab and Edit profile: pick / capture / remove, with upload
 * progress. Returns the state holder; call [PhotoChanger.open] to show the sheet.
 */
internal class PhotoChanger {
    var sheet by mutableStateOf(false)
    var progress by mutableStateOf<Float?>(null)
    fun open() {
        if (progress == null) sheet = true
    }
}

@Composable
internal fun rememberPhotoChanger(profile: Profile?): PhotoChanger {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { PhotoChanger() }
    val uid = profile?.uid

    fun upload(uri: Uri?) {
        val me = uid ?: return
        state.progress = 0f
        scope.launch {
            try {
                val file = uri?.let { Cloudinary.describe(context, it, "avatar.jpg") }
                Profiles.setPhoto(context, me, file) { p -> state.progress = p }
                Toasts.show(if (file != null) "Profile photo updated." else "Profile photo removed.")
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                state.progress = null
            }
        }
    }

    val pick = rememberImagePicker { uri -> upload(uri) }
    val camera = rememberCameraCapture { uri -> upload(uri) }

    if (state.sheet) {
        NSheet(onDismiss = { state.sheet = false }) {
            NText("Profile photo", NookType.headline)
            ListRow(
                title = "Choose a photo",
                subtitle = "From your gallery",
                icon = Icons.Outlined.PhotoLibrary,
                showChevron = false,
                onClick = {
                    state.sheet = false
                    pick()
                },
            )
            ListRow(
                title = "Take a photo",
                icon = Icons.Outlined.PhotoCamera,
                showChevron = false,
                onClick = {
                    state.sheet = false
                    camera()
                },
            )
            if (!profile?.photoURL.isNullOrEmpty()) {
                ListRow(
                    title = "Remove photo",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    showChevron = false,
                    onClick = {
                        state.sheet = false
                        upload(null)
                    },
                )
            }
        }
    }
    return state
}

/** Avatar with a camera badge and an upload spinner. */
@Composable
internal fun EditableAvatar(profile: Profile?, changer: PhotoChanger, size: androidx.compose.ui.unit.Dp) {
    val c = Nook.colors
    Box(
        Modifier.pressScale(enabled = changer.progress == null, scaleTo = 0.94f) { changer.open() },
    ) {
        Avatar(profile?.displayName ?: "?", profile?.photoURL, size = size, seed = profile?.uid)
        if (changer.progress != null) {
            Box(Modifier.size(size).background(c.overlay, CircleShape), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(24.dp)
                .background(c.primary, CircleShape)
                .border(2.dp, c.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CameraAlt, contentDescription = "Change profile photo", tint = c.onPrimary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun AccountTab(nav: NavController) {
    val c = Nook.colors
    val context = LocalContext.current
    val activity = LocalActivity.current
    val meState by Session.me.collectAsState()
    val profile = (meState as? ProfileState.Ready)?.profile
    val lock by LockStore.record.collectAsState()
    val disappearing by Prefs.disappearingDefault.collectAsState()
    val theme by Prefs.theme.collectAsState()
    val photo = rememberPhotoChanger(profile)

    var confirm by remember { mutableStateOf<AccountConfirm?>(null) }
    var working by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }

    fun onConfirm() {
        val which = confirm ?: return
        working = true
        // Not tied to this screen: sign-out tears the signed-in UI down while it runs.
        AppScope.scope.launch {
            try {
                if (which == AccountConfirm.SignOut) {
                    Auth.signOut(context.applicationContext)
                } else {
                    val done = Auth.deleteAccount(activity, profile?.username)
                    if (!done) {
                        working = false
                        return@launch
                    }
                    Toasts.show("Your account was deleted.")
                }
            } catch (e: Exception) {
                Toasts.error(e)
                working = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        NHeader("Account")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AnimatedVisibility(shown, enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }) {
                NCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        EditableAvatar(profile, photo, 60.dp)
                        Column(Modifier.weight(1f)) {
                            Row(
                                Modifier.pressScale(scaleTo = 0.98f) { go(Routes.EDIT_PROFILE) },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                NText(profile?.displayName ?: "…", NookType.headline, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit name", tint = c.textMuted, modifier = Modifier.size(14.dp))
                            }
                            NText("@${profile?.username ?: ""}", NookType.body, c.textMuted, maxLines = 1)
                            Chip("Member", Modifier.padding(top = Spacing.xs), icon = Icons.Outlined.AutoAwesome)
                        }
                    }
                    AnimatedVisibility(photo.progress != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        ProgressBar(photo.progress ?: 0f, Modifier.padding(top = Spacing.md))
                    }
                }
            }

            Column {
                SectionLabel("Settings", Modifier.padding(bottom = Spacing.xxs))
                ListRow(
                    title = "Edit profile",
                    subtitle = "Your name and photo",
                    icon = Icons.Outlined.Person,
                    onClick = { go(Routes.EDIT_PROFILE) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "App lock",
                    subtitle = lock?.let { r ->
                        (if (r.kind == LockKind.Pin) "PIN" else "Password") + if (r.biometric) " · Fingerprint on" else ""
                    } ?: "Off",
                    icon = Icons.Outlined.Lock,
                    onClick = { go(Routes.APP_LOCK) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Notifications",
                    subtitle = "Who it's from, never what they said",
                    icon = Icons.Outlined.Notifications,
                    onClick = { go(Routes.NOTIFICATIONS) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Disappearing default",
                    subtitle = disappearingLabel(disappearing),
                    icon = Icons.Outlined.Timer,
                    onClick = { go(Routes.DISAPPEARING) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Appearance",
                    subtitle = themeLabel(theme),
                    icon = Icons.Outlined.Palette,
                    onClick = { go(Routes.APPEARANCE) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Blocked users",
                    subtitle = "People who can't message or call you",
                    icon = Icons.Outlined.Block,
                    onClick = { go(Routes.BLOCKED) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Help & Feedback",
                    subtitle = "Answers and how to reach us",
                    icon = Icons.AutoMirrored.Outlined.Help,
                    onClick = { go(Routes.HELP) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Privacy & Terms",
                    subtitle = "What we store and why",
                    icon = Icons.Outlined.Shield,
                    onClick = { go(Routes.PRIVACY) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Sign Out",
                    subtitle = "Also removes the app lock on this phone",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    showChevron = false,
                    onClick = { confirm = AccountConfirm.SignOut },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Delete Account",
                    subtitle = "Permanently remove your profile and username",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    showChevron = false,
                    onClick = {
                        typed = ""
                        confirm = AccountConfirm.Delete
                    },
                )
            }
            NText(
                "NOOK ${BuildConfig.VERSION_NAME} · Made for the crew",
                NookType.caption,
                c.textMuted,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
                textAlign = TextAlign.Center,
            )
        }
    }

    when (confirm) {
        AccountConfirm.SignOut -> ConfirmDialog(
            title = "Sign out?",
            message = "You'll need Google to sign back in, and you'll set a new app lock on this phone.",
            confirmLabel = "Sign out",
            loading = working,
            onConfirm = { onConfirm() },
            onDismiss = { confirm = null },
        )
        AccountConfirm.Delete -> {
            val username = profile?.username.orEmpty()
            ConfirmDialog(
                title = "Delete your account?",
                message = "This removes your profile, frees your username and deletes your sign-in. Google will ask you to confirm it's you. This can't be undone.",
                confirmLabel = "Delete forever",
                destructive = true,
                loading = working,
                confirmEnabled = username.isNotEmpty() && typed.trim().removePrefix("@").lowercase() == username,
                onConfirm = { onConfirm() },
                onDismiss = { confirm = null },
                extra = {
                    NTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = "Type your username to confirm",
                        placeholder = username,
                        prefix = "@",
                        maxLength = 30,
                    )
                },
            )
        }
        null -> Unit
    }
}
