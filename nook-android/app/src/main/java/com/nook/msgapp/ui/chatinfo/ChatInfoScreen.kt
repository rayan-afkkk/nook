package com.nook.msgapp.ui.chatinfo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.Chat
import com.nook.core.ChatLogic
import com.nook.core.ChatType
import com.nook.core.Disappearing
import com.nook.core.Limits
import com.nook.core.Profile
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.data.Chats
import com.nook.msgapp.data.Cloudinary
import com.nook.msgapp.data.Private
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.GroupAvatar
import com.nook.msgapp.ui.components.ListRow
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.NToggle
import com.nook.msgapp.ui.components.NTopBar
import com.nook.msgapp.ui.components.ProgressBar
import com.nook.msgapp.ui.components.SectionLabel
import com.nook.msgapp.ui.components.Segmented
import com.nook.msgapp.ui.components.Skeleton
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.components.rememberImagePicker
import com.nook.msgapp.ui.home.ConfirmDialog
import com.nook.msgapp.ui.home.Load
import com.nook.msgapp.ui.home.NSheet
import com.nook.msgapp.ui.home.PersonSelectRow
import com.nook.msgapp.ui.home.UsernameSearch
import com.nook.msgapp.ui.home.disappearingLabel
import com.nook.msgapp.ui.home.displayNameOf
import com.nook.msgapp.ui.home.rememberLoad
import com.nook.msgapp.ui.home.rememberNow
import com.nook.msgapp.ui.home.rememberPresence
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Chat info: the other person (DM) or the group's name, photo and members, plus per-chat settings. */
@Composable
fun ChatInfoScreen(chatId: String, nav: NavController) {
    val c = Nook.colors
    val me = Session.myUid
    val live by rememberLoad(chatId) { Chats.chatFlow(chatId) }
    val listChats by Session.chats.collectAsState()
    val chat: Chat? = (live as? Load.Ready)?.value ?: listChats.firstOrNull { it.id == chatId }

    LaunchedEffect(chat?.members) {
        chat?.members?.let { m -> Profiles.ensure(m.filter { it != me }) }
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        NTopBar(title = if (chat?.type == ChatType.Group) "Group" else "Info", onBack = { nav.popBackStack() })
        when {
            chat != null && chat.type == ChatType.Group -> GroupInfo(chat, me, nav)
            chat != null -> DirectInfo(chat, me)
            live is Load.Failed -> Column(
                Modifier.fillMaxWidth().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NText("Couldn't load this chat", NookType.headline, textAlign = TextAlign.Center)
                NText((live as Load.Failed).message, NookType.body, c.textMuted, textAlign = TextAlign.Center)
            }
            else -> Column(
                Modifier.fillMaxWidth().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Skeleton(Modifier.size(88.dp))
                Skeleton(Modifier.fillMaxWidth(0.6f).height(20.dp))
                Skeleton(Modifier.fillMaxWidth(0.4f).height(14.dp))
            }
        }
    }
}

/** Scroll container for the info body. */
@Composable
private fun InfoBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xxl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        content = content,
    )
}

