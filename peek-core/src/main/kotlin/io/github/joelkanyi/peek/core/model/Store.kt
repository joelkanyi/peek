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

/** What changed between two snapshots of the same store. */
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
