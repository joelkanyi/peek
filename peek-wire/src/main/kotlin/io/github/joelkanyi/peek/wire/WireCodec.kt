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
package io.github.joelkanyi.peek.wire

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/** Thrown when a byte stream is not a valid Peek wire message. */
public class WireFormatException(message: String) : Exception(message)

/**
 * Encodes and decodes [Message]s to a compact binary form, and reads/writes them
 * as length-prefixed frames (4-byte big-endian length, then the payload).
 */
public object WireCodec {

    public fun encode(message: Message): ByteString {
        val sink = Buffer()
        when (message) {
            is Message.Hello -> sink.writeByte(1).writeInt(message.protocolVersion)
            Message.ListStores -> sink.writeByte(2)
            is Message.ReadStore -> sink.writeByte(3).writeStr(message.storeId)
            is Message.PutValue -> sink.writeByte(4).writeStr(message.storeId).writeStr(message.key).writeValue(message.value)
            is Message.RemoveKey -> sink.writeByte(5).writeStr(message.storeId).writeStr(message.key)
            is Message.Welcome -> sink.writeByte(6).writeInt(message.protocolVersion).writeStr(message.appPackage)
            is Message.StoreList -> {
                sink.writeByte(7).writeInt(message.stores.size)
                message.stores.forEach { sink.writeStr(it.id).writeStr(it.displayName).writeByte(it.kind.ordinal) }
            }
            is Message.StoreData -> {
                sink.writeByte(8).writeStr(message.storeId).writeInt(message.entries.size)
                message.entries.forEach { sink.writeStr(it.key).writeValue(it.value) }
            }
            is Message.Changed -> sink.writeByte(9).writeStr(message.storeId)
            Message.Ok -> sink.writeByte(10)
            is Message.Err -> sink.writeByte(11).writeStr(message.message)
        }
        return sink.readByteString()
    }

    public fun decode(bytes: ByteString): Message {
        val source = Buffer().write(bytes)
        return try {
            when (val tag = source.readByte().toInt()) {
                1 -> Message.Hello(source.readInt())
                2 -> Message.ListStores
                3 -> Message.ReadStore(source.readStr())
                4 -> Message.PutValue(source.readStr(), source.readStr(), source.readValue())
                5 -> Message.RemoveKey(source.readStr(), source.readStr())
                6 -> Message.Welcome(source.readInt(), source.readStr())
                7 -> Message.StoreList(List(source.readInt()) { StoreInfo(source.readStr(), source.readStr(), StoreKind.entries[source.readByte().toInt()]) })
                8 -> Message.StoreData(source.readStr(), List(source.readInt()) { WireEntry(source.readStr(), source.readValue()) })
                9 -> Message.Changed(source.readStr())
                10 -> Message.Ok
                11 -> Message.Err(source.readStr())
                else -> throw WireFormatException("unknown message tag $tag")
            }
        } catch (e: WireFormatException) {
            throw e
        } catch (e: Exception) {
            throw WireFormatException(e.message ?: "malformed message")
        }
    }

    /** Write [message] as a length-prefixed frame. */
    public fun writeFrame(sink: BufferedSink, message: Message) {
        val payload = encode(message)
        sink.writeInt(payload.size)
        sink.write(payload)
        sink.flush()
    }

    /** Read one length-prefixed frame, or `null` at end of stream. */
    public fun readFrame(source: BufferedSource): Message? {
        if (!source.request(4)) return null
        val length = source.readInt()
        if (length < 0) throw WireFormatException("negative frame length $length")
        return decode(source.readByteString(length.toLong()))
    }

    private fun BufferedSink.writeStr(value: String): BufferedSink = apply {
        val bytes = value.encodeUtf8()
        writeInt(bytes.size)
        write(bytes)
    }

    private fun BufferedSink.writeValue(value: WireValue): BufferedSink = apply {
        when (value) {
            is WireValue.BoolValue -> writeByte(1).writeByte(if (value.value) 1 else 0)
            is WireValue.IntValue -> writeByte(2).writeInt(value.value)
            is WireValue.LongValue -> writeByte(3).writeLong(value.value)
            is WireValue.FloatValue -> writeByte(4).writeInt(value.value.toRawBits())
            is WireValue.DoubleValue -> writeByte(5).writeLong(value.value.toRawBits())
            is WireValue.StringValue -> writeByte(6).writeStr(value.value)
            is WireValue.StringSetValue -> {
                writeByte(7).writeInt(value.values.size)
                value.values.forEach { writeStr(it) }
            }
            is WireValue.BytesValue -> {
                writeByte(8).writeInt(value.value.size)
                write(value.value)
            }
        }
    }

    private fun BufferedSource.readStr(): String = readByteString(readInt().toLong()).utf8()

    private fun BufferedSource.readValue(): WireValue = when (val tag = readByte().toInt()) {
        1 -> WireValue.BoolValue(readByte().toInt() != 0)
        2 -> WireValue.IntValue(readInt())
        3 -> WireValue.LongValue(readLong())
        4 -> WireValue.FloatValue(Float.fromBits(readInt()))
        5 -> WireValue.DoubleValue(Double.fromBits(readLong()))
        6 -> WireValue.StringValue(readStr())
        7 -> WireValue.StringSetValue(List(readInt()) { readStr() })
        8 -> WireValue.BytesValue(readByteString(readInt().toLong()))
        else -> throw WireFormatException("unknown value tag $tag")
    }
}
