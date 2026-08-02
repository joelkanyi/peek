package io.github.joelkanyi.peek.plugin.ui

import io.github.joelkanyi.peek.core.model.KvEntry
import javax.swing.table.AbstractTableModel

/** Table model for one store's entries: Key, Type, Value. Read-only in P1. */
internal class StoreTableModel : AbstractTableModel() {

    private var entries: List<KvEntry> = emptyList()

    fun setEntries(entries: List<KvEntry>) {
        this.entries = entries
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = entries.size

    override fun getColumnCount(): Int = 3

    override fun getColumnName(column: Int): String = when (column) {
        0 -> "Key"
        1 -> "Type"
        else -> "Value"
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val entry = entries[rowIndex]
        return when (columnIndex) {
            0 -> entry.key
            1 -> entry.value.typeLabel()
            else -> entry.value.display()
        }
    }
}
