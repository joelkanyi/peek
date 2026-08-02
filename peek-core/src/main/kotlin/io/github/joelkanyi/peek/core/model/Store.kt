package io.github.joelkanyi.peek.core.model

import io.github.joelkanyi.peek.core.transport.FileStat

/** The key-value store families Peek understands. */
public enum class StoreType {
    SHARED_PREFERENCES,
    PREFERENCES_DATASTORE,
    PROTO_DATASTORE,
}

/** A located store file: where it is, what kind it is, and when it last changed. */
public class StoreHandle internal constructor(
    public val pkg: AppPackage,
    public val path: String,
    public val type: StoreType,
    public val displayName: String,
    public val stat: FileStat?,
)

/** An immutable decoded store: its handle, its entries in document order, and when it was read. */
public class StoreSnapshot internal constructor(
    public val handle: StoreHandle,
    public val entries: List<KvEntry>,
    public val capturedAtEpochMs: Long,
)

/** One key-value pair inside a store. */
public class KvEntry internal constructor(
    public val key: String,
    public val value: KvValue,
)
