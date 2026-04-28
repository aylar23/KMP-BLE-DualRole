package com.aylar.bledualrole.protocol

/**
 * Wire format: [1B version][1B type][2B seqNum][2B totalLen][2B fragOffset][2B fragLen][N payload][2B CRC16]
 * Header size: 10 bytes. CRC16 appended at end: 2 bytes. Min frame: 12 bytes.
 */
data class Frame(
    val version: Byte = PROTOCOL_VERSION,
    val type: MessageType,
    val seqNum: UShort,
    val totalLen: UShort,
    val fragOffset: UShort,
    val fragLen: UShort,
    val payload: ByteArray,
) {
    companion object {
        const val PROTOCOL_VERSION: Byte = 1
        const val HEADER_SIZE: Int = 10
        const val CRC_SIZE: Int = 2
        const val OVERHEAD: Int = HEADER_SIZE + CRC_SIZE
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Frame) return false
        return version == other.version &&
            type == other.type &&
            seqNum == other.seqNum &&
            totalLen == other.totalLen &&
            fragOffset == other.fragOffset &&
            fragLen == other.fragLen &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = version.toInt()
        result = 31 * result + type.hashCode()
        result = 31 * result + seqNum.hashCode()
        result = 31 * result + totalLen.hashCode()
        result = 31 * result + fragOffset.hashCode()
        result = 31 * result + fragLen.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}