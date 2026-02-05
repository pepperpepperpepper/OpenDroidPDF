package org.opendroidpdf.app.document;

import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;

import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.OutlineItem;
import org.opendroidpdf.R;
import org.opendroidpdf.app.bookmarks.BookmarksStore;
import org.opendroidpdf.app.bookmarks.DocumentBookmark;
import org.opendroidpdf.app.bookmarks.SQLiteBookmarksStore;
import org.opendroidpdf.app.epub.EpubTocParser;

import java.util.ArrayList;
import java.util.List;

/** Bottom-sheet tabbed UI: Bookmarks + Table of Contents. */
final class BookmarksTocUi {
    static final int TAB_BOOKMARKS = 0;
    static final int TAB_TOC = 1;

    private BookmarksTocUi() {}

    static void show(@NonNull AppCompatActivity activity,
                     @NonNull MuPDFReaderView docView,
                     @NonNull OpenDroidPDFCore core,
                     @Nullable DocumentIdentity ident,
                     int initialTab) {
        final String docId = resolveDocId(core, ident);
        final String legacyDocId = ident != null ? ident.legacyDocId() : docId;
        final BookmarksStore store = new SQLiteBookmarksStore(activity.getApplicationContext());
        try { store.migrateDocId(legacyDocId, docId); } catch (Throwable ignore) {}

        int pageCount = 0;
        try {
            android.widget.Adapter adapter = docView.getAdapter();
            pageCount = adapter != null ? adapter.getCount() : 0;
        } catch (Throwable ignore) {
            pageCount = 0;
        }

        final BottomSheetDialog dialog = new BottomSheetDialog(activity, R.style.OpenDroidPDFBottomSheetDialogTheme);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_bookmarks_toc_sheet, null);
        dialog.setContentView(root);

        final View bookmarksContainer = root.findViewById(R.id.bookmarks_tab_container);
        final View tocContainer = root.findViewById(R.id.toc_tab_container);
        final TextView addBookmark = root.findViewById(R.id.bookmarks_add_action);
        final TextView bookmarksEmpty = root.findViewById(R.id.bookmarks_empty);
        final TextView tocEmpty = root.findViewById(R.id.toc_empty);

        final RecyclerView bookmarksRecycler = root.findViewById(R.id.bookmarks_recycler);
        final BookmarksAdapter bookmarksAdapter = new BookmarksAdapter(activity, docView, dialog, store, docId, pageCount);
        bookmarksRecycler.setLayoutManager(new LinearLayoutManager(activity));
        bookmarksRecycler.setAdapter(bookmarksAdapter);

        final RecyclerView tocRecycler = root.findViewById(R.id.toc_recycler);
        final TocAdapter tocAdapter = new TocAdapter(activity, docView, dialog);
        tocRecycler.setLayoutManager(new LinearLayoutManager(activity));
        tocRecycler.setAdapter(tocAdapter);

        final Runnable refreshBookmarks = () -> {
            List<DocumentBookmark> items;
            try { items = store.list(docId); } catch (Throwable t) { items = new ArrayList<>(); }
            bookmarksAdapter.setItems(items);
            boolean empty = items == null || items.isEmpty();
            if (bookmarksEmpty != null) bookmarksEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (bookmarksRecycler != null) bookmarksRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        };

        final Runnable refreshToc = () -> {
            List<TocRow> rows = buildTocRows(core);
            tocAdapter.setItems(rows);
            boolean empty = rows == null || rows.isEmpty();
            if (tocEmpty != null) tocEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (tocRecycler != null) tocRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        };

        refreshBookmarks.run();
        refreshToc.run();
        bookmarksAdapter.setOnDataChanged(refreshBookmarks);

        if (addBookmark != null) {
            addBookmark.setOnClickListener(v -> {
                int pageIndex = 0;
                try { pageIndex = Math.max(0, docView.getSelectedItemPosition()); } catch (Throwable ignore) { pageIndex = 0; }
                String title;
                try {
                    title = activity.getString(R.string.bookmark_default_title, pageIndex + 1);
                } catch (Throwable ignore) {
                    title = "Page " + (pageIndex + 1);
                }
                try {
                    store.insert(docId, pageIndex, title);
                } catch (Throwable ignore) {
                }
                refreshBookmarks.run();
                try {
                    int count = bookmarksAdapter.getItemCount();
                    if (count > 0) {
                        int target = Math.max(0, Math.min(count - 1, bookmarksAdapter.findLastIndexForPage(pageIndex)));
                        bookmarksRecycler.scrollToPosition(target);
                    }
                } catch (Throwable ignore) {
                }
            });
        }

