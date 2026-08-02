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
}

/** Outcome of a [StoreCodec.decode] call. [Failed] keeps the bytes for a hex preview. */
public sealed interface DecodeResult {

    public class Decoded internal constructor(public val snapshot: StoreSnapshot) : DecodeResult

    public class Failed internal constructor(
        public val reason: String,
        public val bytes: ByteString,
    ) : DecodeResult
}
