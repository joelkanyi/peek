# Peek — Implementation Plan: Phase 0 + Phase 1

Execution-ready plan for the implementing model. Signatures, file layout, and
ordered tasks; bodies are yours. Follow DESIGN.md for semantics, ROADMAP.md
for acceptance. Repo: /Users/joelkanyi/StudioProjects/peek (currently the
untouched IntelliJ Platform Plugin Template, single-module).

## 1. Module layout (restructure the template)

```
peek/
├── settings.gradle.kts          # + include(":peek-core", ":peek-core-testing", ":plugin")
│                                # + enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
├── gradle/libs.versions.toml
├── gradle.properties            # template keys stay root-level (plugin train reads them)
├── build.gradle.kts             # root: spotless + kotlinter/ktlint + BCV wiring only
├── peek-core/
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/github/joelkanyi/peek/core/...
│   └── src/test/kotlin/... + src/test/resources/goldens/...
├── peek-core-testing/
│   ├── build.gradle.kts
│   └── src/main/kotlin/io/github/joelkanyi/peek/core/testing/...
└── plugin/
    ├── build.gradle.kts         # the template's root build.gradle.kts moves here, adapted
    └── src/main/kotlin/io/github/joelkanyi/peek/plugin/...
    └── src/main/resources/META-INF/plugin.xml (+ peek-android.xml)
```

Package roots: `io.github.joelkanyi.peek.core` (published later, must be
right from day one) and `io.github.joelkanyi.peek.plugin`. Change
`pluginGroup` to `io.github.joelkanyi.peek`.

### Delete-list (template scaffolding, remove entirely)
- src/main/kotlin/com/github/joelkanyi/peek/MyBundle.kt
- src/main/kotlin/com/github/joelkanyi/peek/services/MyProjectService.kt
- src/main/kotlin/com/github/joelkanyi/peek/startup/MyProjectActivity.kt
- src/main/kotlin/com/github/joelkanyi/peek/toolWindow/MyToolWindowFactory.kt
- src/main/resources/messages/MyBundle.properties (replace with PeekBundle.properties)
- src/test/kotlin/com/github/joelkanyi/peek/MyPluginTest.kt
- src/test/testData/rename/foo.xml, foo_after.xml (whole testData dir)
- README template marketing block; baa.md at repo root; stray .DS_Store files

## 2. Build files

### gradle/libs.versions.toml — add
```toml
[versions]
okio = "3.9.1"            # verify latest stable at implementation time
coroutines = "1.9.0"      # match the platform's bundled coroutines major
assertk = "0.28.1"
kotlinBcv = "0.16.3"
spotless = "6.25.0"

[libraries]
okio = { module = "com.squareup.okio:okio", version.ref = "okio" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test" }
assertk = { module = "com.willowtreeapps.assertk:assertk", version.ref = "assertk" }

[plugins]
kotlinBcv = { id = "org.jetbrains.kotlinx.binary-compatibility-validator", version.ref = "kotlinBcv" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```
ddmlib is NOT a catalog entry — it comes from the org.jetbrains.android
plugin on the IntelliJ Platform classpath, only inside `plugin`.

### peek-core/build.gradle.kts (pure JVM, no IntelliJ SDK)
```kotlin
plugins { alias(libs.plugins.kotlin) /* jvm */ }
kotlin { explicitApi(); jvmToolchain(17) }
dependencies {
    api(libs.okio)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.assertk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.peekCoreTesting)
}
```
Caveat: `kotlin.stdlib.default.dependency = false` is set globally by the
template for the plugin's sake — peek-core must declare
`implementation(kotlin("stdlib"))` explicitly (or scope that property to the
plugin module).

### peek-core-testing/build.gradle.kts
Pure JVM; `api(projects.peekCore)`, `api(libs.assertk)`, explicitApi.

### plugin/build.gradle.kts (adapted from template root)
Keep the template's `intellijPlatform {}` config, plus:
```kotlin
dependencies {
    implementation(projects.peekCore)
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        plugins(providers.gradleProperty("platformPlugins").map { ... })          // marketplace
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { ... })
        pluginVerifier(); zipSigner(); testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.kotlin.test)
    testImplementation(projects.peekCoreTesting)
}
```

