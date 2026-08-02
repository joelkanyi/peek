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

    /** Raw-decoded protobuf tree. Populated in P3; renders as "unsupported" in P1. */
    public class ProtoNode internal constructor() : KvValue

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
