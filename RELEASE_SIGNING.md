# Release signing

The repository never contains a private key. GitHub Actions accepts these repository secrets:

- `BATOMON_RELEASE_KEYSTORE_BASE64`
- `BATOMON_RELEASE_STORE_PASSWORD`
- `BATOMON_RELEASE_KEY_ALIAS`
- `BATOMON_RELEASE_KEY_PASSWORD`

When all four are configured, the tagged-release workflow decodes the keystore only into the ephemeral runner temp directory, signs `assembleRelease`, uploads a professionally named APK, and removes the runner afterward. The keystore is not printed or committed. Keep a backup of the same keystore offline; changing it makes future APK updates unable to update an installed build.

If the secrets are absent, the workflow deliberately falls back to a debug-signed diagnostic APK and says so in the build log. v0.3.0 is the first release for which the four secrets were provisioned and the signed APK was verified locally. v0.2.0 remains debug-signed and must be uninstalled before installing v0.3.0.
