package io.github.joelkanyi.peek.core.codec

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test

class PreferencesPbCodecTest {

    private val codec = PreferencesPbCodec()

    @Test
    fun `decodes every value type in document order`() {
        val bytes = preferencesPb(
            "flag" to vBool(true),
            "count" to vInt(42),
            "ts" to vLong(1712L),
            "ratio" to vFloat(0.5f),
            "threshold" to vDouble(0.65),
            "user" to vString("joel"),
            "tags" to vStringSet("beta", "offline", "sync"),
            "blob" to vBytes("0102".decodeHex()),
        )

        val result = codec.decode(handle(StoreType.PREFERENCES_DATASTORE), bytes, capturedAtEpochMs = 7)
        val snapshot = (result as DecodeResult.Decoded).snapshot

        assertThat(snapshot.capturedAtEpochMs).isEqualTo(7)
        assertThat(snapshot.entries.map { it.key }).containsExactly(
            "flag", "count", "ts", "ratio", "threshold", "user", "tags", "blob",
        )
        val byKey = snapshot.entries.associate { it.key to it.value.unwrap() }
        assertThat(byKey["flag"]).isEqualTo(true)
        assertThat(byKey["count"]).isEqualTo(42)
        assertThat(byKey["ts"]).isEqualTo(1712L)
        assertThat(byKey["ratio"]).isEqualTo(0.5f)
        assertThat(byKey["threshold"]).isEqualTo(0.65)
        assertThat(byKey["user"]).isEqualTo("joel")
        assertThat(byKey["tags"]).isEqualTo(linkedSetOf("beta", "offline", "sync"))
        assertThat(byKey["blob"]).isEqualTo("0102".decodeHex())
    }

    @Test
    fun `negative int32 round-trips`() {
        val bytes = preferencesPb("delta" to vInt(-5))
        val snapshot = (codec.decode(handle(StoreType.PREFERENCES_DATASTORE), bytes, 0) as DecodeResult.Decoded).snapshot
        assertThat(snapshot.entries.single().value.unwrap()).isEqualTo(-5)
    }

    @Test
    fun `empty store decodes to no entries`() {
        val snapshot = (codec.decode(handle(StoreType.PREFERENCES_DATASTORE), okio.ByteString.EMPTY, 0) as DecodeResult.Decoded).snapshot
        assertThat(snapshot.entries).containsExactly()
    }

    @Test
    fun `truncated bytes fail without throwing`() {
        // A length-delimited top-level field claiming 20 bytes but only 2 present.
        val bytes = "0a14ffff".decodeHex()
        val result = codec.decode(handle(StoreType.PREFERENCES_DATASTORE), bytes, 0)
        assertThat(result).isInstanceOf(DecodeResult.Failed::class)
    }

    @Test
    fun `garbage bytes fail without throwing`() {
        val result = codec.decode(handle(StoreType.PREFERENCES_DATASTORE), "not a protobuf".encodeUtf8(), 0)
        assertThat(result).isInstanceOf(DecodeResult.Failed::class)
    }
}
