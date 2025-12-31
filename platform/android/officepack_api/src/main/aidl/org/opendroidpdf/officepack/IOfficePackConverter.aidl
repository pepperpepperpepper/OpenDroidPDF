package org.opendroidpdf.officepack;

import android.os.ParcelFileDescriptor;

interface IOfficePackConverter {
    const int RESULT_OK = 0;
    const int RESULT_UNSUPPORTED = 1;
    const int RESULT_ERROR = 2;

    int convertWordToPdf(in ParcelFileDescriptor input, in ParcelFileDescriptor output);
}
