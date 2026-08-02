package io.github.joelkanyi.peek.wire

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import kotlin.test.Test

class WireCodecTest {

    private val messages: List<Message> = listOf(
        Message.Hello(PROTOCOL_VERSION),
        Message.ListStores,
        Message.ReadStore("sp:user"),
        Message.PutValue("sp:user", "count", WireValue.IntValue(5)),
        Message.RemoveKey("sp:user", "count"),
        Message.Welcome(PROTOCOL_VERSION, "com.example.app"),
        Message.StoreList(
            listOf(
                StoreInfo("sp:user", "user.xml", StoreKind.SHARED_PREFERENCES),
                StoreInfo("ds:settings", "settings", StoreKind.PREFERENCES_DATASTORE),
            ),
        ),
        Message.StoreData(
            "sp:user",
            listOf(
                WireEntry("flag", WireValue.BoolValue(true)),
                WireEntry("tags", WireValue.StringSetValue(listOf("a", "b"))),
                WireEntry("blob", WireValue.BytesValue("0102".decodeHex())),
            ),
        ),
        Message.Changed("sp:user"),
        Message.Ok,
        Message.Err("not debuggable"),
    )

    @Test
    fun `encode then decode round-trips every message`() {
        messages.forEach { assertThat(WireCodec.decode(WireCodec.encode(it))).isEqualTo(it) }
    }

    @Test
    fun `frames round-trip over a single stream`() {
        val buffer = Buffer()
        messages.forEach { WireCodec.writeFrame(buffer, it) }
        messages.forEach { assertThat(WireCodec.readFrame(buffer)).isEqualTo(it) }
        assertThat(WireCodec.readFrame(buffer)).isNull()
    }

    @Test
    fun `every wire value type round-trips`() {
        val values = listOf(
            WireValue.BoolValue(true),
            WireValue.IntValue(-5),
            WireValue.LongValue(1712L),
            WireValue.FloatValue(0.5f),
            WireValue.DoubleValue(0.65),
            WireValue.StringValue("joel"),
            WireValue.StringSetValue(listOf("beta", "offline")),
            WireValue.BytesValue("ff00".decodeHex()),
        )
        values.forEach {
            val message = Message.PutValue("s", "k", it)
            assertThat(WireCodec.decode(WireCodec.encode(message))).isEqualTo(message)
        }
    }
}
