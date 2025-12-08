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

/**
 * Thin façade around {@link MuPDFCore} so upper layers do not talk to JNI bindings directly.
 * This is intentionally minimal for now; future phases can add more document/annotation helpers.
 */
public final class MuPdfRepository {
    private final MuPDFCore core;

    public MuPdfRepository(MuPDFCore core) {
        this.core = core;
    }

    public RectF[] searchPage(int pageIndex, String query) {
        if (query == null || query.isEmpty()) {
            return new RectF[0];
        }
        RectF[] hits = core.searchPage(pageIndex, query);
        return hits != null ? hits : new RectF[0];
    }

    public int getPageCount() {
        return core.countPages();
    }

    public PointF getPageSize(int pageIndex) {
        return core.getPageSize(pageIndex);
    }

    public TextWord[][] extractTextLines(int pageIndex) {
        return core.textLines(pageIndex);
    }

    public byte[] exportPageHtml(int pageIndex) {
        return core.html(pageIndex);
    }

    public boolean saveCopy(String filesystemPath) {
        if (filesystemPath == null || filesystemPath.isEmpty()) {
            return false;
        }
        return core.saveAs(filesystemPath) == 1;
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
        requireExtendedCore().save(context);
    }

    public boolean insertBlankPageAtEnd() {
        return core.insertBlankPageAtEnd();
    }

    public void addInkAnnotation(int pageIndex, PointF[][] arcs) {
        if (arcs == null || arcs.length == 0) {
            return;
        }
        core.addInkAnnotation(pageIndex, arcs);
    }

    public void setInkColor(float red, float green, float blue) {
        core.setInkColor(red, green, blue);
    }

    public void markDocumentDirty() {
        core.setHasAdditionalChanges(true);
    }

    public void refreshAnnotationAppearance(int pageIndex) {
        Bitmap singlePixel = null;
        MuPDFCore.Cookie cookie = newRenderCookie();
        try {
            singlePixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            core.drawPage(singlePixel, pageIndex, 1, 1, 0, 0, 1, 1, cookie);
        } finally {
            cookie.destroy();
            if (singlePixel != null) {
                singlePixel.recycle();
            }
        }
    }

    public boolean isPdfDocument() {
        return core.fileFormat().startsWith("PDF");
    }

    public String getDocumentName() {
        return core.getFileName();
    }

    public Uri getDocumentUri() {
        return requireExtendedCore().getUri();
    }

    public boolean hasUnsavedChanges() {
        return core.hasChanges();
    }

    public Annotation[] loadAnnotations(int pageIndex) {
        Annotation[] annotations = core.getAnnoations(pageIndex);
        return annotations != null ? annotations : new Annotation[0];
    }

    public LinkInfo[] getLinks(int pageIndex) {
        LinkInfo[] links = core.getPageLinks(pageIndex);
        return links != null ? links : new LinkInfo[0];
    }

    public void addMarkupAnnotation(int pageIndex, PointF[] quadPoints, Annotation.Type type) {
        if (quadPoints == null || quadPoints.length == 0 || type == null) {
            return;
        }
        core.addMarkupAnnotation(pageIndex, quadPoints, type);
    }

    public void addTextAnnotation(int pageIndex, PointF[] quadPoints, String text) {
        if (quadPoints == null || quadPoints.length == 0) {
            return;
        }
        core.addTextAnnotation(pageIndex, quadPoints, text);
    }

    public void deleteAnnotation(int pageIndex, int annotationIndex) {
        core.deleteAnnotation(pageIndex, annotationIndex);
    }

    public RectF[] getWidgetAreas(int pageIndex) {
        RectF[] widgets = core.getWidgetAreas(pageIndex);
        return widgets != null ? widgets : new RectF[0];
    }

    public boolean setWidgetText(int pageIndex, String value) {
        return core.setFocusedWidgetText(pageIndex, value);
    }

    public void setWidgetChoice(String[] selected) {
        if (selected == null) {
            return;
        }
        core.setFocusedWidgetChoiceSelected(selected);
    }

    public String checkFocusedSignature() {
        return core.checkFocusedSignature();
    }

    public boolean signFocusedSignature(String keyFile, String password) {
        return core.signFocusedSignature(keyFile, password);
    }

    public boolean javascriptSupported() {
        return core.javascriptSupported();
    }

    public MuPDFCore.Cookie newRenderCookie() {
        return core.new Cookie();
    }

    public void drawPage(Bitmap bitmap, int page, int pageWidth, int pageHeight,
                         int patchX, int patchY, int patchWidth, int patchHeight,
                         MuPDFCore.Cookie cookie) {
        core.drawPage(bitmap, page, pageWidth, pageHeight, patchX, patchY, patchWidth, patchHeight, cookie);
    }

    public void updatePage(Bitmap bitmap, int page, int pageWidth, int pageHeight,
                           int patchX, int patchY, int patchWidth, int patchHeight,
                           MuPDFCore.Cookie cookie) {
        core.updatePage(bitmap, page, pageWidth, pageHeight, patchX, patchY, patchWidth, patchHeight, cookie);
    }

    public PassClickResult passClick(int page, float x, float y) {
        return core.passClickEvent(page, x, y);
    }

    public MuPDFAlert waitForAlert() {
        return core.waitForAlert();
    }

    public void replyToAlert(MuPDFAlert alert) {
        if (alert == null) {
            return;
        }
        core.replyToAlert(alert);
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
}
