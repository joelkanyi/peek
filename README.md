# Peek

[![Version](https://img.shields.io/jetbrains/plugin/v/io.github.joelkanyi.peek.svg)](https://plugins.jetbrains.com/plugin/io.github.joelkanyi.peek)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/io.github.joelkanyi.peek.svg)](https://plugins.jetbrains.com/plugin/io.github.joelkanyi.peek)

Inspect and edit an Android app's key-value storage from your IDE.

<!-- Plugin description -->
Peek is an Android Studio and IntelliJ plugin for inspecting and editing an app's
key-value storage: **SharedPreferences**, **Jetpack DataStore** (Preferences and
Proto), and **Multiplatform Settings**.

Pick a connected device or emulator and a debuggable app, and Peek reads its
stores over adb and renders them as typed, searchable tables, no changes to your
app required. Values keep their real types, changes highlight as they happen, and
you can edit, add, and delete keys. Add the optional `peek-runtime` dependency for
**live mode**: instant push updates and edits that apply while the app runs.
<!-- Plugin description end -->

## Why

Android apps constantly stash state in SharedPreferences and DataStore, and there
is no first-class way to see it. You end up running `adb shell run-as … cat` by
hand and squinting at raw XML, and nothing parses DataStore's binary format at
all. Peek does.

## Features

- **All the stores.** SharedPreferences (XML), Preferences DataStore
  (`.preferences_pb`, hand-decoded), and Proto DataStore (schemaless tree view).
- **Typed and searchable.** Every value keeps its real type; filter by key.
- **Live refresh.** Auto-updates while visible; changed and added keys highlight.
- **Editing.** Edit, add, and delete values. Over adb it writes safely (force-stop
  then write); with the agent it edits live.
- **Snapshots.** Capture an app's stores at a point in time and diff two snapshots
  to see exactly what changed.
- **Live mode (optional).** Add `peek-runtime` to your debug build for push updates
  and instant, no-restart editing that fires the app's own listeners.

## Install

Settings/Preferences → Plugins → Marketplace → search **"Peek"** → Install.

Works in Android Studio and IntelliJ IDEA. Needs an Android SDK (`adb`) on the
machine, resolved automatically.

## Usage

1. Open the **Peek** tool window (docked at the bottom).
2. Pick a **device** and a **debuggable app**.
3. Browse its stores in the left list; the selected store's entries show on the right.
4. Double-click a value to edit it, or use the toolbar to add/delete keys and
   capture snapshots.

## Live mode

For instant updates and no-restart editing, add the agent to your **debug** build:

```kotlin
dependencies {
    debugImplementation("io.github.joelkanyi:peek-runtime:<version>")
}
```

It auto-starts (debug builds only, never shipped) and Peek connects to it
automatically, the status bar shows "live". Currently serves SharedPreferences.

## Modules

- `peek-core` — pure Kotlin/JVM domain: transport seam, store codecs, session logic.
- `peek-wire` — the socket protocol shared by the plugin and the agent.
- `peek-runtime` — the on-device agent (Android, debug-only).
- `plugin` — the IntelliJ plugin.
- `sample` — a tiny app for testing.

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
