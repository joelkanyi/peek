package io.github.joelkanyi.peek.plugin.adb

import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.transport.DeviceTransport
import io.github.joelkanyi.peek.core.transport.ExecResult
import io.github.joelkanyi.peek.core.transport.FileStat
import io.github.joelkanyi.peek.core.transport.TransportCapabilities
import io.github.joelkanyi.peek.core.transport.TransportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException

/**
 * A [DeviceTransport] that drives the `adb` command-line binary. It needs no
 * Android plugin, so Peek works on any IDE with an Android SDK on the machine.
 * All blocking process work runs on [Dispatchers.IO]. Read-only: `capabilities`
 * report no writes and no push events.
 */
internal class AdbCliTransport(private val adbPath: String) : DeviceTransport {

    override val capabilities: TransportCapabilities =
        TransportCapabilities(canWrite = true, canPushEvents = false)

    override suspend fun listDevices(): List<Device> = withContext(Dispatchers.IO) {
        parseDevices(run(listOf(adbPath, "devices", "-l")).stdout.decodeToString())
    }

    override suspend fun listDebuggableProcesses(device: Device): List<AppPackage> = withContext(Dispatchers.IO) {
        // Installed third-party packages, so an app shows up even when it is not running.
        // Debuggability is confirmed by run-as when its stores are located.
        val result = run(listOf(adbPath, "-s", device.serial, "shell", "pm", "list", "packages", "-3"))
        parsePackageList(result.stdout.decodeToString()).map { AppPackage(it, pid = null) }
    }

    override suspend fun exec(device: Device, command: String): ExecResult = withContext(Dispatchers.IO) {
        val result = run(listOf(adbPath, "-s", device.serial, "shell", command))
        ExecResult(result.exitCode, result.stdout.toByteString(), result.stderr)
    }

    override suspend fun listFiles(device: Device, pkg: AppPackage, dir: String): List<String> = withContext(Dispatchers.IO) {
        val result = run(listOf(adbPath, "-s", device.serial, "shell", "run-as", pkg.packageName, "ls", "-1", dir))
        classifyError(result.stderr, pkg.packageName, device.serial)?.let { throw it }
        if (result.exitCode != 0) return@withContext emptyList() // directory absent
        result.stdout.decodeToString().lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }

    override suspend fun readFile(device: Device, pkg: AppPackage, path: String): ByteString = withContext(Dispatchers.IO) {
        // exec-out keeps the byte stream intact (shell would mangle newlines).
        val result = run(listOf(adbPath, "-s", device.serial, "exec-out", "run-as", pkg.packageName, "cat", path))
        classifyError(result.stderr, pkg.packageName, device.serial)?.let { throw it }
        if (result.exitCode != 0) throw TransportException.Io(result.stderr.ifBlank { "cannot read $path" })
        result.stdout.toByteString()
    }

    override suspend fun stat(device: Device, pkg: AppPackage, path: String): FileStat? = withContext(Dispatchers.IO) {
        val result = run(listOf(adbPath, "-s", device.serial, "shell", "run-as", pkg.packageName, "stat", "-c", "%Y %s", path))
        if (result.exitCode != 0) return@withContext null
        val parts = result.stdout.decodeToString().trim().split(Regex("\\s+"))
        val mtimeSeconds = parts.getOrNull(0)?.toLongOrNull() ?: return@withContext null
        FileStat(mtimeEpochMs = mtimeSeconds * 1000, sizeBytes = parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    override suspend fun writeFile(device: Device, pkg: AppPackage, path: String, bytes: ByteString) = withContext(Dispatchers.IO) {
        val tmp = "$path.peek.tmp"
        // dd takes the output path as an argument, so run-as resolves it from the app's home dir.
        // (Shell redirection like `sh -c 'cat > file'` would run under the outer adb shell uid and fail.)
        val write = runWithInput(
            listOf(adbPath, "-s", device.serial, "shell", "run-as", pkg.packageName, "dd", "of=$tmp"),
            bytes,
        )
        classifyError(write.stderr, pkg.packageName, device.serial)?.let { throw it }
        if (write.exitCode != 0) throw TransportException.Io(write.stderr.ifBlank { "failed to write $path" })

        val move = run(listOf(adbPath, "-s", device.serial, "shell", "run-as", pkg.packageName, "mv", tmp, path))
        classifyError(move.stderr, pkg.packageName, device.serial)?.let { throw it }
        if (move.exitCode != 0) throw TransportException.Io(move.stderr.ifBlank { "failed to move into $path" })
    }

    private fun runWithInput(command: List<String>, input: ByteString): ProcResult = try {
        val process = ProcessBuilder(command).start()
        process.outputStream.use { it.write(input.toByteArray()); it.flush() }
        val stdout = process.inputStream.readBytes()
        val stderr = process.errorStream.readBytes().decodeToString()
        ProcResult(process.waitFor(), stdout, stderr)
    } catch (e: IOException) {
        throw TransportException.Io("failed to run adb: ${e.message}")
    }

    private fun run(command: List<String>): ProcResult = try {
        val process = ProcessBuilder(command).start()
        val stdout = process.inputStream.readBytes()
        val stderr = process.errorStream.readBytes().decodeToString()
        ProcResult(process.waitFor(), stdout, stderr)
    } catch (e: IOException) {
        throw TransportException.Io("failed to run adb: ${e.message}")
    }

    private class ProcResult(val exitCode: Int, val stdout: ByteArray, val stderr: String)
}

/** Parse `adb devices -l`. Only devices in the `device` state are returned. */
internal fun parseDevices(stdout: String): List<Device> =
    stdout.lineSequence()
        .drop(1) // "List of devices attached"
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val columns = line.split(Regex("\\s+"))
            val serial = columns.firstOrNull() ?: return@mapNotNull null
            if (columns.getOrNull(1) != "device") return@mapNotNull null
            val model = columns.firstOrNull { it.startsWith("model:") }
                ?.removePrefix("model:")?.replace('_', ' ') ?: serial
            Device(serial, model, apiLevel = 0, isEmulator = serial.startsWith("emulator-"))
        }
        .toList()

/** Parse `pm list packages -3` output into package names. */
internal fun parsePackageList(stdout: String): List<String> =
    stdout.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("package:") }
        .map { it.removePrefix("package:") }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .toList()

/** Parse `ps -A` output into package-like process names (contain a dot, not kernel threads). */
internal fun parseProcessNames(stdout: String): List<String> =
    stdout.lineSequence()
        .drop(1) // header row
        .map { it.trim().substringAfterLast(' ') }
        .filter { it.contains('.') && !it.startsWith("[") }
        .distinct()
        .sorted()
        .toList()

/** Map adb/run-as stderr to a typed [TransportException], or `null` if it is not a known failure. */
internal fun classifyError(stderr: String, packageName: String, serial: String): TransportException? {
    val s = stderr.lowercase()
    return when {
        "not debuggable" in s -> TransportException.NotDebuggable(packageName)
        "unknown package" in s || "is unknown" in s -> TransportException.PackageNotFound(packageName)
        "no devices" in s || ("device" in s && "not found" in s) -> TransportException.DeviceLost(serial)
        else -> null
    }
}
