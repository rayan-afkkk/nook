package com.nook.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure chat helpers (no Firebase, no Android) so they can be unit tested. */
object ChatLogic {
    /** Deterministic id so two people can never end up with two DMs. */
    fun directChatId(a: String, b: String): String {
        val (x, y) = listOf(a, b).sorted()
        return "dm_${x}_$y"
    }

    fun otherMember(members: List<String>, me: String): String? = members.firstOrNull { it != me }

    private fun kindWord(kind: MessageKind) = when (kind) {
        MessageKind.Image -> "Photo"
        MessageKind.File -> "File"
        MessageKind.Voice -> "Voice note"
        MessageKind.Gif -> "GIF"
        MessageKind.Sticker -> "Sticker"
        else -> ""
    }

    fun previewFor(kind: MessageKind, text: String?, fileName: String? = null): String = when {
        kind == MessageKind.Text || kind == MessageKind.System ->
            (text ?: "").replace(Regex("\\s+"), " ").trim().take(120)
        kind == MessageKind.File && !fileName.isNullOrEmpty() -> fileName.take(80)
        else -> kindWord(kind)
    }

    fun lastMessageLine(last: LastMessage?, mine: Boolean): String = when {
        last == null -> "Say hi 👋"
        last.kind == MessageKind.Deleted -> "Message deleted"
        else -> (if (mine) "You: " else "") + last.preview
    }

    fun isUnread(lastMessageAt: Long?, lastRead: Long?, lastSenderIsMe: Boolean): Boolean {
        if (lastMessageAt == null || lastMessageAt == 0L || lastSenderIsMe) return false
        return lastRead == null || lastRead == 0L || lastMessageAt > lastRead
    }

    fun isUnread(chat: Chat, me: String): Boolean =
        isUnread(chat.lastMessageAt, chat.lastRead[me], chat.lastMessage?.senderId == me)

    fun isExpired(expireAt: Long?, now: Long): Boolean = expireAt != null && expireAt <= now

    /** "Seen" shows under my last message when every other member has read past it. */
    fun seenByAll(myLast: Long?, members: List<String>, me: String, lastRead: Map<String, Long>): Boolean {
        if (myLast == null || myLast == 0L) return false
        val others = members.filter { it != me }
        return others.isNotEmpty() && others.all { (lastRead[it] ?: 0L) >= myLast }
    }

    fun sameDay(a: Long, b: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b).atZone(zone).toLocalDate()

    fun dayLabel(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        if (sameDay(ms, now, zone)) return "Today"
        if (sameDay(ms, now - 86_400_000L, zone)) return "Yesterday"
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        val sameYear = d.year == Instant.ofEpochMilli(now).atZone(zone).year
        val pattern = if (sameYear) "EEE, d MMM" else "d MMM yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale).format(d)
    }

    fun timeLabel(ms: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone)
        val pattern = when {
            sameDay(ms, now, zone) -> "h:mm a"
            now - ms < 6 * 86_400_000L -> "EEE"
            else -> "d MMM"
        }
        return DateTimeFormatter.ofPattern(pattern, locale).format(d)
    }

    fun clockLabel(ms: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("h:mm a", locale).format(Instant.ofEpochMilli(ms).atZone(zone))

    fun durationLabel(ms: Long): String {
        val total = max(0L, (ms / 1000.0).roundToInt().toLong())
        return "%d:%02d".format(total / 60, total % 60)
    }

    /** Reaction pills sorted by count. */
    fun groupReactions(reactions: Map<String, String>, me: String): List<ReactionPill> {
        val map = LinkedHashMap<String, ReactionPill>()
        for ((uid, emoji) in reactions) {
            val e = map[emoji] ?: ReactionPill(emoji, 0, false)
            map[emoji] = e.copy(count = e.count + 1, mine = e.mine || uid == me)
        }
        return map.values.sortedByDescending { it.count }
    }

    /** Resamples raw amplitudes (0..32767, MediaRecorder.getMaxAmplitude) to `n` bars in 0..1. */
    fun toWaveform(amplitudes: List<Int>, n: Int = 40): List<Float> {
        if (amplitudes.isEmpty()) return List(n) { 0.15f }
        val norm = amplitudes.map { a ->
            val db = if (a <= 0) -160.0 else 20 * kotlin.math.log10(a / 32767.0)
            min(1.0, max(0.06, (db + 55) / 55)).toFloat()
        }
        return List(n) { i ->
            val start = i * norm.size / n
            val end = max(start + 1, (i + 1) * norm.size / n).coerceAtMost(norm.size)
            val slice = norm.subList(start.coerceAtMost(norm.size - 1), end)
            ((slice.sum() / slice.size) * 100).roundToInt() / 100f
        }
    }

    /** Cloudinary on-the-fly resize for thumbnails (keeps bandwidth low). */
    fun thumbnailUrl(url: String, width: Int): String =
        if (!url.contains("/image/upload/")) url
        else url.replace("/image/upload/", "/image/upload/c_limit,w_$width,q_auto,f_auto/")
}
