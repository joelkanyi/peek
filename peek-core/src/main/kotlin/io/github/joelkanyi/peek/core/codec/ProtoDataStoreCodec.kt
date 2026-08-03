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

import io.github.joelkanyi.peek.core.model.KvEntry
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.ProtoField
import io.github.joelkanyi.peek.core.model.ProtoValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/** Decodes an arbitrary Proto DataStore `.pb` file without its schema, like `protoc --decode_raw`. */
public class ProtoDataStoreCodec : StoreCodec {

    override val type: StoreType = StoreType.PROTO_DATASTORE

    override fun decode(handle: StoreHandle, bytes: ByteString, capturedAtEpochMs: Long): DecodeResult = try {
        val fields = parseMessage(bytes, depth = 0)
        val entry = KvEntry(handle.displayName, KvValue.ProtoNode(fields, bytes))
        DecodeResult.Decoded(StoreSnapshot(handle, listOf(entry), capturedAtEpochMs))
    } catch (e: Exception) {
        DecodeResult.Failed(reason = e.message ?: "not valid protobuf", bytes = bytes)
    }

    override fun encode(snapshot: StoreSnapshot): ByteString =
        throw UnsupportedOperationException("schemaless proto stores are read-only until schema resolution (P5/P7)")

    private fun parseMessage(bytes: ByteString, depth: Int): List<ProtoField> {
        val reader = ProtoReader(bytes)
        val fields = ArrayList<ProtoField>()
        while (!reader.exhausted()) {
            val tag = reader.readTag()
            val value = when (reader.wireType(tag)) {
                WireType.VARINT -> ProtoValue.Varint(reader.readVarint())
                WireType.I64 -> ProtoValue.Fixed64(reader.readFixed64())
                WireType.I32 -> ProtoValue.Fixed32(reader.readFixed32())
                WireType.LEN -> classify(reader.readLengthDelimited(), depth)
                else -> throw ProtoParseException("unknown wire type ${reader.wireType(tag)}")
            }
            fields.add(ProtoField(reader.fieldNumber(tag), value))
        }
        return fields
    }

    /** Guess a length-delimited value: nested message, then UTF-8 text, then raw bytes. */
    private fun classify(bytes: ByteString, depth: Int): ProtoValue {
        if (depth < MAX_DEPTH) {
            val nested = runCatching { parseMessage(bytes, depth + 1) }.getOrNull()
            if (!nested.isNullOrEmpty()) return ProtoValue.Message(nested)
        }
        asText(bytes)?.let { return ProtoValue.Text(it) }
        return ProtoValue.Bytes(bytes)
    }

    /** Returns the text only if [bytes] is valid, printable UTF-8. */
    private fun asText(bytes: ByteString): String? {
        if (bytes.size == 0) return ""
        val text = bytes.utf8() // lenient: invalid bytes become the replacement char
        if (text.encodeUtf8() != bytes) return null // re-encoding differs => not valid UTF-8
        if (text.any { it.isISOControl() && it != '\n' && it != '\t' && it != '\r' }) return null
        return text
    }

    private companion object {
        const val MAX_DEPTH = 20
    }
}
