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
package io.github.joelkanyi.peek.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.github.joelkanyi.peek.core.model.KvValue
import okio.ByteString
import okio.ByteString.Companion.decodeHex

internal val EDITABLE_TYPES: List<String> =
    listOf("String", "Int", "Long", "Float", "Double", "Boolean", "StringSet", "Bytes")

internal fun defaultForType(index: Int): KvValue = when (index) {
    0 -> KvValue.of("")
    1 -> KvValue.of(0)
    2 -> KvValue.of(0L)
    3 -> KvValue.of(0f)
    4 -> KvValue.of(0.0)
    5 -> KvValue.of(false)
    6 -> KvValue.of(emptySet())
    else -> KvValue.of(ByteString.EMPTY)
}

internal fun promptValue(project: Project, key: String, current: KvValue): KvValue? = when (current) {
    is KvValue.BoolValue -> {
        val choice = Messages.showDialog(
            project,
            "Value for \"$key\"",
            "Edit boolean",
            arrayOf("true", "false", "Cancel"),
            if (current.value) 0 else 1,
            null,
        )
        when (choice) {
            0 -> KvValue.of(true)
            1 -> KvValue.of(false)
            else -> null
        }
    }
    is KvValue.IntValue -> parse(project, key, current.value.toString(), "integer") { it.toIntOrNull()?.let(KvValue::of) }
    is KvValue.LongValue -> parse(project, key, current.value.toString(), "long") { it.toLongOrNull()?.let(KvValue::of) }
    is KvValue.FloatValue -> parse(project, key, current.value.toString(), "float") { it.toFloatOrNull()?.let(KvValue::of) }
    is KvValue.DoubleValue -> parse(project, key, current.value.toString(), "double") { it.toDoubleOrNull()?.let(KvValue::of) }
    is KvValue.StringValue -> promptText(project, key, current.value)?.let { KvValue.of(it) }
    is KvValue.StringSetValue -> promptText(project, key, current.values.joinToString(", "))?.let { text ->
        KvValue.of(text.split(",").map(String::trim).filter(String::isNotEmpty).toSet())
    }
    is KvValue.BytesValue -> parse(project, key, current.value.hex(), "hex") { runCatching { it.decodeHex() }.getOrNull()?.let(KvValue::of) }
    is KvValue.ProtoNode -> null
}

private fun parse(project: Project, key: String, initial: String, what: String, convert: (String) -> KvValue?): KvValue? {
    val text = promptText(project, key, initial) ?: return null
    return convert(text.trim()) ?: run {
        Messages.showErrorDialog(project, "\"$text\" is not a valid $what.", "Invalid Value")
        null
    }
}

private fun promptText(project: Project, key: String, initial: String): String? =
    Messages.showInputDialog(project, "New value for \"$key\"", "Edit Value", null, initial, null)