### gradle.properties changes
```properties
pluginGroup = io.github.joelkanyi.peek
# IC does NOT bundle the Android plugin; it is a marketplace plugin there.
# Dev/compile target (IC): marketplace coordinate, version must match platformVersion line:
platformPlugins = org.jetbrains.android:242.23726.103   # pick the version matching 2024.2.x
platformBundledPlugins =
```
CI adds a second verification/run variant with `platformType = AS` (or
`intellijPlatformTesting` block) where the Android plugin is BUNDLED:
there use `bundledPlugins("org.jetbrains.android")`. Encode both in
`plugin/build.gradle.kts` via `intellijPlatformTesting.runIde` registrations
(`runIdeIC`, `runIdeAS`). [doc: plugins.jetbrains.com/docs/intellij/android-studio.html]

### plugin.xml (replace template body)
```xml
<idea-plugin>
  <id>io.github.joelkanyi.peek</id>
  <name>Peek</name>
  <vendor>joelkanyi</vendor>
  <depends>com.intellij.modules.platform</depends>
  <depends optional="true" config-file="peek-android.xml">org.jetbrains.android</depends>
  <resource-bundle>messages.PeekBundle</resource-bundle>
  <extensions defaultExtensionNs="com.intellij">
    <toolWindow factoryClass="io.github.joelkanyi.peek.plugin.ui.PeekToolWindowFactory"
                id="Peek" anchor="bottom" icon="AllIcons.Toolwindows.ToolWindowInspection"/>
    <!-- services are @Service-annotated, no registration needed -->
  </extensions>
</idea-plugin>
```
`peek-android.xml` (P1 registers the ddmlib transport provider EP here so IC
without Android still loads; the transport-provider indirection is P1's only
extension point):
```xml
<idea-plugin>
  <extensions defaultExtensionNs="io.github.joelkanyi.peek">
    <transportProvider implementation="io.github.joelkanyi.peek.plugin.adb.DdmlibTransportProvider"/>
  </extensions>
</idea-plugin>
```
(Declare the `transportProvider` EP in plugin.xml with `<extensionPoints>`;
in P1, absence of any provider ⇒ explanatory empty state. The CLI fallback
provider arrives in P2.)

## 3. peek-core public API (P1 surface — signatures are the contract)

All in `io.github.joelkanyi.peek.core`. explicitApi; value types immutable
with `internal` constructors + companion/factory functions [ref: Kepler].

