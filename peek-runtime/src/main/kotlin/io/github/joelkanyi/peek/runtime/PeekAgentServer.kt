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
package io.github.joelkanyi.peek.runtime

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import io.github.joelkanyi.peek.wire.Message
import io.github.joelkanyi.peek.wire.PROTOCOL_VERSION
import io.github.joelkanyi.peek.wire.WireCodec
import okio.BufferedSink
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException

/**
 * Listens on an abstract local socket and serves the app's SharedPreferences to
 * the Peek plugin over [WireCodec]. Handles one client at a time (the plugin);
 * pushes [Message.Changed] whenever a store changes.
 */
internal class PeekAgentServer(context: Context, private val socketName: String) {

    private val prefs = SharedPreferencesSource(context.applicationContext)
    private val packageName = context.applicationContext.packageName
    private val writeLock = Any()

    @Volatile
    private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({ acceptLoop() }, "peek-agent").apply {
            isDaemon = true
            start()
        }
    }

    private fun acceptLoop() {
        val server = try {
            LocalServerSocket(socketName)
        } catch (e: IOException) {
            Log.w(TAG, "could not bind local socket $socketName: ${e.message}")
            running = false
            return
        }
        while (running) {
            val client = try {
                server.accept()
            } catch (e: IOException) {
                break
            }
            serve(client)
        }
    }

    private fun serve(client: LocalSocket) {
        val source = client.inputStream.source().buffer()
        val sink = client.outputStream.sink().buffer()
        prefs.onChanged = { storeId -> send(sink, Message.Changed(storeId)) }
        try {
            while (running) {
                val message = WireCodec.readFrame(source) ?: break
                handle(message, sink)
            }
        } catch (e: Exception) {
            // client disconnected or malformed stream; drop this connection.
        } finally {
            prefs.onChanged = null
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun handle(message: Message, sink: BufferedSink) {
        val reply = when (message) {
            is Message.Hello -> Message.Welcome(PROTOCOL_VERSION, packageName)
            Message.ListStores -> Message.StoreList(prefs.listStores())
            is Message.ReadStore -> prefs.readStore(message.storeId)
            is Message.PutValue -> prefs.put(message.storeId, message.key, message.value)
            is Message.RemoveKey -> prefs.remove(message.storeId, message.key)
            else -> Message.Err("unexpected message")
        }
        send(sink, reply)
    }

    private fun send(sink: BufferedSink, message: Message) {
        synchronized(writeLock) {
            try {
                WireCodec.writeFrame(sink, message)
            } catch (e: Exception) {
                // connection gone; the read loop will end.
            }
        }
    }

    private companion object {
        const val TAG = "Peek"
    }
}
