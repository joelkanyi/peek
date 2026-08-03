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

import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.ProtoField
import io.github.joelkanyi.peek.core.model.ProtoValue
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel

internal fun buildProtoTreeModel(rootLabel: String, node: KvValue.ProtoNode): TreeModel {
    val root = DefaultMutableTreeNode(rootLabel)
    addFields(root, node.fields)
    return DefaultTreeModel(root)
}

private fun addFields(parent: DefaultMutableTreeNode, fields: List<ProtoField>) {
    for (field in fields) {
        when (val value = field.value) {
            is ProtoValue.Message -> {
                val messageNode = DefaultMutableTreeNode("field ${field.number}  ·  message (${value.fields.size})")
                addFields(messageNode, value.fields)
                parent.add(messageNode)
            }
            else -> parent.add(DefaultMutableTreeNode("field ${field.number}:  ${value.summary()}"))
        }
    }
}

private fun ProtoValue.summary(): String = when (this) {
    is ProtoValue.Varint -> "$value  (varint)"
    is ProtoValue.Fixed32 -> "$value  (i32)"
    is ProtoValue.Fixed64 -> "$value  (i64)"
    is ProtoValue.Text -> "\"$value\"  (string)"
    is ProtoValue.Bytes -> "${value.hex().take(48)}${if (value.size > 24) "…" else ""}  (${value.size} bytes)"
    is ProtoValue.Message -> "message (${fields.size})"
}
