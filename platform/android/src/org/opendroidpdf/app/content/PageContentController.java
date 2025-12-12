package org.opendroidpdf.app.content;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.LinkInfo;
import org.opendroidpdf.TextWord;
import org.opendroidpdf.core.DocumentAnnotationCallback;
import org.opendroidpdf.core.DocumentContentController;
import org.opendroidpdf.core.DocumentLinkCallback;
import org.opendroidpdf.core.DocumentTextCallback;
import org.opendroidpdf.core.DocumentContentController.DocumentJob;

/**
 * Owns page-level async content fetches (text, links, annotations)
 * and their cancellations. Keeps PageView free of job wiring.
 */
public final class PageContentController {

    public interface Host extends PageContentHost { }

    private final DocumentContentController contentController;
    private DocumentJob textJob;
    private DocumentJob linkJob;
    private DocumentJob annotJob;

    public PageContentController(DocumentContentController controller) {
        this.contentController = controller;
    }

    public void loadText(final Host host) {
        if (contentController == null || host == null || textJob != null) return;
        textJob = contentController.loadTextAsync(host.getPageNumber(), new DocumentTextCallback() {
            @Override public void onResult(TextWord[][] result) {
                host.setText(result);
                host.invalidateOverlay();
                textJob = null;
            }
        });
    }

    public void loadLinks(final Host host) {
        if (contentController == null || host == null) return;
        if (linkJob != null) linkJob.cancel();
        linkJob = contentController.loadLinkInfoAsync(host.getPageNumber(), new DocumentLinkCallback() {
            @Override public void onResult(LinkInfo[] result) {
                host.setLinks(result);
                host.invalidateOverlay();
                linkJob = null;
            }
        });
    }

    public void loadAnnotations(final Host host) {
        if (contentController == null || host == null) return;
        if (annotJob != null) annotJob.cancel();
        annotJob = contentController.loadAnnotationsAsync(host.getPageNumber(), new DocumentAnnotationCallback() {
            @Override public void onResult(Annotation[] result) {
                host.setAnnotations(result);
                boolean forceFull = host.consumeForceFullRedrawFlag();
                host.requestRedraw(!forceFull);
                annotJob = null;
            }
        });
    }

    public void cancelAll() {
        if (textJob != null) { textJob.cancel(); textJob = null; }
        if (linkJob != null) { linkJob.cancel(); linkJob = null; }
        if (annotJob != null) { annotJob.cancel(); annotJob = null; }
    }
}
