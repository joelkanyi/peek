package io.github.joelkanyi.peek.runtime

import android.content.Context
import android.util.Log
import io.github.joelkanyi.peek.wire.peekLocalSocketName

/**
 * The on-device agent. Started automatically by [PeekInitProvider] in debug
 * builds; call [start] yourself only if you disable the auto-init provider.
 */
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
