package com.aylar.bledualrole.ble

data class FileProgress(
    val transferId: String,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes == 0L) 0f else bytesTransferred.toFloat() / totalBytes
}