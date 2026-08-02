package io.github.joelkanyi.peek.plugin.services

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/**
 * Project-scoped service that owns a [CoroutineScope] tied to the project's
 * lifecycle. The tool window uses it to drive sessions; the scope is cancelled
 * automatically when the project closes.
 */
@Service(Service.Level.PROJECT)
internal class PeekProjectService(val scope: CoroutineScope)
