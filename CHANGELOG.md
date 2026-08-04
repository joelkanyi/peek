<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Peek Changelog

## [Unreleased]

### Changed
- Removed the upper IDE compatibility bound so Peek stays available in 2025.3 (253) and future releases, including current Android Studio.

## [0.1.0]

First release. Inspect and edit an Android app's key-value storage from the IDE.

### Added
- **Browse** SharedPreferences, Preferences DataStore (`.preferences_pb`, hand-decoded), and Proto DataStore (schemaless field tree) of any debuggable app over adb, as typed, searchable tables. No changes to the app required.
- **Live refresh** while the tool window is visible, with changed and added keys highlighted; the device list stays current as emulators come and go.
- **Editing**: edit, add, and delete values. Over adb, writes are applied safely (force-stop then atomic write) with honest outcomes; with the agent, edits are live and require no restart.
- **Custom store paths** for stores outside the standard directories.
- **Snapshots**: capture an app's stores at a point in time, then compare two snapshots to see exactly what changed. Persisted across restarts.
- **Live mode** via the optional `io.github.joelkanyi:peek-runtime` debug dependency: instant push updates and no-restart editing that fires the app's own listeners. Currently serves SharedPreferences.
- Native tool window UI: an icon action toolbar with an overflow menu and the tool window gear menu.
