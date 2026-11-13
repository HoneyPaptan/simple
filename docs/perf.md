# Performance budgets

Targets from `README.md` plus a few runtime checks for the reviewer. Measured on a signed release build on a real mid-range phone (Android 13, 1080x2400) paired over the tailnet, on 2026-11-06. Release artifact sizes rechecked with `scripts/release 0.1.0-rc1` on 2026-11-09.

| Metric | Goal | How to measure | Measured |
|---|---|---|---|
| Release APK size | under 8 MiB | `./scripts/check` (or `stat` on `app/build/outputs/apk/release/*.apk` after `assembleRelease`) | 5.1 MiB (5,334,335 bytes, 2026-11-09 release dry run) |
| `simpled` binary size | under 10 MiB | `./scripts/check` (local `go build` of `daemon/cmd/simpled`) | 9.0 MiB amd64 (9,466,119 bytes) and 8.3 MiB arm64 (8,716,448 bytes), 2026-11-09 release dry run |
| Phone memory at idle | under 80 MiB | With six agents connected, `adb shell dumpsys meminfo sh.simple.android` after the link is idle ~30 s | 70 MB total PSS with six terminals (45 MB of it graphics for the glass layers) |
| Cold start to first frame | under 300 ms | `adb shell cmd package compile -m speed-profile -f sh.simple.android`, then `adb shell am start -S -W -n sh.simple.android/.MainActivity` five times | 278 to 354 ms, median 297 ms, signed release on a 2018 mid range phone (Android 13), 2026-11-13 |
| Reconnect time | under 500 ms | Tail the app link log while toggling airplane mode or killing `simpled serve` briefly; time from disconnect event to next `hello` success in logs | not measured yet |
| Native whisper library in APK | informational | `unzip -l` on the release APK; `lib/arm64-v8a/libsimple_whisper.so` was about 1.2 MiB with `-Os` native build (under total APK budget) | included in the 5.2 MB above |

## Notes

- Release builds use arm64 only, R8 full mode, and `-Os` with section gc for `libsimple_whisper.so`.
- `./scripts/release <version>` prints daemon and APK sizes against the README caps when cut on the build box.
