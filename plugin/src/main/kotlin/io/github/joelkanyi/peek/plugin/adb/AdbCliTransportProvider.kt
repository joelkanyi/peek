/*
 * Copyright 2026 Joel Kanyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
