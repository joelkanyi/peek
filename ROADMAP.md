# Peek — Phase Ladder (first shippable slice → full vision)

Framing: every capability of the full product lives on this ladder. No
"out of scope" — only "which phase". Each phase = goal + concrete scope +
testable acceptance criteria (happy AND failure/retry path). Phases ship
independently; a phase starts only after the prior one's criteria pass.

## Phase 0 — Skeleton & seam (foundation, no user value yet)
Goal: replace template scaffolding with the module structure and the
dual-channel-ready transport seam.
Scope:
- Modules `peek-core` (pure JVM, explicitApi) + `plugin`; delete My* template classes.
- `DeviceTransport` interface designed for both channel types (capabilities
  flag), `FakeTransport` + snapshot builders in test fixtures.
- Tool window registered with empty state; optional-depends plugin.xml split
  (`org.jetbrains.android` via config-file); runIde boots on IC+Android and AS.
Acceptance:
- Happy: `:peek-core:test` + `:plugin:verifyPlugin` green; tool window opens in runIde on both targets.
- Failure: on IC without the Android plugin, Peek still loads and shows an explanatory panel (no ClassNotFound).

## Phase 1 — First shippable slice: read-only SP + Preferences DataStore over ADB
Goal: pick device + debuggable app, see parsed, typed, searchable KV tables.
Scope:
- ddmlib transport: device list via bridge listener, running debuggable
  processes; manual package entry.
- StoreLocator over `shared_prefs/` and `files/datastore/`.
- Codecs: SharedPreferences XML decode (all 6 types); `.preferences_pb`
  decode (all 8 value types), hand-rolled over Okio, golden-file tested
  against bytes captured from a real device.
- Table UI: type column, search/filter, copy value; manual Refresh.
- Sealed `PeekError` with every P1 arm surfaced (not-debuggable explanation,
  torn-read retry + hex fallback, device-lost banner + auto-resume).
Acceptance:
- Happy: fixture app on an emulator renders all 14 value shapes correctly; search narrows rows.
- Failure: non-debuggable package shows the run-as explanation, never an empty table; mid-write DataStore pull retries once then shows hex-preview row; unplugging mid-refresh pauses and recovers on reconnect without IDE restart.

## Phase 2 — Freshness & resilience: polling refresh, diff, CLI fallback
Goal: live-ish view without an agent; Peek works on IC without the Android plugin.
Scope:
- `RefreshPolicy`: mtime polling (injectable clock), only changed stores
  re-fetched, coalescing, pause-when-hidden, configurable interval.
- Diff vs previous snapshot: changed keys highlighted, added/removed markers.
- `AdbCliTransport` (adb path from ANDROID_HOME/settings) as fallback and as
  the IC-without-Android default.
- Huge-file guard (size threshold, lazy load); EncryptedSharedPreferences
  labeled ciphertext view.
Acceptance:
- Happy: a pref changed in the running app appears highlighted within 2x the poll interval; same flows pass on IC using only the CLI transport.
- Failure: polling a disconnected device yields one paused state, not an error per tick; virtual-time tests prove no overlapping fetches; missing adb binary produces the settings hint, not a stack trace.

## Phase 3 — Proto DataStore (schemaless) + discovery depth
Goal: visibility into arbitrary `.pb` stores and into non-running apps.
Scope:
- Raw wire-format parser → `ProtoNode` tree (nested/UTF-8/hex heuristics).
- User-added custom store paths, persisted (covers custom `produceFile`).
- Opt-in "show all installed" discovery: `pm list packages -3` + parallel
  `run-as` probes (explicit action, never the default — slow/noisy).
Acceptance:
- Happy: proto store with nested messages, repeated fields, packed varints renders a tree matching `protoc --decode_raw` on identical bytes (goldens); a stopped debuggable app is discoverable via full scan and browsable.
- Failure: non-proto binary renders as hex labeled "not valid protobuf", no crash; truncated file handled; full scan on a device with 200 packages stays cancellable and time-bounded.

## Phase 4 — Editing over ADB with honest semantics
Goal: edit/add/delete SP and Preferences DataStore entries; truth in outcomes.
Scope:
- Symmetric encoders, byte-level round-trip tests (decode→encode→decode equality).
- Stale-snapshot mtime guard; atomic write via `run-as` tmp+rename.
- Sealed `WriteOutcome` UI: running app ⇒ offer force-stop+relaunch, else
  `AppliedRequiresAppRestart`; stopped app ⇒ `Applied` with re-fetch verify.
