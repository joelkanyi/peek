# Peek — Research Notes

Sources actually consulted (2026-08). Format per doctrine: source, problem,
decision taken there, why it works, application to Peek.

## 1. androidx DataStore preferences.proto
- Source: androidx repo, `datastore/**/proto/preferences.proto`
  (raw.githubusercontent.com/androidx/androidx, verified this session).
- Problem: typed KV persistence without SharedPreferences' failure modes.
- Decision: one `PreferenceMap { map<string, Value> }` message; `Value` is a
  oneof over bool/float/int32/int64/string/StringSet/double/bytes, fields 1–8.
- Why it works: proto map + oneof gives forward-compatible typed KV in a flat
  file, atomic tmp-rename writes.
- Application: Peek's `.preferences_pb` codec implements exactly these 3
  messages. Small enough to hand-roll over Okio, avoiding protobuf-java on the
  IDE classpath. Field numbers are the wire contract — golden-file tests.

## 2. Android Studio App Inspection (Database/Background Task/Network Inspectors)
- Sources: developer.android.com/studio/inspect/database and /studio/inspect/task;
  JetBrains "Android Plugin Extension Point List"
  (plugins.jetbrains.com/docs/intellij/android-plugin-extension-point-list.html).
- Problem: live, bidirectional inspection of a running debuggable process.
- Decision: on-device inspectors built on `androidx.inspection`
  (Inspector/Connection), injected by Studio's transport; Studio-side tabs
  registered via EP `com.android.tools.idea.appinspection.inspector.ide.appInspectorTabProvider`
  (class `AppInspectorTabProvider`, flagged Non-Dynamic in the EP list).
- Why it works (for Google): they control both ends and ship in lockstep with
  Studio.
- Application: the EP exists and is technically reachable by a third-party
  plugin, but it is in internal `com.android.tools.idea.*` packages with no
  stability contract, is AS-only, and the inspector-jar discovery pipeline is
  built around androidx AAR metadata. Rejected as Peek's foundation; noted as
  a possible future integration if ever stabilized. Its *architecture*
  (desktop UI ↔ typed channel ↔ on-device agent) is the template for Peek's
  later `peek-runtime` phase.

## 3. Facebook Flipper — shared-preferences plugin
- Source: github.com/facebook/flipper (`flipper-plugin-sharedpreferences`);
  deprecation notices in repo/issues.
- Problem: desktop inspection/editing of app internals.
- Decision: in-app SDK opens a channel to a desktop Electron app; the prefs
  plugin calls real `SharedPreferences` APIs in-process, including edits and
  change listeners.
- Why it works: in-process access is always correct — real API calls, real
  listener notifications, live push updates.
- Why it died: heavyweight SDK + separate desktop app + maintenance burden.
- Application: proves in-app-agent UX (live + true edits) and proves the
  adoption tax. Peek inverts the order: value first with zero deps (adb),
  tiny optional agent later, IDE-embedded instead of separate desktop app.

## 4. Chucker / DebugDrawer / Hyperion
- Source: github.com/ChuckerTeam/chucker, github.com/willowtreeapps/Hyperion-Android.
- Problem: debug tooling without desktop tether.
- Decision: debugImplementation-only, on-device UI, zero desktop component.
- Why it works: near-zero integration cost; nothing leaks into release.
- Application: sets the bar for `peek-runtime`: one `debugImplementation`
  line, no code changes, no release footprint. Also evidence that on-device
  UIs are cramped — an IDE surface is the differentiator Peek keeps.

## 5. SharedPreferences internals (AOSP)
- Source: AOSP `android.app.SharedPreferencesImpl`, `com.android.internal.util.XmlUtils`.
- Problem context: XML map serialization + full in-memory cache per process.
- Key facts: `<map>` root; typed child elements (string/int/long/float/
  boolean/set); file loaded once and cached for process lifetime; writes go
  memory-first then async to disk (apply) with backup-file atomicity.
- Application: (a) exact XML schema for codec + writer; (b) the caching fact
  is WHY external writes are invisible to a running app — the basis for
  `AppliedRequiresAppRestart` write semantics and the read-only MVP.

## 6. russhwolf/multiplatform-settings
- Source: github.com/russhwolf/multiplatform-settings README.
- Problem: common KV API across KMP targets.
- Decision: thin expect/actual over platform stores; Android backends are
  `SharedPreferencesSettings` and `DataStoreSettings` (Preferences DataStore).
- Application: Peek supports Multiplatform Settings on Android automatically
  by supporting the underlying files. Non-Android backends are unreachable
  over adb ⇒ out of scope. Only later cosmetic work: labeling stores as
  MPS-managed (undetectable from bytes alone; would need project-code
  inspection).

## 7. IntelliJ Platform / Android Studio plugin development
- Sources: plugins.jetbrains.com/docs/intellij/android-studio.html;
  .../kotlin-coroutines.html; .../tool-windows.html; JetBrains support threads
  on ddmlib breakage (e.g. `com.android.ddmlib.Client` class→interface in
  AS 4.1, intellij-support.jetbrains.com post 360009828200).
- Problem: one plugin serving IC and AS, reaching adb.
- Decisions/facts: declare `<depends>org.jetbrains.android</depends>` to load
  only with the Android plugin present; ddmlib ships inside the Android
  plugin and its packaging/classes have broken plugins across AS majors; tool
  windows via `toolWindow` EP; platform coroutines with `Dispatchers.EDT`,
  services get injected scopes; `PersistentStateComponent` for state;
  build 242 ↔ AS Ladybug line.
- Application: confine ddmlib to one `DeviceTransport` adapter; keep an
  adb-CLI fallback transport; verify over both IC+android and AS in CI.

## 8. Prior art on JetBrains Marketplace
- Source: plugins.jetbrains.com/plugin/8624-android-sharedpreferences-editor;
  marketplace search.
- Findings: one experimental SharedPreferences editor plugin, long stale;
  nothing handles DataStore `.preferences_pb` or proto stores; Device File
  Explorer offers raw files only.
- Application: confirms the gap Peek fills (DataStore parsing, search, diff,
  KMP-aware framing) and that "prefs editor" alone is not a moat.

## 9. Protobuf raw decoding (schemaless)
- Source: protobuf.dev encoding docs (`protoc --decode_raw` semantics).
- Problem: decode Proto DataStore files without the user's schema.
- Decision pattern: parse tag stream (field number + wire type); for
  length-delimited fields, heuristically try nested-message, then UTF-8,
  else hex.
- Application: `ProtoNode` tree in `KvValue`; later phase maps field names by
  reading `.proto` files from the open project (PSI), which no competing tool
  does.

## 10. Square Wire — wire-schema (added for the full-vision rewrite)
- Source: github.com/square/wire (`wire-schema` module).
- Problem: parse/model `.proto` sources on the JVM without shelling out to
  protoc or dragging in protobuf-java's descriptor machinery.
- Decision: Wire ships a standalone schema parser producing a typed model of
  messages/fields/types from proto source files.
- Why it works: pure-JVM, Square-maintained, built exactly for tooling that
  needs proto schemas outside codegen.
- Application: Peek P5 `SchemaResolver` parses project `.proto` files with
  wire-schema, then ranks message types by structural match (field numbers +
  wire types) against the raw-decoded tree. Audit wire-schema's transitive
  deps for IDE-classpath safety before P5 (flagged in DESIGN.md risks).
