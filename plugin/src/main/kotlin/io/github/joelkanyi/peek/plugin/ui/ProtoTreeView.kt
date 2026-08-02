package io.github.joelkanyi.peek.plugin.ui

import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.ProtoField
import io.github.joelkanyi.peek.core.model.ProtoValue
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeModel

/** Builds a Swing tree model from a decoded proto message. */
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
