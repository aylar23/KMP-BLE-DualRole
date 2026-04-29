package com.aylar.bledualrole.protocol

enum class MessageType(val id: Byte) {
    HELLO(0x01),
    HELLO_ACK(0x02),
    MTU_REQUEST(0x03),
    DATA(0x10),
    DATA_ACK(0x11),
    FILE_OFFER(0x20),
    FILE_CHUNK(0x21),
    FILE_COMPLETE(0x22),
    FILE_ACK(0x23),
    FILE_ABORT(0x24),
    FILE_PAUSE(0x25),
    FILE_RESUME(0x26),
    PING(0x30),
    PONG(0x31),
    BYE(0xFF.toByte());

    companion object {
        fun fromId(id: Byte): MessageType? = entries.firstOrNull { it.id == id }
    }
}