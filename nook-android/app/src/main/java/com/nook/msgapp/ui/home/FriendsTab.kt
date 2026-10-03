package com.nook.msgapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.ChatType
import com.nook.core.Limits
import com.nook.core.Profile
import com.nook.core.Username
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.data.Chats
import com.nook.msgapp.data.LoadStatus
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.data.Private
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.data.friendly
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NCard
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NHeader
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.SectionLabel
import com.nook.msgapp.ui.components.Skeleton
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface SearchResult {
    data object Idle : SearchResult
    data object Loading : SearchResult
    data class None(val name: String) : SearchResult
    data class Found(val profile: Profile) : SearchResult
    data class Error(val message: String) : SearchResult
}

@Composable
fun FriendsTab(nav: NavController, focusSignal: Int) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val chats by Session.chats.collectAsState()
    val listStatus by Session.chatsStatus.collectAsState()
    val blocked by Session.blocked.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    val meState by Session.me.collectAsState()
    val myUid = Session.myUid
    val myUsername = (meState as? com.nook.core.ProfileState.Ready)?.profile?.username

    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult>(SearchResult.Idle) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmBlock by remember { mutableStateOf(false) }
    var groupPick by remember { mutableStateOf(false) }
    var handledFocus by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val name = Username.normalize(query)
    val invalid = if (name.isNotEmpty()) Username.validate(name) else null

    // Asked to focus the search (from the Chats tab's "new chat").
    LaunchedEffect(focusSignal) {
        if (focusSignal > handledFocus) {
            handledFocus = focusSignal
            delay(300)
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }

    suspend fun runSearch(n: String) {
        result = SearchResult.Loading
        result = try {
            val p = Profiles.findByUsername(n)
            if (p != null) SearchResult.Found(p) else SearchResult.None(n)
        } catch (e: Exception) {
            SearchResult.Error(e.friendly())
        }
    }

    // Exact lookup, debounced so typing doesn't burn reads.
    LaunchedEffect(name, invalid) {
        if (name.isEmpty() || invalid != null || name.length < Limits.USERNAME_MIN) {
            result = SearchResult.Idle
            return@LaunchedEffect
        }
        delay(650)
        runSearch(name)
    }

    fun message(uid: String) {
        if (busy != null) return
        busy = "message"
        scope.launch {
            try {
                val id = Chats.openDirect(myUid, uid, Prefs.disappearingDefault.value)
                nav.navigate(Routes.chat(id)) { launchSingleTop = true }
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                busy = null
            }
        }
    }

    fun call(uid: String) {
        if (busy != null) return
        busy = "call"
        scope.launch {
            try {
                val id = Chats.openDirect(myUid, uid, Prefs.disappearingDefault.value)
                CallController.startOutgoing(id, uid, false)
            } catch (e: Exception) {
                Toasts.error(e)
            } finally {
                busy = null
            }
        }
    }

    val found = (result as? SearchResult.Found)?.profile
    val foundBlocked = found != null && found.uid in blocked
    val friends = remember(chats, blocked, myUid) { Session.friends(chats, myUid).filter { it !in blocked } }

    Column(Modifier.fillMaxSize()) {
        NHeader("Friends") {
            NIconButton(Icons.Outlined.PersonAdd, "Find by username", {
                runCatching { focus.requestFocus() }
                keyboard?.show()
            })
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "search") {
                NTextField(
                    value = query,
                    onValueChange = { t -> query = t.filterNot { it.isWhitespace() }.lowercase() },
                    modifier = Modifier.focusRequester(focus),
                    label = "Find someone",
                    placeholder = "exact username",
                    prefix = "@",
                    error = invalid,
                    helper = "Usernames are exact, so ask your friend for theirs.",
                    imeAction = ImeAction.Search,
                    onSubmit = {
                        if (name.isNotEmpty() && invalid == null) {
                            keyboard?.hide()
                            scope.launch { runSearch(name) }
                        }
                    },
                    maxLength = Limits.USERNAME_MAX + 1,
                    trailing = {
                        if (result == SearchResult.Loading) {
                            CircularProgressIndicator(color = c.textMuted, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            NIconButton(
                                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                                label = "Search",
                                onClick = {
                                    keyboard?.hide()
                                    scope.launch { runSearch(name) }
                                },
                                enabled = name.isNotEmpty() && invalid == null,
                                size = 36.dp,
                                iconSize = 20.dp,
                            )
                        }
                    },
                )
            }

            when (val r = result) {
                SearchResult.Loading -> item(key = "loading") {
                    NCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Skeleton(Modifier.size(56.dp).clip(CircleShape))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Skeleton(Modifier.fillMaxWidth(0.5f).height(16.dp))
                                Skeleton(Modifier.fillMaxWidth(0.3f).height(12.dp))
                            }
                        }
                    }
                }
                is SearchResult.Found -> item(key = "found:${r.profile.uid}") {
                    FoundCard(
                        profile = r.profile,
                        isMe = r.profile.uid == myUid,
                        blocked = foundBlocked,
                        busy = busy,
                        onMessage = { message(r.profile.uid) },
                        onCall = { call(r.profile.uid) },
                        onAddToGroup = { groupPick = true },
                        onBlock = { confirmBlock = true },
                        onUnblock = {
                            scope.launch {
                                try {
                                    Private.setBlocked(myUid, r.profile.uid, false)
                                    Toasts.show("Unblocked")
                                } catch (e: Exception) {
                                    Toasts.error(e)
                                }
                            }
                        },
                    )
                }
                is SearchResult.None -> item(key = "none") {
                    EmptyState(
                        title = "No one by that name",
                        body = "Nobody has claimed @${r.name}. Check the spelling with your friend.",
                        icon = Icons.Rounded.Search,
                    )
                }
                is SearchResult.Error -> item(key = "error") {
                    EmptyState(title = "Couldn't search", body = r.message, icon = Icons.Outlined.CloudOff)
                }
                SearchResult.Idle -> {
                    if (listStatus != LoadStatus.Ready && chats.isEmpty()) {
                        item(key = "sk") {
                            Column { repeat(3) { SkeletonRow() } }
                        }
                    } else if (friends.isNotEmpty()) {
                        item(key = "label") { SectionLabel("People you chat with", Modifier.padding(top = 0.dp)) }
                        itemsIndexed(friends, key = { _, uid -> "f:$uid" }) { i, uid ->
                            Column {
                                if (i > 0) NDivider(Modifier.padding(start = 64.dp))
                                FriendRow(uid = uid, profile = profiles[uid], onClick = { message(uid) })
                            }
                        }
                    } else {
                        item(key = "empty") {
                            EmptyState(
                                title = "Your people",
                                body = "Friends you chat with will appear here with their online status. You're @${myUsername ?: "…"}.",
                                icon = Icons.Outlined.People,
                            )
                        }
                    }
                }
            }
        }
    }

    if (groupPick && found != null) {
        ChatPickerSheet(
            title = "Add to which group?",
            groupsOnly = true,
            onDismiss = { groupPick = false },
            onPick = { chat, groupName ->
                groupPick = false
                when {
                    chat.type != ChatType.Group -> Toasts.show("Pick a group, not a direct chat.")
                    found.uid in chat.members -> Toasts.show("${found.displayName} is already in $groupName.")
                    else -> scope.launch {
                        try {
                            Chats.addMembers(chat, listOf(found.uid))
                            Toasts.show("Added to $groupName")
                        } catch (e: Exception) {
                            Toasts.error(e)
                        }
                    }
                }
            },
        )
    }

    if (confirmBlock && found != null) {
        ConfirmDialog(
            title = "Block ${found.displayName}?",
            message = "They won't be able to message or call you, and you won't get notifications from them. They aren't told.",
            confirmLabel = "Block",
            destructive = true,
            onDismiss = { confirmBlock = false },
            onConfirm = {
                confirmBlock = false
                scope.launch {
                    try {
                        Private.setBlocked(myUid, found.uid, true)
                        Toasts.show("${found.displayName} is blocked.")
                    } catch (e: Exception) {
                        Toasts.error(e)
                    }
                }
            },
        )
    }
}

