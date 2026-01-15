package org.opendroidpdf.core;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import org.opendroidpdf.BuildConfig;

import java.lang.reflect.Method;

/**
 * Reflection-based bridge to the optional pdfboxops module.
 *
 * We use reflection so the base APK can compile/run without bundling the module.
 * Enable packaging with -Popendroidpdf.enablePdfBoxOps=true.
 */
public final class PdfBoxFacade {
    private static final String OPS_CLASS = "org.opendroidpdf.pdfboxops.PdfBoxOps";
    private static volatile Boolean cachedAvailable = null;

    private PdfBoxFacade() {}

    public static boolean isAvailable() {
        if (!BuildConfig.ENABLE_PDFBOX_OPS) return false;
        Boolean cached = cachedAvailable;
        if (cached != null) return cached;
        try {
            Class.forName(OPS_CLASS, false, PdfBoxFacade.class.getClassLoader());
            cachedAvailable = true;
            return true;
        } catch (Throwable t) {
            cachedAvailable = false;
            return false;
        }
    }

    public static final class FlattenResult {
        public final int pageCount;
        public final boolean hadAcroForm;

        FlattenResult(int pageCount, boolean hadAcroForm) {
            this.pageCount = pageCount;
            this.hadAcroForm = hadAcroForm;
        }
    }

    /**
     * Flatten AcroForm fields using pdfboxops if present.
     */
    public static FlattenResult flattenForm(@NonNull Context ctx,
                                            @NonNull Uri input,
                                            @NonNull Uri output) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("pdfboxops not available");
        }
        Class<?> clazz = Class.forName(OPS_CLASS);
        // Kotlin object => need the singleton instance.
        Object instance = clazz.getField("INSTANCE").get(null);
        Method m = clazz.getMethod("flattenForm", Context.class, Uri.class, Uri.class);
        Object res = m.invoke(instance, ctx, input, output);
        if (res == null) {
            throw new IllegalStateException("flattenForm returned null");
        }
        Method getPageCount = res.getClass().getMethod("getPageCount");
        Method getHadAcroForm = res.getClass().getMethod("getHadAcroForm");
        int pages = ((Number) getPageCount.invoke(res)).intValue();
        boolean hadForm = (Boolean) getHadAcroForm.invoke(res);
        return new FlattenResult(pages, hadForm);
    }
}
