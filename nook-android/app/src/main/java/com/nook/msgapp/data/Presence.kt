package com.nook.msgapp.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class PresenceState(val online: Boolean, val lastChanged: Long?)

/**
 * Presence and typing live in the Realtime Database (free, no Firestore quota):
 *   status/{uid}            { state: 'online' | 'offline', lastChanged }
 *   typing/{chatId}/{uid}   server timestamp while typing
 * onDisconnect() marks people offline even if the app is killed. The connection is closed while
 * NOOK is in the background, which keeps us far below Spark's 100 simultaneous connections.
 */
object Presence {
    private var connectedListener: ValueEventListener? = null
    private var uid: String? = null

    private fun offline() = mapOf("state" to "offline", "lastChanged" to ServerValue.TIMESTAMP)
    private fun online() = mapOf("state" to "online", "lastChanged" to ServerValue.TIMESTAMP)

    fun start(uid: String) {
        stopListening()
        this.uid = uid
        val db = Fb.rtdb
        val statusRef = db.getReference("status/$uid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                statusRef.onDisconnect().setValue(offline()).addOnSuccessListener { statusRef.setValue(online()) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        db.getReference(".info/connected").addValueEventListener(listener)
        connectedListener = listener
    }

    /** App came to the foreground. */
    fun resume() {
        if (uid != null) Fb.rtdb.goOnline()
    }

    /** App went to the background: mark offline and drop the connection. */
    fun pause() {
        val u = uid ?: return
        Fb.rtdb.getReference("status/$u").setValue(offline()).addOnCompleteListener { Fb.rtdb.goOffline() }
    }

    private fun stopListening() {
        connectedListener?.let { Fb.rtdb.getReference(".info/connected").removeEventListener(it) }
        connectedListener = null
    }

    fun stop() {
        val u = uid
        stopListening()
        uid = null
        if (u != null) runCatching { Fb.rtdb.getReference("status/$u").setValue(offline()) }
    }

    /** Live online status for one person. */
    fun flow(uid: String): Flow<PresenceState> = callbackFlow {
        val ref = Fb.rtdb.getReference("status/$uid")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.child("state").getValue(String::class.java)
                val last = snapshot.child("lastChanged").getValue(Long::class.java)
                trySend(PresenceState(state == "online", last))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(PresenceState(false, null))
            }
        }
        ref.addValueEventListener(l)
        awaitClose { ref.removeEventListener(l) }
    }.distinctUntilChanged()

    /* ---------------- typing ---------------- */

    private const val TYPING_TTL_MS = 6000L
    private const val TYPING_THROTTLE_MS = 3000L
    private var lastTypingWrite = 0L
    private var typingChat: String? = null

    /** Throttled: at most one write every 3 seconds while typing. */
    fun setTyping(chatId: String, uid: String, typing: Boolean) {
        val ref = Fb.rtdb.getReference("typing/$chatId/$uid")
        if (!typing) {
            if (typingChat == chatId) {
                typingChat = null
                lastTypingWrite = 0
                ref.removeValue()
            }
            return
        }
        val now = System.currentTimeMillis()
        if (typingChat == chatId && now - lastTypingWrite < TYPING_THROTTLE_MS) return
        lastTypingWrite = now
        typingChat = chatId
        ref.onDisconnect().removeValue()
        ref.setValue(ServerValue.TIMESTAMP)
    }

    /** uids currently typing in a chat (excluding me). */
    fun typingFlow(chatId: String, me: String): Flow<List<String>> = callbackFlow {
        var entries: Map<String, Long> = emptyMap()
        fun compute() {
            val now = System.currentTimeMillis()
            // Server timestamps can be slightly ahead of the phone clock; treat those as fresh.
            trySend(entries.filter { (uid, at) -> uid != me && now - at < TYPING_TTL_MS }.keys.sorted())
        }
        val ref = Fb.rtdb.getReference("typing/$chatId")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                entries = snapshot.children.mapNotNull { c -> c.key?.let { k -> c.getValue(Long::class.java)?.let { k to it } } }.toMap()
                compute()
            }

            override fun onCancelled(error: DatabaseError) {
                entries = emptyMap()
                compute()
            }
        }
        ref.addValueEventListener(l)
        // Expire stale entries even when no new event arrives.
        val ticker = launch {
            while (true) {
                delay(2000)
                compute()
            }
        }
        awaitClose {
            ticker.cancel()
            ref.removeEventListener(l)
        }
    }.distinctUntilChanged()
}
