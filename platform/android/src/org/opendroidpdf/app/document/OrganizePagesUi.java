package org.opendroidpdf.app.document;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.opendroidpdf.MuPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.app.helpers.RequestCodes;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class OrganizePagesUi {

    interface BoolProvider {
        boolean get();
    }

    interface StringConsumer {
        void accept(@NonNull String value);
    }

    interface IntConsumer {
        void accept(int value);
    }

    private final OrganizePagesController.Host host;

    private @Nullable BottomSheetDialog sheetDialog;
    private @Nullable View doneAction;

    OrganizePagesUi(@NonNull OrganizePagesController.Host host) {
        this.host = host;
    }

    void showSheet(@NonNull Runnable onExtract,
                   @NonNull Runnable onMerge,
                   @NonNull Runnable onInsertBlank,
                   @NonNull Runnable onInsertFromPdf,
                   @NonNull Runnable onRemove,
                   @NonNull Runnable onReorder,
                   @NonNull Runnable onRotate,
                   @NonNull Runnable onDone,
                   @NonNull BoolProvider hasStagedChanges,
                   @NonNull Runnable onDiscardConfirmed,
                   @NonNull Runnable onDismissed) {
        AppCompatActivity activity = host.getActivity();
        if (activity == null) return;

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_organize_pages_sheet, null);
        dialog.setContentView(root);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        sheetDialog = dialog;

        View extract = root.findViewById(R.id.organize_pages_action_extract);
        if (extract != null) extract.setOnClickListener(v -> onExtract.run());

        View merge = root.findViewById(R.id.organize_pages_action_merge);
        if (merge != null) merge.setOnClickListener(v -> onMerge.run());

        View insertBlank = root.findViewById(R.id.organize_pages_action_insert_blank);
        if (insertBlank != null) insertBlank.setOnClickListener(v -> onInsertBlank.run());

        View insertFromPdf = root.findViewById(R.id.organize_pages_action_insert_from_pdf);
        if (insertFromPdf != null) insertFromPdf.setOnClickListener(v -> onInsertFromPdf.run());

        View remove = root.findViewById(R.id.organize_pages_action_remove);
        if (remove != null) remove.setOnClickListener(v -> onRemove.run());

        View reorder = root.findViewById(R.id.organize_pages_action_reorder);
        if (reorder != null) reorder.setOnClickListener(v -> onReorder.run());

        View rotate = root.findViewById(R.id.organize_pages_action_rotate);
        if (rotate != null) rotate.setOnClickListener(v -> onRotate.run());

        View cancel = root.findViewById(R.id.organize_pages_action_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(v -> confirmDiscardAndClose(dialog, hasStagedChanges, onDiscardConfirmed));
        }

        View done = root.findViewById(R.id.organize_pages_action_done);
        doneAction = done;
        updateDoneEnabledState(hasStagedChanges.get());
        if (done != null) done.setOnClickListener(v -> onDone.run());

        dialog.setOnDismissListener(d -> {
            if (sheetDialog == dialog) sheetDialog = null;
            doneAction = null;
            onDismissed.run();
        });

        dialog.show();
    }

    boolean dismissSheetIfShown() {
        BottomSheetDialog dialog = sheetDialog;
        if (dialog == null) return false;
        dialog.dismiss();
        return true;
    }

    void updateDoneEnabledState(boolean hasStagedChanges) {
        View done = doneAction;
        if (done == null) return;
        done.setEnabled(hasStagedChanges);
        done.setAlpha(hasStagedChanges ? 1f : 0.38f);
    }

    void promptMergeAppend() {
        final Context ctx = host.getContext();
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_prompt_merge_title)
                .setMessage(R.string.organize_pages_prompt_merge_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.okay, (d, w) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(DocumentAccessIntents.MIME_PDF);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    host.startActivityForResult(intent, RequestCodes.ORGANIZE_PAGES_PICK_MERGE);
                })
                .show();
    }

    void promptRotatePages(@NonNull StringConsumer onValidRotateExpr) {
        final Context ctx = host.getContext();

        final LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        container.setPadding(pad, pad, pad, pad);

        final EditText pagesField = new EditText(ctx);
        pagesField.setHint(R.string.organize_pages_prompt_pages_hint);
        pagesField.setInputType(InputType.TYPE_CLASS_TEXT);
        pagesField.setSingleLine();
        container.addView(pagesField);

        final RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.VERTICAL);

        RadioButton cw = new RadioButton(ctx);
        cw.setId(View.generateViewId());
        cw.setText(R.string.organize_pages_rotate_cw_90);
        rg.addView(cw);

        RadioButton d180 = new RadioButton(ctx);
        d180.setId(View.generateViewId());
        d180.setText(R.string.organize_pages_rotate_180);
        rg.addView(d180);

        RadioButton ccw = new RadioButton(ctx);
        ccw.setId(View.generateViewId());
        ccw.setText(R.string.organize_pages_rotate_ccw_90);
        rg.addView(ccw);

        rg.check(cw.getId());
        container.addView(rg);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_prompt_rotate_title)
                .setMessage(R.string.organize_pages_prompt_pages)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_accept, (d, w) -> {
                    String pages = pagesField.getText() != null ? pagesField.getText().toString().trim() : "";
                    if (pages.isEmpty()) pages = "1-z";

                    int checked = rg.getCheckedRadioButtonId();
                    String rotatePrefix;
                    if (checked == ccw.getId()) {
                        rotatePrefix = "-90:";
                    } else if (checked == d180.getId()) {
                        rotatePrefix = "+180:";
                    } else {
                        rotatePrefix = "+90:";
                    }

                    String expr = rotatePrefix + pages;
                    onValidRotateExpr.accept(expr);
                })
                .show();
    }

    void promptForPageSpec(int titleRes, int positiveRes, @NonNull final StringConsumer onValid) {
        final Context ctx = host.getContext();
        final EditText field = new EditText(ctx);
        field.setHint(R.string.organize_pages_prompt_pages_hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setSingleLine();

        final LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (ctx.getResources().getDisplayMetrics().density * 16);
        container.setPadding(pad, pad, pad, pad);
        container.addView(field);

        new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setMessage(R.string.organize_pages_prompt_pages)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(positiveRes, (d, w) -> {
                    String spec = field.getText() != null ? field.getText().toString().trim() : "";
                    if (spec.isEmpty()) {
                        host.showInfo(ctx.getString(R.string.organize_pages_error_pages_required));
                        return;
                    }
                    onValid.accept(spec);
                })
                .show();
    }

    void showReorderDialog(@NonNull MuPdfRepository repo, int pageCount, @NonNull StringConsumer onAcceptSpec) {
        final Context ctx = host.getContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_reorder_pages, null, false);
        RecyclerView recycler = root.findViewById(R.id.reorder_pages_recycler);
        if (recycler == null) {
            host.showInfo(ctx.getString(R.string.not_supported));
            return;
        }

        final ReorderPagesAdapter adapter = new ReorderPagesAdapter(ctx, repo, pageCount);
        recycler.setLayoutManager(new LinearLayoutManager(ctx));
        recycler.setAdapter(adapter);

        ItemTouchHelper helper = new ItemTouchHelper(adapter.touchCallback());
        helper.attachToRecyclerView(recycler);
        adapter.setItemTouchHelper(helper);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_prompt_reorder_title)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_accept, (d, w) -> onAcceptSpec.accept(adapter.buildPageSpec()))
                .create();
        dialog.setOnDismissListener(d -> adapter.release());
        dialog.show();
    }

    void promptForInsertPosition(int titleRes,
                                 @NonNull MuPdfRepository repo,
                                 int pageCount,
                                 @NonNull final IntConsumer onValid) {
        final Context ctx = host.getContext();
        View root = LayoutInflater.from(ctx).inflate(R.layout.dialog_insert_position, null, false);
        RecyclerView recycler = root.findViewById(R.id.insert_position_recycler);
        TextView help = root.findViewById(R.id.insert_position_help);
        if (help != null) {
            help.setText(ctx.getString(R.string.organize_pages_insert_position_help, pageCount + 1));
        }
        if (recycler == null) {
            host.showInfo(ctx.getString(R.string.not_supported));
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(titleRes)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .create();

        final InsertPositionAdapter adapter = new InsertPositionAdapter(ctx, repo, pageCount, value -> {
            dialog.dismiss();
            onValid.accept(value);
        });
        recycler.setLayoutManager(new LinearLayoutManager(ctx));
        recycler.setAdapter(adapter);

        dialog.setOnDismissListener(d -> adapter.release());
        dialog.show();
    }

    private void confirmDiscardAndClose(@NonNull BottomSheetDialog dialog,
                                        @NonNull BoolProvider hasStagedChanges,
                                        @NonNull Runnable onDiscardConfirmed) {
        if (!hasStagedChanges.get()) {
            dialog.dismiss();
            return;
        }
        final Context ctx = host.getContext();
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.organize_pages_discard_title)
                .setMessage(R.string.organize_pages_discard_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_discard, (d, w) -> {
                    onDiscardConfirmed.run();
                    dialog.dismiss();
                })
                .show();
    }

    private static final class ReorderPagesAdapter extends RecyclerView.Adapter<ReorderPagesAdapter.Holder> {
        private static final int THUMBNAIL_WIDTH_DP = 56;
        private static final int THUMBNAIL_CACHE_SIZE = 32;

        private final MuPdfRepository repo;
        private final List<Integer> pages;
        private @Nullable ItemTouchHelper helper;
        private final int thumbnailWidthPx;
        private final android.util.LruCache<Integer, Bitmap> thumbnailCache;
        private final ExecutorService thumbExecutor;
        private final Set<Integer> inFlight = Collections.synchronizedSet(new HashSet<>());
        private volatile boolean released;

        ReorderPagesAdapter(@NonNull Context ctx, @NonNull MuPdfRepository repo, int pageCount) {
            this.repo = repo;
            List<Integer> out = new ArrayList<>(Math.max(0, pageCount));
            for (int i = 1; i <= pageCount; i++) out.add(i);
            this.pages = out;

            float density = 1f;
            try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
            thumbnailWidthPx = Math.max(1, Math.round(density * THUMBNAIL_WIDTH_DP));
            thumbnailCache = new android.util.LruCache<Integer, Bitmap>(THUMBNAIL_CACHE_SIZE) {
                @Override protected int sizeOf(@NonNull Integer key, @NonNull Bitmap value) {
                    return 1;
                }
            };
            thumbExecutor = Executors.newSingleThreadExecutor();
        }

        void setItemTouchHelper(@NonNull ItemTouchHelper helper) {
            this.helper = helper;
        }

        @NonNull ItemTouchHelper.Callback touchCallback() {
            return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
                @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    int from = viewHolder.getBindingAdapterPosition();
                    int to = target.getBindingAdapterPosition();
                    if (from < 0 || to < 0 || from == to) return false;
                    Collections.swap(pages, from, to);
                    notifyItemMoved(from, to);
                    return true;
                }

                @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }

                @Override public boolean isLongPressDragEnabled() { return false; }
                @Override public boolean isItemViewSwipeEnabled() { return false; }
            };
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reorder_page, parent, false);
            return new Holder(v);
        }

        @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
            Integer page = pages.get(position);
            holder.boundPage = page != null ? page : -1;
            if (holder.label != null) {
                holder.label.setText(holder.itemView.getContext().getString(R.string.organize_pages_page_label, page));
            }
            bindThumbnail(holder, page);
            if (holder.handle != null) {
                holder.handle.setOnTouchListener((v, event) -> {
                    if (event != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                        ItemTouchHelper h = helper;
                        if (h != null) {
                            h.startDrag(holder);
                            return true;
                        }
                    }
                    return false;
                });
            }
        }

        @Override public int getItemCount() {
            return pages.size();
        }

        void release() {
            released = true;
            try { thumbExecutor.shutdownNow(); } catch (Throwable ignore) {}
            try { thumbnailCache.evictAll(); } catch (Throwable ignore) {}
            try { inFlight.clear(); } catch (Throwable ignore) {}
        }

        private void bindThumbnail(@NonNull Holder holder, @Nullable Integer page1Based) {
            if (released) return;
            if (holder.thumbnail == null || page1Based == null) return;
            Bitmap cached = thumbnailCache.get(page1Based);
            if (cached != null) {
                holder.thumbnail.setImageBitmap(cached);
                return;
            }
            holder.thumbnail.setImageDrawable(null);
            if (!inFlight.add(page1Based)) return;
            thumbExecutor.execute(() -> {
                Bitmap bm = null;
                try {
                    bm = renderThumbnail(page1Based);
                } catch (Throwable ignore) {
                    bm = null;
                } finally {
                    try { inFlight.remove(page1Based); } catch (Throwable ignore) {}
                }
                if (released || bm == null) return;
                thumbnailCache.put(page1Based, bm);
                try {
                    holder.itemView.post(() -> {
                        if (released) return;
                        if (holder.boundPage != page1Based) return;
                        Bitmap latest = thumbnailCache.get(page1Based);
                        if (latest != null && holder.thumbnail != null) {
                            holder.thumbnail.setImageBitmap(latest);
                        }
                    });
                } catch (Throwable ignore) {
                }
            });
        }

        @Nullable
        private Bitmap renderThumbnail(int page1Based) {
            int pageIndex = page1Based - 1;
            if (pageIndex < 0) return null;
            PointF size = null;
            try { size = repo.getPageSize(pageIndex); } catch (Throwable ignore) { size = null; }
            float ratio = 1.294f;
            if (size != null && size.x > 0 && size.y > 0) {
                ratio = size.y / size.x;
            }
            int w = thumbnailWidthPx;
            int h = Math.max(1, Math.round(w * ratio));
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            MuPDFCore.Cookie cookie = repo.newRenderCookie();
            try {
                repo.drawPage(bm, pageIndex, w, h, 0, 0, w, h, cookie);
            } finally {
                try { cookie.destroy(); } catch (Throwable ignore) {}
            }
            return bm;
        }

        @NonNull String buildPageSpec() {
            if (pages.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pages.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(pages.get(i));
            }
            return sb.toString();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final @Nullable TextView label;
            final @Nullable ImageView thumbnail;
            final @Nullable View handle;
            int boundPage = -1;

            Holder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.reorder_page_label);
                thumbnail = itemView.findViewById(R.id.reorder_page_thumbnail);
                handle = itemView.findViewById(R.id.reorder_page_handle);
            }
        }
    }

    private static final class InsertPositionAdapter extends RecyclerView.Adapter<InsertPositionAdapter.Holder> {
        private static final int THUMBNAIL_WIDTH_DP = 56;
        private static final int THUMBNAIL_CACHE_SIZE = 32;

        private final Context ctx;
        private final MuPdfRepository repo;
        private final int pageCount;
        private final int thumbnailWidthPx;
        private final android.util.LruCache<Integer, Bitmap> thumbnailCache;
        private final ExecutorService thumbExecutor;
        private final Set<Integer> inFlight = Collections.synchronizedSet(new HashSet<>());
        private final IntConsumer onSelected;
        private volatile boolean released;

        InsertPositionAdapter(@NonNull Context ctx,
                              @NonNull MuPdfRepository repo,
                              int pageCount,
                              @NonNull IntConsumer onSelected) {
            this.ctx = ctx;
            this.repo = repo;
            this.pageCount = Math.max(0, pageCount);
            this.onSelected = onSelected;

            float density = 1f;
            try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
            thumbnailWidthPx = Math.max(1, Math.round(density * THUMBNAIL_WIDTH_DP));
            thumbnailCache = new android.util.LruCache<Integer, Bitmap>(THUMBNAIL_CACHE_SIZE) {
                @Override protected int sizeOf(@NonNull Integer key, @NonNull Bitmap value) {
                    return 1;
                }
            };
            thumbExecutor = Executors.newSingleThreadExecutor();
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_insert_position, parent, false);
            return new Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            int insertBeforePage = position + 1;
            holder.insertBeforePage = insertBeforePage;

            int page1Based;
            String label;
            if (insertBeforePage <= pageCount) {
                page1Based = insertBeforePage;
                label = ctx.getString(R.string.organize_pages_insert_before_page_label, insertBeforePage);
            } else {
                page1Based = Math.max(1, pageCount);
                label = ctx.getString(R.string.organize_pages_insert_at_end_label, pageCount);
            }

            if (holder.label != null) holder.label.setText(label);
            holder.boundThumbnailPage = page1Based;
            bindThumbnail(holder, page1Based);

            holder.itemView.setOnClickListener(v -> {
                if (released) return;
                onSelected.accept(holder.insertBeforePage);
            });
        }

        @Override public int getItemCount() {
            return pageCount + 1;
        }

        void release() {
            released = true;
            try { thumbExecutor.shutdownNow(); } catch (Throwable ignore) {}
            try { thumbnailCache.evictAll(); } catch (Throwable ignore) {}
            try { inFlight.clear(); } catch (Throwable ignore) {}
        }

        private void bindThumbnail(@NonNull Holder holder, int page1Based) {
            if (released) return;
            if (holder.thumbnail == null) return;
            Bitmap cached = thumbnailCache.get(page1Based);
            if (cached != null) {
                holder.thumbnail.setImageBitmap(cached);
                return;
            }
            holder.thumbnail.setImageDrawable(null);
            if (!inFlight.add(page1Based)) return;
            thumbExecutor.execute(() -> {
                Bitmap bm = null;
                try {
                    bm = renderThumbnail(page1Based);
                } catch (Throwable ignore) {
                    bm = null;
                } finally {
                    try { inFlight.remove(page1Based); } catch (Throwable ignore) {}
                }
                if (released || bm == null) return;
                thumbnailCache.put(page1Based, bm);
                try {
                    holder.itemView.post(() -> {
                        if (released) return;
                        if (holder.boundThumbnailPage != page1Based) return;
                        Bitmap latest = thumbnailCache.get(page1Based);
                        if (latest != null && holder.thumbnail != null) {
                            holder.thumbnail.setImageBitmap(latest);
                        }
                    });
                } catch (Throwable ignore) {
                }
            });
        }

        @Nullable
        private Bitmap renderThumbnail(int page1Based) {
            int pageIndex = page1Based - 1;
            if (pageIndex < 0) return null;
            PointF size = null;
            try { size = repo.getPageSize(pageIndex); } catch (Throwable ignore) { size = null; }
            float ratio = 1.294f;
            if (size != null && size.x > 0 && size.y > 0) {
                ratio = size.y / size.x;
            }
            int w = thumbnailWidthPx;
            int h = Math.max(1, Math.round(w * ratio));
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            MuPDFCore.Cookie cookie = repo.newRenderCookie();
            try {
                repo.drawPage(bm, pageIndex, w, h, 0, 0, w, h, cookie);
            } finally {
                try { cookie.destroy(); } catch (Throwable ignore) {}
            }
            return bm;
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final @Nullable TextView label;
            final @Nullable ImageView thumbnail;
            int insertBeforePage;
            int boundThumbnailPage;

            Holder(@NonNull View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.insert_position_label);
                thumbnail = itemView.findViewById(R.id.insert_position_thumbnail);
            }
        }
    }
}

