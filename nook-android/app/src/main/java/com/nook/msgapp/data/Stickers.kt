package com.nook.msgapp.data

import android.content.Context
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.nook.core.Limits
import com.nook.core.StickerPack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

object Stickers {
    /** Live list of packs, only while a sticker screen or panel is open. */
    fun packsFlow(): Flow<List<StickerPack>> = Fb.stickerPacks()
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(50)
        .flow()
        .map { snap -> snap.documents.mapNotNull { it.toStickerPack() } }

    private suspend fun upload(context: Context, file: PickedFile, onProgress: (Float) -> Unit): Pair<String, String> {
        val r = Cloudinary.upload(context, file, "image", onProgress)
        return r.url to r.publicId
    }

    suspend fun createPack(context: Context, me: String, name: String, file: PickedFile, onProgress: (Float) -> Unit = {}) {
        val clean = name.trim().take(30)
        if (clean.isEmpty()) throw UserFacingError("Name the pack.")
        val (url, publicId) = upload(context, file, onProgress)
        Fb.stickerPacks().add(
            mapOf(
                "name" to clean,
                "createdBy" to me,
                "createdAt" to FieldValue.serverTimestamp(),
                "stickers" to listOf(mapOf("url" to url, "publicId" to publicId, "addedBy" to me)),
            ),
        ).await()
    }

    suspend fun addSticker(context: Context, me: String, pack: StickerPack, file: PickedFile, onProgress: (Float) -> Unit = {}) {
        if (pack.stickers.size >= Limits.MAX_STICKERS_PER_PACK) throw UserFacingError("That pack is full. Start a new one.")
        val (url, publicId) = upload(context, file, onProgress)
        Fb.stickerPacks().document(pack.id)
            .update("stickers", FieldValue.arrayUnion(mapOf("url" to url, "publicId" to publicId, "addedBy" to me)))
            .await()
    }

    suspend fun deletePack(packId: String) {
        Fb.stickerPacks().document(packId).delete().await()
    }
}
