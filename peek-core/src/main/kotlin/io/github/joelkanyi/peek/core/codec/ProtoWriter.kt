package io.github.joelkanyi.peek.core.codec

import okio.Buffer
import okio.ByteString

/** Minimal protobuf wire writer over Okio: the inverse of [ProtoReader]. */
internal class ProtoWriter {
    private val buffer = Buffer()

    fun toByteString(): ByteString = buffer.snapshot()

    fun varintField(field: Int, value: Long): ProtoWriter = apply { tag(field, WireType.VARINT); varint(value) }

    fun fixed32Field(field: Int, bits: Int): ProtoWriter = apply { tag(field, WireType.I32); buffer.writeIntLe(bits) }

    fun fixed64Field(field: Int, bits: Long): ProtoWriter = apply { tag(field, WireType.I64); buffer.writeLongLe(bits) }

    fun lengthField(field: Int, value: ByteString): ProtoWriter = apply {
        tag(field, WireType.LEN)
        varint(value.size.toLong())
        buffer.write(value)
    }

    private fun tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

    private fun varint(value: Long) {
        var x = value
        while (true) {
            val b = x and 0x7F
            x = x ushr 7
            if (x != 0L) buffer.writeByte((b or 0x80).toInt()) else { buffer.writeByte(b.toInt()); return }
        }
    }
}
