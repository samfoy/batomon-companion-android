# Batomon Companion for Android

An experimental, GPLv3 Android companion for AYN Thor and Android handhelds. It is designed for a split-display setup: Batomon stays on the upper/default display while Companion uses the Thor lower touchscreen (1240×1080). The app currently ships a diagnostic capture pipeline and an honest, fixture-driven recognition shell; it does not claim to recognize live Batomon state until real Thor fixtures have been collected and calibrated.

## Status: 0.3.0 diagnostic/manual MVP

- Launches the activity on the smallest non-default display when Android exposes one; falls back to the current display.
- Requests Android MediaProjection permission and captures the default display with an RGBA `ImageReader` in a foreground service.
- Throttles samples, uses `acquireLatestImage()`, and closes all images/surfaces/threads on stop.
- Keeps raw frames ephemeral. The current recognizer returns `UNKNOWN` until fixture-backed recognizers are added.
- Includes Room models for local runs and observations plus Live/Runs/Comps/Dex/Settings lower-screen skeletons.
- Adds functional manual run entry: editable mode/opening board, round wins/losses, lives, finish result, persisted history, and recaps.
- Adds a searchable text-only Balance 24 item reference catalog; artwork is not redistributed while licensing is pending. See [REFERENCE.md](REFERENCE.md).
- Expands the offline reference to 136 Batomon, 93 trinkets, 24 trainers, and 40 items, with detail dialogs and category/search controls.
- Replaces free-text opening-board entry with six structured Batomon selectors.
- Adds history filtering, opening-board grouping, round win-rate summary, safe delete confirmation, and JSON export.
- Adds an explicit SAF export for one redacted diagnostic frame; nothing is saved unless the user chooses a destination.
- Adds resolution-normalized ROIs, perceptual hashes, template matching, confidence thresholds, and golden tests. No live-game accuracy is claimed.

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The release build is reproducible from the checked-in Gradle wrapper and dependencies. `local.properties` is machine-local and is not committed. CI builds the debug APK and runs unit tests.

## Install on Thor

1. Install the APK from GitHub Releases.
2. Put Batomon on the upper/default display and Companion on the lower display.
3. Open Companion and press **START CAPTURE**.
4. Approve the system screen-capture prompt. Android requires consent for each projection session.
5. Stop the foreground capture notification when finished.

MediaProjection captures the default display on the Android configurations this app targets. This is intentional: the game must remain above. If Android presents no secondary display, Companion runs as a normal single-screen app.

## Emulator / fixture workflow

The testable boundary is `FrameRecognizer`. Add redacted, redistributable frame fixtures only when you have permission to share them, then implement recognizers under `recognition/` and add golden tests. The diagnostic MVP deliberately avoids synthetic accuracy claims. A future diagnostics screen will export selected crops for private calibration; raw captures are not uploaded.

Local emulator evidence is recorded in [EMULATOR.md](EMULATOR.md). The host lacks KVM; bounded software-acceleration attempts reached guest display initialization but did not complete boot, so no emulator split-display result is claimed.

## Design notes

The capture lifecycle follows the publicly documented pattern used by [Bifrost](https://github.com/Pollux-MoonBench/Bifrost), a GPLv3 Thor utility: foreground MediaProjection service, RGBA ImageReader, latest-image acquisition, background HandlerThread, throttling, and explicit cleanup. This project is an independent implementation and does not copy Bifrost source. See [ARCHITECTURE.md](ARCHITECTURE.md).

## License

GPLv3. See [LICENSE](LICENSE). Batomon and AYN are trademarks of their respective owners. This is an independent community project.

## Updating from v0.2.0

v0.2.0 was debug-signed. v0.3.0 is the first durable release signed with the retained project keystore, so Android cannot update a v0.2.0 debug installation in place; uninstall v0.2.0 first (local v0.2 data will be removed) or use a separate package install path.
