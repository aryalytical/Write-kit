# Language packages (runtimes, compilers, tools)

The app ships with **no** Python/PHP/Java/C/C++ runtime. Packages are `.zip` archives you install from
**⋮ → Language packages**, either from a local file or from an HTTPS URL you provide. After installation
everything runs offline.

## Archive layout

```
my-package.zip
├── runtime.json        <- required, at the archive root (or inside a single top-level folder)
├── bin/                <- every file under binDirs gets the executable bit
└── lib/ ...
```

## runtime.json

| key | meaning |
|---|---|
| `id` | unique id, `[a-z0-9][a-z0-9._-]*`; installing the same id replaces the old one |
| `name`, `version` | shown in the UI |
| `abi` | `arm64-v8a`, `armeabi-v7a`, `x86_64` or `any`. Installation is refused if the device does not support it |
| `languages` | informational |
| `binDirs` | directories (relative) whose files are made executable and prepended to `PATH` |
| `executables` | extra files to mark executable |
| `env` | environment variables (variables below are expanded) |
| `run` | map: language id → list of steps; each step `{ "cmd": [argv...] }` executed in order, stopping at the first non-zero exit |

The language ids used by the editor are `python`, `php`, `java`, `c`, `cpp`.

Variables expanded in `cmd` and `env`: `${PKG}` package dir, `${FILE}` file being run, `${DIR}` its folder,
`${PROJECT}` project root, `${NAME}` file name without extension, `${BUILD}` per-project cache directory,
`${OUT}` = `${BUILD}/${NAME}`.

Commands are executed directly (no shell). Working directory is the file's folder. stdin is closed (EOF).
Each run has a 120 s timeout. Examples: `docs/examples/*.json`.

## Making a package work on Android

- Binaries must be built for Android (bionic libc) for your device's ABI.
- Packages must be relocatable (no hard-coded install prefix). Termux packages are built for the
  `/data/data/com.termux/files/usr` prefix and generally will NOT work unmodified.
- The app targets API 28 so the OS allows `exec()` of files in app-private storage (see README).
  If your device refuses, the run output shows the real error from the OS.

## Catalog

`app/src/main/assets/packages/catalog.json` lists packages shown in the manager: name, languages,
estimated sizes, ABIs, guide steps, optional `downloadUrl` + `sha256`. `downloadUrl` is empty by default
because this project hosts no archives. If `sha256` is set, downloads are verified and rejected on mismatch.

## Adding a package to the catalog

Append an object to `packages` with the same fields as the existing entries, rebuild the app.
