package io.github.joelkanyi.peek.plugin.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
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
import io.github.joelkanyi.peek.core.model.StoreType
import io.github.joelkanyi.peek.core.session.PeekSession
import io.github.joelkanyi.peek.core.session.SessionState
import io.github.joelkanyi.peek.core.session.StoreState
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.plugin.PeekBundle
import io.github.joelkanyi.peek.plugin.services.PeekProjectService
import io.github.joelkanyi.peek.plugin.transport.TransportProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * The Peek tool window: pick a device and a debuggable app, then browse its
 * stores as typed tables. Stores are listed on the left, the selected store's
 * entries on the right. Auto-refreshes while visible; changed and added keys are
 * highlighted. Read-only in P1. Transport work runs off the EDT; only UI
 * mutation touches the EDT.
 */
internal class PeekPanel(private val project: Project) {

    private val scope: CoroutineScope = project.service<PeekProjectService>().scope
    private val transport: DeviceTransport? =
        TransportProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.createTransport() }

    private val deviceCombo = ComboBox<Device>().apply {
        renderer = SimpleListCellRenderer.create("") { "${it.model} (${it.serial})" }
    }
    private val appCombo = ComboBox<String>().apply {
        setMinimumAndPreferredWidth(JBUI.scale(300))
    }
    private val refreshButton = JButton(PeekBundle.message("peek.action.refresh"))
    private val addPathButton = JButton(PeekBundle.message("peek.action.addPath"))
    private val statusLabel = JBLabel()

    private val storeListModel = DefaultListModel<StoreState>()
    private val storeList = JBList(storeListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SimpleListCellRenderer.create("") { "${it.handle.displayName}  ·  ${it.handle.type.label()}" }
    }
    private val tableModel = StoreTableModel()
    private val table = JBTable(tableModel)
    private val tree = Tree().apply { isRootVisible = true }
    private val detailCards = JPanel(CardLayout())

    private var suppressEvents = false
    private var visible = true
    private var highlightAdded: Set<String> = emptySet()
    private var highlightChanged: Set<String> = emptySet()
    private var session: PeekSession? = null
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

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
            add(JBLabel(PeekBundle.message("peek.label.device")))
            add(deviceCombo)
            add(JBLabel(PeekBundle.message("peek.label.app")))
            add(appCombo)
            add(refreshButton)
            add(addPathButton)
        }
        statusLabel.border = JBUI.Borders.empty(4, 8)

        detailCards.add(JBScrollPane(table), CARD_TABLE)
        detailCards.add(JBScrollPane(tree), CARD_TREE)
        val splitter = JBSplitter(false, 0.3f).apply {
            firstComponent = JBScrollPane(storeList)
            secondComponent = detailCards
        }

        root.add(toolbar, BorderLayout.NORTH)
        root.add(splitter, BorderLayout.CENTER)
        root.add(statusLabel, BorderLayout.SOUTH)

        deviceCombo.addActionListener { if (!suppressEvents) onDeviceChosen() }
        appCombo.addActionListener { if (!suppressEvents) onRefresh() }
        storeList.addListSelectionListener { if (!it.valueIsAdjusting && !suppressEvents) showSelectedStore() }
        refreshButton.addActionListener { onRefresh() }
        addPathButton.addActionListener { onAddPath() }

        loadDevices()
        return root
    }

    /** Tints rows whose key was added (green) or changed (yellow) since the last refresh. */
    private fun installHighlightRenderer() {
        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
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
        })
    }

    private fun loadDevices() = scope.launch {
        val devices = runCatching { transport!!.listDevices() }
            .getOrElse { withContext(Dispatchers.EDT) { status(PeekBundle.message("peek.status.adbError", it.message ?: "")) }; return@launch }
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
        val device = deviceCombo.selectedItem as? Device ?: run { status(PeekBundle.message("peek.status.pickDevice")); return }
        val packageName = (appCombo.selectedItem as? String)?.trim().orEmpty()
        if (packageName.isEmpty()) { status(PeekBundle.message("peek.status.pickApp")); return }
        startSession(device, AppPackage(packageName, pid = null))
    }

    private fun onAddPath() {
        val current = session ?: run { status(PeekBundle.message("peek.status.pickApp")); return }
        val path = Messages.showInputDialog(
            project,
            PeekBundle.message("peek.addPath.message"),
            PeekBundle.message("peek.addPath.title"),
            null,
        )?.trim()
        if (!path.isNullOrEmpty()) current.addCustomPath(path)
    }

    private fun startSession(device: Device, pkg: AppPackage) {
        stopSession()
        table.setPaintBusy(true)
        status(PeekBundle.message("peek.status.loading"))
        val newSession = PeekSession(transport!!, device, pkg, scope)
        session = newSession
        collectJob = scope.launch(Dispatchers.EDT) {
            newSession.state.collect { render(it) }
        }
        newSession.refresh()
        if (visible) newSession.startPolling()
    }

    private fun stopSession() {
        collectJob?.cancel()
        session?.close()
        session = null
    }

    /** Called by the tool window when its visibility changes; pauses polling while hidden. */
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
                val keepPath = (storeList.selectedValue)?.handle?.path
                suppressEvents = true
                storeListModel.clear()
                state.stores.forEach { storeListModel.addElement(it) }
                val keepIndex = state.stores.indexOfFirst { it.handle.path == keepPath }
                suppressEvents = false
                status(PeekBundle.message("peek.status.stores", state.stores.size))
                if (!storeListModel.isEmpty) {
                    storeList.selectedIndex = if (keepIndex >= 0) keepIndex else 0
                } else {
                    tableModel.setEntries(emptyList())
                }
            }
        }
    }

    private fun showSelectedStore() {
        when (val store = storeList.selectedValue) {
            is StoreState.Loaded -> showLoaded(store)
            is StoreState.Unparseable -> {
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
            tree.model = buildProtoTreeModel(store.handle.displayName, protoNode)
            showCard(CARD_TREE)
            status(PeekBundle.message("peek.status.protoFields", protoNode.fields.size, store.handle.displayName))
            return
        }
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

    private fun showCard(card: String) {
        (detailCards.layout as CardLayout).show(detailCards, card)
    }

    private fun clearStores() {
        suppressEvents = true
        storeListModel.clear()
        suppressEvents = false
        highlightAdded = emptySet()
        highlightChanged = emptySet()
        tableModel.setEntries(emptyList())
    }

    private fun status(text: String) {
        statusLabel.text = text
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

    private companion object {
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
