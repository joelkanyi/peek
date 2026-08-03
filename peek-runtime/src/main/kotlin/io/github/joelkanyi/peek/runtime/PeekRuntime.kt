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
import android.util.Log
import io.github.joelkanyi.peek.wire.peekLocalSocketName

/** The on-device agent, started automatically by [PeekInitProvider] in debug builds. */
public object PeekRuntime {

    @Volatile
    private var server: PeekAgentServer? = null

    /** Start the agent. Idempotent. */
    public fun start(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        val socketName = peekLocalSocketName(app.packageName)
        synchronized(this) {
            if (server != null) return
            server = PeekAgentServer(app, socketName).also { it.start() }
        }
        // One greppable line: the agent's presence is disclosed, not hidden.
        Log.i("Peek", "Peek agent started on localabstract:$socketName")
    }
}
