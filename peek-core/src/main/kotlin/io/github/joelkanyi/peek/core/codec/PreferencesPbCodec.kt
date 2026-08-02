package io.github.joelkanyi.peek.core.codec

import io.github.joelkanyi.peek.core.model.KvEntry
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString

/**
 * Decodes a Preferences DataStore `.preferences_pb` file. The file is a plainly
 * serialized `PreferenceMap` message (no header, no framing):
 *
 * ```
 * message PreferenceMap { map<string, Value> preferences = 1; }
 * message Value {
 *   oneof value {
 *     bool boolean = 1; float float = 2; int32 integer = 3; int64 long = 4;
 *     string string = 5; StringSet string_set = 6; double double = 7; bytes bytes = 8;
 *   }
 * }
 * message StringSet { repeated string strings = 1; }
 * ```
 *
 * The schema is decoded by hand over [ProtoReader] rather than pulling protobuf-java
 * onto the IDE classpath.
 */
public class PreferencesPbCodec : StoreCodec {

    override val type: StoreType = StoreType.PREFERENCES_DATASTORE

    override fun decode(handle: StoreHandle, bytes: ByteString, capturedAtEpochMs: Long): DecodeResult {
        return try {
            val entries = ArrayList<KvEntry>()
            val reader = ProtoReader(bytes)
            while (!reader.exhausted()) {
                val tag = reader.readTag()
                if (reader.fieldNumber(tag) == 1 && reader.wireType(tag) == WireType.LEN) {
                    entries.add(decodeEntry(reader.readLengthDelimited()))
                } else {
                    reader.skip(reader.wireType(tag))
                }
            }
            DecodeResult.Decoded(StoreSnapshot(handle, entries, capturedAtEpochMs))
        } catch (e: Exception) {
            DecodeResult.Failed(reason = e.message ?: "unparseable preferences_pb", bytes = bytes)
        }
    }

    /** A single `map<string, Value>` entry: field 1 = key (string), field 2 = value (Value message). */
    private fun decodeEntry(bytes: ByteString): KvEntry {
        val reader = ProtoReader(bytes)
        var key: String? = null
        var value: KvValue? = null
        while (!reader.exhausted()) {
            val tag = reader.readTag()
            when (reader.fieldNumber(tag)) {
                1 -> key = reader.readLengthDelimited().utf8()
                2 -> value = decodeValue(reader.readLengthDelimited())
                else -> reader.skip(reader.wireType(tag))
            }
        }
        return KvEntry(
            key = key ?: throw ProtoParseException("map entry missing key"),
            value = value ?: throw ProtoParseException("map entry missing value"),
        )
    }

    /** The `Value` oneof. Exactly one field is set; unknown fields fall back to raw bytes. */
    private fun decodeValue(bytes: ByteString): KvValue {
        val reader = ProtoReader(bytes)
        var result: KvValue? = null
        while (!reader.exhausted()) {
            val tag = reader.readTag()
            when (reader.fieldNumber(tag)) {
                1 -> result = KvValue.BoolValue(reader.readVarint() != 0L)
                2 -> result = KvValue.FloatValue(Float.fromBits(reader.readFixed32()))
                3 -> result = KvValue.IntValue(reader.readVarint().toInt())
                4 -> result = KvValue.LongValue(reader.readVarint())
                5 -> result = KvValue.StringValue(reader.readLengthDelimited().utf8())
                6 -> result = KvValue.StringSetValue(decodeStringSet(reader.readLengthDelimited()))
                7 -> result = KvValue.DoubleValue(Double.fromBits(reader.readFixed64()))
                8 -> result = KvValue.BytesValue(reader.readLengthDelimited())
                else -> reader.skip(reader.wireType(tag))
            }
        }
        return result ?: KvValue.BytesValue(bytes)
    }

    /** `StringSet { repeated string strings = 1; }`. Insertion order is preserved. */
    private fun decodeStringSet(bytes: ByteString): Set<String> {
        val reader = ProtoReader(bytes)
        val strings = LinkedHashSet<String>()
        while (!reader.exhausted()) {
            val tag = reader.readTag()
            if (reader.fieldNumber(tag) == 1 && reader.wireType(tag) == WireType.LEN) {
                strings.add(reader.readLengthDelimited().utf8())
            } else {
                reader.skip(reader.wireType(tag))
            }
        }
        return strings
    }
}
