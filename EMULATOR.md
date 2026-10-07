# Emulator evidence (v0.2)

The local SDK has an API 35 Google APIs x86_64 image and an AVD named `batomon-api35`. The host does not expose `/dev/kvm`:

```text
KVM=no
KVM requires a CPU that supports vmx or svm
```

I attempted a headless software run with `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off`. The emulator initialized gfxstream, networking, and a 1080×1920 guest display, but did not report `sys.boot_completed=1` within a 30-second polling window; a 45-second bounded run was stopped without leaving a booted ADB device. It logged that TCG did not support AVX/F16C guest features and that full startup may take two minutes or more.

For v0.3 I also ran the same AVD as a live headless session with software acceleration and polled ADB for roughly four minutes (the emulator itself reported full startup may take two minutes or more). It never appeared as an ADB device, never returned `sys.boot_completed=1`, and eventually logged repeated QEMU main-loop/CPU-thread hangs before exiting. Because there was no booted guest, no secondary display could be created with `adb shell cmd overlay`/window-manager commands and no ActivityOptions or MediaProjection smoke test could be claimed. The app's unit tests and APK build remain host-verifiable. Physical Thor or a KVM-enabled emulator is required for the real split-display/capture check.

## Hosted instrumentation lane

`.github/workflows/android.yml` includes a separate GitHub-hosted emulator job using `reactivecircus/android-emulator-runner@v2`, API 35 Google APIs, x86_64, and a Pixel 2 profile. It deliberately keeps projection consent out of the required lane because the system dialog is OEM/API sensitive and would make the build flaky. Instead it verifies:

1. A primary-display `ActivityScenario` launch and companion surface.
2. A secondary-display launch after `adb shell settings put global overlay_display_devices '1240x1080/160'`; the test checks `dumpsys activity activities` for the package on a non-default display.
3. An in-memory Room insert/read of a manual run and observation.

The job uploads `adb devices`, `getprop`, `dumpsys display`, `dumpsys activity activities`, logcat, and a screenshot as `batomon-emulator-evidence`, including when a test fails. The overlay display is an emulator mechanism, not proof of AYN firmware behavior. MediaProjection permission, default-display mirroring, rotation, and recognition accuracy still require physical Thor validation.
