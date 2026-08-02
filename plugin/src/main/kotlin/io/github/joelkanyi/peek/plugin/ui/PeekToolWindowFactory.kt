package io.github.joelkanyi.peek.plugin.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import io.github.joelkanyi.peek.plugin.PeekBundle
import io.github.joelkanyi.peek.plugin.transport.TransportProvider
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Builds the Peek tool window. In P0 it only reports whether a device transport
 * is available; the device/app pickers and store tables arrive in P1.
 */
internal class PeekToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = buildPanel()
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    private fun buildPanel(): JComponent {
        val providers = TransportProvider.EP_NAME.extensionList
        val text = if (providers.isEmpty()) {
            PeekBundle.message("peek.emptyState.noTransport")
        } else {
            PeekBundle.message("peek.emptyState.ready", providers.joinToString { it.displayName })
        }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(JBLabel(text, SwingConstants.CENTER), BorderLayout.CENTER)
        }
    }
}
