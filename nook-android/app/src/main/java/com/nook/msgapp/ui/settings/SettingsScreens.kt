package com.nook.msgapp.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.nook.core.Disappearing
import com.nook.core.LockKind
import com.nook.core.ProfileState
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.data.Private
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.lock.Biometrics
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.push.Notifications
import com.nook.msgapp.ui.LocalActivity
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.CardTone
import com.nook.msgapp.ui.components.Chip
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.ListRow
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.NToggle
import com.nook.msgapp.ui.components.NTopBar
import com.nook.msgapp.ui.components.Segmented
import com.nook.msgapp.ui.home.EditableAvatar
import com.nook.msgapp.ui.home.displayNameOf
import com.nook.msgapp.ui.home.disappearingLabel
import com.nook.msgapp.ui.home.rememberPhotoChanger
import com.nook.msgapp.ui.lock.LockSetupFlow
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import com.nook.msgapp.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ shared scaffolding */

/** Top bar + scrolling padded column, the layout every settings screen uses. */
@Composable
private fun SettingsPage(
    title: String,
    nav: NavController,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Nook.colors.background)) {
        NTopBar(title = title, onBack = { nav.popBackStack() })
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.lg, bottom = Spacing.xxl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

/** Calls [onResume] every time the screen comes back to the foreground (e.g. from Android settings). */
@Composable
private fun OnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onResume() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Opens an Android settings screen without re-locking NOOK on the way back. */
private fun openSettings(context: Context, intent: Intent) {
    LockStore.skipNextBackgroundLock()
    val ok = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    if (!ok) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun appNotificationSettings(context: Context) =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

private fun channelSettings(context: Context, channel: String) =
    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, channel)

/* ------------------------------------------------------------------ edit profile */

@Composable
fun EditProfileScreen(nav: NavController) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    val meState by Session.me.collectAsState()
    val profile = (meState as? ProfileState.Ready)?.profile
    val photo = rememberPhotoChanger(profile)
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Start from the current name once the profile is known.
    LaunchedEffect(profile?.displayName) {
        if (name == null && profile != null) name = profile.displayName
    }
    val draft = name.orEmpty()
    val dirty = draft.trim().isNotEmpty() && draft.trim() != profile?.displayName

    fun save() {
        val uid = profile?.uid ?: return
        if (!dirty || saving) return
        saving = true
        scope.launch {
            try {
                Profiles.setDisplayName(uid, draft)
                Toasts.show("Saved.")
                nav.popBackStack()
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                saving = false
            }
        }
    }

    SettingsPage("Edit profile", nav) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            EditableAvatar(profile, photo, 96.dp)
            NButton(
                title = if (profile?.photoURL.isNullOrEmpty()) "Add a photo" else "Change photo",
                onClick = { photo.open() },
                variant = ButtonVariant.Ghost,
                block = false,
                loading = photo.progress != null,
            )
        }
        NTextField(
            value = draft,
            onValueChange = { name = it },
            label = "Display name",
            placeholder = "Your name",
            helper = "Shown on your messages.",
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
            onSubmit = { save() },
            maxLength = 40,
        )
        NCard(Modifier.fillMaxWidth()) {
            NText("Username", NookType.captionBold, c.textMuted)
            NText("@${profile?.username.orEmpty()}", NookType.bodyLarge, modifier = Modifier.padding(top = 2.dp))
            NText(
                "Usernames are permanent so nobody can take over a name your friends already trust.",
                NookType.caption,
                c.textMuted,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        NButton("Save", { save() }, icon = Icons.Rounded.Check, loading = saving, enabled = dirty)
    }
}

/* ------------------------------------------------------------------ app lock */

private enum class LockMode { Overview, Verify, Change }

