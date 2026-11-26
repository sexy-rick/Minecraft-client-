# Handoff
## 1. Goal
Improve diagnostics for Minecraft 1.26.66.xx in-process hosting without claiming unverified causes, altering integrity checks or falling back to external Minecraft.
## 2. Current state
Live log has a Copy diagnostic action with device/version details, recent focused events, last Android exit and last Java crash, with a privacy warning. Native dependency plans and pre-load attempts are logged; retained markers are evidence, not proof of a cause. Actual device compatibility remains unverified. Never patch integrity dependencies, fake exports or silently launch external Minecraft.
## 3. Active files
DiagnosticReport.kt, DiagnosticReportCollector.kt, LogScreen.kt, GameHost.kt, ElfDependencies.kt, NativeLoadPlan.kt, DiagnosticReportTest.kt, ElfDependenciesTest.kt, earlier load-plan and source guardrail tests.
## 4. Changes made
Prior changes preserve original native libraries and remove external handoffs. Added bounded report formatting and copy UI, dependency and per-attempt logging, previous-marker diagnostics, and explicit ELF parse failure logging. Added JVM tests for formatting, truncation, retained events and invalid ELF files.
## 5. Failed attempts
Device log provided is from the older v3 sanitized loader; the actual current failure remains unknown. Gradle distribution is not installed and services.gradle.org is inaccessible, and there is no Android SDK/device. Formatter tests run via standalone Kotlin/JVM with lightweight JUnit-compatible assertions, not Android Gradle. GitHub run 45785781405 failed during the setup-android action; its detailed logs redirect to a host blocked in this sandbox, so its precise cause is unknown. ELF parser originally silently treated bad files as having no dependencies; regression test exposed and fixed that behavior.
## 6. Specific next steps
Build on an Android host with `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Install and launch on the device; after a crash reopen and wait briefly, then open Live log, tap Copy diagnostic, review and share the report. If insufficient, use Share for full log/exit trace, after reviewing its contents. Inspect the report for the first actual load or activity failure before making compatibility changes.

## CI note
A branch-scoped Actions workflow at `.github/workflows/android.yml` uses the preinstalled runner SDK, installs components, runs guardrails and Android unit tests, builds the debug APK, and uploads it. SDK and Gradle errors are annotated for API access. Verify the next run after pushing; device launch still requires separate testing.
