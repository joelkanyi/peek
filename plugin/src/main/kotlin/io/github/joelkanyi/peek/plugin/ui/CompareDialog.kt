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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import io.github.joelkanyi.peek.core.model.Capture
import io.github.joelkanyi.peek.core.session.Presence
import io.github.joelkanyi.peek.core.session.diffCaptures
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/** Picks two snapshots and shows what changed between them as a tree. */
internal class CompareDialog(project: Project, private val captures: List<Capture>) : DialogWrapper(project) {

    private val beforeCombo = ComboBox(captures.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { "${it.name}  (${it.stores.size} stores)" }
    }
    private val afterCombo = ComboBox(captures.toTypedArray()).apply {
        renderer = SimpleListCellRenderer.create("") { "${it.name}  (${it.stores.size} stores)" }
        if (captures.size > 1) selectedIndex = captures.size - 1
    }
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Choose two snapshots and Compare"))
    private val tree = Tree(treeModel)

    init {
        title = "Compare Snapshots"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val pickers = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            add(JBLabel("Before:"))
            add(beforeCombo)
            add(JBLabel("After:"))
            add(afterCombo)
            add(JButton("Compare").apply { addActionListener { compare() } })
        }
        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(560, 420)
            add(pickers, BorderLayout.NORTH)
            add(JBScrollPane(tree), BorderLayout.CENTER)
        }
    }

    private fun compare() {
        val before = beforeCombo.selectedItem as? Capture ?: return
        val after = afterCombo.selectedItem as? Capture ?: return
        val root = DefaultMutableTreeNode("${before.name}  →  ${after.name}")
        var anyChange = false
        for (delta in diffCaptures(before, after).stores) {
            if (delta.presence == Presence.BOTH && delta.isEmpty) continue
            anyChange = true
            val storeNode = DefaultMutableTreeNode(
                when (delta.presence) {
                    Presence.BEFORE_ONLY -> "${delta.displayName}  (store removed)"
                    Presence.AFTER_ONLY -> "${delta.displayName}  (store added)"
                    Presence.BOTH -> delta.displayName
                },
            )
            delta.added.forEach { storeNode.add(DefaultMutableTreeNode("+ $it  (added)")) }
            delta.changed.forEach { storeNode.add(DefaultMutableTreeNode("~ $it  (changed)")) }
            delta.removed.forEach { storeNode.add(DefaultMutableTreeNode("- $it  (removed)")) }
            root.add(storeNode)
        }
        if (!anyChange) root.add(DefaultMutableTreeNode("No differences"))
        treeModel.setRoot(root)
        expandAll()
    }

    private fun expandAll() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }
}
