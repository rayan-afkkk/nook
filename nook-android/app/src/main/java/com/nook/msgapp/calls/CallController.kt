package com.nook.msgapp.calls

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nook.core.Call
import com.nook.core.CallStatus
import com.nook.core.Limits
import com.nook.msgapp.data.Calls
import com.nook.msgapp.data.Session
import com.nook.msgapp.data.friendly
import com.nook.msgapp.push.Notifications
import com.nook.msgapp.push.Ringer
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.LiveKit
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallPhase { Idle, Incoming, Calling, Connecting, Active, Reconnecting, Ended }

data class CallUiState(
    val callId: String? = null,
    val call: Call? = null,
    val phase: CallPhase = CallPhase.Idle,
    val outgoing: Boolean = false,
    val video: Boolean = false,
    val micOn: Boolean = true,
    val cameraOn: Boolean = false,
    val frontCamera: Boolean = true,
    val speakerOn: Boolean = false,
    val remoteJoined: Boolean = false,
    val remoteVideo: VideoTrack? = null,
    val localVideo: VideoTrack? = null,
    val remoteMuted: Boolean = false,
    val connectedAt: Long? = null,
    /** Shown on the end screen: "Declined", "No answer", "Call ended", or an error. */
    val endReason: String? = null,
)

/**
 * One 1:1 call at a time. Firestore holds the call record (ringing → answered → ended);
 * LiveKit carries the audio/video. A foreground service keeps the call alive in the background.
 */
