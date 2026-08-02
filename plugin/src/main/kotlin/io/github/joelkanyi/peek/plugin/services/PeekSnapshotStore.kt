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
package io.github.joelkanyi.peek.plugin.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.annotations.XCollection
import io.github.joelkanyi.peek.core.model.Capture
import io.github.joelkanyi.peek.core.model.CapturedStore
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString.Companion.decodeBase64

/**
 * Persists captured snapshots across IDE restarts. Each store is kept as its raw
 * bytes (base64), so any store type reconstructs exactly. App-level so snapshots
 * are shared across projects.
 */
@Service(Service.Level.APP)
@State(name = "PeekSnapshots", storages = [Storage("peek-snapshots.xml")])
internal class PeekSnapshotStore : PersistentStateComponent<PeekSnapshotStore.State> {

    class State {
        @get:XCollection(style = XCollection.Style.v2)
        var snapshots: MutableList<SnapshotBean> = ArrayList()
    }

    class SnapshotBean {
        var name: String = ""
        var packageName: String = ""
        var capturedAt: Long = 0

        @get:XCollection(style = XCollection.Style.v2)
        var stores: MutableList<StoreBean> = ArrayList()
    }

    class StoreBean {
        var path: String = ""
        var type: String = ""
        var displayName: String = ""
        var bytesBase64: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        state = loaded
    }

    fun beans(): List<SnapshotBean> = state.snapshots.toList()

    fun add(capture: Capture, packageName: String) {
        state.snapshots.add(
            SnapshotBean().apply {
                name = capture.name
                this.packageName = packageName
                capturedAt = capture.capturedAtEpochMs
                stores = capture.stores.mapTo(ArrayList()) { store ->
                    StoreBean().apply {
                        path = store.path
                        type = store.type.name
                        displayName = store.displayName
                        bytesBase64 = store.bytes.base64()
                    }
                }
            },
        )
    }

    fun remove(bean: SnapshotBean) {
        state.snapshots.remove(bean)
    }

    fun rename(bean: SnapshotBean, newName: String) {
        bean.name = newName
    }

    companion object {
        fun getInstance(): PeekSnapshotStore = service()

        /** Reconstruct a [Capture] from a persisted bean. */
        fun toCapture(bean: SnapshotBean): Capture = Capture(
            name = bean.name,
            capturedAtEpochMs = bean.capturedAt,
            stores = bean.stores.map { s ->
                CapturedStore(
                    path = s.path,
                    type = runCatching { StoreType.valueOf(s.type) }.getOrDefault(StoreType.PROTO_DATASTORE),
                    displayName = s.displayName,
                    bytes = s.bytesBase64.decodeBase64() ?: okio.ByteString.EMPTY,
                )
            },
        )
    }
}
