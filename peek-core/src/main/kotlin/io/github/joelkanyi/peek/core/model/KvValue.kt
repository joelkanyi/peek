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
package io.github.joelkanyi.peek.core.model

import okio.ByteString

/**
 * A stored value, keeping its real on-disk type. Handling stays exhaustive via
 * `when`; [ProtoNode] is the raw-decoded tree for schemaless Proto DataStore (P3).
 */
public sealed interface KvValue {

    public class BoolValue internal constructor(public val value: Boolean) : KvValue

    public class IntValue internal constructor(public val value: Int) : KvValue

    public class LongValue internal constructor(public val value: Long) : KvValue

    public class FloatValue internal constructor(public val value: Float) : KvValue

    public class DoubleValue internal constructor(public val value: Double) : KvValue

    public class StringValue internal constructor(public val value: String) : KvValue

    public class StringSetValue internal constructor(public val values: Set<String>) : KvValue

    public class BytesValue internal constructor(public val value: ByteString) : KvValue

    /** Raw-decoded protobuf tree (schemaless Proto DataStore). [bytes] is kept for exact diffing. */
    public class ProtoNode internal constructor(
        public val fields: List<ProtoField>,
        public val bytes: ByteString,
    ) : KvValue

    public companion object {
        public fun of(value: Boolean): KvValue = BoolValue(value)
        public fun of(value: Int): KvValue = IntValue(value)
        public fun of(value: Long): KvValue = LongValue(value)
        public fun of(value: Float): KvValue = FloatValue(value)
        public fun of(value: Double): KvValue = DoubleValue(value)
        public fun of(value: String): KvValue = StringValue(value)
        public fun of(values: Set<String>): KvValue = StringSetValue(values)
        public fun of(value: ByteString): KvValue = BytesValue(value)
    }
}

/** Structural equality for diffing (the value classes use identity equality). */
internal fun KvValue.sameAs(other: KvValue): Boolean = when (this) {
    is KvValue.BoolValue -> other is KvValue.BoolValue && value == other.value
    is KvValue.IntValue -> other is KvValue.IntValue && value == other.value
    is KvValue.LongValue -> other is KvValue.LongValue && value == other.value
    is KvValue.FloatValue -> other is KvValue.FloatValue && value == other.value
    is KvValue.DoubleValue -> other is KvValue.DoubleValue && value == other.value
    is KvValue.StringValue -> other is KvValue.StringValue && value == other.value
    is KvValue.StringSetValue -> other is KvValue.StringSetValue && values == other.values
    is KvValue.BytesValue -> other is KvValue.BytesValue && value == other.value
    is KvValue.ProtoNode -> other is KvValue.ProtoNode && bytes == other.bytes
}
