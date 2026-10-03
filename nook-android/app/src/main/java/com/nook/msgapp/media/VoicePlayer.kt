package com.nook.msgapp.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.nook.msgapp.data.Toasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One shared player for voice notes: starting one stops whatever was playing before.
 * Call from the main thread.
 */
object VoicePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var prepared = false
    private var ticker: Job? = null

    /** Message id of the loaded voice note (playing, paused or loading), or null. */
    private val _currentId = MutableStateFlow<String?>(null)
    val currentId: StateFlow<String?> = _currentId.asStateFlow()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    /** Plays (or resumes) the voice note of message [id]. */
    fun play(id: String, url: String) {
        val current = player
        if (_currentId.value == id && current != null) {
            if (prepared && !current.isPlaying) {
                runCatching {
                    current.start()
                    applySpeed(current)
                }
                _playing.value = true
                startTicker()
            }
            return
        }
        stop()
        val mp = MediaPlayer()
        player = mp
        prepared = false
        _currentId.value = id
        _loading.value = true
        _positionMs.value = 0L
        _durationMs.value = 0L
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener { p ->
                if (player !== p) return@setOnPreparedListener
                prepared = true
                _loading.value = false
                _durationMs.value = p.duration.toLong().coerceAtLeast(0L)
                p.start()
                applySpeed(p)
                _playing.value = true
                startTicker()
            }
            mp.setOnCompletionListener { p -> if (player === p) stop() }
            mp.setOnErrorListener { p, _, _ ->
                if (player === p) {
                    stop()
                    Toasts.show("Couldn't play that voice note.")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stop()
            Toasts.show("Couldn't play that voice note.")
        }
    }

    fun pause() {
        val p = player ?: return
        if (prepared && p.isPlaying) runCatching { p.pause() }
        _playing.value = false
        ticker?.cancel()
        _positionMs.value = runCatching { p.currentPosition.toLong() }.getOrDefault(_positionMs.value)
    }

    fun toggle(id: String, url: String) {
        if (_currentId.value == id && _playing.value) pause() else play(id, url)
    }

    /** Seeks the loaded voice note; ignored for other messages. */
    fun seek(id: String, ms: Long) {
        val p = player ?: return
        if (_currentId.value != id || !prepared) return
        val target = ms.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        runCatching { p.seekTo(target.toInt()) }
        _positionMs.value = target
    }

    /** 1x, 1.5x, 2x... applied now if playing, otherwise on the next start. */
    fun setSpeed(value: Float) {
        _speed.value = value
        val p = player ?: return
        if (prepared && p.isPlaying) applySpeed(p)
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(_speed.value) }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        val p = player
        player = null
        prepared = false
        if (p != null) {
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        _currentId.value = null
        _playing.value = false
        _loading.value = false
        _positionMs.value = 0L
        _durationMs.value = 0L
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                if (prepared) _positionMs.value = runCatching { p.currentPosition.toLong() }.getOrDefault(_positionMs.value)
                delay(100)
            }
        }
    }
}
