# Fixing Plan — Text Annotations (FreeText) UX Parity

Owner: `TextAnnotationController` + selection/gesture layer (**ONE OWNER**; no view/activity-owned ad‑hoc state)

This is a small, execution-oriented checklist distilled from `implementation_plan.md` (“Open decisions / optional refinements”),
focused on the remaining high-ROI FreeText UX gaps.

## Current issues to resolve
- Move/resize affordance is not discoverable (requires long-press arming; UI copy is misleading).
- Selection handles can render underneath other overlays (z-order).
- Re-edit UX is still timing-sensitive in some paths (should be “select, then tap again to edit”).
- Sidecar note rendering can get janky on pages with many notes (layout allocation per frame).

## Execution slices (do in order; each slice = build + Genymotion smokes + commit)
1) **Fix UI copy** for move/resize (string + de/es stubs).
   - [x] Update `R.string.tap_to_move_annotation` to reflect current behavior (“Long-press, release, then drag…”).

2) **Fix selection z-order** so handles always draw on top.
   - [x] Draw item selection box/handles after sidecar/drawing overlays in `PageOverlayView`.

3) **Add a dedicated MOVE handle** (top-center) so move is discoverable without breaking pan-after-zoom.
   - [ ] Extend `ItemSelectionHandles` with a MOVE handle + hit-test.
   - [ ] Render MOVE handle in `ItemSelectionRenderer`.
   - [ ] Update `TextAnnotationManipulationGestureHandler`:
     - drag MOVE handle → start MOVE immediately
     - drag corners → RESIZE
     - drag elsewhere → PAN (default)
   - [ ] Update `scripts/geny_pdf_text_annot_smoke.sh` to drag the MOVE handle (more deterministic than long-press).

4) **Re-edit UX parity** (embedded FreeText should match sidecar notes).
   - [ ] Once selected, tapping the same text annotation opens editor regardless of timing.
   - [ ] Guardrail: tapping on handles must NOT open the editor.
   - [ ] If any “double tap” window remains, use `ViewConfiguration.getDoubleTapTimeout()` (no hard-coded 900ms).

5) **Sidecar note render perf** (only if still needed).
   - [ ] Cache `StaticLayout` in `SidecarAnnotationRenderer` keyed by `(noteId, widthDoc, fontSizeDoc, text)`.

## Tracking
- Update this file with commit SHAs as slices land.
- Keep `implementation_plan.md` in sync (mark slices done / add notes if behavior changes).

### Completed
- Slices 1–2: build + smoke PASS — `11d81aeb`
