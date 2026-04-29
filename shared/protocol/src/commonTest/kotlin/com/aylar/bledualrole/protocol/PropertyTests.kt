package com.aylar.bledualrole.protocol

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests: generate random inputs and verify invariants hold.
 * No external framework needed — Kotlin's stdlib Random is sufficient for
 * the domain being tested (codec roundtrip, fragmentation reassembly).
 */
class PropertyTests {

    @Test
    fun codec_roundtrip_for_random_payloads() {
        val random = Random(42)
        repeat(200) {
            val size = random.nextInt(0, 300)
            val payload = random.nextBytes(size)
            val type = MessageType.entries.filter { it != MessageType.BYE }.random(random)
            val seqNum = random.nextInt(0, 65536).toUShort()
            val frame = Frame(
                type = type,
                seqNum = seqNum,
                totalLen = size.toUShort(),
                fragOffset = 0u,
                fragLen = size.toUShort(),
                payload = payload,
            )
            val encoded = FrameCodec.encode(frame)
            val decoded = FrameCodec.decode(encoded).getOrThrow()
            assertEquals(frame, decoded, "Roundtrip failed for seqNum=$seqNum size=$size")
        }
    }

    @Test
    fun fragmentation_reassembly_invariants_for_random_payloads() {
        val random = Random(99)
        repeat(100) {
            val size = random.nextInt(0, 2000)
            val mtu = random.nextInt(Frame.OVERHEAD + 1, 256)
            val payload = random.nextBytes(size)
            val seqNum = random.nextInt(0, 65536).toUShort()

            val frames = Fragmenter.fragment(MessageType.DATA, seqNum, payload, mtu)

            // Every frame fits within MTU
            frames.forEach { frame ->
                assertTrue(
                    FrameCodec.encode(frame).size <= mtu,
                    "Frame size ${FrameCodec.encode(frame).size} exceeds MTU $mtu",
                )
            }

            // All frames carry the correct totalLen
            frames.forEach { assertEquals(size.toUShort(), it.totalLen) }

            // Feeding all frames to the reassembler recovers the original payload
            val reassembler = Reassembler()
            var result: ByteArray? = null
            for (frame in frames) {
                val encoded = FrameCodec.encode(frame)
                val decoded = FrameCodec.decode(encoded).getOrThrow()
                result = reassembler.feed(decoded)
            }
            val reassembled = result ?: if (size == 0) ByteArray(0) else null
            assertTrue(
                reassembled != null && reassembled.contentEquals(payload),
                "Reassembly failed for size=$size mtu=$mtu",
            )
        }
    }

    @Test
    fun crc_corruption_is_always_detected() {
        val random = Random(7)
        repeat(100) {
            val payload = random.nextBytes(random.nextInt(0, 100))
            val frame = Frame(
                type = MessageType.DATA,
                seqNum = 1u,
                totalLen = payload.size.toUShort(),
                fragOffset = 0u,
                fragLen = payload.size.toUShort(),
                payload = payload,
            )
            val encoded = FrameCodec.encode(frame).copyOf()
            // Flip a bit in the middle of the payload area (if non-empty)
            if (encoded.size > Frame.OVERHEAD) {
                val flipIdx = random.nextInt(Frame.HEADER_SIZE, encoded.size - Frame.CRC_SIZE)
                encoded[flipIdx] = (encoded[flipIdx].toInt() xor 0xFF).toByte()
                assertTrue(
                    FrameCodec.decode(encoded).isFailure,
                    "Corrupted frame must fail CRC check",
                )
            }
        }
    }

    @Test
    fun seqNum_wraps_correctly_through_zero() {
        val payload = byteArrayOf(0x01)
        val frames1 = Fragmenter.fragment(MessageType.DATA, 0xFFFEu.toUShort(), payload, mtu = 64)
        val frames2 = Fragmenter.fragment(MessageType.DATA, 0xFFFFu.toUShort(), payload, mtu = 64)
        val frames3 = Fragmenter.fragment(MessageType.DATA, 0x0000u.toUShort(), payload, mtu = 64)
        assertEquals(0xFFFEu.toUShort(), frames1.first().seqNum)
        assertEquals(0xFFFFu.toUShort(), frames2.first().seqNum)
        assertEquals(0x0000u.toUShort(), frames3.first().seqNum)
    }
}
