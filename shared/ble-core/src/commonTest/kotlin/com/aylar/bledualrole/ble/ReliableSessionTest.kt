package com.aylar.bledualrole.ble

import com.aylar.bledualrole.protocol.MessageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReliableSessionTest {

    @Test
    fun data_message_is_acked_and_delivered() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = FakeConnection.pair()
        val aliceSession = ReliableSession(alice, scope = backgroundScope)
        val bobSession = ReliableSession(bob, scope = backgroundScope)

        val received = async { bobSession.incoming.first { it.type == MessageType.DATA } }
        aliceSession.send(MessageType.DATA, "hello".encodeToByteArray())

        assertEquals("hello", received.await().payload.decodeToString())
    }

    @Test
    fun non_data_message_sent_without_window() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = FakeConnection.pair()
        val aliceSession = ReliableSession(alice, scope = backgroundScope)
        val bobSession = ReliableSession(bob, scope = backgroundScope)

        val received = async { bobSession.incoming.first { it.type == MessageType.PING } }
        aliceSession.send(MessageType.PING, ByteArray(0))

        received.await()
        // PING does not go through the reliability path — alice's sentBytes list captures all writes,
        // but none of them should be DATA_ACK from bob
        assertTrue(alice.sentBytes.none { bytes ->
            com.aylar.bledualrole.protocol.FrameCodec.decode(bytes).getOrNull()?.type == MessageType.DATA
        })
    }

    @Test
    fun duplicate_data_frame_is_dropped() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = FakeConnection.pair()
        val aliceSession = ReliableSession(alice, scope = backgroundScope)
        val bobSession = ReliableSession(bob, scope = backgroundScope)

        var receiveCount = 0
        val collectJob = launch {
            bobSession.incoming.collect { if (it.type == MessageType.DATA) receiveCount++ }
        }

        aliceSession.send(MessageType.DATA, "msg".encodeToByteArray())

        // Re-inject the same raw frame bytes that Alice sent
        val dupFrame = alice.sentBytes.first()
        bob.simulateIncoming(dupFrame)

        kotlinx.coroutines.delay(50)
        assertEquals(1, receiveCount, "Duplicate DATA frame must be deduplicated")
        collectJob.cancel()
    }

    @Test
    fun end_to_end_bidirectional_exchange() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = FakeConnection.pair()
        val aliceSession = ReliableSession(alice, scope = backgroundScope)
        val bobSession = ReliableSession(bob, scope = backgroundScope)

        val aliceReceived = async {
            aliceSession.incoming.first { it.type == MessageType.DATA }
        }
        val bobReceived = async {
            bobSession.incoming.first { it.type == MessageType.DATA }
        }

        aliceSession.send(MessageType.DATA, "from alice".encodeToByteArray())
        bobSession.send(MessageType.DATA, "from bob".encodeToByteArray())

        assertEquals("from alice", bobReceived.await().payload.decodeToString())
        assertEquals("from bob", aliceReceived.await().payload.decodeToString())
    }

    @Test
    fun large_message_fragmented_and_reassembled() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = FakeConnection.pair()
        val aliceSession = ReliableSession(alice, scope = backgroundScope)
        val bobSession = ReliableSession(bob, scope = backgroundScope)

        val payload = ByteArray(2_000) { it.toByte() }
        val received = async { bobSession.incoming.first { it.type == MessageType.DATA } }

        aliceSession.send(MessageType.DATA, payload)

        assertTrue(received.await().payload.contentEquals(payload))
    }

    @Test
    fun window_exhaustion_does_not_deadlock() = runTest(UnconfinedTestDispatcher()) {
        val (alice, bob) = FakeConnection.pair()
        // Window = 2, short timeout so sends fail fast
        val aliceSession = ReliableSession(
            connection = alice,
            windowSize = 2,
            ackTimeoutMs = 50,
            maxRetries = 1,
            scope = backgroundScope,
        )
        // Bob's session processes ACKs so messages go through
        ReliableSession(bob, scope = backgroundScope)

        // 5 concurrent sends — window allows 2 at a time; rest queue up
        val results = (1..5).map { i ->
            async { runCatching { aliceSession.send(MessageType.DATA, byteArrayOf(i.toByte())) } }
        }
        results.forEach { it.await() }
        // Test passes if it completes without hanging
    }
}