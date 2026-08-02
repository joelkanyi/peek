package io.github.joelkanyi.peek.runtime

import io.github.joelkanyi.peek.wire.PROTOCOL_VERSION

/** Entry point for the on-device agent. The socket server lands in the next slice. */
public object PeekRuntime {
    public fun protocolVersion(): Int = PROTOCOL_VERSION
}
