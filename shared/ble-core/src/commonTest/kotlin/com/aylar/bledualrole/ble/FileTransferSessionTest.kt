package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FileTransferSessionTest {

    private fun setup(): Pair<FileTransferSession, FileTransferSession> {
        val dispatcher = UnconfinedTestDispatcher()
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)
        val (alice, bob) = FakeConnection.pair()
        val aliceRelSession = ReliableSession(alice, scope = scope)
        val bobRelSession = ReliableSession(bob, scope = scope)
        val aliceFile = FileTransferSession(aliceRelSession, scope = scope)
        val bobFile = FileTransferSession(bobRelSession, scope = scope)
        return aliceFile to bobFile
    }

    @Test
    fun small_file_transfer_end_to_end() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = setup()

        val data = "Hello, BLE file transfer!".encodeToByteArray()
        val received = async { bob.incomingFiles.first() }

        alice.send("tx-1", "hello.txt", data)

        val file = received.await()
        assertEquals("tx-1", file.transferId)
        assertEquals("hello.txt", file.fileName)
        assertTrue(file.data.contentEquals(data))
    }

    @Test
    fun large_file_is_chunked_and_reassembled() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = setup()

        val data = ByteArray(50_000) { (it % 256).toByte() }
        val received = async { bob.incomingFiles.first() }

        alice.send("tx-big", "large.bin", data)

        val file = received.await()
        assertTrue(file.data.contentEquals(data), "Large file must reassemble correctly")
    }

    @Test
    fun progress_events_emitted_during_transfer() = runTest(UnconfinedTestDispatcher()) {
        val (alice, _) = setup()

        val data = ByteArray(10_000) { 0x42 }
        val progressEvents = mutableListOf<FileProgress>()
        val collectJob = kotlinx.coroutines.launch {
            alice.progress.collect { progressEvents += it }
        }

        runCatching { alice.send("tx-prog", "progress.bin", data) }
        collectJob.cancel()

        assertTrue(progressEvents.isNotEmpty(), "Must emit progress events")
        assertTrue(
            progressEvents.last().bytesTransferred <= data.size,
            "Final progress must not exceed file size",
        )
    }

    @Test
    fun encode_decode_offer_roundtrip() {
        val (id, name, size) = FileTransferSession.decodeOffer(
            FileTransferSession.encodeOffer("abc", "file.txt", 12345L),
        )
        assertEquals("abc", id)
        assertEquals("file.txt", name)
        assertEquals(12345L, size)
    }

    @Test
    fun encode_decode_chunk_roundtrip() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val (offset, chunk) = FileTransferSession.decodeChunk(
            FileTransferSession.encodeChunk(999L, data),
        )
        assertEquals(999L, offset)
        assertTrue(chunk.contentEquals(data))
    }

    @Test
    fun encode_decode_complete_roundtrip() {
        val (id, size) = FileTransferSession.decodeComplete(
            FileTransferSession.encodeComplete("transfer-id", 65536L),
        )
        assertEquals("transfer-id", id)
        assertEquals(65536L, size)
    }
}
