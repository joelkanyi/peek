package io.github.joelkanyi.peek.plugin.ui

import io.github.joelkanyi.peek.core.model.KvValue

/** Short type label shown in the Type column. */
internal fun KvValue.typeLabel(): String = when (this) {
    is KvValue.BoolValue -> "Boolean"
    is KvValue.IntValue -> "Int"
    is KvValue.LongValue -> "Long"
    is KvValue.FloatValue -> "Float"
    is KvValue.DoubleValue -> "Double"
    is KvValue.StringValue -> "String"
    is KvValue.StringSetValue -> "StringSet"
    is KvValue.BytesValue -> "Bytes"
    is KvValue.ProtoNode -> "Proto"
}

/** Human-readable value for the Value column. */
internal fun KvValue.display(): String = when (this) {
    is KvValue.BoolValue -> value.toString()
    is KvValue.IntValue -> value.toString()
    is KvValue.LongValue -> value.toString()
    is KvValue.FloatValue -> value.toString()
    is KvValue.DoubleValue -> value.toString()
    is KvValue.StringValue -> value
    is KvValue.StringSetValue -> values.joinToString(", ", "{", "}")
    is KvValue.BytesValue -> value.hex()
    is KvValue.ProtoNode -> "<proto tree>"
}
