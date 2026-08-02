package io.github.joelkanyi.peek.core.transport

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import okio.ByteString

/**
 * The single seam through which peek-core reaches a device. Everything above it
 * is transport-agnostic: the ADB implementation ships first, a socket-agent
 * implementation joins later, and the domain never learns which one it is using.
 */
public interface DeviceTransport {

    /** What this channel can do. UI affordances are gated on these flags, not on the concrete type. */
    public val capabilities: TransportCapabilities

    /** Connected devices and emulators. */
    public suspend fun listDevices(): List<Device>

    /** Debuggable app processes currently running on [device]. */
    public suspend fun listDebuggableProcesses(device: Device): List<AppPackage>

    /** Run a shell [command] on [device] and collect its result. */
    public suspend fun exec(device: Device, command: String): ExecResult

    /**
     * File names directly inside [dir] (relative to the app's private home dir),
     * or an empty list if the directory does not exist. Throws [TransportException]
     * for not-debuggable, unknown-package, or device-loss conditions.
     */
    public suspend fun listFiles(device: Device, pkg: AppPackage, dir: String): List<String>

    /** Read a file (path relative to the app's private home dir). */
    public suspend fun readFile(device: Device, pkg: AppPackage, path: String): ByteString

    /** File metadata, or `null` if the file does not exist. */
    public suspend fun stat(device: Device, pkg: AppPackage, path: String): FileStat?
}

/**
 * Declares what a [DeviceTransport] supports. Read-only ADB has both flags
 * `false`; the agent channel (P6/P7) flips them on.
 */
public class TransportCapabilities(
    public val canWrite: Boolean,
    public val canPushEvents: Boolean,
)

/** Result of a shell command: exit code plus captured output. */
public class ExecResult(
    public val exitCode: Int,
    public val stdout: ByteString,
    public val stderr: String,
)

/** File metadata used for freshness polling and stale-write guards. */
public class FileStat(
    public val mtimeEpochMs: Long,
    public val sizeBytes: Long,
)
