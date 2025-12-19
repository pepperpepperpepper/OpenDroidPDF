package org.opendroidpdf;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.opendroidpdf.core.MuPdfRepository;

/**
 * Debug-only autotest runner extracted from the activity to shrink it. It must
 * live in the base package to access package-private APIs used in tests.
 */
public final class DebugAutotestRunner {
    public interface Host {
        @NonNull MuPDFReaderView getDocView();
        @NonNull MuPdfRepository getRepository();
        @NonNull Context getContext();
        void onSharedPreferenceChanged(@NonNull String key);
        void commitPendingInkToCoreBlocking();
        boolean isAutoTestRan();
        void markAutoTestRan();
        @NonNull String appName();
    }

    private DebugAutotestRunner() {}

    public static void runIfNeeded(@NonNull final Host host, final Intent intent) {
        if (!BuildConfig.DEBUG) return;
        if (host.isAutoTestRan()) return;
        if (intent == null || !intent.getBooleanExtra("autotest", false)) return;
        final MuPDFReaderView docView = host.getDocView();
        if (docView == null) return;

        host.markAutoTestRan();
        final boolean full = intent.getBooleanExtra("autotest_full", false);

        org.opendroidpdf.app.AppCoroutines.launchMainDelayed(
                org.opendroidpdf.app.AppCoroutines.mainScope(),
                1000,
                new Runnable() {
                    @Override public void run() {
                        try {
                            if (intent.getBooleanExtra("autotest_red", false)) {
                                android.content.SharedPreferences sp =
                                        host.getContext().getSharedPreferences(
                                                org.opendroidpdf.SettingsActivity.SHARED_PREFERENCES_STRING,
                                                android.content.Context.MODE_MULTI_PROCESS);
                                sp.edit().putString(org.opendroidpdf.SettingsActivity.PREF_INK_COLOR, "15").apply();
                                host.onSharedPreferenceChanged(org.opendroidpdf.SettingsActivity.PREF_INK_COLOR);
                            }

                            MuPDFView v = (MuPDFView) docView.getSelectedView();
                            if (v instanceof MuPDFPageView) {
                                MuPDFPageView pv = (MuPDFPageView) v;
                                docView.requestMode(MuPDFReaderView.Mode.Drawing);
                                int w = Math.max(1, pv.getWidth());
                                int h = Math.max(1, pv.getHeight());
                                float m = Math.min(w, h) * 0.2f;

                                // First stroke
                                pv.startDraw(m, m);
                                pv.continueDraw(w - m, m);
                                pv.continueDraw(w - m, h - m);
                                pv.continueDraw(m, h - m);
                                pv.continueDraw(m, m);
                                pv.finishDraw();

                                if (full) {
                                    // Exercise undo path
                                    pv.undoDraw();
                                    // Second stroke (different size)
                                    float m2 = Math.min(w, h) * 0.1f;
                                    pv.startDraw(m2, m2);
                                    pv.continueDraw(w - m2, h * 0.5f);
                                    pv.finishDraw();
                                }

                                try {
                                    PointF[][] arcs = pv.getDraw();
                                    if (arcs != null && arcs.length > 0) {
                                        host.getRepository().addInkAnnotation(docView.getSelectedItemPosition(), arcs);
                                    }
                                    // Force dirty flag regardless of whether native reported a change
                                    host.getRepository().forceMarkDirty();
                                } catch (Throwable ignore) {}
                                pv.cancelDraw();
                                docView.requestMode(MuPDFReaderView.Mode.Viewing);
                            }

                            if (full) {
                                try {
                                    int hits = host.getRepository().searchPage(0, "OpenDroidPDF").length;
                                    android.util.Log.i(host.appName(), "AUTOTEST_SEARCH_HITS=" + hits);
                                } catch (Throwable t) {
                                    android.util.Log.e(host.appName(), "AUTOTEST_SEARCH_ERROR=" + t);
                                }
                            }

                            host.commitPendingInkToCoreBlocking();

                            boolean dirty = host.getRepository().hasUnsavedChanges();
                            if (!dirty) {
                                host.getRepository().forceMarkDirty();
                                dirty = host.getRepository().hasUnsavedChanges();
                            }
                            android.util.Log.i(host.appName(), "AUTOTEST_HAS_CHANGES=" + dirty);
                            Uri exported = host.getRepository().exportDocument(host.getContext());
                            if (exported == null) {
                                android.util.Log.e(host.appName(), "AUTOTEST_EXPORT_FAILED");
                                return;
                            }
                            java.io.InputStream in = host.getContext().getContentResolver().openInputStream(exported);
                            java.io.File outFile = new java.io.File(host.getContext().getFilesDir(), "autotest-output.pdf");
                            java.io.OutputStream out = new java.io.FileOutputStream(outFile);
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) > 0) { out.write(buf, 0, len); }
                            in.close();
                            out.close();
                            android.util.Log.i(host.appName(), "AUTOTEST_OUTPUT=" + outFile.getAbsolutePath() + " bytes=" + outFile.length());
                            try { Toast.makeText(host.getContext(), "Autotest exported", Toast.LENGTH_SHORT).show(); } catch (Throwable ignore) {}
                        } catch (Throwable t) {
                            android.util.Log.e(host.appName(), "AUTOTEST_ERROR=" + t);
                        }
                    }
                });
    }
}
