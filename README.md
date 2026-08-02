# Peek

![Build](https://github.com/joelkanyi/peek/workflows/Build/badge.svg)

<!-- Plugin description -->
Peek is an Android Studio and IntelliJ plugin for inspecting an app's key-value
storage: SharedPreferences, Jetpack DataStore (Preferences and Proto), and
Multiplatform Settings.

Pick a connected device or emulator and a debuggable app, and Peek reads its
stores over adb and renders them as typed, searchable tables. No changes to your
app required.
<!-- Plugin description end -->

## Status

Early development. See [`DESIGN.md`](DESIGN.md) for the architecture,
[`ROADMAP.md`](ROADMAP.md) for the phase ladder, and
[`IMPLEMENTATION-P0-P1.md`](IMPLEMENTATION-P0-P1.md) for the current build plan.

## Modules

- `peek-core` — pure Kotlin/JVM domain: the device-transport seam, store codecs,
  and session logic. No IntelliJ or ddmlib dependencies, unit-testable headless.
- `peek-core-testing` — test fixtures (a scriptable `FakeTransport`) for
  exercising `peek-core` without a device.
- `plugin` — the IntelliJ plugin: tool window, services, and the ddmlib transport.

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
