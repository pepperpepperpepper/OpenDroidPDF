# Render Blank PDF Investigation (Genymotion, 2025-12-16)

## Summary
- On Genymotion (localhost:42865), opening `thirdparty_PrZo.pdf` produces a fully uniform gray page; no text or images render.
- A new debug overlay in `PagePatchView` detects uniform patches and draws a red watermark: **"Render failed / blank"**. Latest capture confirms the patch truly contains no content.
- This is a rendering failure, not a missing-permission UX issue. Permission guidance was added earlier, but this blank occurs even with a local `file://` path and no permission errors in logcat.
## 2025-12-16 updates — render path fixed, on-screen still blank
- Fixed core cause of the original blank: the reader view wasn’t attached. `DashboardDelegate` now fetches the live docView, so render actually occurs.
- Added migration for pen thickness (string → float) to stop ClassCast crash on launch.
- Added guards against invalid page renders and removed the red overlay text; logging kept.
- Current status: MuPDF renders page 0 correctly (render dump shows PDF content), app no longer crashes. However, emulator screencaps still show a blank/white page even though `Patch ok page=0` logs fire. Likely the rendered bitmap isn’t hitting the on-screen surface (or is over-painted), not a page-index issue.
- Crash seen after the first guard attempt was a StackOverflow in `PagePatchView.Host.isPageReady` recursion; fixed by qualifying the call.

Artifacts (latest run)
- On-device render dump (shows real content): https://tmp.uh-oh.wtf/2025/12/16/eb7264b2-odp_render_dump_drawPage_latest.png_
- Screencaps still blank/white (no crash):  
  • https://tmp.uh-oh.wtf/2025/12/16/87268e22-tmp_geny_after_fix4.png_  
  • https://tmp.uh-oh.wtf/2025/12/16/fbc675ae-tmp_geny_after_fix6.png_
- UI hierarchy shows OpenDroidPDF is foreground; page index displayed as “0/2,” title “thirdparty_PrZo.pdf”; progress bar visible (content not).

Findings
- Page 0 is correct (MuPDF is zero-based); content renders in the dump, so “page 0 vs page 1” is not the issue.
- Screencap blankness is now likely a display/attachment issue: bitmap produced, but the visible view is white/transparent.

Next debugging steps
- Add a DEBUG-only on-screen overlay of the render dump (or a solid background) to verify the surface receives content.
- Script capture: wait for `Patch ok page=` log before screencap to avoid timing races.
- If still blank: force opaque background on `document_host_container`/`PageView` and trace draw order to see if over-paint/alpha is hiding the bitmap.

## Current Diagnostic Artifacts
- Debug screenshot with watermark: https://tmp.uh-oh.wtf/2025/12/16/79726053-tmp_thirdparty_view_debug.png_
- Earlier blank captures (pre-overlay):
  - https://tmp.uh-oh.wtf/2025/12/16/588d9b4f-tmp_thirdparty_view.png_ (blank)
  - https://tmp.uh-oh.wtf/2025/12/16/37938570-tmp_thirdparty_view2.png_ (also blank)
- Rendered reference of the same PDF via `pdftoppm` (shows text/images): https://tmp.uh-oh.wtf/2025/12/16/4ee76aaa-tmp_thirdparty_render.png_
- Log tail from an earlier run (no obvious MuPDF errors): https://tmp.uh-oh.wtf/2025/12/16/a89099c6-tmp_thirdparty_log.txt_

## What’s been changed so far
1. **Permission guidance:**
   - If core init fails (SecurityException or 0 pages), show a dialog with “Grant access” to re-open via ACTION_OPEN_DOCUMENT.
   - New strings added (en/es/de). This addresses silent blanks from missing SAF permissions.
2. **Blank render detector (DEBUG only):**
   - `PagePatchView` now checks for uniform bitmaps; logs a warning and overlays “Render failed / blank” in red.
   - Confirms the emulator render path returns a uniform patch.
3. **Search query single source of truth:** SearchSession now owns latest/last-submitted query; SearchStateDelegate removed. (Not directly related, but part of this branch.)

## Observations
- The blank occurs even with `file:///sdcard/Download/thirdparty_PrZo.pdf` (no SAF); suggests a render failure inside MuPDF or our draw/update plumbing.
- `PageView.mIsBlank` starts false after setPage; overlay still blank => draw/update likely produced a uniform bitmap.
- No MuPDF exceptions seen in the captured log tail (need deeper logging around `muPdfController.drawPage/updatePage`).
- The device screenshot is uniform gray (240,240,240) with near-zero variance; no text detected by OCR.

## Next debugging steps
- Instrument `MuPDFPageView.drawPage/updatePage` to log when draw returns, including patch size and whether bitmap remains uniform; catch/print any exceptions.
- Log core state on load: page count, page size, isPdfDocument, file format.
- Force a single-page render at a fixed DPI via `MuPdfRepository.drawPage` into a temp bitmap and check uniformity; log result.
- Add a debug “Render self-test” menu item to draw page 0 into a bitmap and dump to `/sdcard/odp_render_test.png` for inspection.
- Capture a fresh `adb logcat -d` after the instrumented build to see MuPDF errors (if any) on Genymotion.

