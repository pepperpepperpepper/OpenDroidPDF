package org.opendroidpdf.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.LinkInfo;
import org.opendroidpdf.MuPDFAlert;
import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.PassClickResult;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.BuildConfig;

/**
 * Thin façade around {@link MuPDFCore} so upper layers do not talk to JNI bindings directly.
 * This is intentionally minimal for now; future phases can add more document/annotation helpers.
 */
public final class MuPdfRepository {
    private final MuPDFCore core;
    private static final String DEBUG_FAIL_NEXT_SAVE_FILE = "odp_debug_fail_next_save";

    public MuPdfRepository(MuPDFCore core) {
        this.core = core;
    }

    public RectF[] searchPage(int pageIndex, String query) {
        if (query == null || query.isEmpty()) {
            return new RectF[0];
        }
        RectF[] hits;
        synchronized (core) {
            hits = core.searchPage(pageIndex, query);
        }
        return hits != null ? hits : new RectF[0];
    }

    public int getPageCount() {
        synchronized (core) {
            return core.countPages();
        }
    }

    /** Applies MuPDF reflow layout (EPUB/HTML). Returns false for fixed-layout docs. */
    public boolean layoutReflow(float pageWidth, float pageHeight, float em) {
        synchronized (core) {
            return core.layoutDocument(pageWidth, pageHeight, em);
        }
    }

    /** Drops cached pages/display lists so subsequent renders pick up layout/CSS changes. */
    public void clearPageCache() {
        synchronized (core) {
            core.clearPageCache();
        }
    }

    public void setUserCss(String css) {
        synchronized (core) {
            core.setUserCss(css);
        }
    }

    public PointF getPageSize(int pageIndex) {
        synchronized (core) {
            return core.getPageSize(pageIndex);
        }
    }

    public TextWord[][] extractTextLines(int pageIndex) {
        synchronized (core) {
            return core.textLines(pageIndex);
        }
    }

    /**
     * Returns an encoded MuPDF {@code fz_location} for the given page number.
     * <p>
     * For reflowable docs (EPUB), this provides a layout-stable position that can be used to restore
     * viewports across relayout.
     */
    public long locationFromPageNumber(int pageIndex) {
        synchronized (core) {
            return core.locationFromPageNumber(pageIndex);
        }
    }

    /** Converts an encoded MuPDF {@code fz_location} back into a page number for the current layout. */
    public int pageNumberFromLocation(long encodedLocation) {
        synchronized (core) {
            return core.pageNumberFromLocation(encodedLocation);
        }
    }

    public byte[] exportPageHtml(int pageIndex) {
        synchronized (core) {
            return core.html(pageIndex);
        }
    }

    public boolean saveCopy(String filesystemPath) {
        if (filesystemPath == null || filesystemPath.isEmpty()) {
            return false;
        }
        synchronized (core) {
            return core.saveAs(filesystemPath) == 1;
        }
    }

    public boolean saveCopy(Context context, Uri uri) throws Exception {
        if (context == null || uri == null) {
            return false;
        }
        requireExtendedCore().saveAs(context, uri);
        return true;
    }

    public Uri exportDocument(Context context) throws Exception {
        return requireExtendedCore().export(context);
    }

    public void saveDocument(Context context) throws Exception {
        if (BuildConfig.DEBUG && consumeDebugFailNextSave(context)) {
            throw new java.io.FileNotFoundException("open failed: EACCES (Permission denied)");
        }
        requireExtendedCore().save(context);
    }

    public boolean insertBlankPageAtEnd() {
        synchronized (core) {
            return core.insertBlankPageAtEnd();
        }
    }

    public void addInkAnnotation(int pageIndex, PointF[][] arcs) {
        if (arcs == null || arcs.length == 0) {
            return;
        }
        synchronized (core) {
            core.addInkAnnotation(pageIndex, arcs);
        }
    }

    public void setInkColor(float red, float green, float blue) {
        synchronized (core) {
            core.setInkColor(red, green, blue);
        }
    }

    public void markDocumentDirty() {
        synchronized (core) {
            core.setHasAdditionalChanges(true);
        }
    }

    /**
     * Debug/testing helper: force the dirty flag even if native change tracking
     * did not notice a mutation (used by autotests).
     */
    public void forceMarkDirty() {
        synchronized (core) {
            core.setHasAdditionalChanges(true);
        }
    }

    public void refreshAnnotationAppearance(int pageIndex) {
        Bitmap singlePixel = null;
        MuPDFCore.Cookie cookie = newRenderCookie();
        try {
            singlePixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            synchronized (core) {
                core.drawPage(singlePixel, pageIndex, 1, 1, 0, 0, 1, 1, cookie);
            }
        } finally {
            cookie.destroy();
            if (singlePixel != null) {
                singlePixel.recycle();
            }
        }
    }

    public boolean isPdfDocument() {
        synchronized (core) {
            return core.fileFormat().startsWith("PDF");
        }
    }

