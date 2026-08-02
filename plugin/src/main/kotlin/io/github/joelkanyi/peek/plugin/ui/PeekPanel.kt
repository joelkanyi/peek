package io.github.joelkanyi.peek.plugin.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import io.github.joelkanyi.peek.core.error.PeekError
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.session.PeekSession
import io.github.joelkanyi.peek.core.session.SessionState
import io.github.joelkanyi.peek.core.session.StoreState
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.plugin.PeekBundle
import io.github.joelkanyi.peek.plugin.services.PeekProjectService
import io.github.joelkanyi.peek.plugin.transport.TransportProvider
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The Peek tool window: pick a device and a debuggable app, then browse its
 * stores as typed tables. Read-only in P1. Transport work runs off the EDT; only
 * UI mutation touches the EDT.
 */
internal class PeekPanel(project: Project) {

    private val scope: CoroutineScope = project.service<PeekProjectService>().scope
    private val transport: DeviceTransport? =
        TransportProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.createTransport() }

    private val deviceCombo = ComboBox<Device>().apply {
        renderer = SimpleListCellRenderer.create("") { "${it.model} (${it.serial})" }
    }
    private val appCombo = ComboBox<String>().apply { isEditable = true }
    private val storeCombo = ComboBox<StoreState>().apply {
        renderer = SimpleListCellRenderer.create("") { "${it.handle.displayName} · ${it.handle.type.label()}" }
    }
    private val refreshButton = JButton(PeekBundle.message("peek.action.refresh"))
    private val statusLabel = JBLabel()
    private val tableModel = StoreTableModel()
    private val table = JBTable(tableModel)

    private var suppressEvents = false
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
            add(JBLabel(PeekBundle.message("peek.label.store")))
            add(storeCombo)
        }
        statusLabel.border = JBUI.Borders.empty(4, 8)

        root.add(toolbar, BorderLayout.NORTH)
        root.add(JBScrollPane(table), BorderLayout.CENTER)
        root.add(statusLabel, BorderLayout.SOUTH)

        deviceCombo.addActionListener { if (!suppressEvents) onDeviceChosen() }
        storeCombo.addActionListener { if (!suppressEvents) showSelectedStore() }
        refreshButton.addActionListener { onRefresh() }

        loadDevices()
        return root
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
        scope.launch {
            val apps = runCatching { transport!!.listDebuggableProcesses(device) }.getOrDefault(emptyList())
            withContext(Dispatchers.EDT) {
                suppressEvents = true
                appCombo.model = DefaultComboBoxModel(apps.map { it.packageName }.toTypedArray())
                appCombo.selectedItem = null
                suppressEvents = false
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
        val newSession = PeekSession(transport!!, device, pkg, scope)
        session = newSession
        collectJob = scope.launch(Dispatchers.EDT) {
            newSession.state.collect { render(it) }
        }
        newSession.refresh()
    }

    private fun render(state: SessionState) {
        when (state) {
            SessionState.Connecting -> status(PeekBundle.message("peek.status.connecting"))
            is SessionState.Failed -> {
                clearStores()
                status(errorMessage(state.error))
            }
            is SessionState.Paused -> {
                clearStores()
                status(PeekBundle.message("peek.status.paused"))
            }
            is SessionState.Active -> {
                suppressEvents = true
                storeCombo.model = DefaultComboBoxModel(state.stores.toTypedArray())
                suppressEvents = false
                status(PeekBundle.message("peek.status.stores", state.stores.size))
                if (state.stores.isNotEmpty()) showSelectedStore() else tableModel.setEntries(emptyList())
            }
        }
    }

    private fun showSelectedStore() {
        when (val store = storeCombo.selectedItem as? StoreState) {
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
        storeCombo.model = DefaultComboBoxModel()
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

private fun io.github.joelkanyi.peek.core.model.StoreType.label(): String = when (this) {
    io.github.joelkanyi.peek.core.model.StoreType.SHARED_PREFERENCES -> "SharedPreferences"
    io.github.joelkanyi.peek.core.model.StoreType.PREFERENCES_DATASTORE -> "Preferences DataStore"
    io.github.joelkanyi.peek.core.model.StoreType.PROTO_DATASTORE -> "Proto DataStore"
}
