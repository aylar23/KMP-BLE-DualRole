package com.aylar.bledualrole.protocol

object Fragmenter {

    fun fragment(
        type: MessageType,
        seqNum: UShort,
        payload: ByteArray,
        mtu: Int,
    ): List<Frame> {
        val maxPayloadPerFrame = mtu - Frame.OVERHEAD
        require(maxPayloadPerFrame > 0) { "MTU $mtu too small for frame overhead ${Frame.OVERHEAD}" }
        val totalLen = payload.size.toUShort()
        if (payload.isEmpty()) {
            return listOf(Frame(type = type, seqNum = seqNum, totalLen = totalLen, fragOffset = 0u, fragLen = 0u, payload = ByteArray(0)))
        }
        val frames = mutableListOf<Frame>()
        var offset = 0
        while (offset < payload.size) {
            val chunkSize = minOf(maxPayloadPerFrame, payload.size - offset)
            val chunk = payload.copyOfRange(offset, offset + chunkSize)
            frames += Frame(
                type = type,
                seqNum = seqNum,
                totalLen = totalLen,
                fragOffset = offset.toUShort(),
                fragLen = chunkSize.toUShort(),
                payload = chunk,
            )
            offset += chunkSize
        }
        return frames
    }
}

class Reassembler {
    private val buffers = mutableMapOf<UShort, ReassemblyBuffer>()

    fun feed(frame: Frame): ByteArray? {
        if (frame.totalLen == frame.fragLen && frame.fragOffset == 0u.toUShort()) {
            return frame.payload
        }
        val buf = buffers.getOrPut(frame.seqNum) { ReassemblyBuffer(frame.totalLen.toInt()) }
        buf.put(frame.fragOffset.toInt(), frame.payload)
        return if (buf.isComplete()) {
            buffers.remove(frame.seqNum)
            buf.assemble()
        } else {
            null
        }
    }

    private class ReassemblyBuffer(private val totalLen: Int) {
        private val data = ByteArray(totalLen)
        private var received = 0

        fun put(offset: Int, chunk: ByteArray) {
            chunk.copyInto(data, offset)
            received += chunk.size
        }

        fun isComplete() = received >= totalLen

        fun assemble(): ByteArray = data.copyOf()
    }
}