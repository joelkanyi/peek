<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Peek Changelog

## [Unreleased]
### Added
- Project skeleton: `peek-core` (pure JVM domain and transport seam), `peek-core-testing` (FakeTransport fixture), and the `plugin` module.
- SharedPreferences XML and Preferences DataStore (`.preferences_pb`) codecs, hand-decoded over Okio.
- Store locator, and a session pipeline (locate, fetch, decode) with torn-read retry and device-loss handling.
- adb command-line transport and a read-only tool window: pick a device and debuggable app, browse stores as typed, searchable tables.
