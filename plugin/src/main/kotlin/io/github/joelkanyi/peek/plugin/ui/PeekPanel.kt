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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import io.github.joelkanyi.peek.core.error.PeekError
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreType
import io.github.joelkanyi.peek.core.session.AgentSession
import io.github.joelkanyi.peek.core.session.PeekSession
import io.github.joelkanyi.peek.core.session.SessionState
import io.github.joelkanyi.peek.core.session.StoreSession
import io.github.joelkanyi.peek.core.session.StoreState
import io.github.joelkanyi.peek.core.session.WriteOutcome
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.plugin.PeekBundle
import io.github.joelkanyi.peek.plugin.adb.AgentConnector
import io.github.joelkanyi.peek.plugin.services.PeekProjectService
import io.github.joelkanyi.peek.plugin.services.PeekSnapshotStore
import io.github.joelkanyi.peek.plugin.transport.TransportProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * The Peek tool window. A device/app picker with an icon action toolbar; stores
 * on the left, the selected store's entries (table) or proto tree on the right.
 * Auto-refreshes while visible; changed/added keys are highlighted; editing and
 * snapshots run through the toolbar and its overflow menu.
 */
internal class PeekPanel(private val project: Project) {

    private val scope: CoroutineScope = project.service<PeekProjectService>().scope
    private val transport: DeviceTransport? =
        TransportProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.createTransport() }
    private val canWrite: Boolean = transport?.capabilities?.canWrite == true

    private val deviceCombo = ComboBox<Device>().apply {
        renderer = SimpleListCellRenderer.create("") { "${it.model}  ${it.serial}" }
    }
    private val appCombo = ComboBox<String>().apply { setMinimumAndPreferredWidth(JBUI.scale(280)) }
    private val statusLabel = JBLabel().apply { border = JBUI.Borders.empty(4, 10) }

    private val storeListModel = DefaultListModel<StoreState>()
    private val storeList = JBList(storeListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        border = JBUI.Borders.empty(2)
        cellRenderer = SimpleListCellRenderer.create("") { "${it.handle.displayName}   ${it.handle.type.label()}" }
    }
    private val tableModel = StoreTableModel()
    private val table = JBTable(tableModel).apply {
        setShowGrid(false)
        rowHeight = JBUI.scale(22)
        emptyText.text = PeekBundle.message("peek.empty.pickApp")
    }
    private val tree = Tree().apply { isRootVisible = true }
    private val detailCards = JPanel(CardLayout())

    private val editActions = listOf(AddKeyAction(), DeleteKeyAction())
    private val addPathAction = AddPathAction()
    private val manageSnapshotsAction = ManageSnapshotsAction()

    private var suppressEvents = false
    private var visible = true
    private var currentHandle: StoreHandle? = null
    private var highlightAdded: Set<String> = emptySet()
    private var highlightChanged: Set<String> = emptySet()
    private var session: StoreSession? = null
    private var live: Boolean = false
    private var collectJob: Job? = null

    val component: JComponent = build()

    private fun build(): JComponent {
        val root = JPanel(BorderLayout())
        if (transport == null) {
            root.add(JBLabel(PeekBundle.message("peek.emptyState.noTransport")), BorderLayout.CENTER)
            return root
        }

        ComboboxSpeedSearch.installSpeedSearch(appCombo) { it }
        installHighlightRenderer()

        val pickers = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(3))).apply {
            add(JBLabel(PeekBundle.message("peek.label.device")))
            add(deviceCombo)
            add(JBLabel(PeekBundle.message("peek.label.app")))
            add(appCombo)
        }
        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyLeft(4)
            add(pickers, BorderLayout.WEST)
            add(buildToolbar().component, BorderLayout.EAST)
        }

        detailCards.add(JBScrollPane(table), CARD_TABLE)
        detailCards.add(JBScrollPane(tree), CARD_TREE)

        val splitter = OnePixelSplitter(false, 0.28f).apply {
            firstComponent = JBScrollPane(storeList)
            secondComponent = detailCards
        }

        root.add(header, BorderLayout.NORTH)
        root.add(splitter, BorderLayout.CENTER)
        root.add(statusLabel, BorderLayout.SOUTH)

        deviceCombo.addActionListener { if (!suppressEvents) onDeviceChosen() }
        appCombo.addActionListener { if (!suppressEvents) onRefresh() }
        storeList.addListSelectionListener { if (!it.valueIsAdjusting && !suppressEvents) showSelectedStore() }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && canWrite) onEditSelected()
            }
        })

        loadDevices()
        return root
    }

    private fun buildToolbar(): ActionToolbar {
        val group = DefaultActionGroup().apply {
            add(RefreshAction())
            addSeparator()
            editActions.forEach { add(it) }
            addSeparator()
            add(SnapshotAction())
            add(MoreGroup())
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("Peek.Toolbar", group, true)
        toolbar.targetComponent = detailCards
        return toolbar
    }

    /** Secondary actions for the tool window's gear (three-dots) menu. */
    fun gearActions(): ActionGroup = DefaultActionGroup().apply {
        add(addPathAction)
        add(manageSnapshotsAction)
    }

    private fun installHighlightRenderer() {
        table.setDefaultRenderer(
            Any::class.java,
            object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    t: JTable,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    val c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column)
                    if (!isSelected) {
                        val key = tableModel.getValueAt(t.convertRowIndexToModel(row), 0) as? String ?: ""
                        c.background = when (key) {
                            in highlightAdded -> ADDED_BG
                            in highlightChanged -> CHANGED_BG
                            else -> t.background
                        }
                    }
                    return c
                }
            },
        )
    }

    private fun loadDevices() = scope.launch {
        val devices = runCatching { transport!!.listDevices() }
            .getOrElse {
                withContext(Dispatchers.EDT) { status(PeekBundle.message("peek.status.adbError", it.message ?: "")) }
                return@launch
            }
        withContext(Dispatchers.EDT) {
            suppressEvents = true
            deviceCombo.model = DefaultComboBoxModel(devices.toTypedArray())
            suppressEvents = false
            if (devices.isEmpty()) status(PeekBundle.message("peek.status.noDevices")) else onDeviceChosen()
        }
    }

    private fun onDeviceChosen() {
        val device = deviceCombo.selectedItem as? Device ?: return
        stopSession()
        clearStores()
        status(PeekBundle.message("peek.status.loadingApps"))
        scope.launch {
            val apps = runCatching { transport!!.listDebuggableProcesses(device) }.getOrDefault(emptyList())
            withContext(Dispatchers.EDT) {
                suppressEvents = true
                appCombo.model = DefaultComboBoxModel(apps.map { it.packageName }.toTypedArray())
                appCombo.selectedItem = null
                suppressEvents = false
                status(PeekBundle.message(if (apps.isEmpty()) "peek.status.noApps" else "peek.status.pickApp"))
            }
        }
    }

    private fun onRefresh() {
        val device = deviceCombo.selectedItem as? Device ?: run {
            status(PeekBundle.message("peek.status.pickDevice"))
            return
        }
        val packageName = (appCombo.selectedItem as? String)?.trim().orEmpty()
        if (packageName.isEmpty()) {
            status(PeekBundle.message("peek.status.pickApp"))
            return
        }
        startSession(device, AppPackage(packageName, pid = null))
    }

    private fun startSession(device: Device, pkg: AppPackage) {
        stopSession()
        table.setPaintBusy(true)
        status(PeekBundle.message("peek.status.loading"))
        scope.launch {
            // Prefer the on-device agent (live) if it answers a handshake; else adb files.
            val agent = withContext(Dispatchers.IO) { AgentConnector.open(device.serial, pkg.packageName) }
                ?.let { socket -> AgentSession(socket, pkg, scope).also { it.connect() } }
            val agentReady = agent != null &&
                withTimeoutOrNull(AGENT_HANDSHAKE_MS) { agent.state.first { it is SessionState.Active } } != null

            withContext(Dispatchers.EDT) {
                val chosen: StoreSession = if (agentReady) {
                    live = true
                    agent!!
                } else {
                    agent?.close()
                    live = false
                    PeekSession(transport!!, device, pkg, scope).also {
                        it.refresh()
                        if (visible) it.startPolling()
                    }
                }
                session = chosen
                collectJob = scope.launch(Dispatchers.EDT) { chosen.state.collect { render(it) } }
            }
        }
    }

    private fun stopSession() {
        collectJob?.cancel()
        session?.close()
        session = null
    }

    fun onVisibilityChanged(nowVisible: Boolean) {
        visible = nowVisible
        val current = session ?: return
        if (nowVisible) current.startPolling() else current.stopPolling()
    }

    private fun render(state: SessionState) {
        when (state) {
            SessionState.Connecting -> status(PeekBundle.message("peek.status.loading"))
            is SessionState.Failed -> {
                table.setPaintBusy(false)
                clearStores()
                status(errorMessage(state.error))
            }
            is SessionState.Paused -> {
                table.setPaintBusy(false)
                clearStores()
                status(PeekBundle.message("peek.status.paused"))
            }
            is SessionState.Active -> {
                table.setPaintBusy(false)
                val keepPath = storeList.selectedValue?.handle?.path
                suppressEvents = true
                storeListModel.clear()
                state.stores.forEach { storeListModel.addElement(it) }
                val keepIndex = state.stores.indexOfFirst { it.handle.path == keepPath }
                suppressEvents = false
                status(PeekBundle.message(if (live) "peek.status.storesLive" else "peek.status.stores", state.stores.size))
                if (!storeListModel.isEmpty) storeList.selectedIndex = if (keepIndex >= 0) keepIndex else 0 else tableModel.setEntries(emptyList())
            }
        }
    }

    private fun showSelectedStore() {
        when (val store = storeList.selectedValue) {
            is StoreState.Loaded -> showLoaded(store)
            is StoreState.Unparseable -> {
                currentHandle = null
                highlightAdded = emptySet()
                highlightChanged = emptySet()
                tableModel.setEntries(emptyList())
                showCard(CARD_TABLE)
                status(PeekBundle.message("peek.status.unparseable", store.reason))
            }
            else -> tableModel.setEntries(emptyList())
        }
    }

    private fun showLoaded(store: StoreState.Loaded) {
        val protoNode = store.snapshot.entries.singleOrNull()?.value as? KvValue.ProtoNode
        if (protoNode != null) {
            currentHandle = null
            tree.model = buildProtoTreeModel(store.handle.displayName, protoNode)
            showCard(CARD_TREE)
            status(PeekBundle.message("peek.status.protoFields", protoNode.fields.size, store.handle.displayName))
            return
        }
        currentHandle = store.handle
        highlightAdded = store.diff.added
        highlightChanged = store.diff.changed
        tableModel.setEntries(store.snapshot.entries)
        showCard(CARD_TABLE)
        val changedCount = store.diff.added.size + store.diff.changed.size
        status(
            if (changedCount > 0) {
                PeekBundle.message("peek.status.entriesChanged", store.snapshot.entries.size, store.handle.displayName, changedCount)
            } else {
                PeekBundle.message("peek.status.entries", store.snapshot.entries.size, store.handle.displayName)
            },
        )
    }

    private fun showCard(card: String) = (detailCards.layout as CardLayout).show(detailCards, card)

    private fun onEditSelected() {
        val handle = currentHandle ?: return
        val entry = tableModel.entryAt(table.convertRowIndexToModel(table.selectedRow.takeIf { it >= 0 } ?: return)) ?: return
        val newValue = promptValue(project, entry.key, entry.value) ?: return
        confirmAndApply(PeekBundle.message("peek.edit.confirm", currentApp())) { session?.putValue(handle, entry.key, newValue) }
    }

    private fun onAddKey() {
        val handle = currentHandle ?: return
        val key = Messages.showInputDialog(project, PeekBundle.message("peek.addKey.message"), PeekBundle.message("peek.addKey.title"), null)?.trim()
        if (key.isNullOrEmpty()) return
        val typeIndex = Messages.showDialog(project, PeekBundle.message("peek.chooseType.message"), PeekBundle.message("peek.chooseType.title"), EDITABLE_TYPES.toTypedArray(), 0, null)
        if (typeIndex < 0) return
        val value = promptValue(project, key, defaultForType(typeIndex)) ?: return
        confirmAndApply(PeekBundle.message("peek.edit.confirm", currentApp())) { session?.putValue(handle, key, value) }
    }

    private fun onDeleteSelected() {
        val handle = currentHandle ?: return
        val entry = tableModel.entryAt(table.convertRowIndexToModel(table.selectedRow.takeIf { it >= 0 } ?: return)) ?: return
        confirmAndApply(PeekBundle.message("peek.edit.deleteConfirm", entry.key, currentApp())) { session?.removeKey(handle, entry.key) }
    }

    private fun confirmAndApply(message: String, action: suspend () -> WriteOutcome?) {
        if (Messages.showYesNoDialog(project, message, PeekBundle.message("peek.edit.title"), Messages.getWarningIcon()) != Messages.YES) return
        table.setPaintBusy(true)
        scope.launch {
            val outcome = runCatching { action() }.getOrNull()
            withContext(Dispatchers.EDT) {
                table.setPaintBusy(false)
                status(outcomeStatus(outcome))
            }
        }
    }

    private fun onSnapshot() {
        val current = session ?: run {
            status(PeekBundle.message("peek.status.pickApp"))
            return
        }
        val name = Messages.showInputDialog(
            project,
            PeekBundle.message("peek.snapshot.message"),
            PeekBundle.message("peek.snapshot.title"),
            null,
            "Snapshot ${PeekSnapshotStore.getInstance().beans().size + 1}",
            null,
        )?.trim()
        if (name.isNullOrEmpty()) return
        val pkg = currentApp()
        scope.launch {
            val capture = runCatching { current.capture(name) }.getOrNull()
            withContext(Dispatchers.EDT) {
                if (capture == null) {
                    status(PeekBundle.message("peek.status.snapshotFailed"))
                } else {
                    PeekSnapshotStore.getInstance().add(capture, pkg)
                    status(PeekBundle.message("peek.status.captured", name, capture.stores.size))
                }
            }
        }
    }

    private fun onAddPath() {
        val current = session ?: run {
            status(PeekBundle.message("peek.status.pickApp"))
            return
        }
        val path = Messages.showInputDialog(project, PeekBundle.message("peek.addPath.message"), PeekBundle.message("peek.addPath.title"), null)?.trim()
        if (!path.isNullOrEmpty()) current.addCustomPath(path)
    }

    private fun clearStores() {
        suppressEvents = true
        storeListModel.clear()
        suppressEvents = false
        currentHandle = null
        highlightAdded = emptySet()
        highlightChanged = emptySet()
        tableModel.setEntries(emptyList())
    }

    private fun status(text: String) {
        statusLabel.text = text
    }

    private fun currentApp(): String = (appCombo.selectedItem as? String) ?: "the app"

    private fun outcomeStatus(outcome: WriteOutcome?): String = when (outcome) {
        WriteOutcome.Applied -> PeekBundle.message("peek.status.savedLive")
        WriteOutcome.AppliedRequiresAppRestart -> PeekBundle.message("peek.status.savedRestart")
        is WriteOutcome.Refused -> PeekBundle.message("peek.status.notSaved", outcome.reason)
        null -> PeekBundle.message("peek.status.notSaved", "")
    }

    private fun errorMessage(error: PeekError): String = when (error) {
        PeekError.AdbUnavailable -> PeekBundle.message("peek.status.adbError", "")
        is PeekError.NotDebuggable -> PeekBundle.message("peek.status.notDebuggable", error.pkg)
        is PeekError.PackageNotFound -> PeekBundle.message("peek.status.packageNotFound", error.pkg)
        is PeekError.DeviceLost -> PeekBundle.message("peek.status.paused")
        is PeekError.FileVanished -> PeekBundle.message("peek.status.unparseable", error.path)
        is PeekError.ParseFailed -> PeekBundle.message("peek.status.unparseable", error.reason)
        is PeekError.TransportFailure -> PeekBundle.message("peek.status.adbError", error.message)
    }

    // --- Actions ---

    private inner class RefreshAction : AnAction(PeekBundle.message("peek.action.refresh"), null, AllIcons.Actions.Refresh) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = session != null
        }
        override fun actionPerformed(e: AnActionEvent) = onRefresh()
    }

    private inner class AddKeyAction : AnAction(PeekBundle.message("peek.action.add"), null, AllIcons.General.Add) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = canWrite && currentHandle != null
        }
        override fun actionPerformed(e: AnActionEvent) = onAddKey()
    }

    private inner class DeleteKeyAction : AnAction(PeekBundle.message("peek.action.delete"), null, AllIcons.General.Remove) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = canWrite && currentHandle != null && table.selectedRow >= 0
        }
        override fun actionPerformed(e: AnActionEvent) = onDeleteSelected()
    }

    private inner class SnapshotAction : AnAction(PeekBundle.message("peek.action.snapshot"), null, AllIcons.Vcs.ShelveSilent) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = session != null
        }
        override fun actionPerformed(e: AnActionEvent) = onSnapshot()
    }

    private inner class AddPathAction : AnAction(PeekBundle.message("peek.action.addPath"), null, AllIcons.Nodes.Folder) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = session != null
        }
        override fun actionPerformed(e: AnActionEvent) = onAddPath()
    }

    private inner class ManageSnapshotsAction : AnAction(PeekBundle.message("peek.action.manageSnapshots"), null, AllIcons.Vcs.History) {
        override fun actionPerformed(e: AnActionEvent) {
            SnapshotsDialog(project).show()
        }
    }

    private inner class MoreGroup : DefaultActionGroup(PeekBundle.message("peek.action.more"), true) {
        init {
            templatePresentation.icon = AllIcons.Actions.More
            add(addPathAction)
            add(manageSnapshotsAction)
        }
    }

    private companion object {
        const val AGENT_HANDSHAKE_MS = 2_000L
        const val CARD_TABLE = "table"
        const val CARD_TREE = "tree"
        val ADDED_BG = JBColor(Color(0xDD, 0xF3, 0xE0), Color(0x2B, 0x3B, 0x2E))
        val CHANGED_BG = JBColor(Color(0xFB, 0xF1, 0xD0), Color(0x3B, 0x36, 0x22))
    }
}

private fun StoreType.label(): String = when (this) {
    StoreType.SHARED_PREFERENCES -> "SharedPreferences"
    StoreType.PREFERENCES_DATASTORE -> "Preferences DataStore"
    StoreType.PROTO_DATASTORE -> "Proto DataStore"
}
