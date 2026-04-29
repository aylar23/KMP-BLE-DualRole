package com.aylar.bledualrole.ble

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * Returns a [Flow] that continuously establishes connections to [peer], yielding each new
 * [Connection] when it comes up and `null` when it drops.
 *
 * Uses [policy] for exponential backoff between attempts.  Backoff resets after a successful
 * connection so flaky-but-recoverable links don't accumulate delay.
 *
 * The flow completes when [maxAttempts] is exhausted or the collecting coroutine is cancelled.
 *
 * Typical usage:
 * ```
 * central.supervisedConnect(peer).collect { conn ->
 *     if (conn != null) startSession(conn) else showReconnecting()
 * }
 * ```
 */
fun BleCentral.supervisedConnect(
    peer: DiscoveredPeer,
    policy: ReconnectPolicy = ReconnectPolicy(),
): Flow<Connection?> = flow {
    var attempts = 0
    var delayMs = policy.initialDelayMs

    while (isActive && attempts < policy.maxAttempts) {
        val connection = runCatching { connect(peer) }.getOrNull()

        if (connection != null) {
            emit(connection)
            delayMs = policy.initialDelayMs
            connection.state.first { it == ConnectionState.DISCONNECTED }
            emit(null)
        }

        attempts++
        if (attempts >= policy.maxAttempts) break

        delay(delayMs)
        delayMs = minOf((delayMs * policy.multiplier).toLong(), policy.maxDelayMs)
    }
}