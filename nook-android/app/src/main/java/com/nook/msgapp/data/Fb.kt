package com.nook.msgapp.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.nook.core.Call
import com.nook.core.CallStatus
import com.nook.core.Chat
import com.nook.core.ChatType
import com.nook.core.Disappearing
import com.nook.core.LastMessage
import com.nook.core.Media
import com.nook.core.Message
import com.nook.core.MessageKind
import com.nook.core.Profile
import com.nook.core.ReplyRef
import com.nook.core.Sticker
import com.nook.core.StickerPack
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

/** Firebase singletons. */
object Fb {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    val rtdb: FirebaseDatabase get() = FirebaseDatabase.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    fun users() = db.collection("users")
    fun usernames() = db.collection("usernames")
    fun chats() = db.collection("chats")
    fun messages(chatId: String) = chats().document(chatId).collection("messages")
    fun calls() = db.collection("calls")
    fun userPrivate(uid: String) = db.collection("userPrivate").document(uid)
    fun stickerPacks() = db.collection("stickerPacks")
}

/** A message the user can read (shown in a toast / inline error). */
class UserFacingError(message: String) : Exception(message)

fun Throwable.friendly(): String {
    // Errors thrown inside a Firestore transaction can come back wrapped.
    val e = if (this !is UserFacingError && cause is UserFacingError) cause!! else this
    return when (e) {
        is UserFacingError -> e.message ?: "Something went wrong."
        is WorkerError -> e.message ?: "Server error."
        is com.google.firebase.firestore.FirebaseFirestoreException -> when (e.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "You don't have access to that."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> "You're offline. It'll sync when you're back."
            else -> "Something went wrong. Please try again."
        }
        is java.io.IOException -> "Network problem. Check your connection and retry."
        else -> e.message?.takeIf { it.length < 140 } ?: "Something went wrong. Please try again."
    }
}

/* ------------------------------------------------------------------ flows */

/** Live query as a Flow (listener removed when the collector stops). */
fun Query.flow(includeMetadata: Boolean = false): Flow<QuerySnapshot> = callbackFlow {
    val reg = addSnapshotListener(if (includeMetadata) MetadataChanges.INCLUDE else MetadataChanges.EXCLUDE) { snap, err ->
        if (err != null) close(err) else if (snap != null) trySend(snap)
    }
    awaitClose { reg.remove() }
}

fun DocumentReference.flow(): Flow<DocumentSnapshot> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) close(err) else if (snap != null) trySend(snap)
    }
    awaitClose { reg.remove() }
}

/* ------------------------------------------------------------------ mapping */

fun Long.toTimestamp() = Timestamp(Date(this))

private fun DocumentSnapshot.ms(field: String, estimate: Boolean = true): Long? =
    (if (estimate) getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) else getTimestamp(field))
        ?.toDate()?.time

private fun Any?.asTimestampMs(): Long? = (this as? Timestamp)?.toDate()?.time

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

@Suppress("UNCHECKED_CAST")
private fun Any?.asStringList(): List<String> = (this as? List<*>)?.filterIsInstance<String>() ?: emptyList()

private fun Any?.asLong(): Long? = (this as? Number)?.toLong()

fun mediaFrom(m: Map<String, Any?>?): Media? {
    val url = m?.get("url") as? String ?: return null
    return Media(
        url = url,
        publicId = m["publicId"] as? String,
        resourceType = m["resourceType"] as? String,
        width = m["width"].asLong()?.toInt(),
        height = m["height"].asLong()?.toInt(),
        size = m["size"].asLong(),
        name = m["name"] as? String,
        mime = m["mime"] as? String,
        durationMs = m["durationMs"].asLong(),
        waveform = (m["waveform"] as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() },
        previewUrl = m["previewUrl"] as? String,
    )
}

fun Media.toMap(): Map<String, Any> = buildMap {
    put("url", url)
    publicId?.let { put("publicId", it) }
    resourceType?.let { put("resourceType", it) }
    width?.let { put("width", it) }
    height?.let { put("height", it) }
    size?.let { put("size", it) }
    name?.let { put("name", it) }
    mime?.let { put("mime", it) }
    durationMs?.let { put("durationMs", it) }
    waveform?.let { put("waveform", it.map { v -> v.toDouble() }) }
    previewUrl?.let { put("previewUrl", it) }
}

fun DocumentSnapshot.toProfile(): Profile? {
    if (!exists()) return null
    return Profile(
        uid = getString("uid") ?: id,
        username = getString("username") ?: return null,
        displayName = getString("displayName") ?: getString("username") ?: "",
        photoURL = getString("photoURL"),
        createdAt = ms("createdAt"),
    )
}

fun DocumentSnapshot.toChat(): Chat? {
    if (!exists()) return null
    val last = get("lastMessage").asMap()?.let {
        LastMessage(
            senderId = it["senderId"] as? String ?: "",
            kind = MessageKind.from(it["kind"] as? String),
            preview = it["preview"] as? String ?: "",
        )
    }
    val lastRead = get("lastRead").asMap()?.mapNotNull { (k, v) -> v.asTimestampMs()?.let { k to it } }?.toMap() ?: emptyMap()
    return Chat(
        id = id,
        type = ChatType.from(getString("type")),
        members = get("members").asStringList(),
        name = getString("name"),
        photoURL = getString("photoURL"),
        createdBy = getString("createdBy") ?: "",
        createdAt = ms("createdAt"),
        lastMessage = last,
        lastMessageAt = ms("lastMessageAt"),
        lastRead = lastRead,
        mutedBy = get("mutedBy").asStringList(),
        disappearing = Disappearing.from(getString("disappearing")),
    )
}

fun DocumentSnapshot.toMessage(): Message? {
    if (!exists()) return null
    val reply = get("replyTo").asMap()?.let {
        ReplyRef(
            id = it["id"] as? String ?: "",
            senderId = it["senderId"] as? String ?: "",
            kind = MessageKind.from(it["kind"] as? String),
            text = it["text"] as? String ?: "",
        )
    }
    @Suppress("UNCHECKED_CAST")
    val reactions = (get("reactions") as? Map<String, Any?>)?.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }?.toMap() ?: emptyMap()
    return Message(
        id = id,
        senderId = getString("senderId") ?: "",
        kind = MessageKind.from(getString("kind")),
        text = getString("text"),
        media = mediaFrom(get("media").asMap()),
        replyTo = reply,
        reactions = reactions,
        forwarded = getBoolean("forwarded") == true,
        createdAt = ms("createdAt"),
        expireAt = ms("expireAt", estimate = false),
        pending = metadata.hasPendingWrites(),
    )
}

fun DocumentSnapshot.toCall(): Call? {
    if (!exists()) return null
    return Call(
        id = id,
        chatId = getString("chatId") ?: "",
        members = get("members").asStringList(),
        callerId = getString("callerId") ?: "",
        calleeId = getString("calleeId") ?: "",
        video = getBoolean("video") == true,
        status = CallStatus.from(getString("status")),
        startedAt = ms("startedAt"),
        answeredAt = ms("answeredAt"),
        endedAt = ms("endedAt"),
    )
}

fun DocumentSnapshot.toStickerPack(): StickerPack? {
    if (!exists()) return null
    val stickers = (get("stickers") as? List<*>)?.mapNotNull { s ->
        val m = s.asMap() ?: return@mapNotNull null
        val url = m["url"] as? String ?: return@mapNotNull null
        Sticker(url, m["publicId"] as? String ?: "", m["addedBy"] as? String ?: "")
    } ?: emptyList()
    return StickerPack(id, getString("name") ?: "Stickers", getString("createdBy") ?: "", stickers)
}
