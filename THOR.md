# AYN Thor test checklist

## Physical hardware

- [ ] Android version and firmware recorded
- [ ] Companion appears on lower 1240×1080 display
- [ ] Batomon remains interactive on upper/default 1920×1080 display
- [ ] MediaProjection consent appears and capture notification is visible
- [ ] Capture status changes to `CAPTURING DEFAULT DISPLAY`
- [ ] Display swap, sleep/wake, and game relaunch tested
- [ ] Capture stops cleanly from notification and process restart
- [ ] Settings → Save redacted current frame prompts for a user-selected URI and saves one marked PNG

## What this build cannot establish

The emulator can test the Android lifecycle and UI but cannot prove AYN's exact firmware routing. The shipped recognizer is experimental and conservatively returns low-confidence candidates until real fixtures calibrate it; `UNKNOWN` is expected. Collect private, permission-cleared crops from representative title/shop/battle/result screens before treating any candidate as game state or tuning thresholds.

## Emulator

The hosted Android CI job boots API 35 with hardware acceleration, runs the default-display UI test, then sets `overlay_display_devices=1240x1080/160` and verifies that the app reports a non-default display in `dumpsys activity`. If the local host lacks KVM or an installed system image, run unit tests and UI build locally, then use physical Thor for the MediaProjection/capture smoke test.
