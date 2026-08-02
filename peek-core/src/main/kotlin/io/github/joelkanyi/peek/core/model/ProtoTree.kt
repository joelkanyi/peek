package io.github.joelkanyi.peek.core.model

import okio.ByteString

/**
 * A field of a schemaless protobuf message: its wire field number and a decoded
 * value. Without the `.proto` schema, field names are unknown, so only numbers
 * are shown (P5 resolves names from the project).
 */
public class ProtoField internal constructor(
    public val number: Int,
    public val value: ProtoValue,
)

/** A decoded protobuf value. Length-delimited fields are guessed as message, text, or raw bytes. */
public sealed interface ProtoValue {

    public class Varint internal constructor(public val value: Long) : ProtoValue

    public class Fixed32 internal constructor(public val value: Int) : ProtoValue

    public class Fixed64 internal constructor(public val value: Long) : ProtoValue

    public class Text internal constructor(public val value: String) : ProtoValue

    public class Bytes internal constructor(public val value: ByteString) : ProtoValue

    public class Message internal constructor(public val fields: List<ProtoField>) : ProtoValue
}
