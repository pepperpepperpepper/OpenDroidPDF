package org.opendroidpdf.app.document;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bottom-sheet grid of page thumbnails for fast visual page jumping. */
final class ThumbnailsUi {

    interface PageSelectionListener {
        void onSelectPage(int pageIndex);
    }

    private ThumbnailsUi() {}

    static void show(@NonNull AppCompatActivity activity,
                     @NonNull MuPDFReaderView docView,
                     @NonNull MuPdfRepository repo,
                     @NonNull PageSelectionListener onSelectPage) {
        int pageCount = 0;
        try { pageCount = Math.max(0, repo.getPageCount()); } catch (Throwable ignore) { pageCount = 0; }
        if (pageCount <= 0) {
            try { org.opendroidpdf.app.ui.UiUtils.showInfo(activity, activity.getString(R.string.not_supported)); } catch (Throwable ignore) {}
            return;
        }

        int currentPage = 0;
        try { currentPage = Math.max(0, docView.getSelectedItemPosition()); } catch (Throwable ignore) { currentPage = 0; }
        if (currentPage >= pageCount) currentPage = Math.max(0, pageCount - 1);
        final int currentPageFinal = currentPage;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_thumbnails_sheet, null);
        dialog.setContentView(root);

        RecyclerView recycler = root.findViewById(R.id.thumbnails_recycler);
        if (recycler == null) {
            dialog.dismiss();
            return;
        }

        final Context ctx = activity;
        int spacingPx = dpToPx(ctx, 8);
        int padPx = 0;
        try { padPx = ctx.getResources().getDimensionPixelSize(R.dimen.dialog_padding_horizontal); } catch (Throwable ignore) { padPx = dpToPx(ctx, 16); }
        int widthPx = 0;
        try { widthPx = ctx.getResources().getDisplayMetrics().widthPixels; } catch (Throwable ignore) { widthPx = 0; }
        int availablePx = Math.max(1, widthPx - padPx * 2);
        int desiredCellPx = dpToPx(ctx, 118);
        int spanCount = Math.max(2, Math.min(6, availablePx / Math.max(1, desiredCellPx)));
        int thumbWidthPx = Math.max(1, (availablePx - spacingPx * (spanCount + 1)) / spanCount);

