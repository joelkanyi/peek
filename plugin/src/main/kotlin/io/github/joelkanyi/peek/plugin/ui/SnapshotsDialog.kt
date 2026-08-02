package io.github.joelkanyi.peek.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.github.joelkanyi.peek.plugin.services.PeekSnapshotStore
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/** Lists captured snapshots and lets the user rename, delete, or compare them. */
internal class SnapshotsDialog(private val project: Project) : DialogWrapper(project) {

    private val store = PeekSnapshotStore.getInstance()
    private val listModel = DefaultListModel<PeekSnapshotStore.SnapshotBean>()
    private val list = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SimpleListCellRenderer.create("") {
            "${it.name}   ·   ${it.packageName}   ·   ${it.stores.size} stores"
        }
    }

    init {
        title = "Manage Snapshots"
        reload()
        init()
    }

    override fun createCenterPanel(): JComponent {
        val buttons = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyLeft(8)
            add(button("Rename…") { onRename() })
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(button("Delete") { onDelete() })
            add(Box.createVerticalStrut(JBUI.scale(6)))
            add(button("Compare…") { onCompare() })
        }
        return JPanel(BorderLayout()).apply {
            preferredSize = JBUI.size(520, 340)
            add(JBScrollPane(list), BorderLayout.CENTER)
            add(buttons, BorderLayout.EAST)
        }
    }

    private fun button(text: String, action: () -> Unit): JButton =
        JButton(text).apply {
            alignmentX = JComponent.LEFT_ALIGNMENT
            maximumSize = JBUI.size(120, 30)
            addActionListener { action() }
        }

    private fun reload() {
        listModel.clear()
        store.beans().forEach { listModel.addElement(it) }
    }

    private fun onRename() {
        val bean = list.selectedValue ?: return
        val name = Messages.showInputDialog(project, "New name", "Rename Snapshot", null, bean.name, null)?.trim()
        if (!name.isNullOrEmpty()) {
            store.rename(bean, name)
            reload()
        }
    }

    private fun onDelete() {
        val bean = list.selectedValue ?: return
        if (Messages.showYesNoDialog(project, "Delete snapshot \"${bean.name}\"?", "Delete Snapshot", Messages.getWarningIcon()) == Messages.YES) {
            store.remove(bean)
            reload()
        }
    }

    private fun onCompare() {
        val captures = store.beans().map { PeekSnapshotStore.toCapture(it) }
        if (captures.size < 2) {
            Messages.showInfoMessage(project, "Capture at least two snapshots to compare.", "Compare Snapshots")
            return
        }
        CompareDialog(project, captures).show()
    }
}
