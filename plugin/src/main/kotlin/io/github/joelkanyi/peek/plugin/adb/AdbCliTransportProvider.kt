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
}

/** Resolve the `adb` binary from the Android SDK, falling back to `adb` on the PATH. */
internal fun resolveAdbPath(): String {
    val exe = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
    val home = System.getProperty("user.home").orEmpty()
    val sdkRoots = buildList {
        System.getenv("ANDROID_HOME")?.let { add(it) }
        System.getenv("ANDROID_SDK_ROOT")?.let { add(it) }
        add("$home/Library/Android/sdk") // macOS default
        add("$home/Android/Sdk") // Linux default
        System.getenv("LOCALAPPDATA")?.let { add("$it/Android/Sdk") } // Windows default
    }
    return sdkRoots
        .map { File(it, "platform-tools/$exe") }
        .firstOrNull { it.canExecute() }
        ?.absolutePath ?: exe // fall back to PATH lookup
}
