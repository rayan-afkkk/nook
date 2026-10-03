package com.nook.msgapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nook.core.Chat
import com.nook.core.ChatType
import com.nook.core.Limits
import com.nook.core.Profile
import com.nook.core.Username
import com.nook.msgapp.data.Presence
import com.nook.msgapp.data.PresenceState
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.friendly
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.ListRow
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.Skeleton
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/* Small building blocks shared by the home tabs, settings, groups, chat info and sticker screens. */

/** Work that must finish even if the screen that started it goes away (sign-out, account deletion). */
internal object AppScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}

/** Loading state of a live Firestore flow (which closes with an error on permission/network failures). */
internal sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** Collects a flow as [Load], never crashing the UI when the listener fails. */
@Composable
internal fun <T> rememberLoad(key: Any?, create: () -> Flow<T>): State<Load<T>> {
    val flow = remember(key) {
        create()
            .map<T, Load<T>> { Load.Ready(it) }
            .catch { emit(Load.Failed(it.friendly())) }
    }
    return flow.collectAsState(initial = Load.Loading)
}

/** Live online state of one person (offline while unknown). */
@Composable
internal fun rememberPresence(uid: String?): PresenceState {
    val offline = remember { PresenceState(false, null) }
    val flow = remember(uid) {
        if (uid.isNullOrEmpty()) flowOf(offline) else Presence.flow(uid).catch { emit(offline) }
    }
    val state by flow.collectAsState(initial = offline)
    return state
}

/** "Now", refreshed every minute so time labels stay fresh. */
@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

internal fun displayNameOf(p: Profile?): String = p?.displayName ?: "…"

/** Title for a chat (group name or the other person's name). */
internal fun chatName(chat: Chat, me: String, profiles: Map<String, Profile>): String =
    Session.titleFor(chat, me, profiles)

/** NOOK bottom sheet: surface colour, soft scrim, padded column. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Nook.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.text,
        scrimColor = c.overlay,
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.border) },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            content = content,
        )
    }
}

/** Confirmation dialog (sign out, block, leave group...). */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    loading: Boolean = false,
    confirmEnabled: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Nook.colors
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        containerColor = c.surface,
        titleContentColor = c.text,
        textContentColor = c.textMuted,
        shape = RoundedCornerShape(Radius.card),
        title = { NText(title, NookType.headline) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                NText(message, NookType.body, c.textMuted)
                extra?.invoke(this)
            }
        },
        confirmButton = {
            NButton(
                title = confirmLabel,
                onClick = onConfirm,
                variant = if (destructive) ButtonVariant.Destructive else ButtonVariant.Primary,
                loading = loading,
                enabled = confirmEnabled,
                block = false,
            )
        },
        dismissButton = {
            NButton(title = "Cancel", onClick = onDismiss, variant = ButtonVariant.Ghost, enabled = !loading, block = false)
        },
    )
}

/** Avatar + two lines skeleton, used while lists load. */
@Composable
internal fun SkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Skeleton(Modifier.size(46.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Skeleton(Modifier.fillMaxWidth(0.5f).height(14.dp))
            Skeleton(Modifier.fillMaxWidth(0.8f).height(11.dp))
        }
    }
}

/** Pick one of my chats (sending a GIF, sticker, or adding someone to a group). */
@Composable
internal fun ChatPickerSheet(
    title: String,
    onPick: (Chat, String) -> Unit,
    onDismiss: () -> Unit,
    groupsOnly: Boolean = false,
) {
    val chats by Session.chats.collectAsState()
    val blocked by Session.blocked.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    val me = Session.myUid
    val visible = remember(chats, blocked, groupsOnly) {
        chats.filter { c ->
            if (groupsOnly && c.type != ChatType.Group) return@filter false
            !(c.type == ChatType.Direct && c.members.any { it != me && it in blocked })
        }
    }
    NSheet(onDismiss) {
        NText(title, NookType.headline)
        if (visible.isEmpty()) {
            EmptyState(
                title = if (groupsOnly) "No groups yet" else "No chats yet",
                body = if (groupsOnly) "Make a group from the Chats tab first." else "Start a chat from the Friends tab first.",
                icon = Icons.Outlined.Forum,
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                items(visible, key = { it.id }) { c ->
                    val name = chatName(c, me, profiles)
                    ListRow(
                        title = name,
                        icon = if (c.type == ChatType.Group) Icons.Outlined.Group else Icons.Outlined.Person,
                        showChevron = false,
                        onClick = { onPick(c, name) },
                    )
                }
            }
        }
    }
}

