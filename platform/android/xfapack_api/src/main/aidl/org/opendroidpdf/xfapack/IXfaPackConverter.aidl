package org.opendroidpdf.xfapack;

import android.os.ParcelFileDescriptor;

interface IXfaPackConverter {
    // Return codes
    const int RESULT_OK = 0;
    const int RESULT_UNSUPPORTED = 1;
    const int RESULT_ERROR = 2;
    const int RESULT_PASSWORD_REQUIRED = 3;

    // Conversion modes
    const int MODE_CONVERT_TO_ACROFORM = 0;
    const int MODE_FLATTEN_TO_PDF = 1;

    int convertXfaToPdf(in ParcelFileDescriptor input, in ParcelFileDescriptor output, int mode);

    // Optional password-enabled variant. Callers should prefer this for encrypted PDFs.
    // If the password is wrong or missing, the implementation returns RESULT_PASSWORD_REQUIRED.
    int convertXfaToPdfWithPassword(in ParcelFileDescriptor input, in ParcelFileDescriptor output, int mode, String password);
}
