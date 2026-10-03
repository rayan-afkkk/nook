package com.nook.msgapp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.ChatLogic
import com.nook.core.ChatType
import com.nook.msgapp.data.Session
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing

enum class HomeTab(val label: String, val active: ImageVector, val inactive: ImageVector) {
    Chats("Chats", Icons.Rounded.Forum, Icons.Outlined.Forum),
    Friends("Friends", Icons.Rounded.People, Icons.Outlined.People),
    Stickers("Stickers", Icons.Rounded.EmojiEmotions, Icons.Outlined.EmojiEmotions),
    Calls("Calls", Icons.Rounded.Call, Icons.Outlined.Call),
    Account("Account", Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle),
}

/** Signed-in home: the five tabs above a custom bottom tab bar. */
@Composable
fun HomeScreen(nav: NavController) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
    // Bumped to ask the Friends tab to focus its username search.
    var focusSearch by remember { mutableIntStateOf(0) }
    val holder = rememberSaveableStateHolder()

    val chats by Session.chats.collectAsState()
    val blocked by Session.blocked.collectAsState()
    val me = Session.myUid
    val hasUnread = chats.any { c ->
        val blockedDm = c.type == ChatType.Direct && c.members.any { it != me && it in blocked }
        !blockedDm && me !in c.mutedBy && ChatLogic.isUnread(c, me)
    }

    BackHandler(enabled = tab != HomeTab.Chats) { tab = HomeTab.Chats }

    Column(Modifier.fillMaxSize().background(Nook.colors.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { w -> if (forward) w / 12 else -w / 12 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { w -> if (forward) -w / 16 else w / 16 })
                },
                label = "homeTab",
            ) { current ->
                holder.SaveableStateProvider(current.name) {
                    Box(Modifier.fillMaxSize().background(Nook.colors.background)) {
                        when (current) {
                            HomeTab.Chats -> ChatsTab(
                                nav = nav,
                                onFindFriend = {
                                    tab = HomeTab.Friends
                                    focusSearch++
                                },
                            )
                            HomeTab.Friends -> FriendsTab(nav = nav, focusSignal = focusSearch)
                            HomeTab.Stickers -> StickersTab(nav = nav)
                            HomeTab.Calls -> CallsTab(nav = nav)
                            HomeTab.Account -> AccountTab(nav = nav)
                        }
                    }
                }
            }
        }
        TabBar(selected = tab, chatsBadge = hasUnread, onSelect = { tab = it })
    }
}

/** Active tab: filled icon + cream label. Inactive: muted outline. Hairline top border. */
@Composable
private fun TabBar(selected: HomeTab, chatsBadge: Boolean, onSelect: (HomeTab) -> Unit) {
    val c = Nook.colors
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().background(c.background)) {
        NDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = Spacing.xxs, bottom = Spacing.xxs),
        ) {
            HomeTab.entries.forEach { t ->
                val focused = t == selected
                val badge = t == HomeTab.Chats && chatsBadge
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .semantics {
                            this.selected = focused
                            contentDescription = if (badge) "${t.label}, unread messages" else t.label
                        }
                        .pressScale(scaleTo = 0.9f, role = Role.Tab) {
                            if (!focused) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(t)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Box {
                        Icon(
                            if (focused) t.active else t.inactive,
                            contentDescription = null,
                            tint = if (focused) c.text else c.textMuted,
                            modifier = Modifier.size(23.dp),
                        )
                        if (badge) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-2).dp)
                                    .size(10.dp)
                                    .background(c.accent, CircleShape)
                                    .border(2.dp, c.background, CircleShape),
                            )
                        }
                    }
                    NText(t.label, NookType.micro, if (focused) c.text else c.textMuted, maxLines = 1)
                }
            }
        }
    }
}