/** Exact-username lookup used to add people to a group. */
@Composable
internal fun UsernameSearch(
    me: String,
    exclude: List<String>,
    onPick: (Profile) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Add by username",
) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Profile?>(null) }
    val name = Username.normalize(q)
    val invalid = if (name.isNotEmpty()) Username.validate(name) else null

    fun search() {
        if (name.isEmpty() || invalid != null || busy) return
        busy = true
        message = null
        found = null
        scope.launch {
            try {
                val p = Profiles.findByUsername(name)
                when {
                    p == null -> message = "Nobody has claimed @$name."
                    p.uid == me -> message = "That's you."
                    p.uid in exclude -> message = "@${p.username} is already here."
                    else -> found = p
                }
            } catch (e: Exception) {
                message = e.friendly()
            } finally {
                busy = false
            }
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        NTextField(
            value = q,
            onValueChange = { t ->
                q = t.filterNot { it.isWhitespace() }.lowercase()
                found = null
                message = null
            },
            label = label,
            prefix = "@",
            placeholder = "exact username",
            error = invalid,
            helper = message,
            imeAction = ImeAction.Search,
            onSubmit = { search() },
            maxLength = Limits.USERNAME_MAX + 1,
            trailing = {
                if (busy) {
                    CircularProgressIndicator(color = c.textMuted, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    NIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        label = "Search",
                        onClick = { search() },
                        enabled = name.isNotEmpty() && invalid == null,
                        size = 36.dp,
                        iconSize = 20.dp,
                    )
                }
            },
        )
        val p = found
        if (p != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.surface, RoundedCornerShape(Radius.md))
                    .border(1.dp, c.border, RoundedCornerShape(Radius.md))
                    .pressScale(scaleTo = 0.98f) {
                        onPick(p)
                        found = null
                        q = ""
                    }
                    .padding(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Avatar(p.displayName, p.photoURL, size = 40.dp, seed = p.uid)
                Column(Modifier.weight(1f)) {
                    NText(p.displayName, NookType.bodyBold, maxLines = 1)
                    NText("@${p.username}", NookType.caption, c.textMuted, maxLines = 1)
                }
                NText("Add", NookType.label, c.accent)
            }
        }
    }
}

/** Round check mark used by multi-select rows. */
@Composable
internal fun SelectMark(selected: Boolean) {
    val c = Nook.colors
    Box(
        Modifier
            .size(24.dp)
            .background(if (selected) c.accent else Color.Transparent, CircleShape)
            .border(1.5.dp, if (selected) c.accent else c.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Row for picking a person (new group, add members). */
@Composable
internal fun PersonSelectRow(uid: String, profile: Profile?, selected: Boolean, onToggle: () -> Unit) {
    val online = rememberPresence(uid).online
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(scaleTo = 0.98f, onClick = onToggle)
            .heightIn(min = 60.dp)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Avatar(displayNameOf(profile), profile?.photoURL, size = 42.dp, online = online, seed = uid)
        Column(Modifier.weight(1f)) {
            NText(displayNameOf(profile), NookType.bodyBold, maxLines = 1)
            NText(profile?.let { "@${it.username}" } ?: " ", NookType.caption, Nook.colors.textMuted, maxLines = 1)
        }
        SelectMark(selected)
    }
}

@Composable
internal fun VSpace(h: Dp) = Spacer(Modifier.height(h))
