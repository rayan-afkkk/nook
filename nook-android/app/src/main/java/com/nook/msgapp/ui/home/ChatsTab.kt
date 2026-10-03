package com.nook.msgapp.ui.home

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.Chat
import com.nook.core.ChatLogic
import com.nook.core.ChatType
import com.nook.core.MessageKind
import com.nook.core.Profile
import com.nook.msgapp.data.LoadStatus
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.DashedCard
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.GroupAvatar
import com.nook.msgapp.ui.components.ListRow
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NHeader
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.Segmented
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay

private enum class ChatFilter(val label: String) { All("All"), Direct("Direct"), Groups("Groups"), Unread("Unread") }

@Composable
fun ChatsTab(nav: NavController, onFindFriend: () -> Unit) {
    val c = Nook.colors
    val chats by Session.chats.collectAsState()
    val status by Session.chatsStatus.collectAsState()
    val blocked by Session.blocked.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    val me = Session.myUid
    val now = rememberNow()

    var filter by rememberSaveable { mutableStateOf(ChatFilter.All) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var composeOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val visible = remember(chats, filter, q, blocked, me, profiles) {
        val needle = q.trim().lowercase()
        chats.filter { chat ->
            val other = if (chat.type == ChatType.Direct) ChatLogic.otherMember(chat.members, me) else null
            when {
                other != null && other in blocked -> false
                filter == ChatFilter.Direct && chat.type != ChatType.Direct -> false
                filter == ChatFilter.Groups && chat.type != ChatType.Group -> false
                filter == ChatFilter.Unread && !ChatLogic.isUnread(chat, me) -> false
                needle.isNotEmpty() && !chatName(chat, me, profiles).lowercase().contains(needle) -> false
                else -> true
            }
        }
    }
    val open: (Chat) -> Unit = { chat -> nav.navigate(Routes.chat(chat.id)) { launchSingleTop = true } }

    Column(Modifier.fillMaxSize()) {
        NHeader("Chats") {
            NIconButton(
                icon = if (searching) Icons.Rounded.Close else Icons.Rounded.Search,
                label = if (searching) "Close search" else "Search chats",
                onClick = {
                    searching = !searching
                    q = ""
                },
            )
            NIconButton(Icons.Outlined.GroupAdd, "New group", { nav.navigate(Routes.NEW_GROUP) })
            NIconButton(Icons.Rounded.Edit, "New chat", { composeOpen = true })
        }

        Column(
            Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AnimatedVisibility(
                visible = searching,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                SearchField(value = q, onChange = { q = it })
            }
            Segmented(
                options = ChatFilter.entries.map { it to it.label },
                selected = filter,
                onSelect = { filter = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when {
            (status == LoadStatus.Loading || status == LoadStatus.Idle) && chats.isEmpty() -> {
                Column(Modifier.padding(horizontal = Spacing.lg)) {
                    repeat(5) { SkeletonRow() }
                }
            }
            status == LoadStatus.Error && chats.isEmpty() -> {
                EmptyState(
                    title = "Couldn't load chats",
                    body = "Check your connection.",
                    icon = Icons.Outlined.CloudOff,
                )
            }
            chats.isEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    DashedCard(Modifier.fillMaxWidth()) {
                        NText("Start your first chat", NookType.subhead)
                        Spacer(Modifier.size(Spacing.xxs))
                        NText(
                            "Find a friend by their exact username and say hi.",
                            NookType.body,
                            c.textMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.size(Spacing.md))
                        NButton("Find a friend", onFindFriend, icon = Icons.Outlined.PersonAdd, block = false)
                    }
                    EmptyState(
                        title = "No chats yet",
                        body = "Your conversations will show up here.",
                        icon = Icons.Outlined.ChatBubbleOutline,
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xxl),
                ) {
                    if (visible.isEmpty()) {
                        item(key = "empty") {
                            val searchingText = q.isNotBlank()
                            EmptyState(
                                title = when {
                                    searchingText -> "No matches"
                                    filter == ChatFilter.Unread -> "All caught up"
                                    filter == ChatFilter.Groups -> "No groups yet"
                                    else -> "Nothing here"
                                },
                                body = when {
                                    searchingText -> "No chat has that name."
                                    filter == ChatFilter.Unread -> "New messages you haven't read will wait here."
                                    filter == ChatFilter.Groups -> "Make a group for the crew and everyone's in one place."
                                    else -> "Chats will show up here."
                                },
                                icon = when {
                                    searchingText -> Icons.Rounded.Search
                                    filter == ChatFilter.Unread -> Icons.Rounded.DoneAll
                                    filter == ChatFilter.Groups -> Icons.Outlined.People
                                    else -> Icons.Outlined.ChatBubbleOutline
                                },
                                action = if (filter == ChatFilter.Groups && !searchingText) "New group" else null,
                                onAction = { nav.navigate(Routes.NEW_GROUP) },
                            )
                        }
                    }
                    items(visible, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            me = me,
                            now = now,
                            profiles = profiles,
                            onClick = { open(chat) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (composeOpen) {
        NSheet(onDismiss = { composeOpen = false }) {
            NText("New chat", NookType.headline)
            ListRow(
                title = "New message",
                subtitle = "Find a friend by username",
                icon = Icons.Outlined.PersonAdd,
                onClick = {
                    composeOpen = false
                    onFindFriend()
                },
            )
            ListRow(
                title = "New group",
                subtitle = "Name it and add your crew",
                icon = Icons.Outlined.People,
                onClick = {
                    composeOpen = false
                    nav.navigate(Routes.NEW_GROUP)
                },
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Nook.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { focus.requestFocus() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(c.surface, RoundedCornerShape(Radius.pill))
            .border(1.dp, c.border, RoundedCornerShape(Radius.pill))
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        Box(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            if (value.isEmpty()) NText("Search by name", NookType.body, c.textMuted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.take(60)) },
                singleLine = true,
                textStyle = NookType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
    }
}

private fun kindIcon(kind: MessageKind?): ImageVector? = when (kind) {
    MessageKind.Image -> Icons.Outlined.Image
    MessageKind.Voice -> Icons.Outlined.Mic
    MessageKind.Gif -> Icons.Outlined.Movie
    MessageKind.Sticker -> Icons.Outlined.EmojiEmotions
    MessageKind.File -> Icons.Outlined.Description
    else -> null
}

/** One chat in the list: avatar, name, time, last message line, unread badge, muted bell. */
@Composable
private fun ChatRow(
    chat: Chat,
    me: String,
    now: Long,
    profiles: Map<String, Profile>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Nook.colors
    val otherId = if (chat.type == ChatType.Direct) ChatLogic.otherMember(chat.members, me) else null
    val other = otherId?.let { profiles[it] }
    val online = rememberPresence(otherId).online
    val name = Session.titleFor(chat, me, profiles)
    val last = chat.lastMessage
    val mineLast = last?.senderId == me
    val unread = ChatLogic.isUnread(chat, me)
    val muted = me in chat.mutedBy
    val icon = kindIcon(last?.kind)
    val line = ChatLogic.lastMessageLine(last, mineLast)
    val at = chat.lastMessageAt

    Row(
        modifier
            .fillMaxWidth()
            .pressScale(scaleTo = 0.98f, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (chat.type == ChatType.Group) {
            GroupAvatar(chat, 46.dp)
        } else {
            Avatar(name, other?.photoURL, size = 46.dp, online = online, seed = otherId)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                NText(name, NookType.bodyBold, modifier = Modifier.weight(1f), maxLines = 1)
                if (muted) {
                    Icon(Icons.Outlined.NotificationsOff, contentDescription = "Muted", tint = c.textMuted, modifier = Modifier.size(14.dp))
                }
                NText(
                    if (at != null && at > 0) ChatLogic.timeLabel(at, now) else "",
                    NookType.micro,
                    if (unread) c.accent else c.textMuted,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = if (unread) c.text else c.textMuted, modifier = Modifier.size(14.dp))
                }
                NText(
                    line,
                    if (unread) NookType.captionBold else NookType.caption,
                    if (unread) c.text else c.textMuted,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                if (unread) {
                    Box(
                        Modifier
                            .background(c.accent, RoundedCornerShape(Radius.pill))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        NText("New", NookType.micro, Color.Black)
                    }
                } else {
                    Spacer(Modifier.width(0.dp))
                }
            }
        }
    }
}
