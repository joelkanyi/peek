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
import assertk.assertions.isEqualTo
import io.github.joelkanyi.peek.core.model.Capture
import io.github.joelkanyi.peek.core.model.CapturedStore
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test

class CaptureDiffTest {

    private fun sp(path: String, xml: String) =
        CapturedStore(path, StoreType.SHARED_PREFERENCES, path.substringAfterLast('/'), xml.encodeUtf8())

    @Test
    fun `diffs changed, added, and removed keys and whole stores`() {
        val before = Capture(
            "A",
            1,
            listOf(
                sp("shared_prefs/p.xml", "<map><int name=\"a\" value=\"1\" /><int name=\"gone\" value=\"9\" /></map>"),
                sp("shared_prefs/old.xml", "<map/>"),
            ),
        )
        val after = Capture(
            "B",
            2,
            listOf(
                sp("shared_prefs/p.xml", "<map><int name=\"a\" value=\"2\" /><int name=\"added\" value=\"3\" /></map>"),
                sp("shared_prefs/new.xml", "<map><int name=\"x\" value=\"1\" /></map>"),
            ),
        )

        val diff = diffCaptures(before, after)

        val p = diff.stores.first { it.path == "shared_prefs/p.xml" }
        assertThat(p.presence).isEqualTo(Presence.BOTH)
        assertThat(p.changed).contains("a")
        assertThat(p.added).contains("added")
        assertThat(p.removed).contains("gone")

        assertThat(diff.stores.first { it.path == "shared_prefs/old.xml" }.presence).isEqualTo(Presence.BEFORE_ONLY)
        assertThat(diff.stores.first { it.path == "shared_prefs/new.xml" }.presence).isEqualTo(Presence.AFTER_ONLY)
    }
}