```kotlin
// transport/
public interface DeviceTransport {
    public val capabilities: TransportCapabilities
    public suspend fun listDevices(): List<Device>
    public suspend fun listDebuggableProcesses(device: Device): List<AppPackage>
    public suspend fun exec(device: Device, command: String): ExecResult
    public suspend fun readFile(device: Device, pkg: AppPackage, path: String): ByteString
    public suspend fun stat(device: Device, pkg: AppPackage, path: String): FileStat?
    // writeFile lands in P4; capabilities gates UI affordances until then
}
public class TransportCapabilities internal constructor(
    public val canWrite: Boolean,      // false for P1 adb read impl
    public val canPushEvents: Boolean, // false until AgentChannel (P6)
)
public class ExecResult internal constructor(
    public val exitCode: Int, public val stdout: ByteString, public val stderr: String)
public class FileStat internal constructor(
    public val mtimeEpochMs: Long, public val sizeBytes: Long)

// model/
public class Device internal constructor(
    public val serial: String, public val model: String,
    public val apiLevel: Int, public val isEmulator: Boolean)
public class AppPackage internal constructor(
    public val packageName: String, public val pid: Int?)   // pid null = not running
public enum class StoreType { SHARED_PREFERENCES, PREFERENCES_DATASTORE, PROTO_DATASTORE }
public class StoreHandle internal constructor(
    public val pkg: AppPackage, public val path: String,
    public val type: StoreType, public val displayName: String,
    public val stat: FileStat?)
public class StoreSnapshot internal constructor(
    public val handle: StoreHandle, public val entries: List<KvEntry>,
    public val capturedAtEpochMs: Long)
public class KvEntry internal constructor(public val key: String, public val value: KvValue)

public sealed interface KvValue {
    public class BoolValue internal constructor(public val value: Boolean) : KvValue
    public class IntValue internal constructor(public val value: Int) : KvValue
    public class LongValue internal constructor(public val value: Long) : KvValue
    public class FloatValue internal constructor(public val value: Float) : KvValue
    public class DoubleValue internal constructor(public val value: Double) : KvValue
    public class StringValue internal constructor(public val value: String) : KvValue
    public class StringSetValue internal constructor(public val values: Set<String>) : KvValue
    public class BytesValue internal constructor(public val value: ByteString) : KvValue
    public class ProtoNode internal constructor(/* P3; declare now, arm renders "unsupported" in P1 */) : KvValue
}
// Factory conveniences: KvValue.of(true), KvValue.of("x"), etc.

// locator/
public class StoreLocator(private val transport: DeviceTransport) {
    public suspend fun locate(device: Device, pkg: AppPackage): LocateResult
}
public sealed interface LocateResult {
    public class Located internal constructor(public val handles: List<StoreHandle>) : LocateResult
    public class NotDebuggable internal constructor(public val raw: String) : LocateResult
    public class PackageNotFound internal constructor(public val raw: String) : LocateResult
}

// codec/
public interface StoreCodec {
    public val type: StoreType
    public fun decode(handle: StoreHandle, bytes: ByteString, capturedAtEpochMs: Long): DecodeResult
}
public sealed interface DecodeResult {
    public class Decoded internal constructor(public val snapshot: StoreSnapshot) : DecodeResult
    public class Failed internal constructor(
        public val reason: String, public val bytes: ByteString) : DecodeResult  // hex preview source
}
public class SharedPreferencesXmlCodec : StoreCodec   // type = SHARED_PREFERENCES
public class PreferencesPbCodec : StoreCodec          // type = PREFERENCES_DATASTORE

// session/
public class PeekSession(
    transport: DeviceTransport, device: Device, pkg: AppPackage,
    scope: CoroutineScope, /* clock injectable for tests */) {
    public val state: StateFlow<SessionState>
    public fun refresh()      // manual in P1; RefreshPolicy drives it in P2
    public fun close()
}
public sealed interface SessionState {
    public data object Connecting : SessionState
    public class Active internal constructor(
        public val stores: List<StoreState>) : SessionState
    public class Paused internal constructor(public val error: PeekError) : SessionState  // device lost
    public class Failed internal constructor(public val error: PeekError) : SessionState
}
public sealed interface StoreState { /* Loading | Loaded(snapshot) | Unparseable(reason, hexPreview) */ }

// error/
public sealed interface PeekError {
    public data object AdbUnavailable : PeekError
    public class DeviceLost internal constructor(public val serial: String) : PeekError
    public class NotDebuggable internal constructor(public val pkg: String) : PeekError
    public class PackageNotFound internal constructor(public val pkg: String) : PeekError
    public class FileVanished internal constructor(public val path: String) : PeekError
    public class ParseFailed internal constructor(public val path: String, public val reason: String) : PeekError
    public class TransportFailure internal constructor(public val message: String) : PeekError
}
```

peek-core-testing: `FakeTransport` (scripted responses per path, mutable
files map, failure injection), `storeHandle {}` / `snapshot {}` builders,
golden-file loader helper.

## 4. plugin module classes (P1)

