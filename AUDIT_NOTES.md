# OfflineIDE source audit notes

## Changes in this revision

- Updated Android Gradle Plugin to 9.1.1, Kotlin Android plugin to 2.2.10, and the configured Gradle distribution to 9.4.1 for running Gradle on Java 26.
- Added temporary AGP 9 migration bridge properties (`android.builtInKotlin=false`, `android.newDsl=false`) to preserve the existing Kotlin plugin and DSL.
- Added wrapper bootstrap scripts. The official Gradle Wrapper JAR and launch scripts are not included in this archive because Gradle is not installed in the editing environment and network access to the Gradle distribution host was unavailable. Generate them once using the bootstrap script after installing Gradle 9.4.1.
- Added bounded ZIP extraction and improved path validation.
- Added project/file-name and project-root validation, safer file move verification, and safer imported filename handling.
- Added runtime manifest validation for IDs, ABI, relative paths, environment keys, commands, and size.
- Made runtime package replacement staged with a best-effort rollback.
- Added HTTPS-only redirect validation and a download size cap.
- Updated build documentation for Java 26.

## Important unverified items

- This environment has Java 21, no installed Gradle, no Android SDK, and no network access to download Gradle/Android dependencies. Therefore no Gradle build, Android resource link, project unit-test task, lint, APK build, or device test was run. Selected pure-Kotlin sources and the modified project/runtime utility sources were compiled with `kotlinc` (the latter against lightweight API stubs, not the real Android SDK).
- All 28 XML files and all 5 JSON files parsed successfully. Resource-reference scan found no missing resource references. Shell syntax checks passed. ZIP smoke tests passed for ordinary extraction, ZIP-slip rejection, and extraction size limits.
- React vendor assets are still not bundled. Run `scripts/fetch_react_vendor.sh` on an internet-connected machine if React preview is required in the APK.
- Python, PHP, Java, C and C++ runtime packages are not bundled; the package catalog contains no downloadable URLs/checksums. Those languages need compatible runtime packages installed separately.
- `targetSdk = 28` remains intentionally unchanged because the current runtime design relies on executing installed native binaries from app-private storage. This is a distribution/security trade-off and is not Google Play ready.
- The AGP 9 compatibility bridge is transitional. Verify the actual build and resolve any API/DSL issues reported by Gradle before treating this source as build-ready.
