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
- [ ] Build with Android CI and verify the summary against the phone's actual tombstone; unavailable or omitted mappings remain possible.
