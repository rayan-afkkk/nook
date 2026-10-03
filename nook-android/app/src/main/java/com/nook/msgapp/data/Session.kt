package com.nook.msgapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.nook.core.AppStage
import com.nook.core.AuthStatus
import com.nook.core.Chat
import com.nook.core.ChatLogic
import com.nook.core.ChatType
import com.nook.core.Profile
import com.nook.core.ProfileState
import com.nook.core.Stage
import com.nook.msgapp.lock.LockStore
import com.nook.msgapp.push.Push
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LoadStatus { Idle, Loading, Ready, Error }

/**
 * App-wide signed-in state. One listener each for: my profile, my chat list, my private doc.
 * Everything else is listened to only while a screen needs it (Spark budget).
 */
object Session {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _auth = MutableStateFlow(AuthStatus.Loading)
    val authStatus: StateFlow<AuthStatus> = _auth.asStateFlow()

    private val _uid = MutableStateFlow<String?>(null)
    val uid: StateFlow<String?> = _uid.asStateFlow()

    private val _me = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val me: StateFlow<ProfileState> = _me.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _chatsStatus = MutableStateFlow(LoadStatus.Idle)
    val chatsStatus: StateFlow<LoadStatus> = _chatsStatus.asStateFlow()

    private val _blocked = MutableStateFlow<List<String>>(emptyList())
    val blocked: StateFlow<List<String>> = _blocked.asStateFlow()

    /** The chat currently on screen (its pushes are not shown as toasts). */
    @Volatile var activeChatId: String? = null

    val stage: StateFlow<AppStage> = combine(
        combine(Prefs.onboardingSeen, Prefs.permissionsPrimed) { a, b -> a to b },
        _auth,
        _me,
        LockStore.configured,
    ) { (seen, primed), auth, me, lock ->
        Stage.compute(seen, primed, auth, me, lock)
    }.stateIn(scope, SharingStarted.Eagerly, AppStage.Loading)

    private val registrations = mutableListOf<ListenerRegistration>()
    private var started = false

    val myUid: String get() = _uid.value.orEmpty()
    val myProfile: Profile? get() = (_me.value as? ProfileState.Ready)?.profile

    fun start() {
        if (started) return
        started = true
        Fb.auth.addAuthStateListener(FirebaseAuth.AuthStateListener { a ->
            val user = a.currentUser
            if (user == null) {
                stopListeners()
                _uid.value = null
                _me.value = ProfileState.Loading
                _auth.value = AuthStatus.SignedOut
            } else if (_uid.value != user.uid) {
                _uid.value = user.uid
                _auth.value = AuthStatus.SignedIn
                startListeners(user.uid)
            }
        })
    }

    private fun startListeners(uid: String) {
        stopListeners()
        _me.value = ProfileState.Loading
        registrations += Fb.users().document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                // Offline on first launch: keep waiting rather than sending them to "pick a username".
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener
            // A cached "missing" result isn't trusted: wait for the server before asking for a username.
            if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
            val p = snap.toProfile()
            _me.value = if (p == null) ProfileState.Missing else ProfileState.Ready(p).also { Profiles.put(p) }
            if (p != null && !servicesStarted) startServices(uid)
        }
        _chatsStatus.value = LoadStatus.Loading
        registrations += Chats.listQuery(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                _chatsStatus.value = LoadStatus.Error
                return@addSnapshotListener
            }
            val chats = snap?.documents?.mapNotNull { it.toChat() } ?: return@addSnapshotListener
            _chats.value = chats
            _chatsStatus.value = LoadStatus.Ready
            val people = mutableSetOf<String>()
            chats.forEach { c ->
                if (c.type == ChatType.Direct) ChatLogic.otherMember(c.members, uid)?.let(people::add) else people.addAll(c.members)
            }
            people.remove(uid)
            Profiles.ensure(people)
        }
        registrations += Fb.userPrivate(uid).addSnapshotListener { snap, _ ->
            @Suppress("UNCHECKED_CAST")
            _blocked.value = (snap?.get("blocked") as? List<String>) ?: emptyList()
        }
    }

    private var servicesStarted = false

    /** Presence + push only once the account has a profile (rules need it). */
    private fun startServices(uid: String) {
        servicesStarted = true
        Presence.start(uid)
        scope.launch { Push.register(uid) }
    }

    private fun stopListeners() {
        registrations.forEach { it.remove() }
        registrations.clear()
        servicesStarted = false
    }

    /** Called on sign-out / account deletion. */
    fun resetData() {
        stopListeners()
        _chats.value = emptyList()
        _chatsStatus.value = LoadStatus.Idle
        _blocked.value = emptyList()
        Profiles.reset()
    }

    fun chat(chatId: String): Chat? = _chats.value.firstOrNull { it.id == chatId }

    /** Everyone I share a chat with, most recent first (derived from the chat list; no extra reads). */
    fun friends(chats: List<Chat>, me: String): List<String> {
        val seen = LinkedHashSet<String>()
        chats.forEach { c -> c.members.forEach { if (it != me) seen.add(it) } }
        return seen.toList()
    }

    /** Title for a chat row/header: group name, or the other person's display name. */
    fun titleFor(chat: Chat, me: String, profiles: Map<String, Profile>): String =
        if (chat.type == ChatType.Group) chat.name ?: "Group"
        else profiles[ChatLogic.otherMember(chat.members, me)]?.displayName ?: "…"
}
