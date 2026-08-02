/*
 * Copyright 2026 Joel Kanyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.joelkanyi.peek.core.codec

import okio.Buffer
import okio.BufferedSource
import okio.ByteString

/** Protobuf wire types. See https://protobuf.dev/programming-guides/encoding/. */
internal object WireType {
    const val VARINT = 0
    const val I64 = 1
    const val LEN = 2
    const val I32 = 5
}

/** Thrown when the byte stream is not valid protobuf. */
internal class ProtoParseException(message: String) : Exception(message)

/**
 * A minimal protobuf wire-format reader over Okio. It decodes tags and the four
 * wire types Peek needs; it knows nothing about any schema. The Preferences
 * DataStore codec drives it with the known `preferences.proto` layout; P3's raw
 * decoder will drive it schemalessly.
 */
internal class ProtoReader(private val source: BufferedSource) {

    constructor(bytes: ByteString) : this(Buffer().write(bytes))

    fun exhausted(): Boolean = source.exhausted()

    /** Read a field tag. The field number is `tag ushr 3`, the wire type is `tag and 7`. */
    fun readTag(): Int = readVarint().toInt()

    fun fieldNumber(tag: Int): Int = tag ushr 3

    fun wireType(tag: Int): Int = tag and 0x7

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = source.readByte().toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw ProtoParseException("varint exceeds 64 bits")
    }

    /** 32-bit little-endian fixed field (used for `float`). */
    fun readFixed32(): Int = source.readIntLe()

    /** 64-bit little-endian fixed field (used for `double`). */
    fun readFixed64(): Long = source.readLongLe()

    /** Length-delimited field: a varint length followed by that many bytes. */
    fun readLengthDelimited(): ByteString {
        val length = readVarint()
        if (length < 0 || length > Int.MAX_VALUE) throw ProtoParseException("invalid length $length")
        return source.readByteString(length)
    }

    /** Skip a field of the given [wireType] whose tag was already read. */
    fun skip(wireType: Int) {
        when (wireType) {
            WireType.VARINT -> readVarint()
            WireType.I64 -> source.skip(8)
            WireType.LEN -> source.skip(readVarint())
            WireType.I32 -> source.skip(4)
            else -> throw ProtoParseException("unknown wire type $wireType")
        }
    }
}
