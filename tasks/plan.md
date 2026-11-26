# Native library load failure
- [x] Trace the reported unresolved symbol to native extraction and the load plan.
- [x] Add regression tests for dependency ordering, unrelated libraries, missing libraries and previous native crashes.
- [x] Extract original libraries unchanged, invalidate modified cache, and honor declared dependencies.
- [x] Run available JVM/linker checks; Android build and device verification remain blocked by the sandbox environment.

# In-process-only launch
- [x] Identify all automatic external-game handoffs and misleading UI copy.
- [x] Add regression guardrails for launch, activity failures, and UI error reporting.
- [x] Remove external launch and intent forwarding while retaining explicit store access.
- [ ] Verify on Android with normal launch, preparation failure, activity failure, and content import.

# Shareable diagnostics
- [x] Add a bounded, focused diagnostic report with device and Minecraft details, relevant app events and previous process evidence.
- [x] Log native dependency plans and attempts before native loading, without claiming an unverified crash cause.
- [x] Provide a copy action with a privacy warning and verify formatter boundaries and fallback behavior.
- [x] Run JVM and source checks; Android build/device validation unavailable in this sandbox.

# GitHub Actions build
- [x] Add a branch-scoped Android CI workflow with JVM unit tests and a debug APK artifact.
- [x] Push the current branch and inspect the Actions result and artifact.

# Minecraft 1.26.66.1 native abort
- [x] Identify reported bytecode lookup failure and correlate with the loader's classpath order.
- [x] Test and bind the installed base APK to the existing protection runtime before engine load, without modifying protection code.
- [x] Verify unit tests and Android CI.
- [ ] Obtain a fresh device report for runtime confirmation.

# Native SIGSEGV after APK binding
- [x] Identify the precise failure stage from the device report.
- [ ] Obtain the crashing thread backtrace from the crash buffer before choosing a fix.

# Native crash diagnostics
- [x] Use the shared crash buffer to identify the crashing frame without guessing a native cause.
- [x] Stop rendering Android 12+ binary tombstones as text; summarize matching app backtraces from logcat.
- [x] Inspect the user-provided ARM64 library at the reported crash offset, without modification.
- [ ] Determine why the faulting read reaches an inaccessible mapping before attempting a native-loading fix.

# Faulting memory mapping diagnostics
- [x] Inspect the public AOSP tombstone schema and add a bounded, PID-matched fault-mapping summary without raw memory, addresses or paths.
- [x] Exercise tagged ARM64 addresses, mapping permissions, gaps, PID mismatch and malformed/oversized input in JVM tests.
- [x] Build with Android CI (run 45814436643).
- [ ] Verify the summary against the phone's actual tombstone; unavailable or omitted mappings remain possible.
- [x] Obtain actual phone report: PID-matched fault is in an anonymous no-access mapping.
- [x] Confirm the user can launch unmodified Minecraft independently as a diagnostic control.
- [ ] Determine the origin/bounds of the data being read in the hosted process.

# Kitsuri source comparison
- [x] Inspect Kitsuri-Studios/Minimal-Launcher at 35d73b606bd3491fe6243a4dcb3f5587baa85f71, including its pinned unpaircore gitlink.
- [x] Distinguish its in-process loader from its replacement PairIP implementation; neither a fix preserving the original library nor compatibility with 1.26.66.1 is demonstrated.
- [ ] Continue investigating host/runtime differences without bypassing integrity code or copying GPL source into this project.

# Experimental Kitsuri-based in-process build (explicit user choice)
- [x] Integrate the pinned BSD-licensed Kitsuri UnpairCore replacement for arm64 with required notices, without modifying the installed Minecraft APK.
- [x] Update dependency planning and diagnostics so the replacement is explicit, loaded before the engine, and cannot silently fall back to the original runtime.
- [x] Run seven standalone planner tests and six source guardrails.
- [x] Android CI run 45829963581 built the APK and verified the replacement exports and license notices (artifact 15121687350).
- [ ] Test Minecraft 1.26.66.1 on the phone; compatibility is unknown.

# Java class-resolution launch failure
- [x] Correlate the 19:04:16 crash with the successful native load and `NoClassDefFoundError` for Minecraft's MainActivity.
- [x] Remove the early `GameActivity` class literal from GameHost and put intent creation/start in the existing error handler; source regression test fails before and passes after.
- [x] Android CI run 45832402663 passed tests, build and APK verification (artifact 15123570217).
- [ ] With user consent, validate on-device; ART resolution timing is a hypothesis, not a proven root cause.

# Native library lookup failure (19:23:31)
- [x] Distinguish successful earlier loads from later name-based load and inaccessible linker namespace.
- [x] Examine Android-derived DexPathList and Runtime semantics; identify appended path order as a hypothesis, not a confirmed cause.
- [x] Prioritize extracted cache before system paths without overriding host libraries; log name-based resolutions before activity.
- [x] Run four standalone Kotlin ordering tests and eight Python guardrails.
- [ ] With push authorization, run Android CI and provide the resulting APK if successful.
- [ ] Test on the phone; establish actual lookup and namespace behavior.
