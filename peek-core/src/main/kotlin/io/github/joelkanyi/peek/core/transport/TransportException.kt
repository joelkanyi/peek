package io.github.joelkanyi.peek.core.transport

/**
 * Failures a [DeviceTransport] raises. The mechanism detail (adb stderr text,
 * socket errors) is interpreted inside the transport and surfaced as these typed
 * cases, so the domain never parses raw output.
 */
public sealed class TransportException(message: String) : Exception(message) {

    /** The app exists but is not debuggable, so its storage cannot be read. */
    public class NotDebuggable(public val packageName: String) :
        TransportException("package not debuggable: $packageName")

    /** The app is not installed for the current user. */
    public class PackageNotFound(public val packageName: String) :
        TransportException("package not found: $packageName")

    /** The device disconnected. */
    public class DeviceLost(public val serial: String) :
        TransportException("device lost: $serial")

    /** Any other transport-level I/O failure. */
    public class Io(message: String) : TransportException(message)
}
