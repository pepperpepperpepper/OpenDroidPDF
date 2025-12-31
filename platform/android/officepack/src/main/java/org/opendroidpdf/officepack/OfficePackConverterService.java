package org.opendroidpdf.officepack;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Minimal Office Pack conversion service.
 *
 * <p>W2b ships this service as a secure binding target. The engine is stubbed and may return
 * "unsupported" until W2c lands.</p>
 */
public final class OfficePackConverterService extends Service {

    private static final String TAG = "OfficePackConverter";

    private final IOfficePackConverter.Stub binder = new IOfficePackConverter.Stub() {
        @Override
        public int convertWordToPdf(ParcelFileDescriptor input, ParcelFileDescriptor output) {
            Log.i(TAG, "convertWordToPdf called");

            if (input == null || output == null) {
                Log.w(TAG, "convertWordToPdf: missing input or output PFD");
                return RESULT_ERROR;
            }

            try (FileInputStream in = new FileInputStream(input.getFileDescriptor());
                 FileOutputStream out = new FileOutputStream(output.getFileDescriptor());
                 BufferedInputStream bin = new BufferedInputStream(in);
                 BufferedOutputStream bout = new BufferedOutputStream(out)) {

                int result = WordToPdfConverter.convert(bin, bout);
                bout.flush();
                return result;
            } catch (Throwable t) {
                Log.e(TAG, "convertWordToPdf failed", t);
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
