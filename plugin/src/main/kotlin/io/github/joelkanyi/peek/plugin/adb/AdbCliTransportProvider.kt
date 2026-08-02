package io.github.joelkanyi.peek.plugin.adb

import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.plugin.transport.TransportProvider
import java.io.File

/**
 * Registers the adb command-line transport. It resolves the `adb` binary from
 * the Android SDK environment, falling back to `adb` on the PATH. Always returns
 * a transport; if adb is genuinely missing, device listing surfaces a clear error.
 */
internal class AdbCliTransportProvider : TransportProvider {

    override val displayName: String = "adb"

    override fun createTransport(): DeviceTransport = AdbCliTransport(resolveAdbPath())

    private fun resolveAdbPath(): String {
        val exe = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val fromSdk = listOfNotNull(System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
            .map { File(it, "platform-tools/$exe") }
            .firstOrNull { it.canExecute() }
        return fromSdk?.absolutePath ?: exe // fall back to PATH lookup
    }
}