object CallController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    var room: Room? = null
        private set
    private var jobs = mutableListOf<Job>()
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isBusy(): Boolean = _state.value.phase.let { it != CallPhase.Idle && it != CallPhase.Ended }

    /* ---------------------------------------------------------------- entry points */

    /** I'm calling someone from a DM. */
    fun startOutgoing(chatId: String, other: String, video: Boolean) {
        if (isBusy()) return
        reset()
        _state.value = CallUiState(phase = CallPhase.Calling, outgoing = true, video = video, cameraOn = video, speakerOn = video)
        jobs += scope.launch {
            try {
                val callId = Calls.start(chatId, Session.myUid, other, video)
                _state.update { it.copy(callId = callId) }
                watchCall(callId)
                // Join the room right away so audio is ready the moment they answer.
                join(callId, video)
                jobs += scope.launch {
                    delay(Limits.CALL_RING_TIMEOUT_MS)
                    if (_state.value.phase == CallPhase.Calling || (_state.value.phase == CallPhase.Active && !_state.value.remoteJoined && _state.value.call?.status == CallStatus.Ringing)) {
                        runCatching { Calls.setStatus(callId, CallStatus.Missed) }
                        finish("No answer")
                    }
                }
            } catch (e: Exception) {
                finish(e.friendly())
            }
        }
    }

    /** A call is ringing on this phone (push while open, or the full-screen notification). */
    fun showIncoming(callId: String, acceptNow: Boolean) {
        if (_state.value.callId == callId && isBusy()) {
            if (acceptNow && _state.value.phase == CallPhase.Incoming) accept()
            return
        }
        if (isBusy()) return
        reset()
        _state.value = CallUiState(callId = callId, phase = CallPhase.Incoming)
        Notifications.dismissCall(appContext, callId)
        jobs += scope.launch {
            val call = runCatching { Calls.get(callId) }.getOrNull()
            if (call == null || call.status != CallStatus.Ringing || call.calleeId != Session.myUid) {
                finish(if (call?.status == CallStatus.Cancelled) "Missed call" else "Call ended")
                return@launch
            }
            _state.update { it.copy(call = call, video = call.video, cameraOn = call.video, speakerOn = call.video) }
            watchCall(callId)
            if (acceptNow) accept() else Ringer.start(appContext)
        }
    }

    fun accept() {
        val s = _state.value
        val callId = s.callId ?: return
        if (s.phase != CallPhase.Incoming) return
        Ringer.stop()
        _state.update { it.copy(phase = CallPhase.Connecting) }
        jobs += scope.launch {
            try {
                Calls.setStatus(callId, CallStatus.Answered)
                join(callId, s.video)
            } catch (e: Exception) {
                finish(e.friendly())
            }
        }
    }

    fun decline() {
        val callId = _state.value.callId ?: return
        Ringer.stop()
        scope.launch { runCatching { Calls.setStatus(callId, CallStatus.Declined) } }
        finish("Declined")
    }

    fun hangUp() {
        val s = _state.value
        val callId = s.callId
        if (callId != null) {
            val status = if (s.outgoing && !s.remoteJoined && s.call?.status != CallStatus.Answered) CallStatus.Cancelled else CallStatus.Ended
            scope.launch { runCatching { Calls.setStatus(callId, status) } }
        }
        finish("Call ended")
    }

    /** The caller hung up before I answered (push from the Worker). */
    fun remoteCancelled(callId: String) {
        scope.launch {
            if (_state.value.callId == callId && _state.value.phase == CallPhase.Incoming) finish("Missed call")
        }
    }

    /** The end screen was dismissed. */
    fun clear() {
        if (_state.value.phase == CallPhase.Ended) _state.value = CallUiState()
    }

    /* ---------------------------------------------------------------- controls */

    fun toggleMic() {
        val r = room ?: return
        val on = !_state.value.micOn
        _state.update { it.copy(micOn = on) }
        scope.launch { runCatching { r.localParticipant.setMicrophoneEnabled(on) } }
    }

    fun toggleCamera() {
        val r = room ?: return
        val on = !_state.value.cameraOn
        _state.update { it.copy(cameraOn = on) }
        scope.launch {
            runCatching { r.localParticipant.setCameraEnabled(on) }
            refreshLocalVideo()
        }
    }

    fun flipCamera() {
        val track = _state.value.localVideo as? LocalVideoTrack ?: return
        val front = !_state.value.frontCamera
        runCatching { track.switchCamera(position = if (front) CameraPosition.FRONT else CameraPosition.BACK) }
        _state.update { it.copy(frontCamera = front) }
    }

    fun toggleSpeaker() {
        val on = !_state.value.speakerOn
        _state.update { it.copy(speakerOn = on) }
        applySpeaker(on)
    }

    private fun applySpeaker(on: Boolean) {
        val handler = room?.audioHandler as? AudioSwitchHandler ?: return
        val devices = handler.availableAudioDevices
        val target = if (on) devices.firstOrNull { it is AudioDevice.Speakerphone }
        else devices.firstOrNull { it is AudioDevice.BluetoothHeadset || it is AudioDevice.WiredHeadset }
            ?: devices.firstOrNull { it is AudioDevice.Earpiece }
        if (target != null) handler.selectDevice(target)
    }

    /** Mic/camera permission was just granted: (re)publish the tracks. */
    fun permissionsGranted() {
        val r = room ?: return
        val s = _state.value
        scope.launch {
            runCatching { r.localParticipant.setMicrophoneEnabled(s.micOn) }
            if (s.cameraOn) runCatching { r.localParticipant.setCameraEnabled(true) }
            refreshLocalVideo()
            startService()
        }
    }

    /* ---------------------------------------------------------------- internals */

    private fun watchCall(callId: String) {
        jobs += scope.launch {
            runCatching {
                Calls.callFlow(callId).collect { call ->
                    if (call == null) return@collect
                    _state.update { it.copy(call = call) }
                    when (call.status) {
                        CallStatus.Answered -> if (_state.value.phase == CallPhase.Calling) {
                            _state.update { it.copy(phase = if (room != null) CallPhase.Active else CallPhase.Connecting) }
                        }
                        CallStatus.Declined -> if (_state.value.outgoing) finish("Declined")
                        CallStatus.Cancelled -> if (!_state.value.outgoing) finish("Missed call")
                        CallStatus.Missed -> finish(if (_state.value.outgoing) "No answer" else "Missed call")
                        CallStatus.Ended -> finish("Call ended")
                        CallStatus.Ringing -> Unit
                    }
                }
            }
        }
    }

    private suspend fun join(callId: String, video: Boolean) {
        val (token, url) = Calls.token(callId)
        if (!isBusy()) return
        val r = LiveKit.create(appContext)
        room = r
        jobs += scope.launch {
            r.events.collect { e ->
                when (e) {
                    is RoomEvent.ParticipantConnected -> _state.update { it.copy(remoteJoined = true, connectedAt = it.connectedAt ?: System.currentTimeMillis(), phase = CallPhase.Active) }
                    is RoomEvent.ParticipantDisconnected -> if (r.remoteParticipants.isEmpty()) {
                        // The other person hung up or lost connection after joining.
                        _state.value.callId?.let { id -> scope.launch { runCatching { Calls.setStatus(id, CallStatus.Ended) } } }
                        finish("Call ended")
                    }
                    is RoomEvent.TrackSubscribed -> {
                        val t = e.track
                        if (t is VideoTrack) _state.update { it.copy(remoteVideo = t) }
                        _state.update { it.copy(remoteJoined = true, connectedAt = it.connectedAt ?: System.currentTimeMillis(), phase = CallPhase.Active) }
                    }
                    is RoomEvent.TrackUnsubscribed -> if (e.track is VideoTrack) _state.update { it.copy(remoteVideo = null) }
                    is RoomEvent.TrackMuted -> if (e.participant != r.localParticipant && e.publication.kind == Track.Kind.AUDIO) _state.update { it.copy(remoteMuted = true) }
                    is RoomEvent.TrackUnmuted -> if (e.participant != r.localParticipant && e.publication.kind == Track.Kind.AUDIO) _state.update { it.copy(remoteMuted = false) }
                    is RoomEvent.Reconnecting -> _state.update { it.copy(phase = CallPhase.Reconnecting) }
                    is RoomEvent.Reconnected -> _state.update { it.copy(phase = CallPhase.Active) }
                    is RoomEvent.Disconnected -> if (isBusy()) finish("Call ended")
                    else -> Unit
                }
            }
        }
        r.connect(url, token)
        startService()
        runCatching { r.localParticipant.setMicrophoneEnabled(_state.value.micOn) }
        if (video) runCatching { r.localParticipant.setCameraEnabled(true) }
        refreshLocalVideo()
        applySpeaker(_state.value.speakerOn)
        val alreadyThere = r.remoteParticipants.isNotEmpty()
        _state.update {
            it.copy(
                phase = if (it.outgoing && !alreadyThere && it.call?.status != CallStatus.Answered) CallPhase.Calling else CallPhase.Active,
                remoteJoined = it.remoteJoined || alreadyThere,
                connectedAt = if (alreadyThere) (it.connectedAt ?: System.currentTimeMillis()) else it.connectedAt,
            )
        }
    }

    private fun refreshLocalVideo() {
        val r = room ?: return
        val track = r.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        _state.update { it.copy(localVideo = if (it.cameraOn) track else null) }
    }

    private fun startService() {
        // A microphone foreground service may only start once the permission is granted.
        if (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val s = _state.value
        val name = s.call?.let { c -> com.nook.msgapp.data.Profiles.get(if (c.callerId == Session.myUid) c.calleeId else c.callerId)?.displayName } ?: "NOOK call"
        val intent = Intent(appContext, CallService::class.java).putExtra(CallService.EXTRA_NAME, name).putExtra(CallService.EXTRA_VIDEO, s.video)
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
    }

    private fun finish(reason: String) {
        Ringer.stop()
        _state.value.callId?.let { Notifications.dismissCall(appContext, it) }
        val r = room
        room = null
        jobs.forEach { it.cancel() }
        jobs = mutableListOf()
        if (r != null) {
            runCatching { r.disconnect() }
            runCatching { r.release() }
        }
        runCatching { appContext.stopService(Intent(appContext, CallService::class.java)) }
        _state.update { it.copy(phase = CallPhase.Ended, endReason = reason, remoteVideo = null, localVideo = null) }
    }

    private fun reset() {
        jobs.forEach { it.cancel() }
        jobs = mutableListOf()
        room = null
        _state.value = CallUiState()
    }
}
