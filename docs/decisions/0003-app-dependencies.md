# App dependencies

Date: 2026-11-04

Android Gradle Plugin 9.4.1 and Kotlin 2.4.20 are a pair we have built and shipped before, so builds stay on a known good combination.

Room 2.7.2 with KSP 2.4.20-1.0.30 stores hosts, agents, inbox, messages, and workspaces locally with compile time DAO checks.

OkHttp 4.12.0 handles pinned TLS pairing, the host WebSocket, and MockWebServer in JVM tests.

Compose BOM 2026.09.00 with foundation and ui only keeps the placeholder UI free of Material 3. `ui-test-junit4` and `ui-test-manifest` back JVM semantics tests for accessibility regressions.

kotlinx.serialization 1.9.0 and kotlinx.coroutines 1.10.2 match the daemon side JSON and async style from `docs/protocol.md`.

`@hugeicons/core-free-icons` (MIT) is a dev only Node dependency under `scripts/icons/`. The APK bundles only paths emitted by `scripts/icons/generate.mjs` into `IconsGenerated.kt`.
