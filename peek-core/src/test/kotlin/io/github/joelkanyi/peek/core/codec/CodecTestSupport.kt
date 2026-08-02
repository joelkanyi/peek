package io.github.joelkanyi.peek.core.codec

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreType
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/** Reduce a [KvValue] to a plain comparable so tests can assert without equals overrides. */
internal fun KvValue.unwrap(): Any = when (this) {
    is KvValue.BoolValue -> value
    is KvValue.IntValue -> value
    is KvValue.LongValue -> value
    is KvValue.FloatValue -> value
    is KvValue.DoubleValue -> value
    is KvValue.StringValue -> value
    is KvValue.StringSetValue -> values
    is KvValue.BytesValue -> value
    is KvValue.ProtoNode -> "proto"
}

internal fun handle(type: StoreType, path: String = "/data/data/app/x"): StoreHandle =
    StoreHandle(AppPackage("com.example.app", null), path, type, path.substringAfterLast('/'), null)

/**
 * An independent protobuf writer, deliberately separate from the production
 * [ProtoReader], so decoding these bytes exercises the reader against input it
 * did not generate.
 */
internal class ProtoTestWriter {
    private val buffer = Buffer()

    fun bytes(): ByteString = buffer.snapshot()

    fun varintField(field: Int, value: Long): ProtoTestWriter = apply { tag(field, 0); varint(value) }
    fun i32Field(field: Int, bits: Int): ProtoTestWriter = apply { tag(field, 5); buffer.writeIntLe(bits) }
    fun i64Field(field: Int, bits: Long): ProtoTestWriter = apply { tag(field, 1); buffer.writeLongLe(bits) }
    fun lenField(field: Int, value: ByteString): ProtoTestWriter = apply {
        tag(field, 2); varint(value.size.toLong()); buffer.write(value)
    }

    private fun tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

    private fun varint(value: Long) {
        var x = value
        while (true) {
            val b = (x and 0x7F)
            x = x ushr 7
            if (x != 0L) buffer.writeByte((b or 0x80).toInt()) else { buffer.writeByte(b.toInt()); return }
        }
    }
}

/** Build a `PreferenceMap` from key -> Value-message-bytes pairs. */
internal fun preferencesPb(vararg entries: Pair<String, ByteString>): ByteString {
    val top = ProtoTestWriter()
    for ((key, valueBytes) in entries) {
        val entry = ProtoTestWriter()
            .lenField(1, key.encodeUtf8())
            .lenField(2, valueBytes)
        top.lenField(1, entry.bytes())
    }
    return top.bytes()
}

internal fun vBool(b: Boolean): ByteString = ProtoTestWriter().varintField(1, if (b) 1 else 0).bytes()
internal fun vFloat(f: Float): ByteString = ProtoTestWriter().i32Field(2, f.toRawBits()).bytes()
internal fun vInt(i: Int): ByteString = ProtoTestWriter().varintField(3, i.toLong()).bytes()
internal fun vLong(l: Long): ByteString = ProtoTestWriter().varintField(4, l).bytes()
internal fun vString(s: String): ByteString = ProtoTestWriter().lenField(5, s.encodeUtf8()).bytes()
internal fun vStringSet(vararg s: String): ByteString {
    val inner = ProtoTestWriter()
    s.forEach { inner.lenField(1, it.encodeUtf8()) }
    return ProtoTestWriter().lenField(6, inner.bytes()).bytes()
}
internal fun vDouble(d: Double): ByteString = ProtoTestWriter().i64Field(7, d.toRawBits()).bytes()
internal fun vBytes(b: ByteString): ByteString = ProtoTestWriter().lenField(8, b).bytes()
