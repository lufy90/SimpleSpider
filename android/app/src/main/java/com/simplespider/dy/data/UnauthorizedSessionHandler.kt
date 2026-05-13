package com.simplespider.dy.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object UnauthorizedSessionHandler {
    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events = _events.asSharedFlow()

    fun notifyUnauthorized() {
        _events.tryEmit(Unit)
    }
}
