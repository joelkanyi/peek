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
package io.github.joelkanyi.peek.wire

import okio.ByteString

/** The current wire protocol version. Bumped on any incompatible change. */
public const val PROTOCOL_VERSION: Int = 1

/** A typed value carried over the wire (the store types the agent can read/write). */
public sealed interface WireValue {
    public data class BoolValue(val value: Boolean) : WireValue
    public data class IntValue(val value: Int) : WireValue
    public data class LongValue(val value: Long) : WireValue
    public data class FloatValue(val value: Float) : WireValue
    public data class DoubleValue(val value: Double) : WireValue
    public data class StringValue(val value: String) : WireValue
    public data class StringSetValue(val values: List<String>) : WireValue
    public data class BytesValue(val value: ByteString) : WireValue
}

/** The store families the agent exposes. */
public enum class StoreKind { SHARED_PREFERENCES, PREFERENCES_DATASTORE }

/** A store the agent can serve. */
public data class StoreInfo(val id: String, val displayName: String, val kind: StoreKind)

/** One key-value pair in a store's data. */
public data class WireEntry(val key: String, val value: WireValue)

/** A protocol message between the Peek plugin (client) and the on-device agent (server). */
public sealed interface Message {

    public data class Hello(val protocolVersion: Int) : Message
    public data object ListStores : Message
    public data class ReadStore(val storeId: String) : Message
    public data class PutValue(val storeId: String, val key: String, val value: WireValue) : Message
    public data class RemoveKey(val storeId: String, val key: String) : Message

    public data class Welcome(val protocolVersion: Int, val appPackage: String) : Message
    public data class StoreList(val stores: List<StoreInfo>) : Message
    public data class StoreData(val storeId: String, val entries: List<WireEntry>) : Message
    public data class Changed(val storeId: String) : Message
    public data object Ok : Message
    public data class Err(val message: String) : Message
}
