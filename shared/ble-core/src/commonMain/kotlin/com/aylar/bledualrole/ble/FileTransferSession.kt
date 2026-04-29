package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.Frame
import com.aylar.bledualrole.protocol.FrameCodec
import com.aylar.bledualrole.protocol.Fragmenter
import com.aylar.bledualrole.protocol.MessageType
import com.aylar.bledualrole.protocol.Reassembler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Application-level file transfer built on [ReliableSession].
 *
 * ## Sender flow
 * 1. [send] — encodes FILE_OFFER and sends it via the reliable session (with ACK).
 * 2. Waits for FILE_ACK from the receiver.
 * 3. Splits [data] into chunks ≤ effective MTU bytes and sends each as a FILE_CHUNK frame.
 *    Chunks use write-without-response for throughput; every [flushEvery] chunks a
 *    write-with-response frame is inserted as a pipeline flush.
 * 4. Sends FILE_COMPLETE (with ACK) to close the transfer.
 * 5. Emits [FileProgress] events throughout.
 *
 * ## Receiver flow
 * Call [receiveLoop] once (e.g. in a coroutine launched at session startup).
 * It listens on [session.incoming] for FILE_* messages and exposes received files via
 * [incomingFiles].
 *
 * ## Pause / resume
 * The sender respects the [paused] flag.  Call [pause] / [resume] from any thread/coroutine;
 * the sending loop will block at the next chunk boundary while paused.
 *
 * ## Throughput note
 * FILE_CHUNK payload = [8B offset][data], no BLE-level ACK for individual chunks.
 * FILE_COMPLETE is sent reliably; if the receiver detects a size mismatch it replies
 * FILE_ABORT and the caller receives [FileTransferException].
 */
