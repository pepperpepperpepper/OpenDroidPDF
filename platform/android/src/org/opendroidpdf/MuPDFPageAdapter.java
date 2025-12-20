package org.opendroidpdf;

import android.content.Context;
import android.graphics.PointF;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.app.document.DocumentType;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;

public class MuPDFPageAdapter extends BaseAdapter {
    private static final int PAGE_SIZE_PREFETCH_LIMIT = 32;
    private final Context mContext;
    private final FilePicker.FilePickerSupport mFilePickerSupport;
    private final MuPdfController muPdfController;
    private final org.opendroidpdf.app.reader.ReaderComposition readerComposition;
    private final SparseArray<PointF> mPageSizes = new SparseArray<PointF>();
    private final Object pageSizeLock = new Object();
    private final ExecutorService pageSizeExecutor = Executors.newSingleThreadExecutor();
    
    public MuPDFPageAdapter(Context c,
                            MuPdfController controller,
                            FilePicker.FilePickerSupport filePickerSupport,
                            String docId,
                            DocumentType docType,
                            boolean canSaveToCurrentUri) {
        mContext = c;
        muPdfController = controller;
        mFilePickerSupport = filePickerSupport;
        readerComposition = new org.opendroidpdf.app.reader.ReaderComposition(
                mContext,
                muPdfController,
                docId,
                docType,
                canSaveToCurrentUri);

        if (muPdfController != null) {
            pageSizeExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    int numPages = getCount();
                    int limit = Math.min(numPages, PAGE_SIZE_PREFETCH_LIMIT);
                    for (int position = 0; position < limit; position++) {
                        PointF size = muPdfController.pageSize(position);
                        cachePageSize(position, size);
                    }
                    pageSizeExecutor.shutdown();
                }
            });
        } else {
            pageSizeExecutor.shutdown();
        }
    }

    /** Returns the active sidecar annotation session for this document, if any. */
    public @Nullable SidecarAnnotationSession sidecarSessionOrNull() {
        return readerComposition != null ? readerComposition.sidecarSession() : null;
    }

    private void cachePageSize(int position, PointF size) {
        synchronized (pageSizeLock) {
            mPageSizes.put(position, size);
        }
    }

    private PointF getCachedPageSize(int position) {
        synchronized (pageSizeLock) {
            return mPageSizes.get(position);
        }
    }
    
    @Override
    public int getCount() {
        return muPdfController != null ? muPdfController.pageCount() : 0;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }
    
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final MuPDFPageView pageView;
        if (convertView == null) {
            pageView = new MuPDFPageView(mContext, mFilePickerSupport, muPdfController, parent, readerComposition);
        } else {
            pageView = (MuPDFPageView) convertView;
            // Only reset when the adapter truly reuses a view for a different position.
            // Avoid clearing immediately after a successful render of the same page.
            if (pageView.getPageNumber() != position) {
                pageView.resetForReuse();
            }
        }
        
        PointF pageSize = getCachedPageSize(position);
        if (pageSize != null) {
                // We already know the page size. Set it up
                // immediately
            pageView.setPage(position, pageSize);
        } else {
                // Page size as yet unknown so find it out
            PointF size = muPdfController.pageSize(position);
            cachePageSize(position, size);
            pageView.setPage(position, size);
                // Warning: Page size must be known for measuring so 
                // we can't do this in background, but we try to fetch
                // all page sizes in the background when the adapter is created
        }
        return pageView;
    }
}
