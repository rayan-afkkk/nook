package com.nook.msgapp.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

/** Renders a LiveKit video track (local preview or the other person). */
@Composable
fun VideoView(track: VideoTrack, modifier: Modifier = Modifier, mirror: Boolean = false, fill: Boolean = true) {
    val room = CallController.room ?: return
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureViewRenderer(ctx).also { view ->
                room.initVideoRenderer(view)
                view.setScalingType(if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            }
        },
        update = { view ->
            view.setMirror(mirror)
            val current = view.tag as? VideoTrack
            if (current !== track) {
                current?.removeRenderer(view)
                track.addRenderer(view)
                view.tag = track
            }
        },
        onRelease = { view ->
            (view.tag as? VideoTrack)?.removeRenderer(view)
            view.tag = null
            view.release()
        },
    )
}
