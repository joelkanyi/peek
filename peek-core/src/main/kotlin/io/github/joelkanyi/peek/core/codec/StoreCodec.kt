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
package io.github.joelkanyi.peek.core.codec

import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.StoreSnapshot
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString

/**
 * Decodes the raw bytes of one store format into a [StoreSnapshot]. Encoding
 * (for editing) is added per round-trippable format in P4.
 */
public interface StoreCodec {

    /** The store format this codec handles. */
    public val type: StoreType

    /** Decode [bytes] read at [capturedAtEpochMs] into a snapshot, or report why it failed. */
    public fun decode(handle: StoreHandle, bytes: ByteString, capturedAtEpochMs: Long): DecodeResult

    /** Encode a (possibly edited) snapshot back to bytes. Throws for formats that cannot be written. */
    public fun encode(snapshot: StoreSnapshot): ByteString
}

/** Outcome of a [StoreCodec.decode] call. [Failed] keeps the bytes for a hex preview. */
public sealed interface DecodeResult {

    public class Decoded internal constructor(public val snapshot: StoreSnapshot) : DecodeResult

    public class Failed internal constructor(
        public val reason: String,
        public val bytes: ByteString,
    ) : DecodeResult
}