    public String getDocumentName() {
        synchronized (core) {
            return core.getFileName();
        }
    }

    public Uri getDocumentUri() {
        return requireExtendedCore().getUri();
    }

    public boolean hasUnsavedChanges() {
        synchronized (core) {
            return core.hasChanges();
        }
    }

    public Annotation[] loadAnnotations(int pageIndex) {
        Annotation[] annotations;
        synchronized (core) {
            annotations = core.getAnnoations(pageIndex);
        }
        return annotations != null ? annotations : new Annotation[0];
    }

    public LinkInfo[] getLinks(int pageIndex) {
        LinkInfo[] links;
        synchronized (core) {
            links = core.getPageLinks(pageIndex);
        }
        return links != null ? links : new LinkInfo[0];
    }

    public void addMarkupAnnotation(int pageIndex, PointF[] quadPoints, Annotation.Type type) {
        if (quadPoints == null || quadPoints.length == 0 || type == null) {
            return;
        }
        synchronized (core) {
            core.addMarkupAnnotation(pageIndex, quadPoints, type);
        }
    }

    public void addTextAnnotation(int pageIndex, PointF[] quadPoints, String text) {
        if (quadPoints == null || quadPoints.length == 0) {
            return;
        }
        synchronized (core) {
            core.addTextAnnotation(pageIndex, quadPoints, text);
        }
    }

    public void deleteAnnotation(int pageIndex, int annotationIndex) {
        synchronized (core) {
            core.deleteAnnotation(pageIndex, annotationIndex);
        }
    }

    public void deleteAnnotationByObjectNumber(int pageIndex, long objectNumber) {
        synchronized (core) {
            core.deleteAnnotationByObjectNumber(pageIndex, objectNumber);
        }
    }

    public void updateAnnotationContentsByObjectNumber(int pageIndex, long objectNumber, String text) {
        synchronized (core) {
            core.updateAnnotationContentsByObjectNumber(pageIndex, objectNumber, text);
        }
    }

    public void updateAnnotationRectByObjectNumber(int pageIndex, long objectNumber, float left, float top, float right, float bottom) {
        synchronized (core) {
            core.updateAnnotationRectByObjectNumber(pageIndex, objectNumber, left, top, right, bottom);
        }
    }

    public void updateFreeTextStyleByObjectNumber(int pageIndex, long objectNumber, float fontSize, float r, float g, float b) {
        synchronized (core) {
            core.updateFreeTextStyleByObjectNumber(pageIndex, objectNumber, fontSize, r, g, b);
        }
    }

    public int getBaseResolutionDpi() {
        synchronized (core) {
            return core.getBaseResolutionDpi();
        }
    }

    /** Returns true when the user has explicitly resized this FreeText box (suppresses auto-fit behaviors). */
    public boolean getFreeTextUserResizedByObjectNumber(int pageIndex, long objectNumber) {
        synchronized (core) {
            return core.getFreeTextUserResizedByObjectNumber(pageIndex, objectNumber);
        }
    }

    public void setFreeTextUserResizedByObjectNumber(int pageIndex, long objectNumber, boolean userResized) {
        synchronized (core) {
            core.setFreeTextUserResizedByObjectNumber(pageIndex, objectNumber, userResized);
        }
    }

    public float getFreeTextFontSizeByObjectNumber(int pageIndex, long objectNumber) {
        synchronized (core) {
            return core.getFreeTextFontSizeByObjectNumber(pageIndex, objectNumber);
        }
    }

    public int getFreeTextAlignmentByObjectNumber(int pageIndex, long objectNumber) {
        synchronized (core) {
            return core.getFreeTextAlignmentByObjectNumber(pageIndex, objectNumber);
        }
    }

    public void updateFreeTextAlignmentByObjectNumber(int pageIndex, long objectNumber, int alignment) {
        synchronized (core) {
            core.updateFreeTextAlignmentByObjectNumber(pageIndex, objectNumber, alignment);
        }
    }

    public RectF[] getWidgetAreas(int pageIndex) {
        RectF[] widgets;
        synchronized (core) {
            widgets = core.getWidgetAreas(pageIndex);
        }
        return widgets != null ? widgets : new RectF[0];
    }

    public boolean setWidgetText(int pageIndex, String value) {
        synchronized (core) {
            boolean ok = core.setFocusedWidgetText(pageIndex, value);
            if (ok) {
                core.setHasAdditionalChanges(true);
            }
            return ok;
        }
    }

    public void setWidgetChoice(String[] selected) {
        if (selected == null) {
            return;
        }
        synchronized (core) {
            core.setFocusedWidgetChoiceSelected(selected);
            core.setHasAdditionalChanges(true);
        }
    }

    public String checkFocusedSignature() {
        synchronized (core) {
            return core.checkFocusedSignature();
        }
    }

    public boolean signFocusedSignature(String keyFile, String password) {
        synchronized (core) {
            return core.signFocusedSignature(keyFile, password);
        }
    }