@Composable
fun AppLockSettingsScreen(nav: NavController) {
    val c = Nook.colors
    val context = LocalContext.current
    val activity = LocalActivity.current
    val record by LockStore.record.collectAsState()
    val hide by Prefs.hideInSwitcher.collectAsState()
    var mode by rememberSaveable { mutableStateOf(LockMode.Overview) }
    var bioAvailable by remember { mutableStateOf(Biometrics.available(context)) }
    OnResume { bioAvailable = Biometrics.available(context) }

    BackHandler(enabled = mode != LockMode.Overview) { mode = LockMode.Overview }

    val kindWord = if (record?.kind == LockKind.Pin) "PIN" else "password"
    val kindTitle = if (record?.kind == LockKind.Pin) "PIN" else "Password"

    AnimatedContent(
        targetState = mode,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "lockMode",
    ) { m ->
        when (m) {
            LockMode.Change -> Column(Modifier.fillMaxSize().background(c.background)) {
                NTopBar(title = "App lock", onBack = { mode = LockMode.Overview })
                Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.lg)) {
                    LockSetupFlow(
                        verifyCurrent = record != null,
                        onDone = {
                            mode = LockMode.Overview
                            Toasts.show("App lock updated.")
                        },
                        onCancel = { mode = LockMode.Overview },
                        title = "New lock",
                    )
                }
            }
            LockMode.Verify -> VerifyCurrentLock(
                kind = record?.kind ?: LockKind.Pin,
                biometric = record?.biometric == true && bioAvailable,
                onBack = { mode = LockMode.Overview },
                onVerified = { mode = LockMode.Change },
                onBiometric = {
                    Biometrics.prompt(activity, "Confirm it's you") { ok -> if (ok) mode = LockMode.Change }
                },
            )
            LockMode.Overview -> SettingsPage("App lock", nav) {
                NText(
                    if (record != null) {
                        "NOOK locks when you open it and after 30 seconds in the background. Your $kindWord is stored only on this phone as a salted hash."
                    } else {
                        "NOOK has no lock on this phone right now. Set a PIN or password to keep your chats private."
                    },
                    NookType.body,
                    c.textMuted,
                )
                NCard(Modifier.fillMaxWidth(), padded = false) {
                    Column(Modifier.padding(horizontal = Spacing.md)) {
                        ListRow(
                            title = if (record != null) "Change $kindTitle" else "Set a lock",
                            subtitle = "Or switch between PIN and password",
                            icon = Icons.Outlined.VpnKey,
                            onClick = { mode = LockMode.Change },
                        )
                        NDivider(Modifier.padding(start = 46.dp))
                        ListRow(
                            title = "Fingerprint unlock",
                            subtitle = if (bioAvailable) "Unlock with a touch" else "Set up a fingerprint in Android settings first",
                            icon = Icons.Outlined.Fingerprint,
                            showChevron = false,
                            trailing = {
                                NToggle(
                                    checked = record?.biometric == true && bioAvailable,
                                    enabled = bioAvailable && record != null,
                                    onCheckedChange = { on ->
                                        if (on) {
                                            Biometrics.prompt(activity, "Turn on fingerprint unlock") { ok ->
                                                if (ok) LockStore.setBiometric(true)
                                            }
                                        } else {
                                            LockStore.setBiometric(false)
                                        }
                                    },
                                )
                            },
                        )
                        NDivider(Modifier.padding(start = 46.dp))
                        ListRow(
                            title = "Hide in app switcher",
                            subtitle = "Blank preview in recent apps. Also blocks screenshots in NOOK.",
                            icon = Icons.Outlined.VisibilityOff,
                            showChevron = false,
                            trailing = { NToggle(checked = hide, onCheckedChange = { Prefs.setHideInSwitcher(it) }) },
                        )
                    }
                }
                NText(
                    "Forgot it? Choose “Forgot” on the lock screen: you'll sign out, sign back in with Google, and set a new one.",
                    NookType.caption,
                    c.textMuted,
                )
                if (record != null) {
                    NButton(
                        "Lock now",
                        { LockStore.lock() },
                        variant = ButtonVariant.Secondary,
                        icon = Icons.Outlined.Lock,
                    )
                }
            }
        }
    }
}

