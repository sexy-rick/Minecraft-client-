# Project notes
- Preserve installed Minecraft native libraries and their declared dependencies; do not patch out integrity libraries or fake their exports.
- In-process hosting is device- and version-dependent; report verification limits honestly.
- Do not push or change branches unless requested.
- Launch and content-import failures must remain in Empyreal and report their cause; never silently start the external Minecraft app.
- Diagnostic reports must distinguish unknown causes from evidence and warn before sharing device details, paths, or crash messages.
- PairIP bytecode lookup must use the installed game APK; never change or skip integrity execution.
- For native crashes, inspect the crashing thread backtrace; a signal code or another thread’s frames do not establish the cause.
- Android 12+ native exit traces are protobuf; do not interpret them as UTF-8. Use clearly labeled, app-matched crash-buffer frames when available.
- Native tombstone mapping diagnostics must remain bounded, PID-matched and omit memory bytes, addresses and mapping paths; missing mappings do not prove the cause of a crash.
