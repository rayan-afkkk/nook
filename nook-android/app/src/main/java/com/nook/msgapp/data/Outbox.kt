package com.nook.msgapp.data

import android.content.Context
import com.nook.core.Disappearing
import com.nook.core.Media
import com.nook.core.MessageKind
import com.nook.core.ReplyRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A media message that is still uploading (shown as a bubble with progress, retry and cancel). */
data class OutboxItem(
    val id: String,
    val chatId: String,
    val kind: MessageKind,
    val file: PickedFile,
    val progress: Float = 0f,
    val error: String? = null,
    val replyTo: ReplyRef? = null,
    val durationMs: Long? = null,
    val waveform: List<Float>? = null,
    val width: Int? = null,
    val height: Int? = null,
)

/**
 * Uploads to Cloudinary first, then writes the message with a pre-allocated id so a retry can
 * never create a duplicate.
 */
object Outbox {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _items = MutableStateFlow<List<OutboxItem>>(emptyList())
    val items: StateFlow<List<OutboxItem>> = _items.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val disappearingFor = mutableMapOf<String, Disappearing>()

    fun enqueue(
        context: Context,
        chatId: String,
        disappearing: Disappearing,
        kind: MessageKind,
        file: PickedFile,
        replyTo: ReplyRef? = null,
        durationMs: Long? = null,
        waveform: List<Float>? = null,
        width: Int? = null,
        height: Int? = null,
    ) {
        val item = OutboxItem(Chats.newMessageId(chatId), chatId, kind, file, replyTo = replyTo, durationMs = durationMs, waveform = waveform, width = width, height = height)
        disappearingFor[item.id] = disappearing
        _items.update { it + item }
        run(context.applicationContext, item)
    }

    fun retry(context: Context, id: String) {
        val item = _items.value.firstOrNull { it.id == id } ?: return
        update(id) { it.copy(error = null, progress = 0f) }
        run(context.applicationContext, item)
    }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        disappearingFor.remove(id)
        _items.update { list -> list.filterNot { it.id == id } }
    }

    private fun update(id: String, f: (OutboxItem) -> OutboxItem) =
        _items.update { list -> list.map { if (it.id == id) f(it) else it } }

    private fun resourceType(kind: MessageKind) = when (kind) {
        MessageKind.Image, MessageKind.Sticker -> "image"
        MessageKind.Voice -> "video"
        else -> "raw"
    }

    private fun run(context: Context, item: OutboxItem) {
        jobs[item.id]?.cancel()
        jobs[item.id] = scope.launch {
            try {
                val r = Cloudinary.upload(context, item.file, resourceType(item.kind)) { p -> update(item.id) { it.copy(progress = p) } }
                val media = Media(
                    url = r.url,
                    publicId = r.publicId,
                    resourceType = r.resourceType,
                    width = r.width ?: item.width,
                    height = r.height ?: item.height,
                    size = r.bytes,
                    name = item.file.name.take(120),
                    mime = item.file.mime,
                    durationMs = item.durationMs ?: r.durationMs,
                    waveform = item.waveform,
                )
                Chats.send(
                    chatId = item.chatId,
                    disappearing = disappearingFor[item.id] ?: Disappearing.Off,
                    me = Session.myUid,
                    draft = Draft(kind = item.kind, media = media, replyTo = item.replyTo),
                    messageId = item.id,
                )
                cancel(item.id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                update(item.id) { it.copy(error = e.friendly()) }
            }
        }
    }
}
