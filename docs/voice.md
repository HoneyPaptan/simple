# On device voice (whisper.cpp)

## Vendored whisper.cpp

| Field | Value |
|---|---|
| Upstream tag | `v1.7.4` |
| Upstream commit | `8a9ad7844d6e2a10cddf4b92de4089d7ac2b14a9` |
| Path | `third_party/whisper.cpp` |

Built with the Android NDK through CMake (`app/src/main/cpp/CMakeLists.txt`, library `simple_whisper`). JNI entry points live in `WhisperNative`.

## Native size per ABI

Measured on the build box after `./scripts/check` (release APK, `.so` only):

| ABI | `libsimple_whisper.so` (stripped, release) |
|---|---|
| arm64-v8a | 1,241,896 bytes (~1.18 MiB) |
| x86_64 (debug) | built for the Instabox emulator; same CMake target, not shipped in release APK |

Budget: under 3 MiB growth per ABI from whisper.

## Models (on demand)

Download URL pattern: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/<file>`.

Default choice: **tiny.en**. Nothing ships in the APK.

| id | File | Bytes | SHA256 |
|---|---|---:|---|
| tiny.en | ggml-tiny.en.bin | 77704715 | 921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f |
| base.en | ggml-base.en.bin | 147964211 | a03779c86df3323075f5e796cb2ce5029f00ec8869eee3fdfb897afe36c6d002 |
| small.en | ggml-small.en.bin | 487614201 | c6138d6d58ecc8322097e0f987c32f1be8bb0a18532a3f88f734d1bbf9c41e5d |
| tiny | ggml-tiny.bin | 77691713 | be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21 |
| base | ggml-base.bin | 147951465 | 60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe |

Stored under app private storage (`files/voice-models/`). Downloads require Wi Fi unless the user confirms on mobile data. Failed hash checks delete the file and show an error.

## What was tested

| Area | How |
|---|---|
| JNI bridge | `./scripts/check` builds the app on the box with NDK CMake; release `libsimple_whisper.so` size is recorded above |
| Model catalog | `VoiceCatalogTest` checks ids, file names, and SHA256 table entries |
| Replacements | `VoiceReplacementTest` checks whole word, case insensitive rules and empty `heard` skipped |
| Composer dictation | Unit tests for replacement pipeline; live mic and transcription are verified on the Instabox emulator by the reviewer |
| Image attach | `attachments_test.go` checks reserved paths stay under `.simple/attachments`, size and MIME limits, and deny outside the workspace |
| Markup | UI implemented with undo, redo, crop, and flatten to PNG; reviewer checks light and dark on the emulator |
