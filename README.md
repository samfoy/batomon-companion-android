# Batomon Companion for Android

An experimental, GPLv3 Android companion for AYN Thor and Android handhelds. It is designed for a split-display setup: Batomon stays on the upper/default display while Companion uses the Thor lower touchscreen (1240×1080). The app currently ships a diagnostic capture pipeline and an honest, fixture-driven recognition shell; it does not claim to recognize live Batomon state until real Thor fixtures have been collected and calibrated.

## Status: 0.4.0 experimental recognition

- Launches the activity on the smallest non-default display when Android exposes one; falls back to the current display.
- Requests Android MediaProjection permission and captures the default display with an RGBA `ImageReader` in a foreground service.
- Throttles samples, uses `acquireLatestImage()`, and closes all images/surfaces/threads on stop.
- Keeps raw frames ephemeral. v0.4 now exercises the complete experimental observation path: ImageReader frame → row-stride-safe bitmap conversion → rotation/letterbox normalization → bounded downsample → scene and board recognizers → confidence/debounce → lower-screen diagnostics. It is deliberately conservative and does not claim live-game accuracy.
- Includes Room models for local runs and observations plus Live/Runs/Comps/Dex/Settings lower-screen skeletons.
- Adds functional manual run entry: editable mode/opening board, round wins/losses, lives, finish result, persisted history, and recaps.
- Adds a searchable text-only Balance 24 item reference catalog; artwork is not redistributed while licensing is pending. See [REFERENCE.md](REFERENCE.md).
- Expands the offline reference to 136 Batomon, 93 trinkets, 24 trainers, and 40 items, with detail dialogs and category/search controls.
- Replaces free-text opening-board entry with six structured Batomon selectors.
- Adds history filtering, opening-board grouping, round win-rate summary, safe delete confirmation, and JSON export.
- Adds an explicit SAF export for one redacted diagnostic frame; nothing is saved unless the user chooses a destination.
- Adds resolution-normalized ROIs, perceptual hashes, derived sprite signatures, template matching, confidence thresholds, and golden tests. Automatic state commits require repeated high-confidence observations; the current heuristic scene scores are below that threshold, so `UNKNOWN` is expected until Thor fixtures calibrate the pipeline.
- Adds an opt-in local calibration profile for the board ROI. Users can adjust normalized bounds in Settings after saving an explicitly exported diagnostic frame; raw frames are never uploaded or silently retained.

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

Local emulator evidence is recorded in [EMULATOR.md](EMULATOR.md). The host lacks KVM; bounded software-acceleration attempts reached guest display initialization but did not complete boot, so no local emulator split-display or capture result is claimed. GitHub Actions has a separate hosted API 35 x86_64 lane that runs the primary-display UI test, enables a 1240×1080 overlay display, checks the routed activity through `dumpsys activity`, and runs an in-memory Room persistence smoke test. Its logcat, display/activity dumps, device properties, and screenshot are uploaded as evidence, including on failure.

## Design notes

The capture lifecycle follows the publicly documented pattern used by [Bifrost](https://github.com/Pollux-MoonBench/Bifrost), a GPLv3 Thor utility: foreground MediaProjection service, RGBA ImageReader, latest-image acquisition, background HandlerThread, throttling, and explicit cleanup. This project is an independent implementation and does not copy Bifrost source. See [ARCHITECTURE.md](ARCHITECTURE.md).

## License

GPLv3. See [LICENSE](LICENSE). Batomon and AYN are trademarks of their respective owners. This is an independent community project.

## Updating from v0.2.0

v0.2.0 was debug-signed. v0.3.0 and later releases use the same durable project signing identity, so they update in place. Android cannot update a v0.2.0 debug installation in place; uninstall v0.2.0 first (local v0.2 data will be removed) or use a separate package install path.
