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

The emulator can test the Android lifecycle and UI but cannot prove AYN's exact firmware routing. The MVP contains no real game recognizers, so `UNKNOWN` is expected. Collect private, permission-cleared crops from representative title/shop/battle/result screens before adding recognizers and accuracy tests.

## Emulator

An emulator with a secondary display can exercise display selection. If the local host lacks KVM or an installed system image, run unit tests and UI build locally, then use physical Thor for the display/capture smoke test.
