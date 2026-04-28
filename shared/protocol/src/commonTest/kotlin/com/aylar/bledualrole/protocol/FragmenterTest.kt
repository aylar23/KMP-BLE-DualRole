package com.aylar.bledualrole.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FragmenterTest {

    @Test
    fun singleFrame_whenPayloadFitsInMtu() {
        val payload = ByteArray(10) { it.toByte() }
        val frames = Fragmenter.fragment(MessageType.DATA, 1u, payload, mtu = 100)
        assertEquals(1, frames.size)
        assertTrue(frames[0].payload.contentEquals(payload))
    }

    @Test
    fun multipleFrames_whenPayloadExceedsMtu() {
        val payload = ByteArray(100) { it.toByte() }
        val mtu = 30 // max payload per frame = 30 - 12 = 18
        val frames = Fragmenter.fragment(MessageType.DATA, 1u, payload, mtu = mtu)
        assertTrue(frames.size > 1)
        // reassemble and verify
        val reassembler = Reassembler()
        var result: ByteArray? = null
        for (frame in frames) {
            result = reassembler.feed(frame)
        }
        assertTrue(result != null && result.contentEquals(payload))
    }

    @Test
    fun reassembler_returnsPayload_forSingleFrame() {
        val payload = "hello".encodeToByteArray()
        val frame = Frame(
            type = MessageType.DATA,
            seqNum = 5u,
            totalLen = payload.size.toUShort(),
            fragOffset = 0u,
            fragLen = payload.size.toUShort(),
            payload = payload,
        )
        val result = Reassembler().feed(frame)
        assertTrue(result != null && result.contentEquals(payload))
    }

    @Test
    fun roundtrip_largePayload() {
        val payload = ByteArray(500) { (it % 256).toByte() }
        val frames = Fragmenter.fragment(MessageType.FILE_CHUNK, 3u, payload, mtu = 50)
        val reassembler = Reassembler()
        var result: ByteArray? = null
        for (frame in frames) {
            val encoded = FrameCodec.encode(frame)
            val decoded = FrameCodec.decode(encoded).getOrThrow()
            result = reassembler.feed(decoded)
        }
        assertTrue(result != null && result.contentEquals(payload))
    }

    @Test
    fun allFrames_haveCorrectTotalLen() {
        val payload = ByteArray(200) { 0 }
        val frames = Fragmenter.fragment(MessageType.DATA, 0u, payload, mtu = 40)
        frames.forEach { assertEquals(200u.toUShort(), it.totalLen) }
    }
}