package ua.com.radiokot.osmanddisplay.base.extension

import com.welie.blessed.BluetoothBytesParser

fun ByteArray.encodeAsHexString(): String {
    return BluetoothBytesParser.asHexString(this)
}

fun String.decodeHexString(): ByteArray {
    return BluetoothBytesParser.string2bytes(this)
}