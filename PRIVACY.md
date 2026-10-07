# Privacy and capture behavior

- The user explicitly starts every capture session and approves Android's system MediaProjection prompt.
- Capture is processed locally by a foreground service. The app has no Internet permission in 0.1.0.
- Raw frames are not written to disk, uploaded, or logged. Each acquired `Image` is closed after processing.
- Structured run and observation records are stored in a local Room database for the future history UI.
- The app does not use Accessibility, root, memory inspection, game patching, automated input, or outcome manipulation.
- The capture notification is always visible while the foreground service is running.
- Future diagnostic export will be opt-in and must be manually copied by the user; it will never be an automatic upload path.
- v0.2's diagnostic export uses Android's Storage Access Framework. It writes exactly one user-requested PNG to the URI the user chooses, redacts common system strips, and displays a visible `BATOMON DIAGNOSTIC · REDACTED` marker. It does not silently create files.

Because MediaProjection can expose sensitive screen content, only grant permission while Batomon is on the default display and stop capture when done.