        GridLayoutManager lm = new GridLayoutManager(ctx, spanCount);
        recycler.setLayoutManager(lm);
        recycler.setHasFixedSize(false);
        recycler.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacingPx));

        final PageSelectionListener dismissingListener = pageIndex -> {
            try { dialog.dismiss(); } catch (Throwable ignore) {}
            try { onSelectPage.onSelectPage(pageIndex); } catch (Throwable ignore) {}
        };
        final PageThumbnailsAdapter adapter = new PageThumbnailsAdapter(ctx, repo, pageCount, currentPage, thumbWidthPx, dismissingListener);
        recycler.setAdapter(adapter);

        recycler.post(() -> {
            try { recycler.scrollToPosition(currentPageFinal); } catch (Throwable ignore) {}
        });

        dialog.setOnDismissListener(d -> adapter.release());
        dialog.show();
    }

    private static int dpToPx(@NonNull Context ctx, int dp) {
        float density = 1f;
        try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
        return Math.max(1, Math.round(dp * density));
    }

    private static final class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int spacingPx;

        GridSpacingItemDecoration(int spanCount, int spacingPx) {
            this.spanCount = Math.max(1, spanCount);
            this.spacingPx = Math.max(0, spacingPx);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            if (position < 0) return;
            int column = position % spanCount;
            outRect.left = spacingPx - column * spacingPx / spanCount;
            outRect.right = (column + 1) * spacingPx / spanCount;
            if (position < spanCount) outRect.top = spacingPx;
            outRect.bottom = spacingPx;
        }
    }

    private static final class PageThumbnailsAdapter extends RecyclerView.Adapter<PageThumbnailsAdapter.Holder> {
        private static final int THUMBNAIL_CACHE_SIZE = 48;

        private final Context ctx;
        private final MuPdfRepository repo;
        private final int pageCount;
        private final int currentPageIndex;
        private final int thumbnailWidthPx;
        private final android.util.LruCache<Integer, Bitmap> cache;
        private final ExecutorService executor;
        private final Set<Integer> inFlight = Collections.synchronizedSet(new HashSet<>());
        private final ConcurrentHashMap<Integer, MuPDFCore.Cookie> inFlightCookies = new ConcurrentHashMap<>();
        private final PageSelectionListener onSelectPage;
        private volatile boolean released;

        PageThumbnailsAdapter(@NonNull Context ctx,
                              @NonNull MuPdfRepository repo,
                              int pageCount,
                              int currentPageIndex,
                              int thumbnailWidthPx,
                              @NonNull PageSelectionListener onSelectPage) {
            this.ctx = ctx;
            this.repo = repo;
            this.pageCount = Math.max(0, pageCount);
            this.currentPageIndex = Math.max(0, currentPageIndex);
            this.thumbnailWidthPx = Math.max(1, thumbnailWidthPx);
            this.onSelectPage = onSelectPage;
            cache = new android.util.LruCache<Integer, Bitmap>(THUMBNAIL_CACHE_SIZE) {
                @Override protected int sizeOf(@NonNull Integer key, @NonNull Bitmap value) { return 1; }
            };
            executor = Executors.newSingleThreadExecutor();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_page_thumbnail, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.boundPageIndex = position;
            holder.itemView.setSelected(position == currentPageIndex);
            if (holder.label != null) holder.label.setText(String.valueOf(position + 1));
            try {
                holder.itemView.setContentDescription(ctx.getString(R.string.thumbnail_item_content_description, position + 1, pageCount));
            } catch (Throwable ignore) {
            }

            holder.itemView.setOnClickListener(v -> {
                try { onSelectPage.onSelectPage(position); } catch (Throwable ignore) {}
            });

            bindThumbnail(holder, position);
        }

        @Override
        public int getItemCount() {
            return pageCount;
        }

        void release() {
            released = true;
            try {
                for (MuPDFCore.Cookie cookie : inFlightCookies.values()) {
                    if (cookie != null) cookie.abort();
                }
            } catch (Throwable ignore) {
            }
            try { executor.shutdownNow(); } catch (Throwable ignore) {}
            try { inFlight.clear(); } catch (Throwable ignore) {}
            try { inFlightCookies.clear(); } catch (Throwable ignore) {}
            try { cache.evictAll(); } catch (Throwable ignore) {}
        }

        private void bindThumbnail(@NonNull Holder holder, int pageIndex) {
            if (released || holder.thumbnail == null) return;

            Bitmap cached = cache.get(pageIndex);
            if (cached != null) {
                applyBitmap(holder, cached);
                return;
            }

            holder.thumbnail.setImageDrawable(null);
            int defaultHeight = Math.max(1, Math.round(thumbnailWidthPx * 1.4f));
            ViewGroup.LayoutParams lp = holder.thumbnail.getLayoutParams();
            if (lp != null && lp.height != defaultHeight) {
                lp.height = defaultHeight;
                holder.thumbnail.setLayoutParams(lp);
            }

            if (!inFlight.add(pageIndex)) return;
            executor.execute(() -> {
                RenderedThumbnail rendered = null;
                try {
                    rendered = renderThumbnail(pageIndex);
                } catch (Throwable ignore) {
                    rendered = null;
                } finally {
                    try { inFlight.remove(pageIndex); } catch (Throwable ignore) {}
                }
                if (released || rendered == null || rendered.bitmap == null) return;
                cache.put(pageIndex, rendered.bitmap);
                try {
                    holder.itemView.post(() -> {
                        if (released) return;
                        if (holder.boundPageIndex != pageIndex) return;
                        Bitmap latest = cache.get(pageIndex);
                        if (latest != null) applyBitmap(holder, latest);
                    });
                } catch (Throwable ignore) {
                }
            });
        }

        private void applyBitmap(@NonNull Holder holder, @NonNull Bitmap bitmap) {
            if (holder.thumbnail == null) return;
            ViewGroup.LayoutParams lp = holder.thumbnail.getLayoutParams();
            if (lp != null) {
                lp.height = bitmap.getHeight();
                holder.thumbnail.setLayoutParams(lp);
            }
            holder.thumbnail.setImageBitmap(bitmap);
        }

        @Nullable
        private RenderedThumbnail renderThumbnail(int pageIndex) {
            if (pageIndex < 0 || pageIndex >= pageCount) return null;

            float ratio = 1.4f;
            try {
                PointF size = repo.getPageSize(pageIndex);
                if (size != null && size.x > 0 && size.y > 0) ratio = size.y / size.x;
            } catch (Throwable ignore) {
                ratio = 1.4f;
            }

            int w = thumbnailWidthPx;
            int h = Math.max(1, Math.round(w * ratio));
            Bitmap bm;
            try {
                bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            } catch (Throwable ignore) {
                return null;
            }

            MuPDFCore.Cookie cookie = repo.newRenderCookie();
            inFlightCookies.put(pageIndex, cookie);
            try {
                repo.drawPage(bm, pageIndex, w, h, 0, 0, w, h, cookie);
                if (cookie.aborted()) {
                    try { bm.recycle(); } catch (Throwable ignore) {}
                    return null;
                }
            } finally {
                inFlightCookies.remove(pageIndex);
                try { cookie.destroy(); } catch (Throwable ignore) {}
            }
            return new RenderedThumbnail(bm);
        }

        private static final class RenderedThumbnail {
            final Bitmap bitmap;
            RenderedThumbnail(@NonNull Bitmap bitmap) { this.bitmap = bitmap; }
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final @Nullable ImageView thumbnail;
            final @Nullable TextView label;
            int boundPageIndex = -1;

            Holder(@NonNull View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.page_thumbnail_image);
                label = itemView.findViewById(R.id.page_thumbnail_label);
            }
        }
    }
}
