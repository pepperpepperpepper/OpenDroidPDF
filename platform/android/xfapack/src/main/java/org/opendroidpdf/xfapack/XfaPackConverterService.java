package org.opendroidpdf.xfapack;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * XFA Pack conversion service.
 *
 * <p>This pack intentionally does <b>not</b> ship an XFA runtime. It can only handle the
 * “hybrid/static XFA” case where the PDF contains an embedded AcroForm fallback, by stripping
 * the {@code /XFA} marker so viewers treat it as a standard AcroForm PDF. Dynamic XFA forms
 * (no AcroForm fallback) remain unsupported.</p>
 */
public final class XfaPackConverterService extends Service {

    private static final String TAG = "XfaPackConverter";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PDFBoxResourceLoader.init(getApplicationContext());
        } catch (Throwable t) {
            Log.e(TAG, "PDFBoxResourceLoader.init failed", t);
        }
    }

    private final IXfaPackConverter.Stub binder = new IXfaPackConverter.Stub() {
        @Override
        public int convertXfaToPdf(ParcelFileDescriptor input,
                                  ParcelFileDescriptor output,
                                  int mode) {
            Log.i(TAG, "convertXfaToPdf called mode=" + mode);

            if (input == null || output == null) {
                Log.w(TAG, "convertXfaToPdf: missing input or output PFD");
                return RESULT_ERROR;
            }

            try (FileInputStream in = new FileInputStream(input.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(output.getFileDescriptor());
                 BufferedInputStream bin = new BufferedInputStream(in);
                 BufferedOutputStream bout = new BufferedOutputStream(out)) {

                int result = XfaToPdfConverter.convert(bin, bout, mode, getCacheDir());
                bout.flush();
                return result;
            } catch (Throwable t) {
                Log.e(TAG, "convertXfaToPdf failed", t);
                return RESULT_ERROR;
            } finally {
                try {
                    input.close();
                } catch (IOException ignore) {
                }
                try {
                    output.close();
                } catch (IOException ignore) {
                }
            }
        }

        @Override
        public int convertXfaToPdfWithPassword(ParcelFileDescriptor input,
                                              ParcelFileDescriptor output,
                                              int mode,
                                              String password) {
            Log.i(TAG, "convertXfaToPdfWithPassword called mode=" + mode);

            if (input == null || output == null) {
                Log.w(TAG, "convertXfaToPdfWithPassword: missing input or output PFD");
                return RESULT_ERROR;
            }

            try (FileInputStream in = new FileInputStream(input.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(output.getFileDescriptor());
                 BufferedInputStream bin = new BufferedInputStream(in);
                 BufferedOutputStream bout = new BufferedOutputStream(out)) {

                int result = XfaToPdfConverter.convert(bin, bout, mode, getCacheDir(), password);
                bout.flush();
                return result;
            } catch (Throwable t) {
                Log.e(TAG, "convertXfaToPdfWithPassword failed", t);
                return RESULT_ERROR;
            } finally {
                try {
                    input.close();
                } catch (IOException ignore) {
                }
                try {
                    output.close();
                } catch (IOException ignore) {
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