## Hypotheses to validate
- MuPDF failing to decode page due to missing fonts/resources in this environment.
- Surface/bitmap format mismatch on Genymotion (virgl). Maybe the draw call succeeds but produces transparent/white.
- Patch flow not triggered (HQ/entire views not requesting render) because size/adapter not set—though overlay shows view exists.

## User-facing impact
- On some devices (Pixel Fold per user report) and Genymotion, PDFs open to a blank page with no error. The new permission dialog mitigates SAF cases, but render failures remain.

## Action items
- Add instrumentation (above) and re-run on Genymotion.
- Once a clear error is captured, patch MuPDF call path or fallback render strategy and verify via updated screenshots.

## 2025-12-16 update — root cause found and partially validated
- Cause: MuPDF was rendering, but the UI was blank because the `MuPDFReaderView` was never attached to the document container. `DashboardDelegate` captured a `null` docView at construction and always tried to attach that null.
- Fix: `DashboardDelegate` now fetches the live `docView` from the activity when attaching, so the reader view is actually inserted into `fragment_document_host.xml`.
- Instrumentation: added core/patch logging, uniform-bitmap detection, and a first-render bitmap dump (`/sdcard/Download/odp_render_dump_drawPage.png`, non-uniform and shows page content).
- Screenshots: local pulls of emulator screens still sometimes show the launcher instead of the opened app (timing issue on capture). Need a reliable scripted capture after the app is foregrounded; current captures are inconclusive, but the dumped render bitmap confirms content is produced.

### 2025-12-16 evening — debug overlay + scripted capture
- Added a DEBUG on-surface overlay in `PagePatchView` that paints a 240px thumbnail of the rendered bitmap in the top-left and forces an opaque background. This shows whether the bitmap reaches the view surface.
- Rebuilt, reinstalled, launched `thirdparty_PrZo.pdf`, waited for `Patch ok page=0`, then captured a screencap. Result: https://tmp.uh-oh.wtf/2025/12/16/3a52e438-tmp_geny_overlay.png_ (top-left shows the thumbnail; center of the page still appears uniform gray).
- Pixel stats: overlay corner is highly non-uniform (stddev ~107), confirming the thumbnail is drawn; center region remains near-white (stddev ~14), so the main surface is still blank even though the patch is rendered and delivered to the view.
- Logcat confirms render path executes without crashes; MuPDF render dump still shows full content.

### 2025-12-16 later — keep bitmap alive, limit resets
- Added first-patch callbacks/logs; `PageView` now logs `first patch rendered page=0 bmp=1080x1397`. Adapter only resets reused views when position changes; `PageView.setPage` only calls `reset()` if the page number changes. `PagePatchView.reset()` no longer nulls the bitmap to avoid wiping the image immediately after render.
- Despite this, the main area is still blank. New screencaps: https://tmp.uh-oh.wtf/2025/12/16/751c1707-tmp_geny_overlay3.png_ and https://tmp.uh-oh.wtf/2025/12/16/658051a6-tmp_geny_overlay6.png_ — thumbnails show content, main view remains white.
- Logcat still shows a `reset bitmap=null` shortly after the first patch, likely from the HQ discard path, plus repeated "Goto page -1" spam. Renders succeed; no crashes.

### 2025-12-16 latest — HQ discard no-reset, layout logging
- HQ discard no longer clears the bitmap; added layout logging in `PageView.onLayout`. Adapter reset limited to page changes; `setPage` only resets when the page id changes.
- Logs now show two consecutive layouts: page -1 (entire visible, HQ gone) then page 0. The “Goto page -1 … cannot make displaylist from page -1” spam persists right after the first patch, likely hiding/overpainting the rendered content.
- Latest screencap still shows the loading placeholder instead of the page: https://tmp.uh-oh.wtf/2025/12/16/1e82f42f-tmp_geny_overlay8.png_ (thumbnail proves the bitmap exists; main surface blank/placeholder).

### 2025-12-16 20:30 UTC — On-screen render fixed
- Root cause of “blank screen after successful render”: `ReaderView` started with `mCurrent = INVALID_POSITION`, so the recycler immediately evicted the only child (page 0) during `removeSuperfluousChildren()`, clearing the bitmaps. Fix: initialize `mCurrent = 0` when the adapter is set and has pages.
- Additional hardening: clamp negative page indices in native `gotoPageInternal` and `updatePageInternal`, and in `MuPDFCore.updatePage`, eliminating the “Goto page -1” spam.
- Result: the PDF now renders visibly on Genymotion. New screencap with real content: https://tmp.uh-oh.wtf/2025/12/16/a73dea71-tmp_geny_fix2.png_.
- Debug trace shows no post-render resets; PagePatchView.reset no longer fires during steady state.

Next targeted fixes (if further polish is needed):
- Hide placeholder/progress once the first patch arrives to avoid the spinner overlay lingering.
- Keep HQ/entire views visibility logged; ensure layout doesn’t oscillate between pages in multi-page docs.

Next targeted fixes (not yet applied):
- Stop `layoutOrDiscardHq` from calling `reset()` (hide without clearing) and log visibility/layout of entire/HQ views.
- Force patch views visible during first render and log their measured size to confirm they’re on screen.
