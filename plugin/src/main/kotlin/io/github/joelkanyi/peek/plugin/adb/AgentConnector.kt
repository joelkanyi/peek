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

import io.github.joelkanyi.peek.wire.peekLocalSocketName
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Opens a socket to the on-device agent (if present) by forwarding its abstract
 * local socket to a local TCP port. Returns a connected socket; the caller
 * handshakes to confirm the agent is actually listening, and falls back to the
 * adb file transport if not.
 */
internal object AgentConnector {

    fun open(serial: String, packageName: String): Socket? {
        val adb = resolveAdbPath()
        val socketName = peekLocalSocketName(packageName)
        val port = forward(adb, serial, socketName) ?: return null
        return runCatching {
            Socket().apply { connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS) }
        }.getOrNull()
    }

    /** `adb forward tcp:0 localabstract:<name>` prints the assigned local port. */
    private fun forward(adb: String, serial: String, socketName: String): Int? = try {
        val process = ProcessBuilder(adb, "-s", serial, "forward", "tcp:0", "localabstract:$socketName").start()
        val out = process.inputStream.readBytes().decodeToString().trim()
        process.errorStream.readBytes()
        if (process.waitFor() == 0) out.toIntOrNull() else null
    } catch (e: Exception) {
        null
    }

    private const val CONNECT_TIMEOUT_MS = 1500
}
