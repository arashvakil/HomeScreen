# HomeScreen development

Native Kotlin / Compose for TV launcher. Keep the six-column layout, local artwork,
persisted favorite order, static focus border, and window-focus-aware restoration.

Use an isolated branch from the fetched default branch and a reviewed PR. Preserve
unrelated changes and keep signing keys, device addresses, and runtime data out of Git.

Run Java 17 + Android SDK 34 checks:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

For release: use a local ignored signing configuration, then `:app:assembleRelease`.
See README.md for release packaging and the explicit publication script. Publishing
is a release action; never run scripts/deploy-update.sh as a test.

Verify on a TV: D-pad focus, arrange/save/reload, Settings, Home from another app,
and launch after reboot with Home on Fire. Do not enable competing accessibility
redirectors. The launcher does not uninstall Fire OS packages.

Updater: only release builds schedule checks. Keep HTTPS, checksum, package/version,
and signing-certificate validation. Fire OS may require user install confirmation.
