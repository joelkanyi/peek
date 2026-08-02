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
package io.github.joelkanyi.peek.plugin.adb

import io.github.joelkanyi.peek.core.transport.TransportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbParsingTest {

    @Test
    fun `parses connected devices and skips offline ones`() {
        val out = """
            List of devices attached
            emulator-5554          device product:sdk_gphone64 model:Pixel_8 device:emu transport_id:1
            0A281FDD40012345       device product:redfin model:Pixel_5 device:redfin transport_id:2
            1B2C3D4E               offline
        """.trimIndent()

        val devices = parseDevices(out)

        assertEquals(2, devices.size)
        assertEquals("emulator-5554", devices[0].serial)
        assertEquals("Pixel 8", devices[0].model)
        assertTrue(devices[0].isEmulator)
        assertEquals("Pixel 5", devices[1].model)
        assertTrue(!devices[1].isEmulator)
    }

    @Test
    fun `extracts package-like process names and drops kernel threads`() {
        val out = """
            USER  PID  PPID  VSZ  RSS  WCHAN  ADDR S NAME
            root  1    0     100  10   0      0    S init
            u0_a1 2000 800   200  20   0      0    S com.example.myapp
            u0_a2 2100 800   200  20   0      0    S com.example.myapp:remote
            root  3    2     0    0    0      0    S [kworker/0:0]
        """.trimIndent()

        val names = parseProcessNames(out)

        assertTrue("com.example.myapp" in names || "com.example.myapp:remote" in names)
        assertTrue(names.none { it.startsWith("[") })
        assertTrue("init" !in names)
    }

    @Test
    fun `parses installed package list`() {
        val out = """
            package:com.dlight.atlas.debug
            package:com.example.other
        """.trimIndent()

        val packages = parsePackageList(out)

        assertEquals(listOf("com.dlight.atlas.debug", "com.example.other"), packages)
    }

    @Test
    fun `classifies run-as failures`() {
        assertTrue(classifyError("run-as: Package 'com.x' is not debuggable", "com.x", "s") is TransportException.NotDebuggable)
        assertTrue(classifyError("run-as: unknown package: com.x", "com.x", "s") is TransportException.PackageNotFound)
        assertTrue(classifyError("error: device 'emulator-5554' not found", "com.x", "s") is TransportException.DeviceLost)
        assertNull(classifyError("ls: shared_prefs: No such file or directory", "com.x", "s"))
    }
}
