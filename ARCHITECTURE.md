# Architecture

```text
MainActivity (lower display UI)
  ├── DisplayManager + ActivityOptions.launchDisplayId
  ├── MediaProjection consent
  └── Live / Runs / Comps / Dex / Settings views

CaptureService (foreground mediaProjection service)
  ├── VirtualDisplay → RGBA ImageReader
  ├── HandlerThread + acquireLatestImage() + 700 ms throttle
  └── FrameRecognizer → temporal state machine (fixture-driven)

Room
  ├── runs
  └── observations
```

## Display model

At startup, the app chooses the smallest available display whose ID is not `Display.DEFAULT_DISPLAY`, then relaunches itself there using `ActivityOptions.setLaunchDisplayId`. This is a heuristic because display IDs can change across boots and firmware. A routed intent flag prevents relaunch loops. With one display, it simply stays where launched.

## Capture model

Android's user-approved MediaProjection session is passed to `CaptureService`. The service creates a virtual display sized to the default display metrics and attaches it to an RGBA ImageReader. The callback uses `acquireLatestImage`, discarding stale frames, and samples at most once every 700 ms. Every image is closed immediately; no raw frame is persisted or sent over the network. Recognition code should copy only the crop it needs before returning.

## Recognition contract

`FrameRecognizer` returns a scene and confidence. `SceneRecognizer` selects the highest-confidence recognizer, while `TemporalDebouncer` requires stable repeated results. There are no production recognizers in 0.1.0 because no real Batomon/Thor fixture set is included. This prevents the app from inventing a run from an uncalibrated screenshot.