Acceptance:
- Happy: edit with app stopped, relaunch, app reads the new value; add and delete round-trip for every value type.
- Failure: store modified on-device between snapshot and write ⇒ `Refused(StaleSnapshot)` + re-read prompt; killing adb mid-write leaves the original file intact (integration test).

## Phase 5 — Project-aware intelligence: schema resolution, MPS badges, snapshots
Goal: decode proto stores with real field names from the open project; recognize Multiplatform Settings; time-travel.
Scope:
- `SchemaResolver`: PSI/FilenameIndex scan for `.proto` files (read actions),
  parsed with `com.squareup.wire:wire-schema`; structural-match ranking
  (field numbers + wire types); auto-pick unique confident match, ranked
  picker otherwise; per-store persistence; raw-view toggle retained.
- Multiplatform Settings detection (Gradle dep + call sites) → store badges.
- `SnapshotStore`: named captures, diff-across-time view, JSON export.
- Multi-user/work-profile support (`run-as --user`).
Acceptance:
- Happy: fixture project's proto store shows named fields automatically; renaming the message drops to ranked picker; snapshot diff shows exactly the changed keys between two captures; export re-imports losslessly (P7 import verified against this format).
- Failure: project with zero/unparseable .proto files degrades to raw mode with a notice, wire-schema parse errors reported per-file, never fatal.

## Phase 6 — peek-runtime agent: live push (read side)
Goal: opt-in `debugImplementation("io.github.joelkanyi:peek-runtime:x")` upgrades sessions to sub-second pushed updates.
Scope:
- `peek-wire` module: length-prefixed framed protocol, versioned handshake.
- `peek-runtime`: tiny Android lib (no UI, no transitive bloat),
  localabstract server socket; registers SP/DataStore change listeners.
- Plugin: `adb forward` probe + handshake auto-detect; in-place session
  upgrade to `AgentChannel`; capability badge; downgrade on socket loss.
- Publishing per doctrine §14 (vanniktech plugin, Central Portal, RELEASING.md,
  explicitApi + BCV dumps).
Acceptance:
- Happy: with agent present, an in-app change appears <500 ms with no polling; removing the dependency cleanly restores Phase-2 polling behavior.
- Failure: protocol-version skew rejects with "update peek-runtime" and falls back to Channel A; socket death mid-session downgrades silently with badge change; release build contains zero agent bytes (verified by APK scan test).

## Phase 7 — Full correct editing via the agent
Goal: the destination for writes — live, in-process, correct.
Scope:
- Agent-side edit commands executing real `SharedPreferences.Editor` /
  `DataStore.updateData`; app listeners fire; outcome `Applied`.
- Proto DataStore editing, type-checked against the P5 resolved schema.
- Snapshot import/restore (push a captured snapshot back to the device via
  whichever channel is active, same outcome gating).
Acceptance:
- Happy: an edit from Peek fires the app's own OnSharedPreferenceChangeListener / DataStore collector without restart; a proto field edit of the wrong type is rejected at plan time, not on device.
- Failure: concurrent in-app write during an agent edit resolves via updateData's transactional retry, final state consistent; agent loss mid-edit reports `Refused(TransportLost)` with no partial write.

## Phase 8 — KMP platform reach (committed)
Goal: Peek becomes a true KMP key-value inspector — non-Android Multiplatform
Settings backends via agent-channel variants.
Scope:
- `peek-wire` over TCP (KMP targets exercised for real from here; the module
  is KMP-ready since P6).
- iOS simulator: NSUserDefaults — plist read via `xcrun simctl`/filesystem
  first, agent variant for live push and edits.
- Desktop/JVM: `java.util.prefs` agent (same wire protocol over TCP).
- Later in-phase: JS storage backend.
- Per-platform StoreLocators; channel picker generalizes device picker.
- Marketplace polish rides here: listing, docs, TELEMETRY.md + opt-in consent
  flow (off by default, disclosed event list).
Acceptance:
- Happy: per platform, mirrors P1+P6 bars — typed read of the platform store, live push <500 ms with agent, snapshot/diff working unchanged (transport-agnostic session proven).
- Failure: simulator/process absent yields the same paused-session surfaces as device loss; telemetry sends nothing until consent recorded (asserted in tests).

---

## Formerly open vision questions — now locked [doc: Joel, 2026-08]

1. Platform reach: KMP-wide destination; P8 committed (above).
2. Monetization: free and open forever, Apache-2.0 for plugin and all libs.
3. Snapshots/time-travel: headline feature; full P5/P7 investment.
4. Telemetry: anonymous, opt-in, off by default, fully disclosed (DESIGN §8a).
5. Agent init: zero-config ContentProvider auto-init with the three-way
   hidden-behavior mitigation (DESIGN §3a).
