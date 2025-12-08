package org.opendroidpdf;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PrintDocumentAdapter;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;
import android.print.PageRange;

import java.io.FileOutputStream;
import java.io.InputStream;

public class PdfPrintAdapter extends PrintDocumentAdapter {
    private final Context context;
    private final Uri source;

    public PdfPrintAdapter(Context context, Uri source) {
        this.context = context.getApplicationContext();
        this.source = source;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                         CancellationSignal cancellationSignal,
                         LayoutResultCallback callback, android.os.Bundle extras) {
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }
        PrintDocumentInfo info = new PrintDocumentInfo.Builder("document")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build();
        callback.onLayoutFinished(info, true);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal,
                        WriteResultCallback callback) {
        try (InputStream in = context.getContentResolver().openInputStream(source);
             FileOutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
            if (in == null) {
                callback.onWriteFailed("Failed to open source");
                return;
            }
            byte[] buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) >= 0 && !cancellationSignal.isCanceled()) {
                out.write(buffer, 0, read);
            }
            if (cancellationSignal.isCanceled()) {
                callback.onWriteCancelled();
            } else {
                out.flush();
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            }
        } catch (Exception e) {
            callback.onWriteFailed(e.getMessage());
        }
    }
}