/** Asks for the current PIN/password (or fingerprint) before it can be changed. */
@Composable
private fun VerifyCurrentLock(
    kind: LockKind,
    biometric: Boolean,
    onBack: () -> Unit,
    onVerified: () -> Unit,
    onBiometric: () -> Unit,
) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var fails by remember { mutableIntStateOf(0) }
    var coolingDown by remember { mutableStateOf(false) }
    val word = if (kind == LockKind.Pin) "PIN" else "password"

    LaunchedEffect(coolingDown) {
        if (coolingDown) {
            delay(30_000)
            coolingDown = false
            fails = 0
            error = null
        }
    }

    fun check() {
        if (secret.isEmpty() || checking || coolingDown) return
        checking = true
        scope.launch {
            val ok = LockStore.verify(secret)
            checking = false
            if (ok) {
                secret = ""
                onVerified()
            } else {
                fails++
                secret = ""
                if (fails >= 5) {
                    coolingDown = true
                    error = "Too many tries. Wait 30 seconds."
                } else {
                    error = "That's not your current $word."
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        NTopBar(title = "App lock", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            NText("Enter your current $word", NookType.headline)
            NText("Confirm it's you before choosing a new one.", NookType.body, c.textMuted)
            NTextField(
                value = secret,
                onValueChange = { v ->
                    secret = if (kind == LockKind.Pin) v.filter { it.isDigit() } else v
                    if (!coolingDown) error = null
                },
                label = "Current $word",
                password = true,
                keyboardType = if (kind == LockKind.Pin) KeyboardType.NumberPassword else KeyboardType.Password,
                imeAction = ImeAction.Done,
                onSubmit = { check() },
                error = error,
                maxLength = 64,
            )
            NButton("Continue", { check() }, loading = checking, enabled = secret.isNotEmpty() && !coolingDown)
            if (biometric) {
                NButton("Use fingerprint", onBiometric, variant = ButtonVariant.Ghost, icon = Icons.Outlined.Fingerprint)
            }
        }
    }
}

/* ------------------------------------------------------------------ blocked */

@Composable
fun BlockedScreen(nav: NavController) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    val blocked by Session.blocked.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    val busy: SnapshotStateList<String> = remember { mutableStateListOf() }
    LaunchedEffect(blocked) { Profiles.ensure(blocked) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        NTopBar(title = "Blocked", onBack = { nav.popBackStack() })
        if (blocked.isEmpty()) {
            EmptyState(
                title = "No one's blocked",
                body = "People you block can't message or call you. Block someone from their profile or chat info.",
                icon = Icons.Outlined.Block,
                modifier = Modifier.padding(top = Spacing.xl),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
            ) {
                itemsIndexed(blocked, key = { _, uid -> uid }) { i, uid ->
                    val p = profiles[uid]
                    val name = displayNameOf(p)
                    Column(Modifier.animateItem()) {
                        if (i > 0) NDivider(Modifier.padding(start = 60.dp))
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Avatar(name, p?.photoURL, size = 44.dp, seed = uid)
                            Column(Modifier.weight(1f)) {
                                NText(name, NookType.bodyBold, maxLines = 1)
                                NText(p?.let { "@${it.username}" } ?: " ", NookType.caption, c.textMuted, maxLines = 1)
                            }
                            NButton(
                                title = "Unblock",
                                onClick = {
                                    if (uid !in busy) {
                                        busy.add(uid)
                                        scope.launch {
                                            try {
                                                Private.setBlocked(Session.myUid, uid, false)
                                                Toasts.show("$name unblocked")
                                            } catch (e: Exception) {
                                                Toasts.error(e)
                                            } finally {
                                                busy.remove(uid)
                                            }
                                        }
                                    }
                                },
                                variant = ButtonVariant.Secondary,
                                block = false,
                                loading = uid in busy,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ disappearing default */

@Composable
fun DisappearingScreen(nav: NavController) {
    val c = Nook.colors
    val value by Prefs.disappearingDefault.collectAsState()
    SettingsPage("Disappearing", nav) {
        NText(
            "New chats you start will use this timer. Anyone in a chat can change its timer later from the chat info.",
            NookType.body,
            c.textMuted,
        )
        Segmented(
            options = Disappearing.entries.map { it to disappearingLabel(it) },
            selected = value,
            onSelect = { Prefs.setDisappearingDefault(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        NCard(Modifier.fillMaxWidth(), tone = CardTone.Highlight) {
            NText("How it works", NookType.subhead, c.onHighlight)
            NText(
                "Messages vanish from everyone's screen when their timer runs out, and are deleted from the server the next time someone in the chat opens it. Someone could still screenshot or photograph a message before it disappears.",
                NookType.body,
                c.onHighlight.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

/* ------------------------------------------------------------------ notifications */

@Composable
fun NotificationsScreen(nav: NavController) {
    val c = Nook.colors
    val context = LocalContext.current
    val toasts by Prefs.messageNotifications.collectAsState()
    var granted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var fullScreen by remember { mutableStateOf(Notifications.canUseFullScreenIntent(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    OnResume {
        granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        fullScreen = Notifications.canUseFullScreenIntent(context)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        asked = true
        granted = ok || NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    SettingsPage("Notifications", nav) {
        NCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NText("On this phone", NookType.subhead, modifier = Modifier.weight(1f))
                Chip(if (granted) "Allowed" else "Off", selected = granted)
            }
            NText(
                "NOOK notifications only ever say who something is from, like “Ali sent you a photo” or “New message in The Boys”. What people write is never put in a notification.",
                NookType.body,
                c.textMuted,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
            when {
                granted -> NButton(
                    "Open Android settings",
                    { openSettings(context, appNotificationSettings(context)) },
                    variant = ButtonVariant.Secondary,
                    icon = Icons.Outlined.Settings,
                )
                Build.VERSION.SDK_INT >= 33 && !asked -> NButton(
                    "Allow notifications",
                    {
                        LockStore.skipNextBackgroundLock()
                        request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    icon = Icons.Outlined.Notifications,
                )
                else -> NButton(
                    "Turn on in Android settings",
                    { openSettings(context, appNotificationSettings(context)) },
                    icon = Icons.Outlined.Settings,
                )
            }
        }

        NCard(Modifier.fillMaxWidth(), padded = false) {
            Column(Modifier.padding(horizontal = Spacing.md)) {
                ListRow(
                    title = "Messages",
                    subtitle = "Sound, vibration and pop-up for new messages",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    onClick = { openSettings(context, channelSettings(context, Notifications.MESSAGES)) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Incoming calls",
                    subtitle = "Ringtone and full-screen ringing",
                    icon = Icons.Outlined.Call,
                    onClick = { openSettings(context, channelSettings(context, Notifications.CALLS)) },
                )
                NDivider(Modifier.padding(start = 46.dp))
                ListRow(
                    title = "Message banners in NOOK",
                    subtitle = "A quick note when a message arrives while you're in another chat",
                    icon = Icons.Outlined.Notifications,
                    showChevron = false,
                    trailing = { NToggle(checked = toasts, onCheckedChange = { Prefs.setMessageNotifications(it) }) },
                )
            }
        }

        NCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NText("Incoming calls", NookType.subhead, modifier = Modifier.weight(1f))
                Chip(if (fullScreen) "Full screen" else "Banner only", selected = fullScreen)
            }
            NText(
                "Calls ring full screen, even on the lock screen. On Android 14 and newer this needs the “full-screen notifications” permission. Some phones (Xiaomi, Oppo, Vivo, Huawei…) also need NOOK set to “No restrictions” in battery settings, or calls can't reach a closed app.",
                NookType.body,
                c.textMuted,
                modifier = Modifier.padding(vertical = Spacing.sm),
            )
            if (!fullScreen && Build.VERSION.SDK_INT >= 34) {
                NButton(
                    "Allow full-screen calls",
                    {
                        openSettings(
                            context,
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")),
                        )
                    },
                    icon = Icons.Outlined.Call,
                )
            }
        }
        NText(
            "Mute a single chat from its info screen (tap the name at the top of the chat).",
            NookType.caption,
            c.textMuted,
        )
    }
}

/* ------------------------------------------------------------------ appearance */

@Composable
fun AppearanceScreen(nav: NavController) {
    val c = Nook.colors
    val theme by Prefs.theme.collectAsState()
    SettingsPage("Appearance", nav) {
        NText("Pick how NOOK looks on this phone.", NookType.body, c.textMuted)
        Segmented(
            options = listOf(ThemeMode.System to "System", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark"),
            selected = theme,
            onSelect = { Prefs.setTheme(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        NCard(Modifier.fillMaxWidth()) {
            NText(
                when (theme) {
                    ThemeMode.System -> "Follows your phone"
                    ThemeMode.Light -> "Light"
                    ThemeMode.Dark -> "Dark"
                },
                NookType.subhead,
            )
            NText(
                when (theme) {
                    ThemeMode.System -> "NOOK switches between light and dark with your phone's setting."
                    ThemeMode.Light -> "Warm cream pages with charcoal ink."
                    ThemeMode.Dark -> "Black and charcoal with cream text. Easy on the eyes at night."
                },
                NookType.body,
                c.textMuted,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

/* ------------------------------------------------------------------ help */

private val FAQ = listOf(
    "How do friends find me?" to
        "By your exact username. There's no public directory, so share yours with the people you want to talk to.",
    "I forgot my PIN or password" to
        "Tap “Forgot” on the lock screen. You'll sign out, sign back in with Google and choose a new one. Your chats are kept in your account.",
    "Can I change my username?" to
        "No. Usernames are permanent so nobody can take over a name your friends already trust.",
    "Are my messages end-to-end encrypted?" to
        "No. Messages are encrypted in transit and at rest by Google Firebase, and only chat members can read them through the app, but they are not end-to-end encrypted. See Privacy & Terms.",
    "Something's broken" to
        "Tell whoever set up NOOK for your group. Mention your phone model and what you tapped just before it went wrong.",
)

@Composable
fun HelpScreen(nav: NavController) {
    val c = Nook.colors
    SettingsPage("Help", nav) {
        FAQ.forEach { (q, a) ->
            NCard(Modifier.fillMaxWidth()) {
                NText(q, NookType.bodyBold)
                NText(a, NookType.body, c.textMuted, modifier = Modifier.padding(top = Spacing.xxs))
            }
        }
    }
}

/* ------------------------------------------------------------------ privacy */

private val PRIVACY = listOf(
    "What NOOK is" to
        "A private chat app for a small group of friends. It is run by the person who set it up for your group, not by a company.",
    "Not end-to-end encrypted" to
        "Messages, profiles and chat details are stored in Google Firebase. They are encrypted in transit and at rest by Google, and security rules only let chat members read a chat, but they are not end-to-end encrypted: whoever administers the Firebase project could technically access them.",
    "What we store" to
        "Your Google display name and photo, your username, the chats you are in and their messages, and a device token so we can notify you. Photos, voice notes and files are stored with Cloudinary. Your app lock never leaves your phone.",
    "Notifications" to
        "Notifications only say who something is from. Message text is never placed in a notification or in server logs.",
    "Disappearing messages" to
        "When a timer runs out, messages are hidden immediately and deleted from the server the next time a member opens the chat. Anyone could still screenshot a message before it disappears.",
    "Deleting your account" to
        "Account > Delete Account removes your profile, frees your username, deletes your push tokens and your sign-in.",
    "Fair use" to
        "Be kind. Don't use NOOK to harass anyone or share anything illegal. The group's admin can remove accounts that do.",
)

@Composable
fun PrivacyScreen(nav: NavController) {
    val c = Nook.colors
    SettingsPage("Privacy & Terms", nav) {
        PRIVACY.forEachIndexed { i, (title, body) ->
            val highlight = i == 1
            NCard(Modifier.fillMaxWidth(), tone = if (highlight) CardTone.Highlight else CardTone.Surface) {
                NText(title, NookType.subhead, if (highlight) c.onHighlight else c.text)
                NText(
                    body,
                    NookType.body,
                    if (highlight) c.onHighlight.copy(alpha = 0.9f) else c.textMuted,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
    }
}
