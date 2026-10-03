package com.nook.msgapp.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nook.core.Chat
import com.nook.core.ChatLogic
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.GroupAvatar
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing

/**
 * Pick one or more of your chats (forwarding). [onSend] gets the chosen chats with their titles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPickerSheet(
    title: String,
    me: String,
    onDismiss: () -> Unit,
    onSend: (List<Pair<Chat, String>>) -> Unit,
    excludeChatId: String? = null,
) {
    val c = Nook.colors
    val chats by Session.chats.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    val list = remember(chats, excludeChatId) { chats.filter { it.id != excludeChatId } }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surface,
        contentColor = c.text,
        scrimColor = c.overlay,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.lg)) {
            NText(title, NookType.headline)
            if (list.isEmpty()) {
                EmptyState("No chats yet", "Start a chat from the Friends tab first.", icon = Icons.Outlined.ChatBubbleOutline)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = Spacing.xs)) {
                    items(list, key = { it.id }) { chat ->
                        val name = Session.titleFor(chat, me, profiles)
                        val on = chat.id in selected
                        val box by animateColorAsState(if (on) c.accent else Color.Transparent, tween(140), label = "pick")
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .pressScale(scaleTo = 0.98f) {
                                    selected = if (on) selected - chat.id else selected + chat.id
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            if (chat.isGroup) {
                                GroupAvatar(chat, 36.dp)
                            } else {
                                val other = ChatLogic.otherMember(chat.members, me)
                                Avatar(name, profiles[other]?.photoURL, size = 36.dp, seed = other ?: chat.id)
                            }
                            NText(name, NookType.bodyBold, modifier = Modifier.weight(1f), maxLines = 1)
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(box)
                                    .border(1.5.dp, if (on) c.accent else c.border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                NButton(
                    title = if (selected.size > 1) "Send to ${selected.size} chats" else "Send",
                    onClick = {
                        val picked = list.filter { it.id in selected }.map { it to Session.titleFor(it, me, profiles) }
                        if (picked.isNotEmpty()) onSend(picked)
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
                )
            }
        }
    }
}
