package com.nook.msgapp.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.nook.core.Call
import com.nook.core.CallStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

object Calls {
    /** Creates the call record and asks the Worker to ring the other person (high-priority push). */
    suspend fun start(chatId: String, me: String, other: String, video: Boolean): String {
        val ref = Fb.calls().document()
        ref.set(
            mapOf(
                "chatId" to chatId,
                "members" to listOf(me, other).sorted(),
                "callerId" to me,
                "calleeId" to other,
                "video" to video,
                "status" to "ringing",
                "startedAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        Worker.callInBackground("/call/invite", mapOf("callId" to ref.id))
        return ref.id
    }

    suspend fun setStatus(callId: String, status: CallStatus) {
        val patch = mutableMapOf<String, Any>("status" to status.wire)
        if (status == CallStatus.Answered) patch["answeredAt"] = FieldValue.serverTimestamp()
        if (status.isFinal) patch["endedAt"] = FieldValue.serverTimestamp()
        Fb.calls().document(callId).update(patch).await()
        // Stop the other phone ringing.
        if (status == CallStatus.Cancelled || status == CallStatus.Missed || status == CallStatus.Declined) {
            Worker.callInBackground("/call/cancel", mapOf("callId" to callId))
        }
    }

    suspend fun token(callId: String): Pair<String, String> {
        val res = Worker.call("/call/token", mapOf("callId" to callId))
        return res.getString("token") to res.getString("url")
    }

    fun callFlow(callId: String): Flow<Call?> = Fb.calls().document(callId).flow().map { it.toCall() }

    suspend fun get(callId: String): Call? = Fb.calls().document(callId).get().await().toCall()

    /** Call history, listened to only while the Calls tab is visible. */
    fun historyFlow(me: String): Flow<List<Call>> = Fb.calls()
        .whereArrayContains("members", me)
        .orderBy("startedAt", Query.Direction.DESCENDING)
        .limit(50)
        .flow()
        .map { snap -> snap.documents.mapNotNull { it.toCall() } }
}