/** Mute + disappearing timer, shared by DMs and groups. */
@Composable
private fun SharedSettings(chat: Chat, me: String) {
    val scope = rememberCoroutineScope()
    val muted = me in chat.mutedBy
    NCard(Modifier.fillMaxWidth(), padded = false) {
        Box(Modifier.padding(horizontal = Spacing.md)) {
            ListRow(
                title = "Mute notifications",
                subtitle = "No pushes from this chat on this account",
                icon = Icons.Outlined.NotificationsOff,
                showChevron = false,
                trailing = {
                    NToggle(
                        checked = muted,
                        onCheckedChange = { v ->
                            scope.launch {
                                try {
                                    Chats.setMuted(chat.id, me, v)
                                } catch (e: Exception) {
                                    Toasts.error(e)
                                }
                            }
                        },
                    )
                },
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel("Disappearing messages", Modifier.padding(top = 0.dp))
        Segmented(
            options = Disappearing.entries.map { it to disappearingLabel(it) },
            selected = chat.disappearing,
            onSelect = { v ->
                if (v != chat.disappearing) {
                    scope.launch {
                        try {
                            Chats.setDisappearing(chat.id, v)
                        } catch (e: Exception) {
                            Toasts.error(e)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        NText(
            if (chat.disappearing == Disappearing.Off) {
                "Messages stay until someone deletes them."
            } else {
                "New messages disappear ${disappearingLabel(chat.disappearing)} after they're sent, for everyone in the chat."
            },
            NookType.caption,
            Nook.colors.textMuted,
        )
    }
}

private fun lastSeen(ms: Long?, now: Long): String {
    if (ms == null || ms <= 0) return "Offline"
    val day = ChatLogic.dayLabel(ms, now)
    val dayText = if (day == "Today" || day == "Yesterday") day.lowercase() else day
    return "Last seen $dayText at ${ChatLogic.clockLabel(ms)}"
}

/* ------------------------------------------------------------------ direct */

@Composable
private fun DirectInfo(chat: Chat, me: String) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    val profiles by Profiles.cache.collectAsState()
    val blockedList by Session.blocked.collectAsState()
    val otherId = ChatLogic.otherMember(chat.members, me).orEmpty()
    val other = profiles[otherId]
    val presence = rememberPresence(otherId)
    val now = rememberNow()
    val blocked = otherId in blockedList
    val name = displayNameOf(other)
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(otherId) {
        if (otherId.isNotEmpty()) runCatching { Profiles.refresh(otherId) }
    }

    InfoBody {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Avatar(name, other?.photoURL, size = 96.dp, online = presence.online && !blocked, seed = otherId)
            NText(name, NookType.title, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = Spacing.xs))
            NText(other?.let { "@${it.username}" } ?: "", NookType.body, c.textMuted)
            if (!blocked) {
                NText(
                    if (presence.online) "Online" else lastSeen(presence.lastChanged, now),
                    NookType.caption,
                    if (presence.online) c.online else c.textMuted,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            NButton(
                "Voice call",
                { CallController.startOutgoing(chat.id, otherId, false) },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Secondary,
                icon = Icons.Outlined.Call,
                enabled = !blocked && otherId.isNotEmpty(),
            )
            NButton(
                "Video call",
                { CallController.startOutgoing(chat.id, otherId, true) },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Secondary,
                icon = Icons.Outlined.Videocam,
                enabled = !blocked && otherId.isNotEmpty(),
            )
        }
        SharedSettings(chat, me)
        NButton(
            title = if (blocked) "Unblock $name" else "Block $name",
            onClick = {
                if (blocked) {
                    scope.launch {
                        try {
                            Private.setBlocked(me, otherId, false)
                            Toasts.show("Unblocked")
                        } catch (e: Exception) {
                            Toasts.error(e)
                        }
                    }
                } else {
                    confirm = true
                }
            },
            variant = ButtonVariant.Destructive,
            icon = Icons.Outlined.Block,
        )
    }

    if (confirm) {
        ConfirmDialog(
            title = "Block $name?",
            message = "They won't be able to message or call you, and you won't get notifications from them. They aren't told.",
            confirmLabel = "Block",
            destructive = true,
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                scope.launch {
                    try {
                        Private.setBlocked(me, otherId, true)
                        Toasts.show("$name is blocked.")
                    } catch (e: Exception) {
                        Toasts.error(e)
                    }
                }
            },
        )
    }
}

/* ------------------------------------------------------------------ group */

@Composable
private fun GroupInfo(chat: Chat, me: String, nav: NavController) {
    val c = Nook.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by Profiles.cache.collectAsState()
    val chatId = chat.id

    var photoSheet by remember { mutableStateOf(false) }
    var photoProgress by remember { mutableStateOf<Float?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    fun setPhoto(url: String?) {
        scope.launch {
            try {
                Chats.setGroupPhoto(chatId, url)
                Toasts.show(if (url != null) "Group photo updated." else "Group photo removed.")
            } catch (e: Exception) {
                Toasts.error(e)
            }
        }
    }

    val picker = rememberImagePicker { uri ->
        photoProgress = 0f
        scope.launch {
            try {
                val file = Cloudinary.describe(context, uri, "group.jpg")
                val result = Cloudinary.upload(context, file, "image") { p -> photoProgress = p }
                Chats.setGroupPhoto(chatId, ChatLogic.thumbnailUrl(result.url, 512))
                Toasts.show("Group photo updated.")
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                photoProgress = null
            }
        }
    }

    InfoBody {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Box(Modifier.pressScale(enabled = photoProgress == null, scaleTo = 0.94f) { photoSheet = true }) {
                GroupAvatar(chat, 96.dp)
                if (photoProgress != null) {
                    Box(Modifier.size(96.dp).background(c.overlay, CircleShape), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(28.dp)
                        .background(c.primary, CircleShape)
                        .border(2.dp, c.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Change group photo", tint = c.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
            Row(
                Modifier
                    .padding(top = Spacing.xs)
                    .pressScale(scaleTo = 0.97f) {
                        renameDraft = chat.name.orEmpty()
                        renameOpen = true
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NText(chat.name ?: "Group", NookType.title, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Rounded.Edit, contentDescription = "Rename group", tint = c.textMuted, modifier = Modifier.size(16.dp))
            }
            NText("${chat.members.size} members", NookType.caption, c.textMuted)
            AnimatedVisibility(photoProgress != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                ProgressBar(photoProgress ?: 0f, Modifier.padding(top = Spacing.xs))
            }
        }

        SharedSettings(chat, me)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionLabel("Members", Modifier.padding(top = 0.dp))
            NCard(Modifier.fillMaxWidth(), padded = false) {
                Column(Modifier.padding(horizontal = Spacing.md)) {
                    // Me first, then everyone else.
                    val ordered = chat.members.sortedBy { if (it == me) 0 else 1 }
                    ordered.forEachIndexed { i, uid ->
                        if (i > 0) NDivider(Modifier.padding(start = 52.dp))
                        MemberRow(uid, profiles[uid], me)
                    }
                }
            }
            NButton(
                "Add members",
                { addOpen = true },
                variant = ButtonVariant.Secondary,
                icon = Icons.Outlined.PersonAdd,
                enabled = chat.members.size < Limits.MAX_GROUP_MEMBERS,
            )
        }

        NButton(
            "Leave group",
            { confirmLeave = true },
            variant = ButtonVariant.Destructive,
            icon = Icons.AutoMirrored.Rounded.Logout,
        )
    }

    if (photoSheet) {
        NSheet(onDismiss = { photoSheet = false }) {
            NText("Group photo", NookType.headline)
            ListRow(
                title = "Choose a photo",
                subtitle = "Everyone in the group sees it",
                icon = Icons.Outlined.PhotoLibrary,
                showChevron = false,
                onClick = {
                    photoSheet = false
                    picker()
                },
            )
            if (!chat.photoURL.isNullOrEmpty()) {
                ListRow(
                    title = "Remove photo",
                    icon = Icons.Outlined.Delete,
                    destructive = true,
                    showChevron = false,
                    onClick = {
                        photoSheet = false
                        setPhoto(null)
                    },
                )
            }
        }
    }

    if (renameOpen) {
        val clean = renameDraft.trim()
        ConfirmDialog(
            title = "Rename group",
            message = "Everyone in the group sees the new name.",
            confirmLabel = "Save",
            loading = renaming,
            confirmEnabled = clean.isNotEmpty() && clean != chat.name,
            onDismiss = { renameOpen = false },
            onConfirm = {
                renaming = true
                scope.launch {
                    try {
                        Chats.renameGroup(chatId, renameDraft)
                        renameOpen = false
                        Toasts.show("Renamed")
                    } catch (e: Exception) {
                        Toasts.error(e)
                    } finally {
                        renaming = false
                    }
                }
            },
            extra = {
                NTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    label = "Group name",
                    placeholder = "The Boys",
                    capitalization = KeyboardCapitalization.Words,
                    maxLength = 40,
                )
            },
        )
    }

    if (addOpen) {
        AddMembersSheet(chat = chat, me = me, onDismiss = { addOpen = false })
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Leave this group?",
            message = "You'll stop getting its messages. Someone in the group can add you back.",
            confirmLabel = "Leave",
            destructive = true,
            loading = leaving,
            onDismiss = { confirmLeave = false },
            onConfirm = {
                leaving = true
                scope.launch {
                    try {
                        Chats.leaveGroup(chatId, me)
                        confirmLeave = false
                        nav.popBackStack(Routes.HOME, false)
                    } catch (e: Exception) {
                        leaving = false
                        Toasts.error(e)
                    }
                }
            },
        )
    }
}

@Composable
private fun MemberRow(uid: String, profile: Profile?, me: String) {
    val c = Nook.colors
    val online = rememberPresence(uid).online
    val base = displayNameOf(profile)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Avatar(base, profile?.photoURL, size = 40.dp, online = online, seed = uid)
        Column(Modifier.weight(1f)) {
            NText(if (uid == me) "$base (you)" else base, NookType.bodyBold, maxLines = 1)
            NText(profile?.let { "@${it.username}" } ?: " ", NookType.caption, c.textMuted, maxLines = 1)
        }
    }
}

/** Friends not in the group (multi-select) plus exact-username lookup. */
@Composable
private fun AddMembersSheet(chat: Chat, me: String, onDismiss: () -> Unit) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    val chats by Session.chats.collectAsState()
    val blocked by Session.blocked.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    val candidates = remember(chats, blocked, chat.members) {
        Session.friends(chats, me).filter { it !in chat.members && it !in blocked }
    }
    val picked = remember { mutableStateListOf<String>() }
    var adding by remember { mutableStateOf(false) }
    val room = Limits.MAX_GROUP_MEMBERS - chat.members.size

    fun add(uids: List<String>, label: String) {
        if (uids.isEmpty() || adding) return
        adding = true
        scope.launch {
            try {
                Chats.addMembers(chat, uids)
                Toasts.show(label)
                onDismiss()
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                adding = false
            }
        }
    }

    NSheet(onDismiss = onDismiss) {
        NText("Add members", NookType.headline)
        NText(
            if (room > 0) "Room for $room more." else "This group is full.",
            NookType.caption,
            c.textMuted,
        )
        UsernameSearch(
            me = me,
            exclude = chat.members,
            label = "Add someone",
            onPick = { p -> add(listOf(p.uid), "Added ${p.displayName}") },
            modifier = Modifier.padding(top = Spacing.xs),
        )
        if (candidates.isNotEmpty()) {
            SectionLabel("People you chat with")
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                items(candidates, key = { it }) { uid ->
                    PersonSelectRow(
                        uid = uid,
                        profile = profiles[uid],
                        selected = uid in picked,
                        onToggle = {
                            if (uid in picked) {
                                picked.remove(uid)
                            } else if (picked.size < room) {
                                picked.add(uid)
                            } else {
                                Toasts.show("Groups can have up to ${Limits.MAX_GROUP_MEMBERS} people.")
                            }
                        },
                    )
                }
            }
            NButton(
                title = when (picked.size) {
                    0 -> "Add"
                    1 -> "Add 1 person"
                    else -> "Add ${picked.size} people"
                },
                onClick = {
                    add(picked.toList(), if (picked.size == 1) "Added ${displayNameOf(profiles[picked[0]])}" else "Added ${picked.size} people")
                },
                icon = Icons.Outlined.PersonAdd,
                loading = adding,
                enabled = picked.isNotEmpty(),
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
