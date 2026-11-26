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
- [ ] Verify unit tests and Android CI; obtain a fresh device report for runtime confirmation.
