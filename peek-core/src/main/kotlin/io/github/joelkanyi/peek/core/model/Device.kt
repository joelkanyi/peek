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
package io.github.joelkanyi.peek.core.model

/**
 * A connected device or emulator. Constructed by [DeviceTransport] implementations,
 * so the constructor is public API.
 */
public class Device(
    public val serial: String,
    public val model: String,
    public val apiLevel: Int,
    public val isEmulator: Boolean,
)

/**
 * An installed app. [pid] is `null` when the app is not currently running.
 * Constructed by [DeviceTransport] implementations, so the constructor is public API.
 */
public class AppPackage(
    public val packageName: String,
    public val pid: Int?,
)
