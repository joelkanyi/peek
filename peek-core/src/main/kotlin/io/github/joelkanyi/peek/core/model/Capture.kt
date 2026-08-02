package io.github.joelkanyi.peek.core.model

import okio.ByteString

/**
 * A named point-in-time snapshot of an app's stores. Each store is kept as its
 * raw bytes, so a capture reconstructs any store type exactly and diffs decode
 * on demand. Constructed by the capture action and by JSON import, so public.
 */
public class Capture(
    public val name: String,
    public val capturedAtEpochMs: Long,
    public val stores: List<CapturedStore>,
)

/** One store within a [Capture]: where it was, its type, and its raw bytes. */
public class CapturedStore(
    public val path: String,
    public val type: StoreType,
    public val displayName: String,
    public val bytes: ByteString,
)
