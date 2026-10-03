package com.nook.msgapp.ui.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.Limits
import com.nook.core.Profile
import com.nook.msgapp.data.Chats
import com.nook.msgapp.data.Prefs
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.NTextField
import com.nook.msgapp.ui.components.NTopBar
import com.nook.msgapp.ui.components.SectionLabel
import com.nook.msgapp.ui.home.PersonSelectRow
import com.nook.msgapp.ui.home.UsernameSearch
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Name a group, pick friends (or add by exact username), create it and open the chat. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGroupScreen(nav: NavController) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    val me = Session.myUid
    val chats by Session.chats.collectAsState()
    val blocked by Session.blocked.collectAsState()
    val profiles by Profiles.cache.collectAsState()
    val friends = remember(chats, blocked, me) { Session.friends(chats, me).filter { it !in blocked } }

    var name by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    // People found by username who aren't in the profile cache's friend list yet.
    val extra = remember { mutableStateListOf<Profile>() }
    var busy by remember { mutableStateOf(false) }

    fun profileOf(uid: String): Profile? = profiles[uid] ?: extra.firstOrNull { it.uid == uid }

    fun toggle(uid: String) {
        if (uid in picked) {
            picked.remove(uid)
        } else {
            if (picked.size + 1 >= Limits.MAX_GROUP_MEMBERS) {
                Toasts.show("Groups can have up to ${Limits.MAX_GROUP_MEMBERS} people.")
                return
            }
            picked.add(uid)
        }
    }

    fun create() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val id = Chats.createGroup(me, name, picked.toList(), Prefs.disappearingDefault.value)
                nav.navigate(Routes.chat(id)) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                Toasts.error(e)
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        NTopBar(title = "New group", onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "name") {
                NTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Group name",
                    placeholder = "The Boys",
                    capitalization = KeyboardCapitalization.Words,
                    maxLength = 40,
                )
            }
            item(key = "chips") {
                AnimatedVisibility(
                    visible = picked.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        picked.forEach { uid ->
                            val p = profileOf(uid)
                            val display = p?.displayName ?: "…"
                            Row(
                                Modifier
                                    .background(c.surface, RoundedCornerShape(Radius.pill))
                                    .border(1.dp, c.border, RoundedCornerShape(Radius.pill))
                                    .padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Avatar(display, p?.photoURL, size = 26.dp, seed = uid)
                                NText(display.substringBefore(' '), NookType.captionBold, maxLines = 1)
                                NIconButton(Icons.Rounded.Close, "Remove $display", { toggle(uid) }, size = 30.dp, iconSize = 14.dp)
                            }
                        }
                    }
                }
            }
            item(key = "search") {
                UsernameSearch(
                    me = me,
                    exclude = picked.toList(),
                    onPick = { p ->
                        if (profiles[p.uid] == null && extra.none { it.uid == p.uid }) extra.add(p)
                        if (p.uid !in picked) toggle(p.uid)
                    },
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            if (friends.isNotEmpty()) {
                item(key = "label") { SectionLabel("People you chat with") }
                items(friends, key = { it }) { uid ->
                    PersonSelectRow(uid = uid, profile = profiles[uid], selected = uid in picked, onToggle = { toggle(uid) })
                }
            } else {
                item(key = "hint") {
                    NText(
                        "Add people by their exact username. Friends you chat with will also show up here.",
                        NookType.caption,
                        c.textMuted,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.background)
                .navigationBarsPadding()
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.md),
        ) {
            NButton(
                title = when (picked.size) {
                    0 -> "Create group"
                    1 -> "Create with 1 friend"
                    else -> "Create with ${picked.size} friends"
                },
                onClick = { create() },
                icon = Icons.Outlined.People,
                loading = busy,
                enabled = name.isNotBlank() && picked.isNotEmpty(),
            )
        }
    }
}
