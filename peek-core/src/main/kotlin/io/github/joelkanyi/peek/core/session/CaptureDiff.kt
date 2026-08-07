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
package io.github.joelkanyi.peek.core.session

import io.github.joelkanyi.peek.core.codec.DecodeResult
import io.github.joelkanyi.peek.core.codec.StoreCodecs
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Capture
import io.github.joelkanyi.peek.core.model.CapturedStore
import io.github.joelkanyi.peek.core.model.KvEntry
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.StoreHandle
import io.github.joelkanyi.peek.core.model.sameAs

/** Whether a store existed in the before capture, the after capture, or both. */
public enum class Presence { BOTH, BEFORE_ONLY, AFTER_ONLY }

/** A key whose value differs between two captures, keeping both sides. */
public class ValueChange internal constructor(
    public val key: String,
    public val before: KvValue,
    public val after: KvValue,
)

/** What changed in one store between two captures. */
public class StoreDelta internal constructor(
    public val path: String,
    public val displayName: String,
    public val presence: Presence,
    public val added: Set<String>,
    public val changed: List<ValueChange>,
    public val removed: Set<String>,
) {
    public val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
}

/** The difference between two captures, per store. */
public class CaptureDiff internal constructor(public val stores: List<StoreDelta>)

/** Diff two captures across time, decoding each store's raw bytes on demand. */
public fun diffCaptures(before: Capture, after: Capture): CaptureDiff {
    val beforeByPath = before.stores.associateBy { it.path }
    val afterByPath = after.stores.associateBy { it.path }

    val deltas = (beforeByPath.keys + afterByPath.keys).map { path ->
        val b = beforeByPath[path]
        val a = afterByPath[path]
        when {
            b != null && a != null -> {
                val bMap = decode(b).associate { it.key to it.value }
                val aMap = decode(a).associate { it.key to it.value }
                val changed = aMap.keys.intersect(bMap.keys)
                    .filter { !bMap.getValue(it).sameAs(aMap.getValue(it)) }
                    .map { ValueChange(it, bMap.getValue(it), aMap.getValue(it)) }
                StoreDelta(path, a.displayName, Presence.BOTH, aMap.keys - bMap.keys, changed, bMap.keys - aMap.keys)
            }
            a != null -> StoreDelta(path, a.displayName, Presence.AFTER_ONLY, decode(a).map { it.key }.toSet(), emptyList(), emptySet())
            else -> StoreDelta(path, b!!.displayName, Presence.BEFORE_ONLY, emptySet(), emptyList(), decode(b).map { it.key }.toSet())
        }
    }
    return CaptureDiff(deltas)
}

private fun decode(store: CapturedStore): List<KvEntry> {
    val handle = StoreHandle(AppPackage("", null), store.path, store.type, store.displayName, stat = null)
    return when (val result = StoreCodecs.codecFor(store.type).decode(handle, store.bytes, capturedAtEpochMs = 0)) {
        is DecodeResult.Decoded -> result.snapshot.entries
        is DecodeResult.Failed -> emptyList()
    }
}
