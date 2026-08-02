package io.github.joelkanyi.peek.core.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A scope-owned timing loop that invokes [onTick] every [intervalMs]. It is
 * paused and resumed by the tool window's visibility, so Peek does no device
 * traffic while it is hidden. Testable on virtual time.
 */
internal class RefreshPolicy(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val onTick: suspend () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                onTick()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
