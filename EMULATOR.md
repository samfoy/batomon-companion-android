# Emulator evidence (v0.2)

The local SDK has an API 35 Google APIs x86_64 image and an AVD named `batomon-api35`. The host does not expose `/dev/kvm`:

```text
KVM=no
KVM requires a CPU that supports vmx or svm
```

I attempted a headless software run with `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel off`. The emulator initialized gfxstream, networking, and a 1080×1920 guest display, but did not report `sys.boot_completed=1` within a 30-second polling window; a 45-second bounded run was stopped without leaving a booted ADB device. It logged that TCG did not support AVX/F16C guest features and that full startup may take two minutes or more.

Because there was no booted guest, no secondary display could be created with `adb shell cmd overlay`/window-manager commands and no ActivityOptions or MediaProjection smoke test could be claimed. The app's unit tests and APK build remain host-verifiable. Physical Thor or a KVM-enabled emulator is required for the real split-display/capture check.
