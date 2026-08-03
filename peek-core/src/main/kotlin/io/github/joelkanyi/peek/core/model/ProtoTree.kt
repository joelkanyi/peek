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
package io.github.joelkanyi.peek.core.model

import okio.ByteString

/** A field of a schemaless protobuf message: its wire field number and a decoded value. */
public class ProtoField internal constructor(
    public val number: Int,
    public val value: ProtoValue,
)

/** A decoded protobuf value. Length-delimited fields are guessed as message, text, or raw bytes. */
public sealed interface ProtoValue {

    public class Varint internal constructor(public val value: Long) : ProtoValue

    public class Fixed32 internal constructor(public val value: Int) : ProtoValue

    public class Fixed64 internal constructor(public val value: Long) : ProtoValue

    public class Text internal constructor(public val value: String) : ProtoValue

    public class Bytes internal constructor(public val value: ByteString) : ProtoValue

    public class Message internal constructor(public val fields: List<ProtoField>) : ProtoValue
}
