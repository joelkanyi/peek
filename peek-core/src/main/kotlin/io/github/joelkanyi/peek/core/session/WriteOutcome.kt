package io.github.joelkanyi.peek.core.session

/** The result of an edit. No silent lies: every arm states exactly what happened. */
public sealed interface WriteOutcome {

    /** The change is on disk and live (only possible via the runtime agent, later). */
    public data object Applied : WriteOutcome

    /** The change is on disk; the app was stopped and must be relaunched to read it. */
    public data object AppliedRequiresAppRestart : WriteOutcome

    /** The change was not made. */
    public class Refused internal constructor(public val reason: String) : WriteOutcome
}
