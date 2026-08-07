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

import io.github.joelkanyi.peek.core.model.KvEntry
import javax.swing.table.AbstractTableModel

internal class StoreTableModel : AbstractTableModel() {

    private var entries: List<KvEntry> = emptyList()
    private var rows: List<List<String>> = emptyList()

    fun setEntries(entries: List<KvEntry>) {
        val rows = entries.map { listOf(it.key, it.value.typeLabel(), it.value.display()) }
        this.entries = entries
        // A poll that re-reads the same store hands back visually identical rows; repainting them flashes the table, so skip it.
        if (rows == this.rows) return
        this.rows = rows
        fireTableDataChanged()
    }

    fun entryAt(row: Int): KvEntry? = entries.getOrNull(row)

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