@Composable
private fun FoundCard(
    profile: Profile,
    isMe: Boolean,
    blocked: Boolean,
    busy: String?,
    onMessage: () -> Unit,
    onCall: () -> Unit,
    onAddToGroup: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    val c = Nook.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(shown, enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 6 }) {
        NCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Avatar(profile.displayName, profile.photoURL, size = 56.dp, seed = profile.uid)
                Column(Modifier.weight(1f)) {
                    NText(profile.displayName, NookType.subhead, maxLines = 1)
                    NText("@${profile.username}", NookType.caption, c.textMuted, maxLines = 1)
                }
            }
            Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                when {
                    isMe -> NText("That's you. Share your username so friends can find you.", NookType.caption, c.textMuted)
                    blocked -> NButton("Unblock", onUnblock, variant = ButtonVariant.Secondary, icon = Icons.Outlined.Block)
                    else -> {
                        NButton("Message", onMessage, icon = Icons.AutoMirrored.Rounded.Chat, loading = busy == "message")
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            NButton(
                                "Call",
                                onCall,
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Secondary,
                                icon = Icons.Outlined.Call,
                                loading = busy == "call",
                            )
                            NButton(
                                "Add to group",
                                onAddToGroup,
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Secondary,
                                icon = Icons.Outlined.People,
                            )
                        }
                        NButton("Block", onBlock, variant = ButtonVariant.Destructive, icon = Icons.Outlined.Block)
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(uid: String, profile: Profile?, onClick: () -> Unit) {
    val c = Nook.colors
    val online = rememberPresence(uid).online
    val name = displayNameOf(profile)
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(scaleTo = 0.98f, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Avatar(name, profile?.photoURL, size = 48.dp, online = online, seed = uid)
        Column(Modifier.weight(1f)) {
            NText(name, NookType.bodyBold, maxLines = 1)
            NText(
                when {
                    online -> "Online"
                    profile != null -> "@${profile.username}"
                    else -> " "
                },
                NookType.caption,
                if (online) c.text else c.textMuted,
                maxLines = 1,
            )
        }
    }
}
