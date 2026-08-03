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

/** The result of an edit. */
public sealed interface WriteOutcome {

    /** The change is on disk and live (only possible via the runtime agent, later). */
    public data object Applied : WriteOutcome

    /** The change is on disk; the app was stopped and must be relaunched to read it. */
    public data object AppliedRequiresAppRestart : WriteOutcome

    /** The change was not made. */
    public class Refused internal constructor(public val reason: String) : WriteOutcome
}
