package io.github.joelkanyi.peek.core.error

/**
 * The complete failure taxonomy. Every arm has a defined UI surface: no blank
 * tables, no silent catch. Handling stays exhaustive via `when`.
 */
public sealed interface PeekError {

    /** No adb available, or no devices connected. */
    public data object AdbUnavailable : PeekError

    /** The selected device dropped mid-session. */
    public class DeviceLost internal constructor(public val serial: String) : PeekError

    /** The app is not debuggable, so its storage cannot be read via run-as. */
    public class NotDebuggable internal constructor(public val pkg: String) : PeekError

    /** The app is not installed for the current user. */
    public class PackageNotFound internal constructor(public val pkg: String) : PeekError

    /** A store file disappeared between listing and reading. */
    public class FileVanished internal constructor(public val path: String) : PeekError

    /** A store file could not be decoded (torn read, corruption). */
    public class ParseFailed internal constructor(public val path: String, public val reason: String) : PeekError

    /** Any other transport-level failure. */
    public class TransportFailure internal constructor(public val message: String) : PeekError
}
