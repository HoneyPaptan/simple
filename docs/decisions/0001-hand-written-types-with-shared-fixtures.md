# Hand written wire types with shared fixtures

Date: 2026-11-04

The spec planned one schema generating Go and Kotlin types. We write the types by hand on both sides from `docs/protocol.md` and keep one JSON fixture per message in `proto/fixtures/`. The Go tests and the Kotlin tests both decode every fixture and re-encode it.

Why: a generator is more code to own than the types it saves, and the fixtures catch drift on both sides just as well.
