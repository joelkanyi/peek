# Peek — Full-Vision Design

**Peek is a KMP key-value inspector, Android-first.** Inspect and manage the
key-value storage of debuggable apps (SharedPreferences, Jetpack DataStore,
Multiplatform Settings across its platform backends) from an IDE tool window.
"Database Inspector, but for preferences" — plus time-travel: named snapshots,
diff-across-time, and export/import are marquee features, not extras.

Free and open forever, Apache-2.0 — plugin and all published libraries.
[doc: Joel's locked decisions, 2026-08; matches the Kepler pattern]

Framing rule: this document describes the COMPLETE product. Nothing is "out of
scope"; every capability carries a phase tag `[P1]`..`[P8]` mapping to
ROADMAP.md. Phase 1 is the first shippable slice, not a cut-down MVP.

Written before code, per the Kepler convention (DESIGN.md → RELEASING.md →
implementation). [ref: joelkanyi/kepler]

Settled product decisions (locked by Joel, treated as fixed inputs):
1. Distribution: `platformType = IC` + `<depends>org.jetbrains.android</depends>`;
   ddmlib is the primary transport, plain adb-CLI fallback keeps
   IC-without-Android working; CI matrix over IC+Android and Android Studio.
2. `peek-runtime` (published under `io.github.joelkanyi`, debugImplementation-
   only socket agent) is in-vision; the transport seam supports two channel
   types from day one.
3. Read-only is acceptable for Phase 1; full editing is the destination —
   first honest ADB force-stop editing, then live in-process editing via the
   agent.
4. Platform reach: KMP-wide. P8 (iOS-simulator NSUserDefaults, desktop/JVM
   prefs, later JS storage) is a committed phase; `peek-wire` is KMP-ready
   from P6.
5. Licensing: Apache-2.0, free forever, no commercial tier.
6. Snapshots/time-travel: headline differentiator (full P5/P7 investment).
7. Telemetry: anonymous, opt-in, off by default, fully disclosed (§8a).
8. Agent init: zero-config ContentProvider auto-init (§3a mitigation).

---

## 1. Problem, users, destination

Developers stash state in SharedPreferences/DataStore and have no first-class
way to see or manage it: hand-typed `run-as cat`, raw Device File Explorer
bytes, a stale SharedPreferences-only marketplace plugin (id 8624), and a
deprecated Flipper. Nothing anywhere parses `.preferences_pb` or proto stores.

**The destination**: open a tool window, pick any device and any debuggable
app, and get every KV store the app owns as live, typed, searchable, editable
tables — SharedPreferences, Preferences DataStore, Proto DataStore decoded
with real field names resolved from the project's own `.proto` files,
Multiplatform Settings recognized and labeled — with pushed sub-second
updates and correct in-process edits when the tiny opt-in agent is present,
honest force-stop edits when it is not, plus snapshot, diff-across-time, and
export/import tooling.

Target users: Android and KMP app developers (Android Studio primary,
IDEA+Android secondary), and library authors debugging settings abstractions.

Permanent non-goals (true even at full vision): release/non-debuggable apps,
rooted-device tricks, SQLite/Room (Database Inspector owns it), being a
general file explorer.

Committed frontier: non-Android Multiplatform Settings backends — iOS
simulator NSUserDefaults, desktop/JVM `java.util.prefs`, later JS storage.
adb cannot reach them; the agent channel does. `[P8, committed]` The
Android-first ladder pays for the KMP destination incrementally: `peek-wire`
is KMP-ready from P6, so P8 adds channel implementations and locators, not a
redesign.

Time-travel is a headline feature: named snapshots, diff-across-time, JSON
export `[P5]` and import/restore `[P7]`. Positioning: no other tool (App
Inspection included) can answer "what changed in my prefs between these two
moments" or restore a captured state onto a device.

---

## 2. Data-access mechanism (settled, rationale preserved)

| Mechanism | Verdict |
|---|---|
| App Inspection framework | **Rejected permanently.** The Studio-side EP exists (`com.android.tools.idea.appinspection.inspector.ide.appInspectorTabProvider`, class `AppInspectorTabProvider`, flagged Non-Dynamic [doc: JetBrains "Android Plugin Extension Point List"]) but sits in internal `com.android.tools.idea.*` packages with no stability contract, is AS-only, and inspector-jar discovery is built for androidx AAR metadata. Basing Peek's existence on it contradicts "boring, predictable". [inference from the above] |
| ADB file access (`exec-out run-as <pkg> …`) | **Channel A.** Zero app-side dependency, decade-stable contract, same mechanism Device File Explorer uses for app-private dirs. Carries P1–P5. |
| `peek-runtime` agent, localabstract socket + `adb forward` | **Channel B.** In-process = always correct: real `Editor.commit()` / `DataStore.updateData`, real listeners, pushed events. Flipper's prefs plugin proves the UX [ref: facebook/flipper]; Chucker proves debugImplementation-only adoption [ref: ChuckerTeam/chucker]. Carries P6+. |

Both channels implement one session contract; the UI never knows which is
active beyond a capability badge. Channel B is auto-detected per app (forward
probe + handshake) and upgrades a session in place; socket loss degrades back
to Channel A. `[P6]`

---

## 3. On-disk formats (codec contracts)

### SharedPreferences XML `[P1]`
[doc: AOSP `SharedPreferencesImpl` / `XmlUtils` output]
```xml
<map>
    <string name="user">joel</string>
    <int name="count" value="3" />
    <long name="ts" value="1712" />
    <float name="ratio" value="0.5" />
    <boolean name="flag" value="true" />
    <set name="tags"><string>a</string><string>b</string></set>
</map>
```
Six types; strings as element text, scalars as `value` attributes. Fully
round-trippable → symmetric encoder `[P4]`. EncryptedSharedPreferences files
render honestly as labeled ciphertext, never decrypted `[P2]`.

### Preferences DataStore `[P1]`
[doc: androidx source `datastore/**/proto/preferences.proto`, verified 2026-08]
```proto
message PreferenceMap { map<string, Value> preferences = 1; }
message Value { oneof value {
  bool boolean = 1; float float = 2; int32 integer = 3; int64 long = 4;
  string string = 5; StringSet string_set = 6; double double = 7; bytes bytes = 8; } }
message StringSet { repeated string strings = 1; }
```
`.preferences_pb` is this message serialized plainly, at
`files/datastore/<name>.preferences_pb` by default [doc: androidx
`preferencesDataStoreFile`]. Peek hand-rolls this codec over Okio — three tiny
messages, and a raw wire parser is needed anyway — keeping protobuf-java off
the IDE classpath (classic plugin classloader conflict). [inference; ref:
doctrine "no hidden behavior", Okio] Symmetric encoder `[P4]`.

### Proto DataStore — BOTH modes, phased (decided)
- **Schemaless raw decode `[P3]`**: the `protoc --decode_raw` algorithm [doc:
  protobuf.dev encoding spec] — tag/wire-type stream → `ProtoNode` tree;
  length-delimited payloads tried as nested message → UTF-8 → hex.
  Golden-tested against `--decode_raw` output on the same bytes.
- **Field-name resolution from the project `[P5]`** — mechanism decided:
  scan the open project for `.proto` files (PSI/`FilenameIndex` over source
  roots, inside read actions), parse them with
  **`com.squareup.wire:wire-schema`** — pure-JVM Square library that parses
  proto sources into a typed schema model without protoc [ref: square/wire
  wire-schema; aligns with Square-first doctrine, avoids bundling protoc].
  Candidate message types ranked by structural match against the raw tree
  (field numbers + wire types); auto-pick on a unique confident match,
  otherwise a ranked user picker; choice persisted per `StoreHandle`. Named
  decode overlays the raw tree; raw view stays one toggle away. Resolved
  schema also type-checks proto edits `[P7]`. [inference: structural-match
  ranking; no prior art does this]

### Multiplatform Settings
Android backends are `SharedPreferencesSettings` and `DataStoreSettings`
[doc: russhwolf/multiplatform-settings README] → file-level support is free
from `[P1]`. Recognition is the added value: detect MPS usage in the open
project (Gradle dep + call sites) and badge the stores it manages `[P5]`.
Non-Android backends: `[P8, committed]` via agent-channel variants (TCP to
iOS simulator / desktop JVM processes) — the session contract is
transport-agnostic precisely so this needs no redesign, only new channel
implementations and store locators.

### 3a. Agent initialization (decided): zero-config ContentProvider auto-init
`peek-runtime` self-initializes via a manifest-merged `ContentProvider`
(androidx-startup/Chucker style [ref: ChuckerTeam/chucker]) — add the
`debugImplementation` line and Peek works, no code. This is deliberate
"hidden wiring, disclosed behavior", mitigating the doctrine's no-hidden-
behavior rule three ways [doc: Joel's locked decision + mitigations]:
1. `debugImplementation`-only: the provider can never exist in a release
   build (verified by APK-scan test, P6 acceptance).
2. One greppable startup log line (`Peek agent listening on <socket>`),
   always emitted.
3. Documented opt-out: `tools:node="remove"` on the provider in the app
   manifest, shown in the README.

---

## 4. Core abstractions

Immutable value types, `internal` constructors + factory functions, sealed
outcomes handled exhaustively, `explicitApi()`. [ref: OkHttp `HttpUrl`;
Kepler `KeplerUri`; doctrine §5–6]

| Abstraction | Responsibility | Phase |
|---|---|---|
| `Device`, `AppPackage` | Value types: serial/model/api; package/debuggable/pid | P1 |
| `DeviceTransport` | THE seam: `exec`, `readFile`, `writeFile`, `stat`, `capabilities` | P1 (writeFile P4) |
| `AdbTransport` (ddmlib) / `AdbCliTransport` | Channel A impls; ddmlib confined to ONE adapter class — its API has broken plugins across AS majors (`Client` class→interface in AS 4.1 [doc: JetBrains support post 360009828200]) | P1 / P2 |
| `AgentChannel` | Channel B: framed socket protocol, handshake, pushed events, in-process commands | P6 |
| `StoreLocator` | Scan `shared_prefs/`, `files/datastore/` (+ custom paths P3) → `List<StoreHandle>` | P1 |
| `StoreHandle` | Value type: package, path, `StoreType`, mtime, size | P1 |
| `StoreCodec` | Per-format decode → `StoreSnapshot`; encode for round-trippable formats | P1 (encode P4) |
| `StoreSnapshot` / `KvEntry` / sealed `KvValue` | Immutable decoded store; arms: Bool/Int/Long/Float/Double/String/StringSet/Bytes/`ProtoNode` | P1 (ProtoNode P3) |
| `SchemaResolver` | wire-schema project scan + structural match → named proto decode | P5 |
| `PeekSession` | Per (device,pkg) pipeline owner: snapshot cache, diffing, `Flow<SessionState>`, channel upgrade/downgrade | P1 (upgrade P6) |
| `RefreshPolicy` | Poll loop: injectable clock, coalescing, pause-when-hidden; replaced by push under Channel B | P2 [ref: KB coroutine-policy-object] |
| `EditPlan` → `WriteOutcome` | Sealed: `Applied`, `AppliedRequiresAppRestart`, `Refused(StaleSnapshot \| AppRunning \| Unsupported \| TransportLost)` | P4 |
| `SnapshotStore` | Named point-in-time captures; diff across time; JSON export; import/restore | P5 (import P7) |
| `PeekError` | Sealed failure taxonomy (§7) | P1 |

`peek-core` never sees ddmlib, IntelliJ, or adb string plumbing — only
`DeviceTransport` and value types. [ref: doctrine §8]

## 5. Pipeline

```
 Device/app picker (UI)
      |            discovery: ddmlib device listener + running debuggable
      v            clients [P1]; manual package entry [P1]; opt-in full scan:
+-------------+    pm list packages -3 + parallel run-as probes [P3]
|  Discovery  |
+-------------+
      |
      v
+-------------+   well-known dirs [P1] + custom paths [P3]
| StoreLocator|--> List<StoreHandle>
+-------------+
      |
      v
+-------------+   Channel A: exec-out run-as cat  [P1]
|  Fetcher    |   Channel B: agent read command    [P6]
+-------------+
      |
      v
+-------------+   XML [P1] | preferences_pb [P1] | raw proto [P3]
| StoreCodec  |   | named proto via SchemaResolver [P5]
+-------------+
      |
      v
+-------------+   diff vs previous snapshot -> changed/added/removed marks [P2]
| PeekSession |--> Flow<SessionState> --> table/tree UI (EDT)
+-------------+
      ^   ^
      |   +-- RefreshPolicy: mtime poll, only changed stores re-fetched [P2]
      +------ AgentChannel: pushed change events, <500 ms [P6]

 Edit path:
 UI edit -> EditPlan -> gate
   Channel A [P4]: stat mtime (changed since snapshot? -> Refused(StaleSnapshot))
        -> app running? offer force-stop+relaunch; outcome
           AppliedRequiresAppRestart if user declines stop
        -> Codec.encode -> run-as sh -c 'cat > .peek-tmp && mv .peek-tmp target'
           (mirrors DataStore's own tmp+rename atomicity [doc: androidx
           SingleProcessDataStore write path])
        -> re-fetch, byte-level round-trip verify -> WriteOutcome
   Channel B [P7]: agent executes real SharedPreferences.Editor /
        DataStore.updateData; app listeners fire; outcome Applied;
        proto edits type-checked against resolved schema
```

## 6. Modules

```
peek/
├── peek-core/           # pure JVM: domain, codecs, locator, session, policies.
│                        # deps: okio, coroutines, wire-schema (P5). explicitApi.
├── peek-core-testing/   # FakeTransport, snapshot/handle builders, byte goldens.
│                        # [ref: Kepler *-testing artifact]
├── peek-wire/           # [P6] framed protocol shared by plugin & agent
│                        # (pure Kotlin, KMP-ready for P8)
├── peek-runtime/        # [P6] published Android lib, io.github.joelkanyi,
│                        # debugImplementation-only, no UI, no transitive bloat
└── plugin/              # IntelliJ: tool window, services, transports, PSI scan,
                         # PersistentStateComponent, notifications
```

Codecs are golden-file tested against bytes captured from real devices
(fixed-byte goldens [ref: KB ble-transport-request-response]); `peek-core`
tests run headless, policies on virtual time.

## 7. Failure taxonomy → surfacing

Sealed `PeekError`; every arm has a defined UI surface; no silent catch.
[ref: doctrine "no hidden behavior"]

| Failure | Detection | Surface | Phase |
|---|---|---|---|
| No adb / no devices | bridge init / empty list | empty-state with fix hint (ANDROID_HOME / adb path setting) | P1 |
| Device lost mid-session | ddmlib listener | banner, session paused, auto-resume on reconnect [ref: KB coroutine-policy-object] | P1 |
| App not debuggable | run-as error text | locked badge + run-as explanation | P1 |
| Unknown package / wrong user profile | run-as error | error row; `--user` support | P1 / P5 |
| File vanished between ls and cat | cat exit code | drop handle, refresh list | P1 |
| Torn read (DataStore mid-write) | parse failure | retry once @200 ms, then "unparseable" row with hex preview | P1 |
| Unknown proto schema | always for .pb | raw tree labeled "schemaless" — a mode, not an error | P3 |
| Ambiguous schema match | >1 confident candidate | ranked picker, choice persisted | P5 |
| Stale snapshot on write | mtime pre-check | `Refused(StaleSnapshot)` + re-read prompt | P4 |
| Write interrupted | tmp+rename | original intact by construction; proven by kill-mid-write integration test | P4 |
| Agent version skew | handshake version field | "update peek-runtime" notice, fall back to Channel A | P6 |
| Agent socket death | frame read failure | silent downgrade to Channel A + capability badge change | P6 |
| Huge file (blob abuse) | stat size threshold | lazy load + truncation warning | P2 |

## 8. IntelliJ Platform integration

- plugin.xml: `<depends>com.intellij.modules.platform</depends>` +
  `<depends optional="true" config-file="peek-android.xml">org.jetbrains.android</depends>`;
  ddmlib transport registered only via the optional descriptor, `AdbCliTransport`
  is the default elsewhere `[P2]`. sinceBuild 242 (AS Ladybug line); CI
  verification over IC+Android and AS via `intellijPlatformTesting`. [doc:
  plugins.jetbrains.com Android Studio plugin dev page; gradle.properties]
- Tool window EP, bottom anchor (mirrors App Inspection placement).
- Project-level `PeekProjectService` owning sessions with the injected service
  `CoroutineScope`; app-level service sharing the adb bridge.
- Threading: transport/parsing on `Dispatchers.IO`; UI mutation on EDT via
  `Dispatchers.EDT`; no `runBlocking` on EDT; long ops in
  `withBackgroundProgress`; PSI scans (P5) inside read actions only. [doc:
  IntelliJ Platform kotlin-coroutines + threading docs]
- `PersistentStateComponent`: last device/pkg, custom paths, refresh interval,
  per-store schema choices, snapshot metadata, adb path, telemetry consent.

### 8a. Telemetry (decided): anonymous, opt-in, off by default, disclosed
[doc: Joel's locked decision] Doctrine tension ("no hidden behavior")
acknowledged; mitigated by consent-first design:
- Nothing is collected until the user explicitly opts in via a one-time,
  dismissible prompt (default answer: off). Toggle lives in Peek settings.
- If enabled, collected data is minimal and anonymous: plugin version, IDE
  build, feature-usage counters (e.g. "opened proto store", "used snapshot
  diff"), error-category counts from the sealed `PeekError` taxonomy. Never:
  package names, keys, values, file paths, device serials, or anything
  project-identifying.
- The exact event list is published in the repo (TELEMETRY.md) and versioned;
  the consent prompt links to it. Implementation lands with Marketplace
  polish (P8-adjacent), never before the consent flow exists.

## 9. Risks

1. ddmlib churn across AS majors — one-adapter confinement + CLI fallback.
2. External writes to a running app are inherently unsafe (SharedPreferences
   in-process cache [doc: AOSP SharedPreferencesImpl]; DataStore single-writer
   invariant [doc: androidx docs]) — hence the force-stop gate and the agent
   as the only "live edit" path. Never blur this line in UI copy.
3. protobuf classloader conflicts — no protobuf-java; hand-rolled codec.
   wire-schema's own runtime deps must be audited before P5 [inference —
   verify then].
4. Custom `produceFile` DataStore locations break scanning — custom paths P3.
5. Google shipping a first-party prefs inspector — Peek's moat: KMP framing,
   schema resolution, editing, snapshots/diffs.

## 10. Decision provenance summary

| Decision | Origin |
|---|---|
| Dual-channel seam (ADB + agent), App Inspection rejected | [doc] EP list Non-Dynamic/internal + [ref] Flipper/Chucker evidence + [inference] |
| preferences_pb codec shape | [doc] androidx preferences.proto (verified) |
| SP XML schema + cache-driven write semantics | [doc] AOSP SharedPreferencesImpl/XmlUtils |
| Proto name resolution via wire-schema + structural-match ranking | [ref] square/wire; [inference] ranking design |
| Discovery ladder: running-debuggable + manual (P1), opt-in full scan (P3) | [inference] full `pm list` + probe is slow/noisy as default, cheap as opt-in |
| Poll-then-push refresh; tmp+rename writes; sealed WriteOutcome | [doc] DataStore atomicity; [ref] doctrine sealed-outcome rule |
| Hand-rolled proto wire codec over Okio | [inference] IDE classpath risk; [ref] Okio |
| Optional-depends plugin.xml split for IC-without-Android | [doc] IntelliJ plugin dependency docs (optional depends + config-file) |
| KMP-wide reach, P8 committed | [doc] Joel decision 2026-08 |
| Apache-2.0, free forever, all artifacts | [doc] Joel decision; [ref] Kepler licensing |
| Snapshots/time-travel as headline feature | [doc] Joel decision |
| Telemetry opt-in/off-by-default/disclosed | [doc] Joel decision; mitigation design [inference] |
| ContentProvider auto-init + 3-way mitigation | [doc] Joel decision; [ref] Chucker/androidx-startup; mitigations [inference] |
