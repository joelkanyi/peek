package io.github.joelkanyi.peek.core.codec

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test

class SharedPreferencesXmlCodecTest {

    private val codec = SharedPreferencesXmlCodec()

    private fun decode(xml: String): DecodeResult =
        codec.decode(handle(StoreType.SHARED_PREFERENCES), xml.encodeUtf8(), capturedAtEpochMs = 3)

    @Test
    fun `decodes every value type in document order`() {
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="user">joel</string>
                <int name="count" value="42" />
                <long name="ts" value="1712" />
                <float name="ratio" value="0.5" />
                <boolean name="flag" value="true" />
                <set name="tags">
                    <string>beta</string>
                    <string>offline</string>
                </set>
            </map>
        """.trimIndent()

        val snapshot = (decode(xml) as DecodeResult.Decoded).snapshot

        assertThat(snapshot.entries.map { it.key })
            .containsExactly("user", "count", "ts", "ratio", "flag", "tags")
        val byKey = snapshot.entries.associate { it.key to it.value.unwrap() }
        assertThat(byKey["user"]).isEqualTo("joel")
        assertThat(byKey["count"]).isEqualTo(42)
        assertThat(byKey["ts"]).isEqualTo(1712L)
        assertThat(byKey["ratio"]).isEqualTo(0.5f)
        assertThat(byKey["flag"]).isEqualTo(true)
        assertThat(byKey["tags"]).isEqualTo(linkedSetOf("beta", "offline"))
    }

    @Test
    fun `empty string element decodes to empty string`() {
        val xml = "<map><string name=\"empty\"></string></map>"
        val snapshot = (decode(xml) as DecodeResult.Decoded).snapshot
        assertThat(snapshot.entries.single().value.unwrap()).isEqualTo("")
    }

    @Test
    fun `empty map decodes to no entries`() {
        val snapshot = (decode("<map></map>") as DecodeResult.Decoded).snapshot
        assertThat(snapshot.entries).containsExactly()
    }

    @Test
    fun `unsupported element fails`() {
        val xml = "<map><double name=\"x\" value=\"1.0\" /></map>"
        assertThat(decode(xml)).isInstanceOf(DecodeResult.Failed::class)
    }

    @Test
    fun `malformed xml fails without throwing`() {
        assertThat(decode("<map><string name=\"x\">unclosed")).isInstanceOf(DecodeResult.Failed::class)
    }
}
