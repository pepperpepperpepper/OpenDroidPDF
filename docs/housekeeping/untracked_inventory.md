# OpenDroidPDF Untracked File Inventory

Last refreshed: 2026-01-14.

## Candidate for Version Control
- `docs/forms.md` &rarr; user-facing forms explanation; review and commit.
- `docs/licensing/qpdf_pdfbox.md` &rarr; third-party notices for QPDF/PDFBox; review and commit.
- `docs/qa/` &rarr; authored QA documentation; review and commit.
- `platform/android/UPGRADE-TO-ANDROID15.md` &rarr; technical note; verify accuracy before adding.
- `platform/android/tests/` &rarr; instrumentation skeleton (`androidTest/`); decide whether to integrate with refactor test plan.

## Tooling / Build System Assets
- `platform/android/gradle{w,w.bat}`, `platform/android/gradle/`, `.gradle-bin`, `gradle.properties`, `settings.gradle` &rarr; full Gradle wrapper copy checked into `platform/android/`. Either adopt as canonical wrapper or delete in favour of root-level tooling. Do **not** commit until a single wrapper strategy is chosen.
- `thirdparty_build/` &rarr; MuPDF/third-party build outputs; keep ignored and purge via cleanup script.

## Test Artifacts & Logs (Delete or Scripted Cleanup)
- PNG screenshots & UI dumps (`after_undo.png`, `geny_*`, `opendroidpdf_*`, `screen_runtime.png`, etc.) &rarr; generated testing evidence. Archive externally or add to `.gitignore` and remove via script.
- UI Automator dumps (`ui*.xml`, `ui_dump*`, `uidump*.xml`, `window_dump*.xml`) &rarr; transient diagnostics; delete.
- `logcat_texttool.txt`, `namedump`, `cquote` &rarr; temporary logs/dumps; delete.
- `test-output/`, `test_outputs/` &rarr; ad-hoc result folders; delete or convert to structured test artifacts under `docs/qa/`.

## Build / Runtime Outputs
- `dashboard*.xml`, `ui_menu.xml` &rarr; exported layouts from design tools; confirm if any should replace resources; otherwise delete.
- `platform/android/tests/` instrumentation outputs (if generated) &rarr; handle per testing decision.

## Workspace Cleanup Coverage
The helper script `scripts/cleanup_workspace.sh` now purges the full set of ad-hoc diagnostics we produce during QA:

- Root-level screenshots (`geny_*`, `opendroidpdf_*`, case/doc/screen PNGs) and undo/export PDFs. Legacy `penandpdf_*` names were retired on 2025-11-29; remove any stragglers manually if needed.
- UI Automator/XML dumps (`ui*.xml`, `ui_dump*.xml`, `uidump*.xml`, `window_dump*.xml`, `dashboard*.xml`, `ui_menu.xml`).
- Misc. logs (`logcat_*.txt`, `namedump`, `cquote`) and scratch directories (`test-output/`, `test_outputs/`, `thirdparty_build/`).
- Build caches/outputs (`build/`, `generated/`, `repo/`, and Android Gradle/NDK outputs under `platform/android/`).

All of these patterns now live in `.gitignore` so new artifacts stay out of version control and the cleanup script can be run safely before commits.

## Next Actions
1. Confirm which documentation assets belong in the repo and add them.
2. Keep `scripts/cleanup_workspace.sh` + `.gitignore` aligned as new artifact types appear.
