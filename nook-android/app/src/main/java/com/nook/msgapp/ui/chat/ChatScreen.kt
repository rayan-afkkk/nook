package com.nook.msgapp.ui.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nook.core.Chat
import com.nook.core.ChatLogic
import com.nook.core.Disappearing
import com.nook.core.Limits
import com.nook.core.Media
import com.nook.core.Message
import com.nook.core.MessageKind
import com.nook.core.ReplyRef
import com.nook.msgapp.calls.CallController
import com.nook.msgapp.data.Chats
import com.nook.msgapp.data.Cloudinary
import com.nook.msgapp.data.Draft
import com.nook.msgapp.data.GiphyType
import com.nook.msgapp.data.Outbox
import com.nook.msgapp.data.OutboxItem
import com.nook.msgapp.data.Presence
import com.nook.msgapp.data.PresenceState
import com.nook.msgapp.data.Private
import com.nook.msgapp.data.Profiles
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.Toasts
import com.nook.msgapp.data.UserFacingError
import com.nook.msgapp.data.friendly
import com.nook.msgapp.media.VoicePlayer
import com.nook.msgapp.nav.Routes
import com.nook.msgapp.push.Notifications
import com.nook.msgapp.ui.components.Avatar
import com.nook.msgapp.ui.components.ButtonVariant
import com.nook.msgapp.ui.components.Chip
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.GroupAvatar
import com.nook.msgapp.ui.components.ListRow
import com.nook.msgapp.ui.components.Loading
import com.nook.msgapp.ui.components.NButton
import com.nook.msgapp.ui.components.NDivider
import com.nook.msgapp.ui.components.NIconButton
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.Skeleton
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.components.rememberCameraCapture
import com.nook.msgapp.ui.components.rememberFilePicker
import com.nook.msgapp.ui.components.rememberImagePicker
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/* ------------------------------------------------------------------ list model */

private sealed interface ChatItem {
    val key: String
}

private data class DateItem(override val key: String, val label: String) : ChatItem

private data class MsgItem(
    override val key: String,
    val message: Message,
    val chainedAbove: Boolean,
    val chainedBelow: Boolean,
    val showSender: Boolean,
    val showAvatar: Boolean,
    val footer: String?,
) : ChatItem

private data class OutboxEntry(override val key: String, val item: OutboxItem) : ChatItem

private data object TypingItem : ChatItem {
    override val key: String = "typing"
}

private const val CHAIN_MS = 3 * 60_000L

/** Builds the list newest-first (the LazyColumn is drawn bottom-up). */
private fun buildItems(
    messages: List<Message>,
    outbox: List<OutboxItem>,
    typing: Boolean,
    chat: Chat,
    me: String,
    now: Long,
    blocked: Boolean,
): List<ChatItem> {
    // Messages arrive newest-first; walk them oldest-first.
    val asc = messages.reversed().filter { !(blocked && it.senderId != me) }
    val myLast = asc.lastOrNull { it.senderId == me && it.kind != MessageKind.System }
    val others = chat.members.filter { it != me }
    val out = ArrayList<ChatItem>(asc.size + 8)
    var prev: Message? = null
    asc.forEachIndexed { i, m ->
        val t = m.createdAt ?: now
        val p = prev
        val newDay = p == null || !ChatLogic.sameDay(p.createdAt ?: t, t)
        if (newDay) out.add(DateItem("d-${m.id}", ChatLogic.dayLabel(t, now)))
        val next = asc.getOrNull(i + 1)
        val nextT = next?.createdAt ?: now
        val chainedBelow = next != null &&
            next.senderId == m.senderId &&
            next.kind != MessageKind.System &&
            m.kind != MessageKind.System &&
            nextT - t < CHAIN_MS &&
            ChatLogic.sameDay(t, nextT)
        val chainedAbove = p != null &&
            !newDay &&
            p.senderId == m.senderId &&
            p.kind != MessageKind.System &&
            m.kind != MessageKind.System &&
            t - (p.createdAt ?: t) < CHAIN_MS
        val showSender = chat.isGroup && m.senderId != me && (p == null || newDay || p.senderId != m.senderId || p.kind == MessageKind.System)
        var footer: String? = null
        if (myLast != null && m.id == myLast.id && !m.pending) {
            val seenBy = others.count { (chat.lastRead[it] ?: 0L) >= t }
            footer = when {
                seenBy == 0 -> "Sent"
                !chat.isGroup || ChatLogic.seenByAll(t, chat.members, me, chat.lastRead) -> "Seen"
                else -> "Seen by $seenBy"
            }
        }
        out.add(MsgItem(m.id, m, chainedAbove, chainedBelow, showSender, showAvatar = !chainedBelow, footer = footer))
        prev = m
    }
    outbox.forEach { out.add(OutboxEntry("o-${it.id}", it)) }
    if (typing) out.add(TypingItem)
    return out.reversed()
}

