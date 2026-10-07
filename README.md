# Batomon Companion for Android

An experimental, GPLv3 Android companion for AYN Thor and Android handhelds. It is designed for a split-display setup: Batomon stays on the upper/default display while Companion uses the Thor lower touchscreen (1240×1080). The app currently ships a diagnostic capture pipeline and an honest, fixture-driven recognition shell; it does not claim to recognize live Batomon state until real Thor fixtures have been collected and calibrated.

## Status: 0.1.0 diagnostic MVP

- Launches the activity on the smallest non-default display when Android exposes one; falls back to the current display.
- Requests Android MediaProjection permission and captures the default display with an RGBA `ImageReader` in a foreground service.
- Throttles samples, uses `acquireLatestImage()`, and closes all images/surfaces/threads on stop.
- Keeps raw frames ephemeral. The current recognizer returns `UNKNOWN` until fixture-backed recognizers are added.
- Includes Room models for local runs and observations plus Live/Runs/Comps/Dex/Settings lower-screen skeletons.
- Includes unit tests for the recognition contract and temporal debounce.

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

## Design notes

The capture lifecycle follows the publicly documented pattern used by [Bifrost](https://github.com/Pollux-MoonBench/Bifrost), a GPLv3 Thor utility: foreground MediaProjection service, RGBA ImageReader, latest-image acquisition, background HandlerThread, throttling, and explicit cleanup. This project is an independent implementation and does not copy Bifrost source. See [ARCHITECTURE.md](ARCHITECTURE.md).

## License

GPLv3. See [LICENSE](LICENSE). Batomon and AYN are trademarks of their respective owners. This is an independent community project.
