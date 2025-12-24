# TODO — Close Phase 2 (Activity/Navigation Simplification)

Goal: Close Phase 2 by making `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`
a pure host (lifecycle + wiring), with all flows/state owned by dedicated controllers/host adapters.

## Success criteria (“Phase 2 is closed when…”)

- `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` contains **no flow logic**:
  - open/save/save-as/export/share/print/search/permissions/back all delegate to controllers/host adapters.
- `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` contains **no “document state” derivation**:
  - document type, doc id, save-capability, current page view, sidecar provider, etc. are owned by a single manager/controller.
- Menu visibility/enabled rules are owned by:
  - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/toolbar/ToolbarStateController.java`
  - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/toolbar/MenuStateEvaluator.java`
  - Activity only forwards menu callbacks.
- Dependencies are explicit and narrow:
  - Controllers/services do **not** take a dependency on the activity type.
  - `rg -n "OpenDroidPDFActivity" /mnt/subtitled/repos/penandpdf/platform/android/src` shows:
    - the activity itself, plus
    - a small, explicit set of host adapters under `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/hosts/`,
    - and nothing else.

## Concrete work needed (what it will take)

### A) Move remaining activity helpers into single owners + host adapters

- Document/type/state getters currently on:
  - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`
  - → move into a single owner such as:
    - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentLifecycleManager.java`
    - (or a `DocumentState`-style data holder exposed by it)
  - Expose via a narrow interface (avoid passing the activity around).

- “Current page view / selection editability / sidecar provider” currently reaching into `mDocView`:
  - Move behind a document-view host adapter (so controllers don’t need `OpenDroidPDFActivity` to reach into views).
  - This host adapter should be injected/wired from `ActivityComposition` and/or the document composition root.

- Any remaining permission prompting / SAF launch behavior:
  - Keep centralized in:
    - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/hosts/DocumentAccessHostAdapter.java`
    - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/document/DocumentAccessIntents.java`

- UI side-effects (toasts, alerts):
  - Route through existing UI managers/hosts under:
    - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/ui/`
  - Avoid activity “helper methods” becoming global surface area.

### B) Replace call-sites to use interfaces/adapters (not the activity class)

- Update controllers/services so they depend on:
  - the new interfaces/host adapters, and/or
  - `DocumentLifecycleManager` / `ActivityFacade` as the single source of truth,
  - not `OpenDroidPDFActivity` directly.

### C) Verification + progress accounting (slice loop)

- Build/tests:
  - `cd /mnt/subtitled/repos/penandpdf/platform/android && ./gradlew testDebugUnitTest assembleDebug -x lint`
- Smokes:
  - `/mnt/subtitled/repos/penandpdf/scripts/geny_smoke.sh`
  - `/mnt/subtitled/repos/penandpdf/scripts/geny_epub_smoke.sh`
- Then record the slice in:
  - `/mnt/subtitled/repos/penandpdf/plan.md`
  - `/mnt/subtitled/repos/penandpdf/docs/housekeeping/baseline_smoke.md`

## Suggested next Phase 2 slice (high leverage)

- Target the biggest remaining “non-host” chunk in:
  - `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java`
  - Specifically: document-type + current-page-view + sidecar-provider helpers.
- Push ownership into a single doc/view host adapter and delete the activity helper methods.

## Progress (landed slices)

- 2025-12-24: Centralized document-type + current-page-view + sidecar-provider helpers in `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/app/hosts/DocumentViewHostAdapter.java` and removed them from `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` (callers rewired). Commit: `4b90e5af`.
- 2025-12-24: Removed remaining toolbar-state helpers from `/mnt/subtitled/repos/penandpdf/platform/android/src/org/opendroidpdf/OpenDroidPDFActivity.java` by routing draw/erase mode through `DrawingService` and selection editability through `DocumentViewHostAdapter`. Commit: `99b325ed`.