private fun relativeLastSeen(ms: Long?, now: Long): String {
    if (ms == null || ms == 0L) return "Offline"
    val mins = ((now - ms) / 60_000.0).roundToInt()
    if (mins < 1) return "Last seen just now"
    if (mins < 60) return "Last seen $mins min ago"
    val hours = (mins / 60.0).roundToInt()
    if (hours < 24) return "Last seen $hours h ago"
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    return "Last seen $date"
}

/** Reads a picked image's pixel size without decoding it. */
private fun imageSize(context: Context, uri: Uri): Pair<Int, Int>? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else null
}.getOrNull()

private val TIMER_OPTIONS = listOf(
    Triple(Disappearing.Off, "Off", "Messages stay until someone deletes them"),
    Triple(Disappearing.Day, "24 hours", "New messages vanish a day after they’re sent"),
    Triple(Disappearing.Week, "7 days", "New messages vanish a week after they’re sent"),
)

/* ------------------------------------------------------------------ screen */

@Composable
fun ChatScreen(chatId: String, nav: NavController) {
    val uid by Session.uid.collectAsState()
    val chats by Session.chats.collectAsState()
    val listChat = remember(chats, chatId) { chats.firstOrNull { it.id == chatId } ?: Session.chat(chatId) }
    var remoteChat by remember(chatId) { mutableStateOf<Chat?>(null) }
    var missing by remember(chatId) { mutableStateOf(false) }
    val needRemote = listChat == null

    // Not in my chat list (older than the first 100, or just created): listen to the doc itself.
    LaunchedEffect(chatId, needRemote) {
        if (!needRemote || chatId.isEmpty()) return@LaunchedEffect
        Chats.chatFlow(chatId)
            .catch { missing = true }
            .collect { found ->
                if (found == null) {
                    missing = true
                } else {
                    remoteChat = found
                    missing = false
                }
            }
    }

    val chat = listChat ?: remoteChat
    val me = uid
    if (chat == null) {
        if (missing || chatId.isEmpty()) ChatMissing(nav) else ChatSkeleton()
        return
    }
    if (me.isNullOrEmpty()) {
        ChatSkeleton()
        return
    }
    ChatView(chat, me, nav)
}

@Composable
private fun ChatMissing(nav: NavController) {
    Box(Modifier.fillMaxSize().background(Nook.colors.background).statusBarsPadding(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = "Chat not available",
            body = "It may have been removed, or you’re no longer a member.",
            icon = Icons.Outlined.ChatBubbleOutline,
            action = "Back to chats",
            onAction = { nav.popBackStack() },
        )
    }
}

