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
package io.github.joelkanyi.peek.core.transport

/** Failures a [DeviceTransport] raises. */
public sealed class TransportException(message: String) : Exception(message) {

    /** The app exists but is not debuggable, so its storage cannot be read. */
    public class NotDebuggable(public val packageName: String) : TransportException("package not debuggable: $packageName")

    /** The app is not installed for the current user. */
    public class PackageNotFound(public val packageName: String) : TransportException("package not found: $packageName")

    /** The device disconnected. */
    public class DeviceLost(public val serial: String) : TransportException("device lost: $serial")

    /** Any other transport-level I/O failure. */
    public class Io(message: String) : TransportException(message)
}
