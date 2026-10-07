# Architecture

```text
MainActivity (lower display UI)
  ├── DisplayManager + ActivityOptions.launchDisplayId
  ├── MediaProjection consent
  └── Live / Runs / Comps / Dex / Settings views

CaptureService (foreground mediaProjection service)
  ├── VirtualDisplay → RGBA ImageReader
  ├── HandlerThread + acquireLatestImage() + 700 ms throttle
  └── row-stride/rotation/letterbox normalization → experimental scene + board matching
      → confidence/debounce → state broadcast (raw frames remain ephemeral)

Room
  ├── runs
  └── observations
```

## Display model

At startup, the app chooses the smallest available display whose ID is not `Display.DEFAULT_DISPLAY`, then relaunches itself there using `ActivityOptions.setLaunchDisplayId`. This is a heuristic because display IDs can change across boots and firmware. A routed intent flag prevents relaunch loops. With one display, it simply stays where launched.

## Capture model

Android's user-approved MediaProjection session is passed to `CaptureService`. The service creates a virtual display sized to the default display metrics and attaches it to an RGBA ImageReader. The callback uses `acquireLatestImage`, discarding stale frames, and samples at most once every 700 ms. Every image is closed immediately; no raw frame is persisted or sent over the network. Recognition code copies a bounded bitmap, normalizes rotation and dark letterbox borders, and downsamples before returning. The bitmap is recycled after the observation broadcast. Raw frames are never persisted unless the user explicitly selects a destination through the diagnostic SAF flow.

## Recognition contract

`FrameRecognizer` returns a scene and confidence. `ExperimentalSceneRecognizer` combines low-cost arena/result cues with optional six-slot sprite signature matching; `ObservationGate` requires three repeated non-unknown observations at confidence ≥0.80 before a state can be committed. The shipped public sprite signatures are derived summaries, not artwork. The scene cues are inferences from public Steam/how-to-play material, not a validated classifier, and remain intentionally below the commit threshold until real Thor fixtures are collected.

`NormalizedRect` and `ThorRegions` express regions as fractions of the captured display, so a 1920×1080 default display and a letterboxed capture can share recognizer configuration. `PerceptualHash` and `TemplateSceneRecognizer` provide a deterministic, pluggable baseline; templates must come from permission-cleared fixtures and are not bundled in v0.2.

## Manual state and diagnostics

Manual runs are saved in Room and record mode, opening-board notes, round outcomes, lives, finish result, and timestamps. MediaProjection state is broadcast from the foreground service to the lower-screen UI. A user can invoke a Storage Access Framework export from Settings; the next frame is copied to the chosen URI with top/bottom strips darkened and a visible diagnostic marker, then the in-memory image is released.
