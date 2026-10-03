package com.nook.msgapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.Call
import com.nook.core.CallStatus
import com.nook.core.ChatLogic
import com.nook.core.Profile
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.data.Calls
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.NHeader
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.Segmented
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Spacing

private enum class CallFilter(val label: String) { All("All"), Missed("Missed") }

private fun isMissed(c: Call, me: String) =
    c.calleeId == me && (c.status == CallStatus.Missed || c.status == CallStatus.Cancelled || c.status == CallStatus.Ringing)

@Composable
fun CallsTab(nav: NavController) {
    val me = Session.myUid
    val history by rememberLoad(me) { Calls.historyFlow(me) }
    val profiles by Profiles.cache.collectAsState()
    val blocked by Session.blocked.collectAsState()
    val now = rememberNow()
    var filter by rememberSaveable { mutableStateOf(CallFilter.All) }

    val calls = (history as? Load.Ready)?.value.orEmpty()
    // Make sure every caller's profile is loaded (people I only called, never chatted with).
    LaunchedEffect(calls) {
        Profiles.ensure(calls.mapNotNull { c -> c.members.firstOrNull { it != me } })
    }
    val visible = remember(calls, filter, me) {
        if (filter == CallFilter.Missed) calls.filter { isMissed(it, me) } else calls
    }

    fun callBack(call: Call) {
        val other = call.members.firstOrNull { it != me } ?: return
        if (other in blocked) {
            Toasts.show("Unblock them first to call.")
            return
        }
        CallController.startOutgoing(call.chatId, other, call.video)
    }

    Column(Modifier.fillMaxSize()) {
        NHeader("Calls")
        Segmented(
            options = CallFilter.entries.map { it to it.label },
            selected = filter,
            onSelect = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.xs),
        )
        when (history) {
            Load.Loading -> Column(Modifier.padding(horizontal = Spacing.lg)) { repeat(3) { SkeletonRow() } }
            is Load.Failed -> EmptyState(
                title = "Couldn't load calls",
                body = "Check your connection and try again.",
                icon = Icons.Outlined.CloudOff,
            )
            is Load.Ready -> {
                if (visible.isEmpty()) {
                    EmptyState(
                        title = if (filter == CallFilter.Missed) "Nothing missed" else "No calls yet",
                        body = if (filter == CallFilter.Missed) {
                            "Calls you didn't pick up will show here in coral."
                        } else {
                            "Call a friend from their chat. Tap a call here to call back."
                        },
                        icon = if (filter == CallFilter.Missed) Icons.Outlined.Call else Icons.Outlined.Videocam,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                        items(visible, key = { it.id }) { call ->
                            val otherId = call.members.firstOrNull { it != me }.orEmpty()
                            CallRow(
                                call = call,
                                me = me,
                                now = now,
                                other = profiles[otherId],
                                otherId = otherId,
                                onCallBack = { callBack(call) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRow(
    call: Call,
    me: String,
    now: Long,
    other: Profile?,
    otherId: String,
    onCallBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Nook.colors
    val name = displayNameOf(other)
    val missed = isMissed(call, me)
    val outgoing = call.callerId == me
    val answered = call.answeredAt
    val ended = call.endedAt
    val length = if (answered != null && ended != null && ended >= answered) ChatLogic.durationLabel(ended - answered) else null
    val detail = when {
        missed -> "Missed"
        call.status == CallStatus.Declined -> "Declined"
        outgoing && answered == null -> "No answer"
        length != null -> length
        outgoing -> "Outgoing"
        else -> "Incoming"
    }
    val tint = if (missed) c.danger else c.textMuted
    val started = call.startedAt

    Row(
        modifier
            .fillMaxWidth()
            .pressScale(scaleTo = 0.98f, onClick = onCallBack)
            .heightIn(min = 66.dp)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Avatar(name, other?.photoURL, size = 46.dp, seed = otherId)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            NText(name, NookType.bodyBold, if (missed) c.danger else c.text, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    if (outgoing) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = if (outgoing) "Outgoing" else "Incoming",
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
                Icon(
                    if (call.video) Icons.Outlined.Videocam else Icons.Outlined.Call,
                    contentDescription = if (call.video) "Video" else "Voice",
                    tint = tint,
                    modifier = Modifier.size(13.dp),
                )
                NText(detail, NookType.caption, tint, maxLines = 1)
            }
        }
        NText(if (started != null) ChatLogic.timeLabel(started, now) else "", NookType.micro, c.textMuted, maxLines = 1)
        NIconButton(
            icon = if (call.video) Icons.Outlined.Videocam else Icons.Outlined.Call,
            label = "Call $name back",
            onClick = onCallBack,
            size = 38.dp,
            iconSize = 20.dp,
        )
    }
}