@Composable
private fun ChatSkeleton() {
    Column(Modifier.fillMaxSize().background(Nook.colors.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Skeleton(Modifier.size(38.dp).clip(CircleShape))
            Skeleton(Modifier.size(width = 140.dp, height = 16.dp))
        }
        NDivider()
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf(0.6f, 0.4f, 0.7f, 0.5f).forEachIndexed { i, w ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (i % 2 == 1) Alignment.CenterEnd else Alignment.CenterStart) {
                    Skeleton(Modifier.fillMaxWidth(w).height(40.dp).clip(RoundedCornerShape(20.dp)))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatView(chat: Chat, me: String, nav: NavController) {
    val c = Nook.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val chatId = chat.id

    val profiles by Profiles.cache.collectAsState()
    val blockedList by Session.blocked.collectAsState()
    val outboxAll by Outbox.items.collectAsState()
    val outbox = remember(outboxAll, chatId) { outboxAll.filter { it.chatId == chatId } }
    val otherId = if (chat.isGroup) null else ChatLogic.otherMember(chat.members, me)
    val other = otherId?.let { profiles[it] }
    val blocked = otherId != null && otherId in blockedList
    val presence by remember(otherId) {
        if (otherId != null) Presence.flow(otherId) else flowOf(PresenceState(false, null))
    }.collectAsState(initial = PresenceState(false, null))
    val typingRaw by remember(chatId, me) { Presence.typingFlow(chatId, me) }.collectAsState(initial = emptyList())
    val typing = if (blocked) emptyList() else typingRaw

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }

    LaunchedEffect(chat.members) { Profiles.ensure(chat.members) }

    /* ---------------- messages (paged by growing the live query) ---------------- */

    var count by remember(chatId) { mutableLongStateOf(Limits.PAGE_SIZE) }
    var messages by remember(chatId) { mutableStateOf<List<Message>>(emptyList()) }
    var loaded by remember(chatId) { mutableStateOf(false) }
    var loadError by remember(chatId) { mutableStateOf<String?>(null) }
    var hasMore by remember(chatId) { mutableStateOf(false) }
    var loadingMore by remember(chatId) { mutableStateOf(false) }
    LaunchedEffect(chatId, count) {
        val limit = count
        Chats.messagesFlow(chatId, limit)
            .catch { e ->
                loadError = e.friendly()
                loaded = true
                loadingMore = false
            }
            .collect { list ->
                messages = list
                loaded = true
                loadError = null
                hasMore = list.size >= limit
                loadingMore = false
            }
    }

    /* ---------------- this chat is on screen ---------------- */

    DisposableEffect(chatId, me) {
        Session.activeChatId = chatId
        Notifications.clearChat(context, chatId)
        onDispose {
            if (Session.activeChatId == chatId) Session.activeChatId = null
            Presence.setTyping(chatId, me, false)
            VoicePlayer.stop()
        }
    }
    LaunchedEffect(chatId) {
        try {
            Chats.cleanupExpired(chatId, me)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: someone else's app will clean up.
        }
    }

    /* ---------------- UI state ---------------- */

    var replyTo by remember(chatId) { mutableStateOf<ReplyRef?>(null) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var moreReactionsFor by remember { mutableStateOf<Message?>(null) }
    var forwarding by remember { mutableStateOf<Message?>(null) }
    var deleting by remember { mutableStateOf<Message?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var timerOpen by remember { mutableStateOf(false) }
    var unblocking by remember { mutableStateOf(false) }
    val lastViewer = remember { arrayOf("") }
    viewer?.let { lastViewer[0] = it }

    val rows = remember(messages, outbox, typing.isNotEmpty(), chat.lastRead, chat.members, chat.type, me, now, blocked) {
        buildItems(messages, outbox, typing.isNotEmpty(), chat, me, now, blocked)
    }
    val latestId = messages.firstOrNull()?.id
    fun nameOf(uid: String): String = if (uid == me) "You" else profiles[uid]?.displayName ?: "Someone"
    val title = Session.titleFor(chat, me, profiles)

    /* ---------------- scrolling, read receipts, paging ---------------- */

    val listState = rememberLazyListState()
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
    val atBottomNow by rememberUpdatedState(atBottom)
    var unseen by remember { mutableIntStateOf(0) }
    val newest = rows.firstOrNull { it is MsgItem || it is OutboxEntry }
    val newestKey = newest?.key
    val newestMine = when (newest) {
        is MsgItem -> newest.message.senderId == me
        is OutboxEntry -> true
        else -> false
    }
    LaunchedEffect(newestKey) {
        if (newestKey == null) return@LaunchedEffect
        if (atBottomNow || newestMine) {
            listState.animateScrollToItem(0)
            unseen = 0
        } else {
            unseen += 1
        }
    }
    LaunchedEffect(atBottom) { if (atBottom) unseen = 0 }

    val lastAt = chat.lastMessageAt ?: 0L
    val myRead = chat.lastRead[me] ?: 0L
    val lastFromOther = chat.lastMessage != null && chat.lastMessage?.senderId != me
    val lastMarked = remember { longArrayOf(0L) }
    LaunchedEffect(atBottom, lastAt, myRead, messages.size) {
        if (!atBottom || !lastFromOther || lastAt <= myRead) return@LaunchedEffect
        val wait = 4_000L - (System.currentTimeMillis() - lastMarked[0])
        if (wait > 0) delay(wait)
        lastMarked[0] = System.currentTimeMillis()
        runCatching { Chats.markRead(chatId, me) }
    }

    val nearTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearTop, hasMore, loadingMore, loaded) {
        if (nearTop && hasMore && !loadingMore && loaded) {
            loadingMore = true
            count += Limits.PAGE_SIZE
        }
    }

    /* ---------------- actions ---------------- */

    fun scrollToBottom() {
        scope.launch { runCatching { listState.animateScrollToItem(0) } }
    }

    fun send(draft: Draft) {
        val d = draft.copy(replyTo = replyTo)
        replyTo = null
        val disappearing = chat.disappearing
        scope.launch {
            try {
                Chats.send(chatId, disappearing, me, d)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toasts.error(e)
            }
        }
        scrollToBottom()
    }

    fun reply(m: Message) {
        if (m.kind == MessageKind.System) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        replyTo = ReplyRef(m.id, m.senderId, m.kind, ChatLogic.previewFor(m.kind, m.text, m.media?.name).take(100))
    }

    fun react(m: Message, emoji: String?) {
        scope.launch {
            try {
                Chats.setReaction(chatId, m.id, me, emoji)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toasts.error(e)
            }
        }
    }

    fun attach(kind: MessageKind, uri: Uri) {
        val reply = replyTo
        val disappearing = chat.disappearing
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    Cloudinary.describe(context, uri, if (kind == MessageKind.Image) "photo.jpg" else "file")
                }
                if (file.size > Limits.MAX_FILE_BYTES) throw UserFacingError("That file is over the 10 MB limit.")
                val dims = if (kind == MessageKind.Image) withContext(Dispatchers.IO) { imageSize(context, uri) } else null
                Outbox.enqueue(context, chatId, disappearing, kind, file, reply, width = dims?.first, height = dims?.second)
                replyTo = null
                runCatching { listState.animateScrollToItem(0) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toasts.error(e)
            }
        }
    }

    val pickImage = rememberImagePicker { uri -> attach(MessageKind.Image, uri) }
    val takePhoto = rememberCameraCapture { uri -> attach(MessageKind.Image, uri) }
    val pickFile = rememberFilePicker { uri -> attach(MessageKind.File, uri) }

    val callOther: ((Boolean) -> Unit)? = otherId?.let { other ->
        { video: Boolean ->
            if (blocked) {
                Toasts.show("Unblock them to call.")
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                CallController.startOutgoing(chatId, other, video)
            }
        }
    }

    val menuActions: List<MenuAction> = menu?.message?.let { m ->
        buildList<MenuAction> {
            if (m.kind != MessageKind.System) add(MenuAction("Reply", Icons.AutoMirrored.Rounded.Reply) { reply(m) })
            if (m.kind == MessageKind.Text && !m.text.isNullOrEmpty()) {
                add(
                    MenuAction("Copy", Icons.Rounded.ContentCopy) {
                        clipboard.setText(AnnotatedString(m.text.orEmpty()))
                        Toasts.show("Copied")
                    },
                )
            }
            if (m.kind != MessageKind.System) add(MenuAction("Forward", Icons.AutoMirrored.Rounded.Forward) { forwarding = m })
            if (m.senderId == me) add(MenuAction("Delete for everyone", Icons.Rounded.Delete, destructive = true) { deleting = m })
        }
    } ?: emptyList()

    val blurRadius by animateDpAsState(if (menu != null) 8.dp else 0.dp, tween(200), label = "blur")

    /* ---------------- layout ---------------- */

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
        ) {
            ChatHeader(
                chat = chat,
                title = title,
                photoURL = other?.photoURL,
                online = presence.online && !blocked,
                status = when {
                    typing.isNotEmpty() ->
                        if (chat.isGroup) typing.joinToString(", ") { nameOf(it).substringBefore(' ') } + " typing…" else "typing…"
                    chat.isGroup -> "${chat.members.size} members"
                    blocked -> "Blocked"
                    presence.online -> "Online"
                    else -> relativeLastSeen(presence.lastChanged, now)
                },
                typing = typing.isNotEmpty(),
                seed = otherId ?: chat.id,
                onBack = { nav.popBackStack() },
                onOpenInfo = { nav.navigate(Routes.chatInfo(chatId)) },
                onTimer = { timerOpen = true },
                onCall = callOther,
            )

            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                when {
                    !loaded && rows.isEmpty() -> Loading()
                    loadError != null && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState("Couldn’t load messages", loadError ?: "", icon = Icons.Rounded.CloudOff)
                    }
                    rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            "Say hi",
                            if (chat.isGroup) "This is the start of your chat in $title." else "This is the start of your chat with $title.",
                            icon = Icons.Outlined.ChatBubbleOutline,
                        )
                    }
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = Spacing.sm, bottom = Spacing.sm),
                    ) {
                        items(
                            rows,
                            key = { it.key },
                            contentType = { item ->
                                when (item) {
                                    is DateItem -> "date"
                                    is MsgItem -> "m-${item.message.kind.wire}"
                                    is OutboxEntry -> "outbox"
                                    TypingItem -> "typing"
                                }
                            },
                        ) { item ->
                            Box(Modifier.animateItem()) {
                                when (item) {
                                    is DateItem -> DateSeparator(item.label)
                                    TypingItem -> TypingDots()
                                    is OutboxEntry -> OutboxBubble(
                                        item.item,
                                        onRetry = { Outbox.retry(context, item.item.id) },
                                        onCancel = { Outbox.cancel(item.item.id) },
                                    )
                                    is MsgItem -> {
                                        val m = item.message
                                        if (m.kind == MessageKind.System) {
                                            SystemLine(m.text.orEmpty())
                                        } else {
                                            MessageRow(
                                                message = m,
                                                me = me,
                                                mine = m.senderId == me,
                                                isGroup = chat.isGroup,
                                                senderName = if (chat.isGroup) nameOf(m.senderId) else null,
                                                senderPhoto = profiles[m.senderId]?.photoURL,
                                                showAvatar = item.showAvatar,
                                                replyAuthor = m.replyTo?.let { nameOf(it.senderId) },
                                                chainedAbove = item.chainedAbove,
                                                chainedBelow = item.chainedBelow,
                                                footer = item.footer,
                                                onReply = { reply(it) },
                                                onLongPress = { msg, rect ->
                                                    keyboard?.hide()
                                                    menu = MenuTarget(msg, rect, msg.senderId == me)
                                                },
                                                onOpenImage = { msg -> msg.media?.url?.let { viewer = it } },
                                                onToggleReaction = { msg, emoji ->
                                                    react(msg, if (msg.reactions[me] == emoji) null else emoji)
                                                },
                                                showSenderLabel = item.showSender,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (loadingMore) {
                            item(key = "loading-more") {
                                Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = c.textMuted, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // Jump to the newest message.
                AnimatedVisibility(
                    visible = !atBottom && rows.isNotEmpty(),
                    enter = scaleIn(tween(180), initialScale = 0.6f) + fadeIn(tween(160)),
                    exit = scaleOut(tween(140), targetScale = 0.6f) + fadeOut(tween(120)),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.md, bottom = Spacing.md),
                ) {
                    Box {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(c.surfaceRaised)
                                .border(1.dp, c.border, CircleShape)
                                .pressScale(scaleTo = 0.88f) {
                                    unseen = 0
                                    scrollToBottom()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "Scroll to newest", tint = c.text, modifier = Modifier.size(22.dp))
                        }
                        if (unseen > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .heightIn(min = 18.dp)
                                    .widthIn(min = 18.dp)
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(c.accent)
                                    .padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                NText(if (unseen > 99) "99+" else unseen.toString(), NookType.micro, Color.Black)
                            }
                        }
                    }
                }
            }

            if (blocked) {
                Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding()) {
                    NDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        NText("You blocked $title. They can’t message or call you.", NookType.caption, c.textMuted, Modifier.weight(1f))
                        NButton(
                            "Unblock",
                            onClick = {
                                val o = otherId ?: return@NButton
                                unblocking = true
                                scope.launch {
                                    try {
                                        Private.setBlocked(me, o, false)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Toasts.error(e)
                                    } finally {
                                        unblocking = false
                                    }
                                }
                            },
                            variant = ButtonVariant.Secondary,
                            block = false,
                            loading = unblocking,
                        )
                    }
                }
            } else {
                Composer(
                    replyTo = replyTo,
                    replyAuthor = replyTo?.let { nameOf(it.senderId) } ?: "",
                    onCancelReply = { replyTo = null },
                    onSendText = { text -> send(Draft(MessageKind.Text, text.trim())) },
                    onTyping = { t -> Presence.setTyping(chatId, me, t) },
                    onAttach = { source ->
                        when (source) {
                            AttachSource.Photo -> pickImage()
                            AttachSource.Camera -> takePhoto()
                            AttachSource.File -> pickFile()
                        }
                    },
                    onVoice = { clip ->
                        Outbox.enqueue(
                            context,
                            chatId,
                            chat.disappearing,
                            MessageKind.Voice,
                            Cloudinary.fromFile(clip.file, "audio/mp4"),
                            replyTo,
                            durationMs = clip.durationMs,
                            waveform = ChatLogic.toWaveform(clip.amplitudes),
                        )
                        replyTo = null
                        scrollToBottom()
                    },
                    onGiphy = { item, type ->
                        // Giphy media is shared: no publicId, so deleting the message never deletes a file.
                        send(
                            Draft(
                                kind = if (type == GiphyType.Gifs) MessageKind.Gif else MessageKind.Sticker,
                                media = Media(url = item.url, previewUrl = item.previewUrl, width = item.width, height = item.height),
                            ),
                        )
                    },
                    onSticker = { s ->
                        // Pack stickers are shared too: never attach the pack's publicId to a message.
                        send(Draft(kind = MessageKind.Sticker, media = Media(url = s.url, width = 512, height = 512)))
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
                )
            }
        }

        MessageMenu(
            target = menu,
            myReaction = menu?.message?.reactions?.get(me),
            actions = menuActions,
            onReact = { emoji -> menu?.message?.let { react(it, emoji) } },
            onMoreReactions = {
                moreReactionsFor = menu?.message
                menu = null
            },
            onClose = { menu = null },
            preview = { m ->
                BubbleBody(
                    message = m,
                    mine = m.senderId == me,
                    replyAuthor = m.replyTo?.let { nameOf(it.senderId) },
                    chainedBelow = false,
                    maxMedia = maxMediaWidth(),
                    interactive = false,
                )
            },
        )

        AnimatedVisibility(visible = viewer != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            ImageViewer(url = viewer ?: lastViewer[0], onClose = { viewer = null })
        }
    }

    /* ---------------- sheets & dialogs ---------------- */

    moreReactionsFor?.let { m ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { moreReactionsFor = null },
            sheetState = sheet,
            containerColor = c.surface,
            contentColor = c.text,
            scrimColor = c.overlay,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                NText("React", NookType.headline, modifier = Modifier.padding(horizontal = Spacing.lg))
                Box(Modifier.fillMaxWidth().height(340.dp)) {
                    EmojiGrid(onEmoji = { e ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        moreReactionsFor = null
                        react(m, if (m.reactions[me] == e) null else e)
                    })
                }
            }
        }
    }

    forwarding?.let { m ->
        ChatPickerSheet(
            title = "Forward to",
            me = me,
            onDismiss = { forwarding = null },
            onSend = { picked ->
                forwarding = null
                scope.launch {
                    var sent = 0
                    for ((target, _) in picked) {
                        try {
                            // A forwarded copy never owns the file (no publicId): deleting it can't remove the original's media.
                            Chats.send(
                                target.id,
                                target.disappearing,
                                me,
                                Draft(kind = m.kind, text = m.text, media = m.media?.copy(publicId = null), forwarded = true),
                            )
                            sent++
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toasts.error(e)
                        }
                    }
                    if (sent > 0) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        Toasts.show(if (picked.size == 1) "Forwarded to ${picked[0].second}" else "Forwarded to $sent chats")
                    }
                }
            },
        )
    }

    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            Chats.deleteMessages(chatId, me, listOf(m), latestId)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Toasts.error(e)
                        }
                    }
                }) {
                    NText("Delete", NookType.label, c.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { NText("Cancel", NookType.label) }
            },
            title = { NText("Delete message?", NookType.subhead) },
            text = { NText("It will be removed for everyone in this chat.", NookType.body, c.textMuted) },
            containerColor = c.surface,
        )
    }

    if (timerOpen) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { timerOpen = false },
            sheetState = sheet,
            containerColor = c.surface,
            contentColor = c.text,
            scrimColor = c.overlay,
        ) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)) {
                NText("Disappearing messages", NookType.headline)
                NText(
                    "Applies to new messages for everyone in this chat. Someone could still screenshot a message before it disappears.",
                    NookType.caption,
                    c.textMuted,
                    Modifier.padding(top = Spacing.xxs, bottom = Spacing.sm),
                )
                TIMER_OPTIONS.forEach { (value, label, subtitle) ->
                    ListRow(
                        label,
                        icon = if (chat.disappearing == value) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked,
                        subtitle = subtitle,
                        showChevron = false,
                        onClick = {
                            timerOpen = false
                            scope.launch {
                                try {
                                    Chats.setDisappearing(chatId, value)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Toasts.error(e)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ header */

@Composable
private fun ChatHeader(
    chat: Chat,
    title: String,
    photoURL: String?,
    online: Boolean,
    status: String,
    typing: Boolean,
    seed: String,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onTimer: () -> Unit,
    onCall: ((Boolean) -> Unit)?,
) {
    val c = Nook.colors
    Column(Modifier.fillMaxWidth().background(c.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Row(
                Modifier
                    .weight(1f)
                    .pressScale(scaleTo = 0.98f, onClick = onOpenInfo)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (chat.isGroup) {
                    GroupAvatar(chat, 38.dp)
                } else {
                    Avatar(title, photoURL, size = 38.dp, online = online, seed = seed)
                }
                Column(Modifier.weight(1f)) {
                    NText(title, NookType.subhead, maxLines = 1)
                    AnimatedContent(
                        targetState = status,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                        label = "status",
                    ) { s ->
                        NText(s, NookType.micro, if (typing) c.accent else c.textMuted, maxLines = 1)
                    }
                }
            }
            val timer = chat.disappearing
            Chip(
                text = if (timer == Disappearing.Off) "Timer" else timer.label,
                selected = timer != Disappearing.Off,
                icon = Icons.Rounded.Timer,
                onClick = onTimer,
            )
            if (onCall != null) {
                NIconButton(Icons.Rounded.Call, "Voice call", { onCall(false) }, size = 38.dp, iconSize = 20.dp)
                NIconButton(Icons.Rounded.Videocam, "Video call", { onCall(true) }, size = 38.dp, iconSize = 21.dp)
            }
        }
        NDivider()
    }
}

/** "Group created" and other system lines. */
@Composable
private fun SystemLine(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xs), contentAlignment = Alignment.Center) {
        NText(text, NookType.caption, Nook.colors.textMuted, textAlign = TextAlign.Center)
    }
}
