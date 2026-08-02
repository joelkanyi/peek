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
package io.github.joelkanyi.peek.plugin.transport

import com.intellij.openapi.extensions.ExtensionPointName
import io.github.joelkanyi.peek.core.transport.DeviceTransport

/**
 * Contributes a [DeviceTransport] to Peek. Registered through the
 * `io.github.joelkanyi.peek.transportProvider` extension point, so the way Peek
 * reaches devices is pluggable: the ddmlib provider registers in P1, the adb-CLI
 * fallback in P2, the socket agent later. When no provider is registered (for
 * example plain IntelliJ without the Android plugin), the tool window shows an
 * explanatory empty state.
 */
public interface TransportProvider {

    /** Human-readable name shown in the UI, e.g. "ddmlib". */
    public val displayName: String

    /** Create the transport, or `null` if it is unavailable in this IDE/environment. */
    public fun createTransport(): DeviceTransport?

    public companion object {
        public val EP_NAME: ExtensionPointName<TransportProvider> =
            ExtensionPointName.create("io.github.joelkanyi.peek.transportProvider")
    }
}