    public boolean javascriptSupported() {
        synchronized (core) {
            return core.javascriptSupported();
        }
    }

    public MuPDFCore.Cookie newRenderCookie() {
        return core.new Cookie();
    }

    public void drawPage(Bitmap bitmap, int page, int pageWidth, int pageHeight,
                         int patchX, int patchY, int patchWidth, int patchHeight,
                         MuPDFCore.Cookie cookie) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MuPdfRepository", "drawPage page=" + page + " view=" + pageWidth + "x" + pageHeight
                    + " patch=" + patchWidth + "x" + patchHeight + "@" + patchX + "," + patchY);
        }
        synchronized (core) {
            core.drawPage(bitmap, page, pageWidth, pageHeight, patchX, patchY, patchWidth, patchHeight, cookie);
        }
        if (BuildConfig.DEBUG && looksUniform(bitmap)) {
            android.util.Log.w("MuPdfRepository", "drawPage produced uniform bitmap page=" + page
                    + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
        }
        maybeDumpOnce(bitmap, "drawPage");
    }

    public void updatePage(Bitmap bitmap, int page, int pageWidth, int pageHeight,
                           int patchX, int patchY, int patchWidth, int patchHeight,
                           MuPDFCore.Cookie cookie) {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MuPdfRepository", "updatePage page=" + page + " view=" + pageWidth + "x" + pageHeight
                    + " patch=" + patchWidth + "x" + patchHeight + "@" + patchX + "," + patchY);
        }
        synchronized (core) {
            core.updatePage(bitmap, page, pageWidth, pageHeight, patchX, patchY, patchWidth, patchHeight, cookie);
        }
        if (BuildConfig.DEBUG && looksUniform(bitmap)) {
            android.util.Log.w("MuPdfRepository", "updatePage produced uniform bitmap page=" + page
                    + " size=" + bitmap.getWidth() + "x" + bitmap.getHeight());
        }
        maybeDumpOnce(bitmap, "updatePage");
    }

    private boolean looksUniform(Bitmap bm) {
        if (bm == null) return false;
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w == 0 || h == 0) return false;
        int[] samples = new int[25];
        int idx = 0;
        for (int yi = 0; yi < 5; yi++) {
            for (int xi = 0; xi < 5; xi++) {
                int x = (int)((xi + 0.5f) * w / 5f);
                int y = (int)((yi + 0.5f) * h / 5f);
                samples[idx++] = bm.getPixel(Math.min(x, w - 1), Math.min(y, h - 1));
            }
        }
        int base = samples[0];
        final int tol = 3;
        for (int i = 1; i < samples.length; i++) {
            int c = samples[i];
            int dr = Math.abs(((c >> 16) & 0xFF) - ((base >> 16) & 0xFF));
            int dg = Math.abs(((c >> 8) & 0xFF) - ((base >> 8) & 0xFF));
            int db = Math.abs((c & 0xFF) - (base & 0xFF));
            if (dr > tol || dg > tol || db > tol) return false;
        }
        return true;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean dumped = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void maybeDumpOnce(Bitmap bm, String label) {
        if (!BuildConfig.DEBUG) return;
        if (bm == null) return;
        if (!dumped.compareAndSet(false, true)) return;
        try {
            java.io.File outDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (outDir == null) outDir = android.os.Environment.getExternalStorageDirectory();
            java.io.File out = new java.io.File(outDir, "odp_render_dump_" + label + ".png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            android.util.Log.i("MuPdfRepository", "Dumped bitmap to " + out.getAbsolutePath()
                    + " uniform=" + looksUniform(bm));
        } catch (Throwable t) {
            android.util.Log.e("MuPdfRepository", "Failed to dump bitmap", t);
        }
    }

    public PassClickResult passClick(int page, float x, float y) {
        synchronized (core) {
            return core.passClickEvent(page, x, y);
        }
    }

    public MuPDFAlert waitForAlert() {
        synchronized (core) {
            return core.waitForAlert();
        }
    }

    public void replyToAlert(MuPDFAlert alert) {
        if (alert == null) {
            return;
        }
        synchronized (core) {
            core.replyToAlert(alert);
        }
    }

    public MuPDFCore getCore() {
        return core;
    }

    private OpenDroidPDFCore requireExtendedCore() {
        if (core instanceof OpenDroidPDFCore) {
            return (OpenDroidPDFCore) core;
        }
        throw new IllegalStateException("MuPdfRepository expects OpenDroidPDFCore backing implementation");
    }

    private static boolean consumeDebugFailNextSave(Context context) {
        if (context == null) return false;
        try {
            java.io.File dir = context.getFilesDir();
            if (dir == null) return false;
            java.io.File f = new java.io.File(dir, DEBUG_FAIL_NEXT_SAVE_FILE);
            if (!f.exists()) return false;
            // Best-effort one-shot.
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }
}
