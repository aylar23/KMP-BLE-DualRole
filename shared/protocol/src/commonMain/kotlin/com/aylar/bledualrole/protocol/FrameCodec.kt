package com.aylar.bledualrole.protocol

object FrameCodec {

    fun encode(frame: Frame): ByteArray {
        val buf = ByteArray(Frame.OVERHEAD + frame.payload.size)
        var pos = 0
        buf[pos++] = frame.version
        buf[pos++] = frame.type.id
        buf[pos++] = (frame.seqNum.toInt() ushr 8).toByte()
        buf[pos++] = frame.seqNum.toByte()
        buf[pos++] = (frame.totalLen.toInt() ushr 8).toByte()
        buf[pos++] = frame.totalLen.toByte()
        buf[pos++] = (frame.fragOffset.toInt() ushr 8).toByte()
        buf[pos++] = frame.fragOffset.toByte()
        buf[pos++] = (frame.fragLen.toInt() ushr 8).toByte()
        buf[pos++] = frame.fragLen.toByte()
        frame.payload.copyInto(buf, pos)
        pos += frame.payload.size
        val crc = crc16(buf, 0, pos)
        buf[pos++] = (crc ushr 8).toByte()
        buf[pos] = crc.toByte()
        return buf
    }

    fun decode(raw: ByteArray): Result<Frame> {
        if (raw.size < Frame.OVERHEAD) {
            return Result.failure(FrameDecodeException("Frame too short: ${raw.size}"))
        }
        val payloadLen = raw.size - Frame.OVERHEAD
        val expectedCrc = crc16(raw, 0, raw.size - Frame.CRC_SIZE)
        val actualCrc = ((raw[raw.size - 2].toInt() and 0xFF) shl 8) or
            (raw[raw.size - 1].toInt() and 0xFF)
        if (expectedCrc != actualCrc) {
            return Result.failure(FrameDecodeException("CRC mismatch: expected $expectedCrc got $actualCrc"))
        }
        val version = raw[0]
        val type = MessageType.fromId(raw[1])
            ?: return Result.failure(FrameDecodeException("Unknown message type: ${raw[1]}"))
        val seqNum = (((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)).toUShort()
        val totalLen = (((raw[4].toInt() and 0xFF) shl 8) or (raw[5].toInt() and 0xFF)).toUShort()
        val fragOffset = (((raw[6].toInt() and 0xFF) shl 8) or (raw[7].toInt() and 0xFF)).toUShort()
        val fragLen = (((raw[8].toInt() and 0xFF) shl 8) or (raw[9].toInt() and 0xFF)).toUShort()
        val payload = raw.copyOfRange(Frame.HEADER_SIZE, Frame.HEADER_SIZE + payloadLen)
        return Result.success(Frame(version, type, seqNum, totalLen, fragOffset, fragLen, payload))
    }

    private fun crc16(data: ByteArray, offset: Int, length: Int): Int {
        var crc = 0xFFFF
        for (i in offset until offset + length) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }
}

class FrameDecodeException(message: String) : Exception(message)