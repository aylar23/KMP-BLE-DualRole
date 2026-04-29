package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Sends a PING every [intervalMs] and expects a PONG back within [timeoutMs].
 * Also responds to incoming PINGs with a PONG.
 *
 * [alive] goes false when [timeoutMs] elapses without a PONG — the caller should treat
 * this as a zombie connection and close/reconnect.
 */
class HeartbeatController(
    private val session: ReliableSession,
    private val intervalMs: Long = 5_000L,
    private val timeoutMs: Long = 15_000L,
    scope: CoroutineScope,
) {
    private val _alive = MutableStateFlow(true)
    val alive: StateFlow<Boolean> = _alive

    private val clock = TimeSource.Monotonic
    private var lastPong = clock.markNow()

    init {
        scope.launch {
            session.incoming.collect { msg ->
                when (msg.type) {
                    MessageType.PONG -> lastPong = clock.markNow()
                    MessageType.PING -> runCatching {
                        session.send(MessageType.PONG, ByteArray(0))
                    }
                    else -> {}
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(intervalMs)
                runCatching { session.send(MessageType.PING, ByteArray(0)) }
                _alive.value = lastPong.elapsedNow().inWholeMilliseconds < timeoutMs
            }
        }
    }
}