package io.github.joelkanyi.peek.wire

/**
 * The abstract-namespace local socket the agent listens on and the plugin forwards
 * to via `adb forward tcp:<port> localabstract:<name>`. Namespaced by package so
 * two agents on one device never collide.
 */
public fun peekLocalSocketName(packageName: String): String = "peek_agent_$packageName"
