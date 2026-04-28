package com.aylar.bledualrole.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameCodecTest {

    @Test
    fun roundtrip_emptyPayload() {
        val frame = Frame(
            type = MessageType.HELLO,
            seqNum = 1u,
            totalLen = 0u,
            fragOffset = 0u,
            fragLen = 0u,
            payload = ByteArray(0),
        )
        val encoded = FrameCodec.encode(frame)
        val decoded = FrameCodec.decode(encoded).getOrThrow()
        assertEquals(frame, decoded)
    }

    @Test
    fun roundtrip_withPayload() {
        val payload = "hello world".encodeToByteArray()
        val frame = Frame(
            type = MessageType.DATA,
            seqNum = 42u,
            totalLen = payload.size.toUShort(),
            fragOffset = 0u,
            fragLen = payload.size.toUShort(),
            payload = payload,
        )
        val decoded = FrameCodec.decode(FrameCodec.encode(frame)).getOrThrow()
        assertEquals(frame, decoded)
    }

    @Test
    fun decode_failsOnCrcMismatch() {
        val payload = "test".encodeToByteArray()
        val frame = Frame(
            type = MessageType.DATA,
            seqNum = 1u,
            totalLen = payload.size.toUShort(),
            fragOffset = 0u,
            fragLen = payload.size.toUShort(),
            payload = payload,
        )
        val encoded = FrameCodec.encode(frame).also { it[it.size - 1] = it[it.size - 1].inc() }
        assertTrue(FrameCodec.decode(encoded).isFailure)
    }

    @Test
    fun decode_failsOnTooShortBuffer() {
        assertTrue(FrameCodec.decode(ByteArray(5)).isFailure)
    }

    @Test
    fun encode_size_equalsOverheadPlusPayload() {
        val payload = ByteArray(100) { it.toByte() }
        val frame = Frame(
            type = MessageType.FILE_CHUNK,
            seqNum = 0u,
            totalLen = 100u,
            fragOffset = 0u,
            fragLen = 100u,
            payload = payload,
        )
        assertEquals(Frame.OVERHEAD + 100, FrameCodec.encode(frame).size)
    }
}