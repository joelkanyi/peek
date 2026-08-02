<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Peek Changelog

## [Unreleased]
### Added
- Project skeleton: `peek-core` (pure JVM domain and transport seam), `peek-core-testing` (FakeTransport fixture), and the `plugin` module.
- SharedPreferences XML and Preferences DataStore (`.preferences_pb`) codecs, hand-decoded over Okio.
- Store locator, and a session pipeline (locate, fetch, decode) with torn-read retry and device-loss handling.
- adb command-line transport and a read-only tool window: pick a device and debuggable app, browse stores as typed, searchable tables.
- Auto-refresh while the tool window is visible, with changed and added keys highlighted.
- Proto DataStore decoding: schemaless protobuf shown as an expandable field tree.
- Custom store paths, to reach stores outside the standard directories.
- Editing SharedPreferences and Preferences DataStore values (edit, add, delete), written back over adb after force-stopping the app, with honest outcomes.
