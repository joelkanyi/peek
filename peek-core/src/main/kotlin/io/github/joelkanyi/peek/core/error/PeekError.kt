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
package io.github.joelkanyi.peek.core.error

/** The complete failure taxonomy. */
public sealed interface PeekError {

    /** No adb available, or no devices connected. */
    public data object AdbUnavailable : PeekError

    /** The selected device dropped mid-session. */
    public class DeviceLost internal constructor(public val serial: String) : PeekError

    /** The app is not debuggable, so its storage cannot be read via run-as. */
    public class NotDebuggable internal constructor(public val pkg: String) : PeekError

    /** The app is not installed for the current user. */
    public class PackageNotFound internal constructor(public val pkg: String) : PeekError

    /** A store file disappeared between listing and reading. */
    public class FileVanished internal constructor(public val path: String) : PeekError

    /** A store file could not be decoded (torn read, corruption). */
    public class ParseFailed internal constructor(public val path: String, public val reason: String) : PeekError

    /** Any other transport-level failure. */
    public class TransportFailure internal constructor(public val message: String) : PeekError
}
