package io.github.joelkanyi.peek.core.locator

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import io.github.joelkanyi.peek.core.model.AppPackage
import io.github.joelkanyi.peek.core.model.Device
import io.github.joelkanyi.peek.core.model.StoreType
import io.github.joelkanyi.peek.core.testing.FakeTransport
import io.github.joelkanyi.peek.core.transport.TransportException
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test

class StoreLocatorTest {

    private val device = Device("emulator-5554", "Pixel 8", 34, isEmulator = true)
    private val pkg = AppPackage("com.example.app", pid = 1234)

    @Test
    fun `classifies stores by directory and extension`() = runTest {
        val transport = FakeTransport(
            files = mapOf(
                "shared_prefs/user_prefs.xml" to "<map/>".encodeUtf8(),
                "shared_prefs/analytics.xml" to "<map/>".encodeUtf8(),
                "shared_prefs/nested/deep.xml" to "<map/>".encodeUtf8(), // not directly in dir
                "files/datastore/settings.preferences_pb" to "x".encodeUtf8(),
                "files/datastore/user.pb" to "x".encodeUtf8(),
                "files/other.txt" to "x".encodeUtf8(), // outside scanned dirs
            ),
        )

        val result = StoreLocator(transport).locate(device, pkg)
        val handles = (result as LocateResult.Located).handles

        assertThat(handles.associate { it.path to it.type }).containsOnly(
            "shared_prefs/user_prefs.xml" to StoreType.SHARED_PREFERENCES,
            "shared_prefs/analytics.xml" to StoreType.SHARED_PREFERENCES,
            "files/datastore/settings.preferences_pb" to StoreType.PREFERENCES_DATASTORE,
            "files/datastore/user.pb" to StoreType.PROTO_DATASTORE,
        )
    }

    @Test
    fun `no stores yields an empty located result`() = runTest {
        val result = StoreLocator(FakeTransport()).locate(device, pkg)
        assertThat((result as LocateResult.Located).handles).isEmpty()
    }

    @Test
    fun `not-debuggable surfaces as NotDebuggable`() = runTest {
        val transport = FakeTransport(listFailure = TransportException.NotDebuggable(pkg.packageName))
        assertThat(StoreLocator(transport).locate(device, pkg)).isInstanceOf(LocateResult.NotDebuggable::class)
    }

    @Test
    fun `unknown package surfaces as PackageNotFound`() = runTest {
        val transport = FakeTransport(listFailure = TransportException.PackageNotFound(pkg.packageName))
        assertThat(StoreLocator(transport).locate(device, pkg)).isInstanceOf(LocateResult.PackageNotFound::class)
    }
}
