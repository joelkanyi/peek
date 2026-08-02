package io.github.joelkanyi.peek.core.model

/**
 * A connected device or emulator. Constructed by [DeviceTransport] implementations,
 * so the constructor is public API.
 */
public class Device(
    public val serial: String,
    public val model: String,
    public val apiLevel: Int,
    public val isEmulator: Boolean,
)

/**
 * An installed app. [pid] is `null` when the app is not currently running.
 * Constructed by [DeviceTransport] implementations, so the constructor is public API.
 */
public class AppPackage(
    public val packageName: String,
    public val pid: Int?,
)
