package com.nook.msgapp.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** App-wide toast bus; the host in the root composable shows them. */
object Toasts {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun show(message: String) {
        _events.tryEmit(message)
    }

    fun error(t: Throwable) = show(t.friendly())
}