| Class | Responsibility |
|---|---|
| `adb/DdmlibTransportProvider` | EP impl; the ONLY file importing `com.android.ddmlib`. Creates `DdmlibTransport`. |
| `adb/DdmlibTransport : DeviceTransport` | Bridge from `AndroidDebugBridge`: device list via `IDeviceChangeListener` (bridged into the suspend API), debuggable clients via `device.clients`, `exec` via `executeShellCommand` with a collecting receiver, `readFile` = `exec-out run-as <pkg> cat <path>` (binary-safe: use exec-out semantics, not shell newline mangling). |
| `services/PeekAppService` (@Service APP) | Owns `AndroidDebugBridge` init/terminate (adb path from Android plugin's SDK), shares it across projects. |
| `services/PeekProjectService` (@Service PROJECT, takes `CoroutineScope`) | Owns `PeekSession` instances; exposes state to UI; disposes on project close. |
| `ui/PeekToolWindowFactory` | Registers the panel; shows the no-transport explanatory state when the EP is empty (IC without Android plugin). |
| `ui/PeekPanel` | Device combo + package combo (+ manual entry field) + refresh action + store tabs; collects `StateFlow` on `Dispatchers.EDT`. |
| `ui/StoreTable` | JBTable per store: key, type, value columns; SpeedSearch filter; copy-value action; hex-preview row rendering for Unparseable. |
| `PeekBundle` | messages/PeekBundle.properties (all user-facing strings, including every PeekError arm). |

Threading rules: every transport/codec call on `Dispatchers.IO` inside the
service scope; only `StateFlow` collection touches EDT; no `runBlocking`.

## 5. Codec algorithms (prose spec)

### SharedPreferencesXmlCodec
Parse with the JDK StAX parser (no new deps). Root `<map>`. Children:
`<string name="k">text</string>` → StringValue (empty element = empty string);
`<int|long|float|boolean name="k" value="v"/>` → typed scalar (boolean:
"true"/"false"); `<set name="k">` containing `<string>` children →
StringSetValue. Unknown element → ParseFailed for that entry, not the file
(collect per-entry errors; render as unparseable rows). XML entity escaping
handled by the parser. Preserve document order.

### PreferencesPbCodec (hand-rolled over Okio, no protobuf-java)
Wire schema [doc: androidx preferences.proto, DESIGN §3]:
`PreferenceMap.preferences` = field 1, map<string, Value> — on the wire, a
repeated embedded message with field 1 = key (string), field 2 = Value.
`Value` oneof: 1 bool(varint), 2 float(fixed32), 3 int32(varint),
4 int64(varint), 5 string(len), 6 StringSet(len; inner: repeated field 1
string), 7 double(fixed64), 8 bytes(len).
Algorithm: read varint tag → field number + wire type; dispatch; unknown
field numbers inside Value ⇒ BytesValue of the raw payload plus a per-entry
warning (forward compatibility, do not fail the store). Malformed varint /
truncated length ⇒ DecodeResult.Failed with reason + original bytes.
Implement one internal `ProtoReader` (Okio `BufferedSource`: varint, fixed32,
fixed64, length-delimited slice) — it is reused verbatim by P3's raw decoder.

### Golden-file strategy
`peek-core/src/test/resources/goldens/`: byte-exact fixtures captured from a
real emulator running the fixture app (see task list): `all_types.xml`,
`all_types.preferences_pb`, plus hand-corrupted variants (`truncated.pb`,
`badtag.pb`, `unknown_field.pb`). Tests assert full decoded `StoreSnapshot`
equality against expected value tables, and failure reasons for corrupt
inputs. Fixture bytes are committed, never regenerated implicitly.
[ref: KB ble-transport-request-response golden pattern]

## 6. Ordered task list

### Phase 0
1. Restructure: create `peek-core`, `peek-core-testing`, `plugin`; move
   template src → `plugin/`; update settings.gradle.kts (+ typesafe
   accessors); root build = spotless/ktlint/BCV only. Delete the delete-list.
2. gradle.properties + plugin.xml rewrite (§2), PeekBundle, tool window EP,
   `transportProvider` extension point declaration, empty-state panel.
3. `peek-core` P1 API skeleton (§3) compiling with explicitApi; `apiDump`
   committed. `FakeTransport` + builders in peek-core-testing.
4. CI: `build`, `apiCheck`, `spotlessCheck`, `verifyPlugin`, `runIdeIC` /
   `runIdeAS` variants configured.
   ✓ P0 acceptance: tests green; tool window opens in runIde on IC and AS;
   IC-without-Android shows explanatory panel, no ClassNotFound.

### Phase 1
5. Codecs first (pure TDD, no device needed): `ProtoReader`, then
   `PreferencesPbCodec`, then `SharedPreferencesXmlCodec`, against
   hand-authored fixtures.
6. Fixture app (`sample/` module or a tiny separate APK, not published):
   writes all 6 SP types + all 8 DataStore value types; capture goldens from
   emulator; swap tests to committed goldens.
   ✓ acceptance: all 14 value shapes decode byte-exactly.
7. `StoreLocator` over FakeTransport: `run-as ls` parsing for `shared_prefs/`
   + `files/datastore/`; not-debuggable / unknown-package detection from
   run-as stderr text.
8. `PeekSession`: Connecting→Active flow, manual refresh, per-store
   Loading/Loaded/Unparseable, torn-read retry-once (virtual-time test),
   Paused on DeviceLost + auto-resume.
9. `DdmlibTransport` + provider + `PeekAppService` (the only ddmlib work).
10. UI: PeekPanel pickers (running debuggable list + manual entry),
    StoreTable with search/copy, error surfaces for every P1 `PeekError` arm.
11. End-to-end verify on emulator against the fixture app.
    ✓ P1 acceptance (ROADMAP): 14 shapes render; search narrows; non-debuggable
    explanation; mid-write retry→hex row; unplug→pause→reconnect→resume
    without IDE restart.

## 7. Explicit out-of-P1 reminders for the implementer
No writes, no polling (manual refresh only), no proto raw decode UI (arm
exists, renders "supported in a later version"), no CLI transport (P2), no
schema resolution, no agent. Do not scaffold them beyond the declared seams.
