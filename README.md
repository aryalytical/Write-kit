# Offline Code (OfflineIDE) — VS Code-inspired Android code editor

Kotlin + native Android Views. Projects are stored privately on the device. The editor, preview and
JavaScript runner work with no network.

> **Status: PARTIALLY AUDITED SOURCE — NOT BUILD-VERIFIED.** Source/resource checks and limited JVM compilation of core logic were performed, but this project has **not** been built with Android Gradle Plugin, installed, or tested on a device. Treat the first full build as a verification step and fix any errors Gradle reports. No APK is supplied.

## Feature status

| Feature | Status |
|---|---|
| Tabs, multi-file editing, undo/redo (per tab) | Implemented (untested) |
| Syntax highlighting: HTML, CSS, JS, JSX, JSON, Python, PHP, Java, C, C++ | Implemented (regex based) |
| Auto-indent, auto-close pairs, bracket matching, comment toggle | Implemented |
| Formatting | Re-indenter only (see limitations) |
| Autocomplete | Keywords + words in file (no semantic completion) |
| Find / replace (case, regex, replace all) | Implemented |
| Symbol toolbar (`{}` `[]` `()` `=>` `;` quotes …), Tab, physical-keyboard shortcuts | Implemented |
| Light / dark / system theme, font size, indent width, word wrap | Implemented |
| Project explorer: create / rename / move / delete files & folders | Implemented |
| Import/export files, folders, zip projects (Storage Access Framework) | Implemented |
| HTML/CSS/JS preview (offline WebView) and JS runner | Implemented |
| React preview (JSX via bundled Babel, mini bundler) | Implemented; needs vendor files (below) |
| Language package manager (install .zip, optional HTTPS download, checksum, uninstall, storage/ABI info) | Implemented |
| Running Python / PHP / Java / C / C++ | Executes real processes from an **installed package**. **No runtimes are bundled** |
| Node.js, TypeScript, LSP, Git, stdin, debugger | Planned — not implemented |

Run never simulates output: with no matching runtime installed it prints that nothing was executed.

## Requirements

- **JDK 26.0.1** to run Gradle (Gradle 9.4.0 introduced Java 26 runtime support; this project uses the 9.4.1 patch)
- Android Studio release compatible with AGP 9.1.1, plus Android SDK Platform 34 and Build-Tools
- Gradle 9.4.1 and Android Gradle Plugin 9.1.1; Kotlin Android plugin 2.2.10
- The app bytecode target remains Java 17; the JDK running Gradle is separate from the bytecode target
- Device/emulator: Android 8.0+ (minSdk 26). Written with mid-range phones such as CMF Phone 1 in mind.

Dependencies (all from Google/Maven Central): core-ktx 1.13.1, appcompat 1.7.0, material 1.12.0,
recyclerview 1.3.2, drawerlayout 1.2.0, documentfile 1.0.1; test: junit 4.13.2.
Plugins: Android Gradle Plugin 9.1.1, Kotlin Android plugin 2.2.10. The project temporarily opts out of AGP 9's built-in Kotlin and new Android DSL migration to preserve the existing Kotlin build configuration while using Java 26; revisit this bridge when migrating to a newer AGP release.

## Build

1. Unzip and open the folder in Android Studio; let Gradle sync (needs internet once, to fetch Gradle & libraries).
2. *(Only for React preview)* on a machine with internet run `scripts/fetch_react_vendor.sh`
   to place `react.production.min.js`, `react-dom.production.min.js` and `babel.min.js` in
   `app/src/main/assets/vendor/`. Skip this and React preview will tell you which files are missing.
3. Generate the official Gradle Wrapper once. This source ZIP does not include the wrapper JAR because it could not be fetched in the packaging environment. Install Gradle 9.4.1 and run `scripts\bootstrap-gradle-wrapper.bat` on Windows (or `sh scripts/bootstrap-gradle-wrapper.sh` on macOS/Linux). Then build with `gradlew.bat assembleDebug` on Windows or `./gradlew assembleDebug` on macOS/Linux. Output: `app/build/outputs/apk/debug/app-debug.apk`.
4. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or run from Android Studio.
   For a release build you must create your own signing key (`assembleRelease` output is unsigned).

## Testing

- Unit tests (pure JVM): `./gradlew testDebugUnitTest` — covers bracket matching, undo merging, formatter,
  tokenizer and language detection.
- Manual checklist: create each template project; edit + save + reopen; undo/redo; find/replace with regex;
  rename/move/delete files; Run on HTML (preview), JS (output panel), React (needs vendor files);
  Run on `.py` with no package (must say nothing executed); install a package and run; rotate the phone;
  hardware keyboard shortcuts.

## Why `targetSdk 28`

Apps targeting Android 10 (API 29) or higher cannot `exec()` binaries from their private data directory, which would
make downloadable compilers/interpreters unusable. `targetSdk = 28` (as used by Termux) allows it, and the app still
installs on current Android versions. Consequences: not publishable on Google Play with this setting, and lint
warnings for the old target are suppressed in `app/build.gradle.kts`. Verify on your device — OEM ROMs vary.

## Offline & privacy

- Source code never leaves the device; nothing is uploaded; no analytics; `allowBackup=false`.
- `INTERNET` permission exists only for the Language Packages screen's download button. Delete the line in
  `AndroidManifest.xml` if you only sideload archives.
- Previews block remote http(s) requests by default (Settings can allow them). WebSocket traffic from preview pages
  cannot be intercepted.

## Using it

- Swipe from the left / tap ☰ for the explorer; tap a folder to expand, long-press for file actions.
- ▶ Run: HTML/CSS → browser preview; `.js` → output panel; `.jsx` → React preview; `.py/.php/.java/.c/.cpp` → installed package.
- ⋮ menu: save all, format, comment, go to line, previews, projects, language packages, settings, feature status.
- Shortcuts (hardware keyboard): Ctrl+S, Ctrl+Z, Ctrl+Shift+Z / Ctrl+Y, Ctrl+F, Ctrl+Shift+F (format), Ctrl+Enter (run),
  Ctrl+/ (comment), Ctrl+W (close tab), Ctrl+B (explorer), Ctrl+` (output), Tab / Shift+Tab (indent / outdent).

## Project layout

```
app/src/main/java/com/offlinecode/editor/
  lang/      LanguageSupport, built-in languages, registry, regex + HTML tokenizers
  view/      CodeEditText, undo, bracket matcher, indenter, formatter, autocomplete, palette
  project/   ProjectManager (files + SAF), templates, explorer adapter
  run/       LocalWebClient (offline serving), JsRunner, ReactBundler, LocalRunner (process exec)
  runtime/   RuntimeManifest, RuntimeManager (install/plan), Downloader
  ui/        MainActivity, PreviewActivity, PackagesActivity, dialogs, find, settings, output panel
  util/      FileUtil (zip, atomic write), Prefs
app/src/main/assets/{packages/catalog.json, vendor/}
docs/        ADDING_LANGUAGES.md, RUNTIME_PACKAGES.md, KNOWN_LIMITATIONS.md, examples/
scripts/     fetch_react_vendor.sh
```

## More documentation

- `docs/ADDING_LANGUAGES.md` — adding editor support for a new language
- `docs/RUNTIME_PACKAGES.md` — building/installing language packages, `runtime.json` format
- `docs/KNOWN_LIMITATIONS.md` — full list of limitations and runtimes needing extra setup

## Runtimes requiring additional setup

Python, PHP, Java (javac/java), C and C++ (clang) — none included, none hosted. You must supply Android-compatible,
relocatable builds packaged per `docs/RUNTIME_PACKAGES.md`. Node.js is planned.
