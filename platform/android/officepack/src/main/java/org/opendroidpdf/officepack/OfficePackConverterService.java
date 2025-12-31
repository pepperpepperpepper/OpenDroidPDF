package org.opendroidpdf.officepack;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

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
            Log.i(TAG, "convertWordToPdf called; returning unsupported (W2b stub)");
            return RESULT_UNSUPPORTED;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
