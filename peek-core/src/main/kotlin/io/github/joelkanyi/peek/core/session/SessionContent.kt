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

import io.github.joelkanyi.peek.core.model.KvEntry
import io.github.joelkanyi.peek.core.model.StoreDiff
import io.github.joelkanyi.peek.core.model.sameAs

// True when two states render identically. Read timestamps and file stat are excluded, so a poll that
// re-reads unchanged bytes compares equal and the session can skip the redundant emission that flashes the UI.
internal fun SessionState.sameContentAs(other: SessionState): Boolean {
    if (this !is SessionState.Active || other !is SessionState.Active) return false
    if (stores.size != other.stores.size) return false
    return stores.indices.all { stores[it].sameContentAs(other.stores[it]) }
}

private fun StoreState.sameContentAs(other: StoreState): Boolean {
    if (this::class != other::class) return false
    if (handle.path != other.handle.path || handle.type != other.handle.type || handle.displayName != other.handle.displayName) {
        return false
    }
    return when (this) {
        is StoreState.Loaded -> {
            other as StoreState.Loaded
            entriesSameContent(snapshot.entries, other.snapshot.entries) && diff.sameContentAs(other.diff)
        }
        is StoreState.Unparseable -> {
            other as StoreState.Unparseable
            reason == other.reason && hexPreview == other.hexPreview
        }
        is StoreState.Loading -> true
    }
}

private fun entriesSameContent(a: List<KvEntry>, b: List<KvEntry>): Boolean {
    if (a.size != b.size) return false
    return a.indices.all { a[it].key == b[it].key && a[it].value.sameAs(b[it].value) }
}

private fun StoreDiff.sameContentAs(other: StoreDiff): Boolean =
    added == other.added && changed == other.changed && removed == other.removed
