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
package io.github.joelkanyi.peek.core.codec

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.github.joelkanyi.peek.core.model.KvValue
import io.github.joelkanyi.peek.core.model.ProtoValue
import io.github.joelkanyi.peek.core.model.StoreType
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.Test

class ProtoDataStoreCodecTest {

    private val codec = ProtoDataStoreCodec()

    @Test
    fun `decodes a schemaless message tree`() {
        val inner = ProtoTestWriter().varintField(1, 1L).bytes()
        val bytes = ProtoTestWriter()
            .varintField(1, 42L)
            .lenField(2, "joel".encodeUtf8())
            .lenField(3, inner)
            .bytes()

        val result = codec.decode(handle(StoreType.PROTO_DATASTORE), bytes, capturedAtEpochMs = 0)
        val node = (result as DecodeResult.Decoded).snapshot.entries.single().value as KvValue.ProtoNode

        assertThat(node.fields).hasSize(3)

        assertThat(node.fields[0].number).isEqualTo(1)
        assertThat((node.fields[0].value as ProtoValue.Varint).value).isEqualTo(42L)

        assertThat(node.fields[1].number).isEqualTo(2)
        assertThat((node.fields[1].value as ProtoValue.Text).value).isEqualTo("joel")

        val nested = node.fields[2].value as ProtoValue.Message
        assertThat((nested.fields.single().value as ProtoValue.Varint).value).isEqualTo(1L)
    }

    @Test
    fun `non-protobuf bytes fail without throwing`() {
        val result = codec.decode(handle(StoreType.PROTO_DATASTORE), "not a protobuf".encodeUtf8(), 0)
        assertThat(result).isInstanceOf(DecodeResult.Failed::class)
    }
}
