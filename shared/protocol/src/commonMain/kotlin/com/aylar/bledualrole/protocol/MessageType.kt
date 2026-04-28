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
    BYE(0xFF.toByte());

    companion object {
        fun fromId(id: Byte): MessageType? = entries.firstOrNull { it.id == id }
    }
}