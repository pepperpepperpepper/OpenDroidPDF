package org.opendroidpdf;

import android.content.Context;
import android.graphics.PointF;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opendroidpdf.core.MuPdfController;

public class MuPDFPageAdapter extends BaseAdapter {
    private final Context mContext;
    private final FilePicker.FilePickerSupport mFilePickerSupport;
    private final MuPdfController muPdfController;
    private final org.opendroidpdf.app.reader.ReaderComposition readerComposition;
    private final SparseArray<PointF> mPageSizes = new SparseArray<PointF>();
    private final Object pageSizeLock = new Object();
    private final ExecutorService pageSizeExecutor = Executors.newSingleThreadExecutor();
    
    public MuPDFPageAdapter(Context c, MuPdfController controller, FilePicker.FilePickerSupport filePickerSupport) {
        mContext = c;
        muPdfController = controller;
        mFilePickerSupport = filePickerSupport;
        readerComposition = new org.opendroidpdf.app.reader.ReaderComposition(mContext, muPdfController);

        if (muPdfController != null) {
            pageSizeExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    int numPages = getCount();
                    for (int position = 0; position < numPages; position++) {
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
