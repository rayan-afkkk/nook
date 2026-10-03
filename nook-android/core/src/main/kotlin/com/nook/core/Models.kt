package com.nook.core

/** All timestamps are epoch milliseconds; null means "not written by the server yet". */

enum class ChatType(val wire: String) {
    Direct("direct"), Group("group");

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.wire == s } ?: Direct
    }
}

enum class Disappearing(val wire: String, val label: String, val ms: Long) {
    Off("off", "Off", 0L),
    Day("24h", "24h", 24L * 60 * 60 * 1000),
    Week("7d", "7d", 7L * 24 * 60 * 60 * 1000);

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.wire == s } ?: Off
    }
}

enum class MessageKind(val wire: String) {
    Text("text"), Image("image"), File("file"), Voice("voice"), Gif("gif"), Sticker("sticker"), System("system"),
    /** Only used in `lastMessage` after the latest message was deleted. */
    Deleted("deleted");

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.wire == s } ?: Text
    }
}

/** Media lives on Cloudinary (uploads) or Giphy (GIFs); Firestore only stores the reference. */
data class Media(
    val url: String,
    val publicId: String? = null,
    /** image | video (audio, Cloudinary's convention) | raw (files) */
    val resourceType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val size: Long? = null,
    val name: String? = null,
    val mime: String? = null,
    val durationMs: Long? = null,
    /** 0..1 amplitudes, ~40 samples, for voice notes. */
    val waveform: List<Float>? = null,
    /** Small still preview for GIFs. */
    val previewUrl: String? = null,
)

data class ReplyRef(val id: String, val senderId: String, val kind: MessageKind, val text: String)

/** chats/{chatId}/messages/{messageId} */
data class Message(
    val id: String,
    val senderId: String,
    val kind: MessageKind,
    val text: String? = null,
    val media: Media? = null,
    val replyTo: ReplyRef? = null,
    val reactions: Map<String, String> = emptyMap(),
    val forwarded: Boolean = false,
    val createdAt: Long? = null,
    val expireAt: Long? = null,
    /** Local only: write not yet acknowledged by the server. */
    val pending: Boolean = false,
)

data class LastMessage(val senderId: String, val kind: MessageKind, val preview: String)

/** chats/{chatId} */
data class Chat(
    val id: String,
    val type: ChatType,
    val members: List<String>,
    val name: String? = null,
    val photoURL: String? = null,
    val createdBy: String = "",
    val createdAt: Long? = null,
    val lastMessage: LastMessage? = null,
    val lastMessageAt: Long? = null,
    val lastRead: Map<String, Long> = emptyMap(),
    val mutedBy: List<String> = emptyList(),
    val disappearing: Disappearing = Disappearing.Off,
) {
    val isGroup get() = type == ChatType.Group
}

/** users/{uid}: public profile, readable by any signed-in user. */
data class Profile(
    val uid: String,
    val username: String,
    val displayName: String,
    val photoURL: String? = null,
    val createdAt: Long? = null,
)

enum class CallStatus(val wire: String) {
    Ringing("ringing"), Answered("answered"), Declined("declined"), Missed("missed"), Cancelled("cancelled"), Ended("ended");

    val isFinal get() = this != Ringing && this != Answered

    companion object {
        fun from(s: String?) = entries.firstOrNull { it.wire == s } ?: Ended
    }
}

/** calls/{callId}: one 1:1 call, readable by its two members. */
data class Call(
    val id: String,
    val chatId: String,
    val members: List<String>,
    val callerId: String,
    val calleeId: String,
    val video: Boolean,
    val status: CallStatus,
    val startedAt: Long? = null,
    val answeredAt: Long? = null,
    val endedAt: Long? = null,
)

data class Sticker(val url: String, val publicId: String, val addedBy: String)

/** stickerPacks/{packId}: shared with everyone on NOOK. */
data class StickerPack(val id: String, val name: String, val createdBy: String, val stickers: List<Sticker>)

data class ReactionPill(val emoji: String, val count: Int, val mine: Boolean)
