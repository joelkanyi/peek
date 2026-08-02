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
import okio.ByteString

/** Minimal protobuf wire writer over Okio: the inverse of [ProtoReader]. */
internal class ProtoWriter {
    private val buffer = Buffer()

    fun toByteString(): ByteString = buffer.snapshot()

    fun varintField(field: Int, value: Long): ProtoWriter = apply {
        tag(field, WireType.VARINT)
        varint(value)
    }

    fun fixed32Field(field: Int, bits: Int): ProtoWriter = apply {
        tag(field, WireType.I32)
        buffer.writeIntLe(bits)
    }

    fun fixed64Field(field: Int, bits: Long): ProtoWriter = apply {
        tag(field, WireType.I64)
        buffer.writeLongLe(bits)
    }

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
            if (x != 0L) {
                buffer.writeByte((b or 0x80).toInt())
            } else {
                buffer.writeByte(b.toInt())
                return
            }
        }
    }
}