        final TabLayout tabs = root.findViewById(R.id.bookmarks_toc_tabs);
        if (tabs != null) {
            tabs.addTab(tabs.newTab().setText(R.string.menu_bookmarks));
            tabs.addTab(tabs.newTab().setText(R.string.menu_toc));

            tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    int pos = tab != null ? tab.getPosition() : 0;
                    setTabVisibility(pos, bookmarksContainer, tocContainer, addBookmark);
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });

            int clamped = Math.max(0, Math.min(1, initialTab));
            TabLayout.Tab initial = tabs.getTabAt(clamped);
            if (initial != null) initial.select();
            setTabVisibility(clamped, bookmarksContainer, tocContainer, addBookmark);
        } else {
            setTabVisibility(TAB_BOOKMARKS, bookmarksContainer, tocContainer, addBookmark);
        }

        dialog.show();
    }

    private static void setTabVisibility(int tab,
                                         @Nullable View bookmarksContainer,
                                         @Nullable View tocContainer,
                                         @Nullable View addBookmark) {
        boolean showBookmarks = tab == TAB_BOOKMARKS;
        if (bookmarksContainer != null) bookmarksContainer.setVisibility(showBookmarks ? View.VISIBLE : View.GONE);
        if (tocContainer != null) tocContainer.setVisibility(showBookmarks ? View.GONE : View.VISIBLE);
        if (addBookmark != null) addBookmark.setVisibility(showBookmarks ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private static String resolveDocId(@NonNull OpenDroidPDFCore core, @Nullable DocumentIdentity ident) {
        if (ident != null) {
            String id = ident.docId();
            if (id != null && !id.trim().isEmpty()) return id;
        }
        try {
            android.net.Uri uri = core.getUri();
            if (uri != null) return DocumentIds.fromUri(uri);
        } catch (Throwable ignore) {
        }
        try {
            String path = core.getPath();
            if (path != null && !path.trim().isEmpty()) return path;
        } catch (Throwable ignore) {
        }
        return "unknown-doc";
    }

    @NonNull
    private static List<TocRow> buildTocRows(@NonNull OpenDroidPDFCore core) {
        DocumentType docType = DocumentType.OTHER;
        try { docType = DocumentType.fromFileFormat(core.fileFormat()); } catch (Throwable ignore) {}

        if (docType == DocumentType.EPUB) {
            String path = null;
            try { path = core.getPath(); } catch (Throwable ignore) {}
            if (path == null || path.trim().isEmpty()) return new ArrayList<>();
            List<EpubTocParser.TocEntry> toc = EpubTocParser.parseFromEpubPath(path);
            ArrayList<TocRow> rows = new ArrayList<>();
            for (EpubTocParser.TocEntry e : toc) {
                if (e == null) continue;
                int page = -1;
                try { page = core.resolveLinkPage(e.href); } catch (Throwable ignore) { page = -1; }
                if (page < 0) continue;
                String title = e.title != null ? e.title : "";
                rows.add(new TocRow(Math.max(0, e.level), title, page));
            }
            return rows;
        }

        try {
            OutlineItem[] outline = core.getOutline();
            if (outline == null || outline.length == 0) return new ArrayList<>();
            ArrayList<TocRow> rows = new ArrayList<>();
            for (OutlineItem it : outline) {
                if (it == null) continue;
                int page = it.page;
                if (page < 0) continue;
                String title = it.title != null ? it.title : "";
                rows.add(new TocRow(Math.max(0, it.level), title, page));
            }
            return rows;
        } catch (Throwable ignore) {
            return new ArrayList<>();
        }
    }

    private static final class TocRow {
        final int level;
        @NonNull final String title;
        final int pageIndex;

        TocRow(int level, @NonNull String title, int pageIndex) {
            this.level = Math.max(0, level);
            this.title = title != null ? title : "";
            this.pageIndex = Math.max(0, pageIndex);
        }
    }

    private static final class TocAdapter extends RecyclerView.Adapter<TocAdapter.Holder> {
        private final AppCompatActivity activity;
        private final MuPDFReaderView docView;
        private final BottomSheetDialog dialog;
        private final int indentPx;
        private List<TocRow> items = new ArrayList<>();

        TocAdapter(@NonNull AppCompatActivity activity, @NonNull MuPDFReaderView docView, @NonNull BottomSheetDialog dialog) {
            this.activity = activity;
            this.docView = docView;
            this.dialog = dialog;
            this.indentPx = dpToPx(activity, 12);
        }

        void setItems(@NonNull List<TocRow> next) {
            items = next != null ? next : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_toc_entry, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final TocRow row = items.get(position);
            if (holder.title != null) holder.title.setText(row.title);
            if (holder.page != null) holder.page.setText(String.valueOf(row.pageIndex + 1));

            if (holder.title != null) {
                ViewGroup.LayoutParams lp = holder.title.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).setMarginStart(Math.max(0, row.level) * indentPx);
                    holder.title.setLayoutParams(lp);
                }
            }

            holder.itemView.setOnClickListener(v -> {
                try { dialog.dismiss(); } catch (Throwable ignore) {}
                try {
                    docView.setDisplayedViewIndex(row.pageIndex, true);
                    docView.setNormalizedScroll(0.0f, 0.0f);
                } catch (Throwable ignore) {
                }
                try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
            });
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        static final class Holder extends RecyclerView.ViewHolder {
            @Nullable final TextView title;
            @Nullable final TextView page;

            Holder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.toc_item_title);
                page = itemView.findViewById(R.id.toc_item_page);
            }
        }
    }

    private static final class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.Holder> {
        private final AppCompatActivity activity;
        private final Context ctx;
        private final MuPDFReaderView docView;
        private final BottomSheetDialog dialog;
        private final BookmarksStore store;
        private final String docId;
        private final int pageCount;
        private Runnable onDataChanged = () -> {};
        private List<DocumentBookmark> items = new ArrayList<>();

        BookmarksAdapter(@NonNull AppCompatActivity activity,
                         @NonNull MuPDFReaderView docView,
                         @NonNull BottomSheetDialog dialog,
                         @NonNull BookmarksStore store,
                         @NonNull String docId,
                         int pageCount) {
            this.activity = activity;
            this.ctx = activity;
            this.docView = docView;
            this.dialog = dialog;
            this.store = store;
            this.docId = docId;
            this.pageCount = Math.max(0, pageCount);
        }

        void setItems(@NonNull List<DocumentBookmark> next) {
            items = next != null ? next : new ArrayList<>();
            notifyDataSetChanged();
        }

        void setOnDataChanged(@NonNull Runnable onDataChanged) {
            this.onDataChanged = onDataChanged != null ? onDataChanged : () -> {};
        }

        int findLastIndexForPage(int pageIndex) {
            int idx = -1;
            for (int i = 0; i < items.size(); i++) {
                DocumentBookmark b = items.get(i);
                if (b != null && b.pageIndex == pageIndex) idx = i;
            }
            return idx >= 0 ? idx : Math.max(0, items.size() - 1);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_document_bookmark, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            final DocumentBookmark bm = items.get(position);
            if (bm == null) return;
            if (holder.title != null) holder.title.setText(bm.title);

            String pageLabel;
            try {
                pageLabel = ctx.getString(R.string.bookmark_page_label, bm.pageIndex + 1, Math.max(1, pageCount));
            } catch (Throwable ignore) {
                pageLabel = "Page " + (bm.pageIndex + 1);
            }
            if (holder.page != null) holder.page.setText(pageLabel);

            holder.itemView.setOnClickListener(v -> {
                try { dialog.dismiss(); } catch (Throwable ignore) {}
                try {
                    docView.setDisplayedViewIndex(bm.pageIndex, true);
                    docView.setNormalizedScroll(0.0f, 0.0f);
                } catch (Throwable ignore) {
                }
                try { activity.invalidateOptionsMenu(); } catch (Throwable ignore) {}
            });

            if (holder.overflow != null) {
                try {
                    holder.overflow.setContentDescription(ctx.getString(R.string.bookmark_item_overflow_content_description, bm.pageIndex + 1));
                } catch (Throwable ignore) {
                }
                holder.overflow.setOnClickListener(v -> showOverflowMenu(holder.overflow, bm));
            }
        }

        private void showOverflowMenu(@NonNull View anchor, @NonNull DocumentBookmark bm) {
            PopupMenu menu = new PopupMenu(ctx, anchor);
            menu.inflate(R.menu.bookmark_item_menu);
            menu.setOnMenuItemClickListener(item -> onMenuItem(item, bm));
            menu.show();
        }

        private boolean onMenuItem(@NonNull MenuItem item, @NonNull DocumentBookmark bm) {
            int id = item.getItemId();
            if (id == R.id.menu_bookmark_rename) {
                showRenameDialog(bm);
                return true;
            }
            if (id == R.id.menu_bookmark_delete) {
                try { store.delete(docId, bm.id); } catch (Throwable ignore) {}
                try { onDataChanged.run(); } catch (Throwable ignore) {}
                return true;
            }
            return false;
        }

        private void showRenameDialog(@NonNull DocumentBookmark bm) {
            final EditText input = new EditText(activity);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setText(bm.title);
            input.setSelection(Math.max(0, input.getText().length()));
            input.setHint(R.string.bookmark_rename_hint);

            AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setTitle(R.string.bookmark_rename_title);
            b.setView(input);
            b.setPositiveButton(android.R.string.ok, (d, w) -> {
                String title = null;
                try { title = input.getText() != null ? input.getText().toString() : null; } catch (Throwable ignore) {}
                if (title == null) return;
                title = title.trim();
                if (title.isEmpty()) return;
                try { store.rename(docId, bm.id, title); } catch (Throwable ignore) {}
                try { onDataChanged.run(); } catch (Throwable ignore) {}
            });
            b.setNegativeButton(android.R.string.cancel, (d, w) -> {});
            b.show();
        }

        @Override
        public int getItemCount() {
            return items != null ? items.size() : 0;
        }

        static final class Holder extends RecyclerView.ViewHolder {
            @Nullable final TextView title;
            @Nullable final TextView page;
            @Nullable final ImageButton overflow;

            Holder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.bookmark_item_title);
                page = itemView.findViewById(R.id.bookmark_item_page);
                overflow = itemView.findViewById(R.id.bookmark_item_overflow);
            }
        }
    }

    private static int dpToPx(@NonNull Context ctx, int dp) {
        float density = 1f;
        try { density = ctx.getResources().getDisplayMetrics().density; } catch (Throwable ignore) { density = 1f; }
        return Math.max(1, Math.round(dp * density));
    }
}
