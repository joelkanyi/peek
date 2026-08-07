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

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsAll
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.github.joelkanyi.peek.core.codec.preferencesPb
import io.github.joelkanyi.peek.core.codec.vInt
import io.github.joelkanyi.peek.core.error.PeekError
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.testing.FakeTransport
import io.github.joelkanyi.peek.core.transport.TransportException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeekSessionTest {

    private val device = Device("emulator-5554", "Pixel 8", 34, isEmulator = true)
    private val pkg = AppPackage("com.example.app", pid = 1234)

    private val validXml = "<map><int name=\"count\" value=\"42\" /></map>".encodeUtf8()
    private val validPb = preferencesPb("count" to vInt(42))
    private val corruptPb = "not a protobuf".encodeUtf8()

    private fun session(transport: FakeTransport, scope: kotlinx.coroutines.CoroutineScope) =
        PeekSession(transport, device, pkg, scope, now = { 7L }, retryDelayMs = 1_000)

    @Test
    fun `loads shared prefs and preferences datastore`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(
            files = mapOf(
                "shared_prefs/p.xml" to validXml,
                "files/datastore/s.preferences_pb" to validPb,
            ),
        )
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()

        val active = s.state.value as SessionState.Active
        assertThat(active.stores).hasSize(2)
        val loaded = active.stores.filterIsInstance<StoreState.Loaded>()
        assertThat(loaded).hasSize(2)
        val count = loaded.flatMap { it.snapshot.entries }.first { it.key == "count" }.value
        assertThat((count as KvValue.IntValue).value).isEqualTo(42)
    }

    @Test
    fun `not-debuggable app fails the session`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(listFailure = TransportException.NotDebuggable(pkg.packageName))
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()

        assertThat(s.state.value)
            .isInstanceOf(SessionState.Failed::class)
            .prop(SessionState.Failed::error).isInstanceOf(PeekError.NotDebuggable::class)
    }

    @Test
    fun `device loss pauses the session`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(listFailure = TransportException.DeviceLost(device.serial))
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()

        assertThat(s.state.value).isInstanceOf(SessionState.Paused::class)
    }

    @Test
    fun `proto datastore decodes to a proto node`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(files = mapOf("files/datastore/u.pb" to validPb))
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()

        val store = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat(store.snapshot.entries.single().value).isInstanceOf(KvValue.ProtoNode::class)
    }

    @Test
    fun `torn read recovers on retry`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(
            files = mapOf("files/datastore/s.preferences_pb" to validPb),
            readSequences = mapOf("files/datastore/s.preferences_pb" to listOf(corruptPb, validPb)),
        )
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()

        val store = (s.state.value as SessionState.Active).stores.single()
        assertThat(store).isInstanceOf(StoreState.Loaded::class)
    }

    @Test
    fun `diff marks changed added and removed keys across refreshes`() = runTest(UnconfinedTestDispatcher()) {
        val first = preferencesPb("count" to vInt(1), "gone" to vInt(9))
        val second = preferencesPb("count" to vInt(2), "added" to vInt(3))
        val path = "files/datastore/s.preferences_pb"
        val transport = FakeTransport(
            files = mapOf(path to first),
            readSequences = mapOf(path to listOf(first, second)),
        )
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()
        val baseline = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat(baseline.diff.isEmpty).isTrue()

        s.refresh()
        advanceUntilIdle()
        val updated = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat(updated.diff.changed).contains("count")
        assertThat(updated.diff.added).contains("added")
        assertThat(updated.diff.removed).contains("gone")
    }

    @Test
    fun `putValue writes back and the reload shows the new value`() = runTest(UnconfinedTestDispatcher()) {
        val path = "shared_prefs/p.xml"
        val transport = FakeTransport(files = mapOf(path to "<map><int name=\"count\" value=\"1\" /></map>".encodeUtf8()))
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()
        val handle = ((s.state.value as SessionState.Active).stores.single() as StoreState.Loaded).handle

        val outcome = s.putValue(handle, "count", KvValue.of(2))
        advanceUntilIdle()

        assertThat(outcome).isEqualTo(WriteOutcome.AppliedRequiresAppRestart)
        val reloaded = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat((reloaded.snapshot.entries.single().value as KvValue.IntValue).value).isEqualTo(2)
    }

    @Test
    fun `removeKey deletes the entry`() = runTest(UnconfinedTestDispatcher()) {
        val path = "shared_prefs/p.xml"
        val xml = "<map><int name=\"a\" value=\"1\" /><int name=\"b\" value=\"2\" /></map>"
        val transport = FakeTransport(files = mapOf(path to xml.encodeUtf8()))
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()
        val handle = ((s.state.value as SessionState.Active).stores.single() as StoreState.Loaded).handle

        s.removeKey(handle, "a")
        advanceUntilIdle()

        val reloaded = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat(reloaded.snapshot.entries.map { it.key }).isEqualTo(listOf("b"))
    }

    @Test
    fun `custom path loads a store outside the standard directories`() = runTest(UnconfinedTestDispatcher()) {
        val path = "files/phenotype/storage-info.pb"
        val transport = FakeTransport(files = mapOf(path to preferencesPb("k" to vInt(1))))
        val s = session(transport, this)

        s.addCustomPath(path)
        advanceUntilIdle()

        val store = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat(store.snapshot.entries.single().value).isInstanceOf(KvValue.ProtoNode::class)
    }

    @Test
    fun `capture reads raw bytes of all located stores`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(
            files = mapOf(
                "shared_prefs/p.xml" to "<map/>".encodeUtf8(),
                "files/datastore/s.preferences_pb" to preferencesPb("k" to vInt(1)),
            ),
        )
        val s = session(transport, this)

        val capture = s.capture("snap")

        assertThat(capture.name).isEqualTo("snap")
        assertThat(capture.stores.map { it.path })
            .containsAll("shared_prefs/p.xml", "files/datastore/s.preferences_pb")
    }

    @Test
    fun `polling re-reads and diffs on each interval`() = runTest(UnconfinedTestDispatcher()) {
        val v1 = preferencesPb("count" to vInt(1))
        val v2 = preferencesPb("count" to vInt(2))
        val path = "files/datastore/s.preferences_pb"
        val transport = FakeTransport(files = mapOf(path to v1), readSequences = mapOf(path to listOf(v1, v2)))
        val s = PeekSession(transport, device, pkg, this, now = { 0L }, retryDelayMs = 1)

        s.startPolling(intervalMs = 1_000)

        advanceTimeBy(1_001)
        runCurrent()
        val first = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat((first.snapshot.entries.single().value as KvValue.IntValue).value).isEqualTo(1)

        advanceTimeBy(1_000)
        runCurrent()
        val second = (s.state.value as SessionState.Active).stores.single() as StoreState.Loaded
        assertThat((second.snapshot.entries.single().value as KvValue.IntValue).value).isEqualTo(2)
        assertThat(second.diff.changed).contains("count")

        s.stopPolling()
    }

    @Test
    fun `an unchanged poll does not emit a new active state`() = runTest(UnconfinedTestDispatcher()) {
        val path = "files/datastore/s.preferences_pb"
        val transport = FakeTransport(files = mapOf(path to preferencesPb("count" to vInt(1))))
        val s = PeekSession(transport, device, pkg, this, now = { 0L }, retryDelayMs = 1)

        val emissions = mutableListOf<SessionState>()
        val collector = launch { s.state.collect { emissions.add(it) } }

        s.startPolling(intervalMs = 1_000)
        advanceTimeBy(1_001)
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        s.stopPolling()
        collector.cancel()

        assertThat(emissions.filterIsInstance<SessionState.Active>()).hasSize(1)
    }

    @Test
    fun `persistently torn read falls back to a hex preview`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(
            files = mapOf("files/datastore/s.preferences_pb" to validPb),
            readSequences = mapOf("files/datastore/s.preferences_pb" to listOf(corruptPb, corruptPb)),
        )
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()

        val store = (s.state.value as SessionState.Active).stores.single() as StoreState.Unparseable
        assertThat(store.hexPreview).isNotEmpty()
    }
}
