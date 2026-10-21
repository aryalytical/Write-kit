# Known limitations

**Verification status:** XML/JSON/resource checks and limited JVM compilation of selected core/changed sources have been performed. The project has NOT been built with Android Gradle Plugin, installed, or tested on a device. Expect possible build errors until the full Gradle build succeeds.

## Execution
- No Python/PHP/Java/C/C++ runtime is included. Run reports "no runtime installed" and executes nothing until you install a package.
- Compatible runtime archives are not provided or hosted by this project; you must build/obtain them (docs/RUNTIME_PACKAGES.md).
- Running native binaries from app storage works only because `targetSdk = 28`. A Play Store release would require targetSdk ≥ 34, which blocks this. Some OEM ROMs may still block exec.
- No interactive stdin; 120 s timeout per run; no debugger.
- JavaScript "Run" is the WebView browser engine, not Node.js (no `require`, `fs`, `process`).
- Python/PHP/Java/C/C++ runtime packages are the only way to run those languages; Java classes in packages are not handled by the default run steps.

## React
- Needs `react`, `react-dom`, `babel.min.js` in `assets/vendor` (run `scripts/fetch_react_vendor.sh` once, with internet).
- Mini-bundler: relative imports only, no npm packages, no TypeScript, no `export * from`, no dynamic `import()`. Regex-based, so imports inside strings/comments can confuse it.
- No hot reload: re-run to refresh.

## Preview
- Remote http(s) requests are blocked unless enabled in Settings. WebSocket connections cannot be intercepted by the WebView client.

## Editor
- Syntax highlighting is regex based (no semantic analysis); strings/comments spanning edge cases may mis-colour. PHP files mixing HTML are only coloured as PHP.
- Highlighting is skipped above 250,000 characters; files > 2 MB are not opened; binary files are refused.
- Autocomplete = keywords + words from the current file. No member/type completion.
- Bracket matching does not skip brackets in strings/comments.
- Formatter is a re-indenter. Python is only whitespace-normalised. JSX tags are not re-nested. PHP files containing `?>` are only whitespace-normalised.
- Indentation uses spaces only. Auto-close pairs do not delete the partner on Backspace.
- Undo history is per open tab and is lost when the tab closes or the app restarts.
- Tab-key and Ctrl shortcuts need a physical keyboard; Ctrl+Z/Y/S/F/Enter/`/`/W/B/` are implemented.

## Storage
- Projects live in private app storage. Use Import/Export (Storage Access Framework) to move files in and out; there is no live editing of arbitrary device folders.

## Planned (not implemented)
Node.js package, TypeScript, LSP/semantic completion, Git, stdin input, debugger, multi-cursor, split view, minimap, snippets.
