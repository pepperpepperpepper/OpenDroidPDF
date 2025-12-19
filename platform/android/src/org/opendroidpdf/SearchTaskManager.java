package org.opendroidpdf;

import java.lang.Runnable;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.opendroidpdf.core.SearchController;
import org.opendroidpdf.core.SearchController.SearchJob;
import org.opendroidpdf.core.SearchCallbacks;
import org.opendroidpdf.app.AppCoroutines;

import kotlinx.coroutines.CoroutineScope;


class SearchProgressDialog extends ProgressDialog {
    public SearchProgressDialog(Context context) {
        super(context);
    }

    private boolean mCancelled = false;
    private boolean mDismissed = false;

    public boolean isCancelled() {
        return mCancelled;
    }

    public boolean isDismissed() {
        return mDismissed;
    }

    @Override
    public void cancel() {
        mCancelled = true;
        super.cancel();
    }

    @Override
    public void dismiss() {
        mDismissed = true;
        super.dismiss();
    }
}
                      
public abstract class SearchTaskManager {
    private static final int SEARCH_PROGRESS_DELAY = 1000;
    protected final Context mContext;
    private final SearchController searchController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable progressDelayRunnable;
    private SearchJob mSearchTask;
    private SearchProgressDialog mActiveProgressDialog;
    private CoroutineScope searchScope;
    
    public SearchTaskManager(Context context, SearchController searchController) {
        mContext = context;
        this.searchController = searchController;
    }

    protected abstract void onTextFound(SearchResult result);
    protected abstract void goToResult(SearchResult result);
    
    public void start(final String text, int direction, int displayPage) {
        if (searchController == null)
            return;
        stop();
        searchScope = AppCoroutines.newMainScope();

        final int increment = direction;
        final int startIndex = displayPage;
        final int pageCount = searchController.pageCount();
        if (pageCount <= 0) {
            return;
        }

        final SearchProgressDialog progressDialog = new SearchProgressDialog(mContext);
        mActiveProgressDialog = progressDialog;
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgressPercentFormat(null);
        progressDialog.setTitle(mContext.getString(R.string.searching_));
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    stop();
                }
            });
        progressDialog.setMax(pageCount);

        progressDialog.setProgress(startIndex);
        cancelPendingProgressDialog();
        progressDelayRunnable = new Runnable() {
            @Override public void run() {
                if (!(progressDialog.isCancelled() || progressDialog.isDismissed())) {
                    progressDialog.show();
                }
            }
        };
        mainHandler.postDelayed(progressDelayRunnable, SEARCH_PROGRESS_DELAY);

        mSearchTask = searchController.startSearch(text, increment, startIndex, new SearchCallbacks() {
                @Override
                public void onProgress(int page) {
                    progressDialog.setProgress(Math.min(pageCount, Math.max(0, page)));
                }

                @Override
                public void onResult(SearchResult result) {
                    onTextFound(result);
                }

                @Override
                public void onFirstResult(SearchResult result) {
                    if(!(progressDialog.isCancelled() || progressDialog.isDismissed())) {
                        progressDialog.dismiss();
                    }
                    goToResult(result);
                }

                @Override
                public void onComplete(SearchResult firstResult) {
                    progressDialog.cancel();
                    mActiveProgressDialog = null;
                    if(firstResult == null) {
                        Toast.makeText(mContext, R.string.text_not_found, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled() {
                    progressDialog.cancel();
                    mActiveProgressDialog = null;
                }
            }, searchScope);
    }

	public void stop() {
		if (mSearchTask != null) {
			mSearchTask.cancel();
			mSearchTask = null;
		}
        if (searchScope != null) {
            AppCoroutines.cancelScope(searchScope);
            searchScope = null;
        }
        cancelPendingProgressDialog();
        if (mActiveProgressDialog != null) {
            mActiveProgressDialog.cancel();
            mActiveProgressDialog = null;
        }
    }

    private void cancelPendingProgressDialog() {
        if (progressDelayRunnable != null) {
            mainHandler.removeCallbacks(progressDelayRunnable);
            progressDelayRunnable = null;
        }
    }

}
