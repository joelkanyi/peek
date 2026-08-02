package io.github.joelkanyi.peek.plugin.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.joelkanyi.peek.core.error.PeekError
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
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
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The Peek tool window: pick a device and a debuggable app, then browse its
 * stores as typed tables. Stores are listed on the left, the selected store's
 * entries on the right. Read-only in P1. Transport work runs off the EDT; only
 * UI mutation touches the EDT.
 */
internal class PeekPanel(project: Project) {

    private val scope: CoroutineScope = project.service<PeekProjectService>().scope
    private val transport: DeviceTransport? =
        TransportProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.createTransport() }

    private val deviceCombo = ComboBox<Device>().apply {
        renderer = SimpleListCellRenderer.create("") { "${it.model} (${it.serial})" }
    }
    private val appCombo = ComboBox<String>().apply {
        isEditable = true
        setMinimumAndPreferredWidth(JBUI.scale(280))
    }
    private val refreshButton = JButton(PeekBundle.message("peek.action.refresh"))
    private val statusLabel = JBLabel()

    private val storeListModel = DefaultListModel<StoreState>()
    private val storeList = JBList(storeListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = SimpleListCellRenderer.create("") { "${it.handle.displayName}  ·  ${it.handle.type.label()}" }
    }
    private val tableModel = StoreTableModel()
    private val table = JBTable(tableModel)

    private var suppressEvents = false
    private var filtering = false
    private var lastFilter: String? = null
    private var allApps: List<String> = emptyList()
    private var session: PeekSession? = null
    private var collectJob: Job? = null

    val component: JComponent = build()

    private fun build(): JComponent {
        val root = JPanel(BorderLayout())
        if (transport == null) {
            root.add(JBLabel(PeekBundle.message("peek.emptyState.noTransport")), BorderLayout.CENTER)
            return root
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
            add(JBLabel(PeekBundle.message("peek.label.device")))
            add(deviceCombo)
            add(JBLabel(PeekBundle.message("peek.label.app")))
            add(appCombo)
            add(refreshButton)
        }
        statusLabel.border = JBUI.Borders.empty(4, 8)

        val splitter = JBSplitter(false, 0.3f).apply {
            firstComponent = JBScrollPane(storeList)
            secondComponent = JBScrollPane(table)
        }

        root.add(toolbar, BorderLayout.NORTH)
        root.add(splitter, BorderLayout.CENTER)
        root.add(statusLabel, BorderLayout.SOUTH)

        deviceCombo.addActionListener { if (!suppressEvents) onDeviceChosen() }
        appCombo.addActionListener { if (!suppressEvents && !filtering) onRefresh() }
        storeList.addListSelectionListener { if (!it.valueIsAdjusting && !suppressEvents) showSelectedStore() }
        refreshButton.addActionListener { onRefresh() }
        installAppFilter()

        loadDevices()
        return root
    }

    /** Live-filters the app dropdown to entries containing the typed text. */
    private fun installAppFilter() {
        val editor = appCombo.editor.editorComponent as? JTextField ?: return
        editor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = scheduleFilter()
            override fun removeUpdate(e: DocumentEvent) = scheduleFilter()
            override fun changedUpdate(e: DocumentEvent) = scheduleFilter()
        })
    }

    private fun scheduleFilter() {
        if (filtering) return
        SwingUtilities.invokeLater {
            val editor = appCombo.editor.editorComponent as? JTextField ?: return@invokeLater
            val text = editor.text
            if (filtering || text == lastFilter) return@invokeLater
            lastFilter = text
            val matches = if (text.isEmpty()) allApps else allApps.filter { it.contains(text, ignoreCase = true) }
            val caret = editor.caretPosition
            filtering = true
            suppressEvents = true
            appCombo.hidePopup()
            appCombo.model = DefaultComboBoxModel(matches.toTypedArray())
            editor.text = text
            editor.caretPosition = caret.coerceAtMost(text.length)
            suppressEvents = false
            filtering = false
            if (matches.isNotEmpty() && editor.isFocusOwner) appCombo.showPopup()
        }
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
        clearStores()
        status(PeekBundle.message("peek.status.loadingApps"))
        scope.launch {
            val apps = runCatching { transport!!.listDebuggableProcesses(device) }.getOrDefault(emptyList())
            withContext(Dispatchers.EDT) {
                allApps = apps.map { it.packageName }
                lastFilter = null
                suppressEvents = true
                appCombo.model = DefaultComboBoxModel(allApps.toTypedArray())
                appCombo.selectedItem = null
                (appCombo.editor.editorComponent as? JTextField)?.text = ""
                suppressEvents = false
                status(PeekBundle.message("peek.status.pickApp"))
            }
        }
    }

    private fun onRefresh() {
        val device = deviceCombo.selectedItem as? Device ?: run { status(PeekBundle.message("peek.status.pickDevice")); return }
        val packageName = (appCombo.editor.item ?: appCombo.selectedItem)?.toString()?.trim().orEmpty()
        if (packageName.isEmpty()) { status(PeekBundle.message("peek.status.pickApp")); return }
        startSession(device, AppPackage(packageName, pid = null))
    }

    private fun startSession(device: Device, pkg: AppPackage) {
        collectJob?.cancel()
        session?.close()
        table.setPaintBusy(true)
        status(PeekBundle.message("peek.status.loading"))
        val newSession = PeekSession(transport!!, device, pkg, scope)
        session = newSession
        collectJob = scope.launch(Dispatchers.EDT) {
            newSession.state.collect { render(it) }
        }
        newSession.refresh()
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
                suppressEvents = true
                storeListModel.clear()
                state.stores.forEach { storeListModel.addElement(it) }
                suppressEvents = false
                status(PeekBundle.message("peek.status.stores", state.stores.size))
                if (!storeListModel.isEmpty) storeList.selectedIndex = 0 else tableModel.setEntries(emptyList())
            }
        }
    }

    private fun showSelectedStore() {
        when (val store = storeList.selectedValue) {
            is StoreState.Loaded -> {
                tableModel.setEntries(store.snapshot.entries)
                status(PeekBundle.message("peek.status.entries", store.snapshot.entries.size, store.handle.displayName))
            }
            is StoreState.Unparseable -> {
                tableModel.setEntries(emptyList())
                status(PeekBundle.message("peek.status.unparseable", store.reason))
            }
            else -> tableModel.setEntries(emptyList())
        }
    }

    private fun clearStores() {
        suppressEvents = true
        storeListModel.clear()
        suppressEvents = false
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
}

private fun StoreType.label(): String = when (this) {
    StoreType.SHARED_PREFERENCES -> "SharedPreferences"
    StoreType.PREFERENCES_DATASTORE -> "Preferences DataStore"
    StoreType.PROTO_DATASTORE -> "Proto DataStore"
}
