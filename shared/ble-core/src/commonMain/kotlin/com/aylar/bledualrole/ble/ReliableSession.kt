package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.Frame
import com.aylar.bledualrole.protocol.FrameCodec
import com.aylar.bledualrole.protocol.Fragmenter
import com.aylar.bledualrole.protocol.MessageType
import com.aylar.bledualrole.protocol.Reassembler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

data class IncomingMessage(val type: MessageType, val payload: ByteArray)

/**
 * Adds ACK/retry, sliding-window flow control, and deduplication on top of a raw [Connection].
 *
 * Only [MessageType.DATA] messages go through the reliability path.  All other message types
 * (PING, PONG, FILE_*, HELLO*, BYE) are sent fire-and-forget and arrive via [incoming].
 *
 * Sliding window: at most [windowSize] DATA messages may be in-flight simultaneously.
 * Callers that exceed the window are suspended (natural backpressure).
 *
 * Deduplication: the last [DEDUP_WINDOW] received sequence numbers are remembered so that
 * re-transmitted DATA frames are silently dropped while the ACK is still re-sent (to handle
 * the case where our ACK was lost).
 */
class ReliableSession(
    val connection: Connection,
    private val windowSize: Int = 8,
    private val ackTimeoutMs: Long = 2_000L,
    private val maxRetries: Int = 3,
    scope: CoroutineScope,
) {
    private val seqMutex = Mutex()
    private var nextSeq: UShort = 0u

    private val window = Semaphore(windowSize)

    private val ackMutex = Mutex()
    private val pendingAcks = mutableMapOf<UShort, CompletableDeferred<Unit>>()

    private val seenMutex = Mutex()
    private val seen = ArrayDeque<UShort>()

    private val _incoming = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val incoming: Flow<IncomingMessage> = _incoming.asSharedFlow()

    init {
        scope.launch { receiveLoop() }
    }

    suspend fun send(type: MessageType, payload: ByteArray) {
        val mtu = connection.mtu.value
        val seqNum = seqMutex.withLock {
            val s = nextSeq
            nextSeq = (nextSeq + 1u).toUShort()
            s
        }
        val frames = Fragmenter.fragment(type, seqNum, payload, mtu)

        if (type == MessageType.DATA) {
            val ack = CompletableDeferred<Unit>()
            window.withPermit {
                ackMutex.withLock { pendingAcks[seqNum] = ack }
                try {
                    var lastError: Throwable =
                        IllegalStateException("Max retries exceeded for seq=$seqNum")
                    repeat(maxRetries) { attempt ->
                        frames.forEach { connection.send(FrameCodec.encode(it)) }
                        try {
                            withTimeout(ackTimeoutMs) { ack.await() }
                            return@withPermit
                        } catch (e: TimeoutCancellationException) {
                            lastError = e
                        }
                    }
                    throw lastError
                } finally {
                    ackMutex.withLock { pendingAcks.remove(seqNum) }
                }
            }
        } else {
            frames.forEach { connection.send(FrameCodec.encode(it)) }
        }
    }

    private suspend fun receiveLoop() {
        val reassembler = Reassembler()
        connection.incoming.collect { raw ->
            val frame = FrameCodec.decode(raw).getOrNull() ?: return@collect
            when (frame.type) {
                MessageType.DATA_ACK -> {
                    ackMutex.withLock { pendingAcks[frame.seqNum] }?.complete(Unit)
                }
                MessageType.DATA -> {
                    sendAck(frame.seqNum)
                    if (!markSeen(frame.seqNum)) return@collect
                    reassembler.feed(frame)?.let { payload ->
                        _incoming.emit(IncomingMessage(frame.type, payload))
                    }
                }
                else -> {
                    reassembler.feed(frame)?.let { payload ->
                        _incoming.emit(IncomingMessage(frame.type, payload))
                    }
                }
            }
        }
    }

    private suspend fun sendAck(seqNum: UShort) {
        val frame = Frame(
            type = MessageType.DATA_ACK,
            seqNum = seqNum,
            totalLen = 0u,
            fragOffset = 0u,
            fragLen = 0u,
            payload = ByteArray(0),
        )
        runCatching { connection.send(FrameCodec.encode(frame)) }
    }

    private suspend fun markSeen(seqNum: UShort): Boolean = seenMutex.withLock {
        if (seqNum in seen) return@withLock false
        seen.addLast(seqNum)
        if (seen.size > DEDUP_WINDOW) seen.removeFirst()
        true
    }

    companion object {
        private const val DEDUP_WINDOW = 128
    }
}