class FileTransferSession(
    private val session: ReliableSession,
    scope: CoroutineScope,
) {
    private val _progress = MutableSharedFlow<FileProgress>(extraBufferCapacity = 64)
    val progress: Flow<FileProgress> = _progress.asSharedFlow()

    data class ReceivedFile(val transferId: String, val fileName: String, val data: ByteArray)

    private val _incomingFiles = MutableSharedFlow<ReceivedFile>(extraBufferCapacity = 4)
    val incomingFiles: Flow<ReceivedFile> = _incomingFiles.asSharedFlow()

    private val pauseMutex = Mutex()
    @Volatile private var paused = false

    init {
        scope.launch { receiveLoop() }
    }

    fun pause() { paused = true }

    fun resume() {
        paused = false
    }

    suspend fun send(transferId: String, fileName: String, data: ByteArray) {
        val offerPayload = encodeOffer(transferId, fileName, data.size.toLong())
        session.send(MessageType.FILE_OFFER, offerPayload)

        session.incoming.first { msg ->
            msg.type == MessageType.FILE_ACK &&
                msg.payload.decodeToString().trim() == transferId
        }

        val mtu = session.connection.mtu.value
        val chunkSize = (mtu - Frame.OVERHEAD - CHUNK_HEADER_SIZE).coerceAtLeast(1)
        var offset = 0
        var chunkIndex = 0

        while (offset < data.size) {
            waitIfPaused()
            val end = minOf(offset + chunkSize, data.size)
            val chunk = data.copyOfRange(offset, end)
            val chunkPayload = encodeChunk(offset.toLong(), chunk)

            val frames = Fragmenter.fragment(
                MessageType.FILE_CHUNK,
                nextSeq(),
                chunkPayload,
                mtu,
            )

            val isFlush = (chunkIndex % FLUSH_EVERY == 0)
            if (isFlush) {
                frames.forEach { session.connection.send(FrameCodec.encode(it)) }
            } else {
                frames.forEach { session.connection.sendNoAck(FrameCodec.encode(it)) }
            }

            offset = end
            chunkIndex++
            _progress.emit(FileProgress(transferId, fileName, offset.toLong(), data.size.toLong()))
        }

        session.send(MessageType.FILE_COMPLETE, encodeComplete(transferId, data.size.toLong()))

        val reply = session.incoming.first { msg ->
            (msg.type == MessageType.FILE_ACK || msg.type == MessageType.FILE_ABORT) &&
                msg.payload.decodeToString().startsWith(transferId)
        }
        if (reply.type == MessageType.FILE_ABORT) {
            throw FileTransferException("Transfer $transferId aborted by receiver")
        }
    }

    private val seqMutex = Mutex()
    private var fileSeq: UShort = 0u
    private suspend fun nextSeq(): UShort = seqMutex.withLock {
        val s = fileSeq; fileSeq = (fileSeq + 1u).toUShort(); s
    }

    private suspend fun waitIfPaused() {
        while (paused) {
            kotlinx.coroutines.delay(50)
        }
    }

    private suspend fun receiveLoop() {
        val buffers = mutableMapOf<String, ReceiveBuffer>()
        val chunkReassemblers = mutableMapOf<String, Reassembler>()

        session.incoming.collect { msg ->
            when (msg.type) {
                MessageType.FILE_OFFER -> {
                    val (id, name, size) = decodeOffer(msg.payload)
                    buffers[id] = ReceiveBuffer(id, name, size)
                    chunkReassemblers[id] = Reassembler()
                    session.send(MessageType.FILE_ACK, id.encodeToByteArray())
                }
                MessageType.FILE_CHUNK -> {
                    val (offset, chunkData) = decodeChunk(msg.payload)
                    val transferId = buffers.keys.lastOrNull() ?: return@collect
                    val buf = buffers[transferId] ?: return@collect
                    buf.put(offset, chunkData)
                    _progress.emit(
                        FileProgress(transferId, buf.fileName, buf.received, buf.totalSize),
                    )
                }
                MessageType.FILE_COMPLETE -> {
                    val (id, expectedSize) = decodeComplete(msg.payload)
                    val buf = buffers.remove(id)
                    chunkReassemblers.remove(id)
                    if (buf == null || buf.received != expectedSize) {
                        session.send(MessageType.FILE_ABORT, id.encodeToByteArray())
                        return@collect
                    }
                    session.send(MessageType.FILE_ACK, id.encodeToByteArray())
                    _incomingFiles.emit(ReceivedFile(id, buf.fileName, buf.assemble()))
                }
                MessageType.FILE_PAUSE -> pause()
                MessageType.FILE_RESUME -> resume()
                else -> {}
            }
        }
    }

    private class ReceiveBuffer(val id: String, val fileName: String, val totalSize: Long) {
        private val data = ByteArray(totalSize.toInt())
        var received: Long = 0L

        fun put(offset: Long, chunk: ByteArray) {
            chunk.copyInto(data, offset.toInt())
            received += chunk.size
        }

        fun assemble(): ByteArray = data.copyOf()
    }

    companion object {
        private const val CHUNK_HEADER_SIZE = 8
        private const val FLUSH_EVERY = 16

        fun encodeOffer(id: String, name: String, size: Long): ByteArray =
            "$id|$name|$size".encodeToByteArray()

        fun decodeOffer(payload: ByteArray): Triple<String, String, Long> {
            val parts = payload.decodeToString().split("|")
            return Triple(parts[0], parts[1], parts[2].toLong())
        }

        fun encodeChunk(offset: Long, data: ByteArray): ByteArray {
            val result = ByteArray(8 + data.size)
            for (i in 0..7) result[i] = (offset ushr ((7 - i) * 8)).toByte()
            data.copyInto(result, 8)
            return result
        }

        fun decodeChunk(payload: ByteArray): Pair<Long, ByteArray> {
            var offset = 0L
            for (i in 0..7) offset = (offset shl 8) or (payload[i].toLong() and 0xFF)
            return Pair(offset, payload.copyOfRange(8, payload.size))
        }

        fun encodeComplete(id: String, size: Long): ByteArray = "$id|$size".encodeToByteArray()

        fun decodeComplete(payload: ByteArray): Pair<String, Long> {
            val parts = payload.decodeToString().split("|")
            return Pair(parts[0], parts[1].toLong())
        }
    }
}

class FileTransferException(message: String) : Exception(message)