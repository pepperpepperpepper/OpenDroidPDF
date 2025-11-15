# OpenDroidPDF Untracked File Inventory

Last refreshed: 2025-11-15.

## Candidate for Version Control
- `plan.md` &rarr; project-wide refactor roadmap (should likely move under `docs/` and track).
- `DEPLOYMENT-FDROID.md` &rarr; deployment SOP; add to repo after validating contents.
- `docs/licensing/` & `docs/qa/` &rarr; authored documentation folders; review and commit.
- `platform/android/UPGRADE-TO-ANDROID15.md` &rarr; technical note; verify accuracy before adding.
- `platform/android/tests/` &rarr; instrumentation skeleton (`androidTest/`); decide whether to integrate with refactor test plan.

## Tooling / Build System Assets
- `platform/android/gradle{w,w.bat}`, `platform/android/gradle/`, `.gradle-bin`, `gradle.properties`, `settings.gradle` &rarr; full Gradle wrapper copy checked into `platform/android/`. Either adopt as canonical wrapper or delete in favour of root-level tooling. Do **not** commit until a single wrapper strategy is chosen.
- `thirdparty_build/` &rarr; MuPDF/third-party build scripts; inspect for required automation before tracking.

## Test Artifacts & Logs (Delete or Scripted Cleanup)
- PNG screenshots & UI dumps (`after_undo.png`, `geny_*`, `penandpdf_*`, `screen_runtime.png`, etc.) &rarr; generated testing evidence. Archive externally or add to `.gitignore` and remove via script.
- UI Automator dumps (`ui*.xml`, `ui_dump*`, `uidump*.xml`, `window_dump*.xml`) &rarr; transient diagnostics; delete.
- `logcat_texttool.txt`, `namedump`, `cquote` &rarr; temporary logs/dumps; delete.
- `test-output/`, `test_outputs/` &rarr; ad-hoc result folders; delete or convert to structured test artifacts under `docs/qa/`.

## Build / Runtime Outputs
- `dashboard*.xml`, `ui_menu.xml` &rarr; exported layouts from design tools; confirm if any should replace resources; otherwise delete.
- `platform/android/tests/` instrumentation outputs (if generated) &rarr; handle per testing decision.

## Proposed Cleanup Script Targets
Add a workspace helper (e.g., `scripts/cleanup_workspace.sh`) that removes:
```
rm -f *.png ui*.xml ui_dump*.xml uidump*.xml window_dump*.xml logcat_*.txt namedump cquote
rm -rf test-output/ test_outputs/ thirdparty_build/
```
Ensure script is opt-in and documented before use.

## Next Actions
1. Confirm which documentation assets belong in the repo and add them.
2. Draft the cleanup script and update `.gitignore` to keep the workspace tidy.
3. Communicate strategy before deleting any artifacts to avoid losing needed references.

