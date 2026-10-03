package com.nook.msgapp.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.nook.msgapp.data.UserFacingError
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
import java.io.File
import kotlin.math.log10

/** A finished recording: the .m4a file, its length and the raw amplitude samples (0..32767). */
class VoiceClip(val file: File, val durationMs: Long, val amplitudes: List<Int>)

/**
 * Hold-to-record voice notes: AAC in an .m4a (MPEG-4) file, 44.1 kHz / 64 kbps mono, written to
 * cacheDir/voice/. getMaxAmplitude() is sampled every ~100 ms for the waveform and the live level.
 * Needs RECORD_AUDIO (the UI asks for it before calling [start]).
 */
class VoiceRecorder(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val samples = ArrayList<Int>()
    private var ticker: Job? = null

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /** Live input level, 0..1. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(appContext) else MediaRecorder()

    /** Starts recording. Throws a [UserFacingError] when the mic is unavailable. */
    fun start() {
        if (recorder != null) return
        if (!hasPermission()) throw UserFacingError("Microphone access is off. Turn it on in Android settings.")
        val dir = File(appContext.cacheDir, "voice").apply { mkdirs() }
        val out = File(dir, "voice-${System.currentTimeMillis()}.m4a")
        val r = newRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            runCatching { r.release() }
            out.delete()
            throw UserFacingError("Couldn't start recording. Is another app using the microphone?")
        }
        recorder = r
        file = out
        samples.clear()
        startedAt = System.currentTimeMillis()
        _elapsedMs.value = 0L
        _level.value = 0f
        _recording.value = true
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(SAMPLE_MS)
                val current = recorder ?: break
                val amp = runCatching { current.maxAmplitude }.getOrDefault(0)
                samples.add(amp)
                _level.value = normalize(amp)
                _elapsedMs.value = System.currentTimeMillis() - startedAt
            }
        }
    }

    /** Stops and returns the clip, or null when nothing usable was recorded (too short or failed). */
    fun stop(): VoiceClip? {
        val r = recorder ?: return null
        val out = file
        val duration = System.currentTimeMillis() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        release(r)
        if (out == null) return null
        if (!ok || duration < MIN_MS || !out.exists() || out.length() == 0L) {
            out.delete()
            return null
        }
        return VoiceClip(out, duration, samples.toList())
    }

    /** Stops and throws the recording away. */
    fun cancel() {
        val r = recorder ?: return
        val out = file
        runCatching { r.stop() }
        release(r)
        out?.delete()
    }

    private fun release(r: MediaRecorder) {
        ticker?.cancel()
        ticker = null
        runCatching { r.reset() }
        runCatching { r.release() }
        recorder = null
        file = null
        _recording.value = false
        _level.value = 0f
    }

    private fun normalize(amp: Int): Float {
        if (amp <= 0) return 0f
        val db = 20 * log10(amp / 32767.0)
        return ((db + 55) / 55).coerceIn(0.0, 1.0).toFloat()
    }

    companion object {
        private const val SAMPLE_MS = 100L
        /** Shorter than this counts as an accidental tap. */
        const val MIN_MS = 700L
    }
}
