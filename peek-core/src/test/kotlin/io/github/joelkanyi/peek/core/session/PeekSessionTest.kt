package io.github.joelkanyi.peek.core.session

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun `proto datastore is reported as not yet supported`() = runTest(UnconfinedTestDispatcher()) {
        val transport = FakeTransport(files = mapOf("files/datastore/u.pb" to validPb))
        val s = session(transport, this)

        s.refresh()
        advanceUntilIdle()

        val store = (s.state.value as SessionState.Active).stores.single()
        assertThat(store).isInstanceOf(StoreState.Unparseable::class)
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
