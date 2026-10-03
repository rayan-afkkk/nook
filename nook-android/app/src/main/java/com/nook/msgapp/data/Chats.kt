package com.nook.msgapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.nook.core.Chat
import com.nook.core.ChatLogic
import com.nook.core.Disappearing
import com.nook.core.Limits
import com.nook.core.Media
import com.nook.core.Message
import com.nook.core.MessageKind
import com.nook.core.ReplyRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class Draft(
    val kind: MessageKind,
    val text: String? = null,
    val media: Media? = null,
    val replyTo: ReplyRef? = null,
    val forwarded: Boolean = false,
)

/** Chat and message reads/writes. Firestore shows writes instantly and queues them while offline. */
object Chats {
    /** The single chat-list query for the whole app (served from cache on reopen). */
    fun listQuery(uid: String): Query = Fb.chats()
        .whereArrayContains("members", uid)
        .orderBy("lastMessageAt", Query.Direction.DESCENDING)
        .limit(100)

    /** Latest `count` messages, newest first (the list is drawn bottom-up). */
    fun messagesFlow(chatId: String, count: Long): Flow<List<Message>> =
        Fb.messages(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(count)
            .flow(includeMetadata = true)
            .map { snap ->
                val now = System.currentTimeMillis()
                snap.documents.mapNotNull { it.toMessage() }.filterNot { ChatLogic.isExpired(it.expireAt, now) }
            }

    fun chatFlow(chatId: String): Flow<Chat?> = Fb.chats().document(chatId).flow().map { it.toChat() }

    /** Opens (or creates) the one DM between two people. */
    suspend fun openDirect(me: String, other: String, disappearing: Disappearing): String {
        if (me == other) throw UserFacingError("You can't message yourself.")
        val id = ChatLogic.directChatId(me, other)
        val ref = Fb.chats().document(id)
        Fb.db.runTransaction<Void?> { tx ->
            if (tx.get(ref).exists()) return@runTransaction null
            tx.set(
                ref,
                mapOf(
                    "type" to "direct",
                    "members" to listOf(me, other).sorted(),
                    "createdBy" to me,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastMessage" to null,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastRead" to mapOf(me to FieldValue.serverTimestamp()),
                    "mutedBy" to emptyList<String>(),
                    "disappearing" to disappearing.wire,
                ),
            )
            null
        }.await()
        return id
    }

    suspend fun createGroup(me: String, name: String, memberUids: List<String>, disappearing: Disappearing): String {
        val clean = name.trim()
        if (clean.isEmpty()) throw UserFacingError("Give the group a name.")
        val members = (listOf(me) + memberUids).distinct()
        if (members.size < 2) throw UserFacingError("Add at least one friend.")
        if (members.size > Limits.MAX_GROUP_MEMBERS) throw UserFacingError("Groups can have up to ${Limits.MAX_GROUP_MEMBERS} people.")
        val ref = Fb.chats().document()
        ref.set(
            mapOf(
                "type" to "group",
                "name" to clean.take(40),
                "photoURL" to null,
                "members" to members,
                "createdBy" to me,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastMessage" to mapOf("senderId" to me, "kind" to "system", "preview" to "Group created"),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastRead" to mapOf(me to FieldValue.serverTimestamp()),
                "mutedBy" to emptyList<String>(),
                "disappearing" to disappearing.wire,
            ),
        ).await()
        return ref.id
    }

    /** Pre-allocates a message id (used by the media outbox so retries don't duplicate). */
    fun newMessageId(chatId: String): String = Fb.messages(chatId).document().id

    /**
     * Writes the message and the chat's "last message" in one batch, then asks the Worker to push.
     * Returns once the batch is queued locally; the server commit happens in the background.
     */
    suspend fun send(chatId: String, disappearing: Disappearing, me: String, draft: Draft, messageId: String? = null): String {
        val id = messageId ?: newMessageId(chatId)
        val message = mutableMapOf<String, Any>(
            "senderId" to me,
            "kind" to draft.kind.wire,
            "createdAt" to FieldValue.serverTimestamp(),
            "reactions" to emptyMap<String, String>(),
        )
        draft.text?.takeIf { it.isNotEmpty() }?.let { message["text"] = it.take(Limits.MAX_TEXT) }
        draft.media?.let { message["media"] = it.toMap() }
        draft.replyTo?.let {
            message["replyTo"] = mapOf("id" to it.id, "senderId" to it.senderId, "kind" to it.kind.wire, "text" to it.text.take(200))
        }
        if (draft.forwarded) message["forwarded"] = true
        if (disappearing.ms > 0) message["expireAt"] = (System.currentTimeMillis() + disappearing.ms).toTimestamp()

        val batch = Fb.db.batch()
        batch.set(Fb.messages(chatId).document(id), message)
        batch.update(
            Fb.chats().document(chatId),
            mapOf(
                "lastMessage" to mapOf(
                    "senderId" to me,
                    "kind" to draft.kind.wire,
                    "preview" to ChatLogic.previewFor(draft.kind, draft.text, draft.media?.name),
                ),
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastRead.$me" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().addOnSuccessListener {
            Worker.callInBackground("/notify", mapOf("chatId" to chatId, "messageId" to id))
        }
        return id
    }

    suspend fun setReaction(chatId: String, messageId: String, me: String, emoji: String?) {
        Fb.messages(chatId).document(messageId).update("reactions.$me", emoji ?: FieldValue.delete()).await()
    }

    /** Deletes for everyone. Media messages go through the Worker so the Cloudinary file is removed too. */
    suspend fun deleteMessages(chatId: String, me: String, messages: List<Message>, latestId: String? = null) {
        val withMedia = messages.filter { !it.media?.publicId.isNullOrEmpty() }
        val plain = messages.filter { it.media?.publicId.isNullOrEmpty() }.toMutableList()
        if (withMedia.isNotEmpty()) {
            if (AppConfig.workerConfigured) {
                Worker.call("/messages/delete", mapOf("chatId" to chatId, "messageIds" to withMedia.map { it.id }))
            } else {
                plain += withMedia
            }
        }
        if (plain.isNotEmpty()) {
            val batch = Fb.db.batch()
            plain.forEach { batch.delete(Fb.messages(chatId).document(it.id)) }
            batch.commit().await()
        }
        if (latestId != null && messages.any { it.id == latestId }) {
            runCatching {
                Fb.chats().document(chatId)
                    .update("lastMessage", mapOf("senderId" to me, "kind" to "deleted", "preview" to ""))
                    .await()
            }
        }
    }

    /** One small write when a chat is opened or scrolled to the bottom (read receipts + unread badges). */
    fun markRead(chatId: String, me: String) {
        Fb.chats().document(chatId).update("lastRead.$me", FieldValue.serverTimestamp())
    }

    suspend fun setDisappearing(chatId: String, value: Disappearing) {
        Fb.chats().document(chatId).update("disappearing", value.wire).await()
    }

    suspend fun setMuted(chatId: String, me: String, muted: Boolean) {
        Fb.chats().document(chatId).update("mutedBy", if (muted) FieldValue.arrayUnion(me) else FieldValue.arrayRemove(me)).await()
    }

    suspend fun renameGroup(chatId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) throw UserFacingError("Give the group a name.")
        Fb.chats().document(chatId).update("name", clean.take(40)).await()
    }

    suspend fun setGroupPhoto(chatId: String, url: String?) {
        Fb.chats().document(chatId).update("photoURL", url).await()
    }

    suspend fun addMembers(chat: Chat, uids: List<String>) {
        if ((chat.members + uids).distinct().size > Limits.MAX_GROUP_MEMBERS) {
            throw UserFacingError("Groups can have up to ${Limits.MAX_GROUP_MEMBERS} people.")
        }
        Fb.chats().document(chat.id).update("members", FieldValue.arrayUnion(*uids.toTypedArray())).await()
    }

    suspend fun leaveGroup(chatId: String, me: String) {
        Fb.chats().document(chatId).update("members", FieldValue.arrayRemove(me)).await()
    }

    /**
     * Disappearing messages: any member's app deletes a small batch of expired messages when the chat
     * opens (no Cloud Functions or TTL needed).
     */
    suspend fun cleanupExpired(chatId: String, me: String): Int {
        val snap = Fb.messages(chatId).whereLessThanOrEqualTo("expireAt", Timestamp.now()).limit(25).get().await()
        if (snap.isEmpty) return 0
        val expired = snap.documents.mapNotNull { it.toMessage() }
        deleteMessages(chatId, me, expired)
        return expired.size
    }
}
