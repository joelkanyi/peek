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

/**
 * What changed between two snapshots of the same store. Empty on the first load
 * (no baseline to compare against).
 */
public class StoreDiff internal constructor(
    public val added: Set<String>,
    public val changed: Set<String>,
    public val removed: Set<String>,
) {
    /** True when nothing changed. */
    public val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()

    public companion object {
        public val NONE: StoreDiff = StoreDiff(emptySet(), emptySet(), emptySet())
    }
}
