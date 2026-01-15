package org.opendroidpdf.app.comments;

import android.graphics.PointF;
import android.graphics.RectF;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.MuPDFReaderView;
import org.opendroidpdf.R;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.app.selection.SidecarSelectionController;
import org.opendroidpdf.app.sidecar.SidecarAnnotationProvider;
import org.opendroidpdf.app.sidecar.SidecarAnnotationSession;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;
import org.opendroidpdf.core.MuPdfRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import kotlinx.coroutines.Job;

/**
 * Minimal “Comments list” UI for jumping between comment-style annotations.
 *
 * <p>Scope: embedded PDF annotations (MuPDF) + sidecar annotations (EPUB / read-only PDFs).</p>
 */
public final class CommentsListController {

    private enum Backend { EMBEDDED, SIDECAR }

    private enum Bucket { NOTE, TEXT_BOX, MARKUP, INK, OTHER }

    private enum Filter {
        ALL(R.string.comments_filter_all),
        NOTES(R.string.comments_filter_notes),
        TEXT_BOXES(R.string.comments_filter_text_boxes),
        MARKUPS(R.string.comments_filter_markups),
        INK(R.string.comments_filter_ink);

        final int labelRes;

        Filter(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    private static final class CommentEntry {
        final Backend backend;
        final Bucket bucket;
        final int pageIndex;
        @NonNull final RectF boundsDoc;
        final long createdAtEpochMs;

        @Nullable final Annotation.Type annotType;
        final long embeddedObjectNumber;

        @Nullable final SidecarSelectionController.Kind sidecarKind;
        @Nullable final String sidecarId;

        @Nullable final String searchText;

        CommentEntry(@NonNull Backend backend,
                     @NonNull Bucket bucket,
                     int pageIndex,
                     @NonNull RectF boundsDoc,
                     long createdAtEpochMs,
                     @Nullable Annotation.Type annotType,
                     long embeddedObjectNumber,
                     @Nullable SidecarSelectionController.Kind sidecarKind,
                     @Nullable String sidecarId,
                     @Nullable String searchText) {
            this.backend = backend;
            this.bucket = bucket;
            this.pageIndex = pageIndex;
            this.boundsDoc = boundsDoc;
            this.createdAtEpochMs = createdAtEpochMs;
            this.annotType = annotType;
            this.embeddedObjectNumber = embeddedObjectNumber;
            this.sidecarKind = sidecarKind;
            this.sidecarId = sidecarId;
            this.searchText = searchText;
        }
    }

    public void show(@NonNull AppCompatActivity activity,
                     @NonNull MuPDFReaderView docView,
                     @NonNull MuPdfRepository repo,
                     @Nullable SidecarAnnotationProvider sidecarProvider) {
        Objects.requireNonNull(activity, "activity required");
        Objects.requireNonNull(docView, "docView required");
        Objects.requireNonNull(repo, "repo required");
        if (activity.isFinishing()) return;

        final View root = LayoutInflater.from(activity).inflate(R.layout.dialog_comments_list, null, false);
        final EditText search = root.findViewById(R.id.comments_search);
        final Spinner filter = root.findViewById(R.id.comments_type_filter);
        final ProgressBar loading = root.findViewById(R.id.comments_loading);
        final ListView list = root.findViewById(R.id.comments_list);
        final TextView empty = root.findViewById(R.id.comments_empty);

        final CommentsAdapter adapter = new CommentsAdapter(activity);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        final ArrayList<String> filterLabels = new ArrayList<>();
        for (Filter f : Filter.values()) {
            filterLabels.add(activity.getString(f.labelRes));
        }
        filter.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, filterLabels));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_comments)
                .setView(root)
                .setNegativeButton(R.string.dismiss, (d, w) -> {})
                .create();

        final AtomicReference<Job> loadJobRef = new AtomicReference<>(null);
        dialog.setOnDismissListener(d -> AppCoroutines.cancel(loadJobRef.getAndSet(null)));

        list.setOnItemClickListener((parent, view, position, id) -> {
            CommentEntry item = adapter.itemAt(position);
            if (item == null) return;
            dialog.dismiss();
            jumpTo(activity, docView, item);
        });

        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Filter next = Filter.ALL;
                try {
                    Filter[] vals = Filter.values();
                    if (position >= 0 && position < vals.length) next = vals[position];
                } catch (Throwable ignore) {
                }
                adapter.setFilter(next);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {
                adapter.setFilter(Filter.ALL);
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.setQuery(s != null ? s.toString() : null);
            }
        });

        dialog.show();

        loading.setVisibility(View.VISIBLE);
        list.setEnabled(false);
        filter.setEnabled(false);
        search.setEnabled(false);

        Job job = AppCoroutines.launchIo(AppCoroutines.ioScope(), () -> {
            final List<CommentEntry> loaded = loadComments(activity, repo, sidecarProvider);
            AppCoroutines.launchMain(AppCoroutines.mainScope(), () -> {
                if (!dialog.isShowing()) return;
                adapter.setItems(loaded);
                loading.setVisibility(View.GONE);
                list.setEnabled(true);
                filter.setEnabled(true);
                search.setEnabled(true);
            });
        });
        loadJobRef.set(job);
    }

    private static void jumpTo(@NonNull AppCompatActivity activity,
                               @NonNull MuPDFReaderView docView,
                               @NonNull CommentEntry entry) {
        if (entry.pageIndex < 0) return;

        docView.setDisplayedViewIndex(entry.pageIndex, true);
        RectF bounds = entry.boundsDoc;
        if (bounds != null) {
            docView.doNextScrollWithCenter();
            docView.setDocRelXScroll(bounds.centerX());
            docView.setDocRelYScroll(bounds.centerY());
            docView.resetupChildren();
        }

        // Selection is best-effort; page views are created lazily after the jump.
        scheduleSelectWithRetries(activity, docView, entry, 6);
    }

    private static void scheduleSelectWithRetries(@NonNull AppCompatActivity activity,
                                                  @NonNull MuPDFReaderView docView,
                                                  @NonNull CommentEntry entry,
                                                  int attemptsRemaining) {
        if (attemptsRemaining <= 0) return;
        AppCoroutines.launchMainDelayed(AppCoroutines.mainScope(), 80L, () -> {
            try {
                android.view.View v = docView.getSelectedView();
                if (!(v instanceof MuPDFPageView)) {
                    scheduleSelectWithRetries(activity, docView, entry, attemptsRemaining - 1);
                    return;
                }
                MuPDFPageView pv = (MuPDFPageView) v;
                if (pv.pageNumber() != entry.pageIndex) {
                    scheduleSelectWithRetries(activity, docView, entry, attemptsRemaining - 1);
                    return;
                }

                switch (entry.backend) {
                    case EMBEDDED:
                        if (entry.embeddedObjectNumber > 0L) {
                            pv.textAnnotationDelegate().selectEmbeddedAnnotationByObjectNumber(entry.embeddedObjectNumber);
                        }
                        break;
                    case SIDECAR:
                        if (entry.sidecarKind == SidecarSelectionController.Kind.NOTE && entry.sidecarId != null) {
                            pv.textAnnotationDelegate().selectSidecarNoteById(entry.sidecarId);
                        } else if (entry.sidecarKind == SidecarSelectionController.Kind.HIGHLIGHT && entry.sidecarId != null) {
                            pv.textAnnotationDelegate().selectSidecarHighlightById(entry.sidecarId);
                        }
                        break;
                }
            } catch (Throwable ignore) {
                scheduleSelectWithRetries(activity, docView, entry, attemptsRemaining - 1);
            }
        });
    }

    @NonNull
    private static List<CommentEntry> loadComments(@NonNull AppCompatActivity activity,
                                                   @NonNull MuPdfRepository repo,
                                                   @Nullable SidecarAnnotationProvider sidecarProvider) {
        final int pages;
        try {
            pages = Math.max(0, repo.getPageCount());
        } catch (Throwable t) {
            return Collections.emptyList();
        }

        final SidecarAnnotationSession sidecar =
                (sidecarProvider instanceof SidecarAnnotationSession) ? (SidecarAnnotationSession) sidecarProvider : null;

        ArrayList<CommentEntry> out = new ArrayList<>();
        for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
            if (sidecar != null) {
                try {
                    for (SidecarNote n : sidecar.notesForPage(pageIndex)) {
                        if (n == null || n.bounds == null || n.id == null) continue;
                        String text = n.text != null ? n.text : "";
                        out.add(new CommentEntry(
                                Backend.SIDECAR,
                                Bucket.NOTE,
                                pageIndex,
                                new RectF(n.bounds),
                                n.createdAtEpochMs,
                                Annotation.Type.TEXT,
                                -1L,
                                SidecarSelectionController.Kind.NOTE,
                                n.id,
                                text));
                    }
                } catch (Throwable ignore) {
                }

                try {
                    for (SidecarHighlight h : sidecar.highlightsForPage(pageIndex)) {
                        if (h == null || h.id == null || h.quadPoints == null || h.quadPoints.length < 4) continue;
                        RectF bounds = quadUnion(h.quadPoints);
                        if (bounds == null) continue;
                        String quote = h.quote != null ? h.quote : "";
                        out.add(new CommentEntry(
                                Backend.SIDECAR,
                                Bucket.MARKUP,
                                pageIndex,
                                bounds,
                                h.createdAtEpochMs,
                                h.type,
                                -1L,
                                SidecarSelectionController.Kind.HIGHLIGHT,
                                h.id,
                                quote));
                    }
                } catch (Throwable ignore) {
                }

                try {
                    for (SidecarInkStroke s : sidecar.inkStrokesForPage(pageIndex)) {
                        if (s == null || s.id == null || s.points == null || s.points.length < 2) continue;
                        RectF bounds = pointsBounds(s.points);
                        if (bounds == null) continue;
                        out.add(new CommentEntry(
                                Backend.SIDECAR,
                                Bucket.INK,
                                pageIndex,
                                bounds,
                                s.createdAtEpochMs,
                                Annotation.Type.INK,
                                -1L,
                                null,
                                s.id,
                                null));
                    }
                } catch (Throwable ignore) {
                }
            }

            Annotation[] annots;
            try {
                annots = repo.loadAnnotations(pageIndex);
            } catch (Throwable t) {
                annots = null;
            }
            if (annots == null || annots.length == 0) continue;
            for (Annotation a : annots) {
                if (a == null || a.type == null) continue;
                if (!isCommentType(a.type)) continue;
                RectF bounds = new RectF(a);
                String text = a.text != null ? a.text : "";
                out.add(new CommentEntry(
                        Backend.EMBEDDED,
                        bucketFor(a.type),
                        pageIndex,
                        bounds,
                        0L,
                        a.type,
                        a.objectNumber,
                        null,
                        null,
                        text));
            }
        }

        out.sort(new CommentSortComparator());
        return out;
    }

    private static final class CommentSortComparator implements Comparator<CommentEntry> {
        @Override
        public int compare(CommentEntry a, CommentEntry b) {
            if (a == b) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            if (a.pageIndex != b.pageIndex) return Integer.compare(a.pageIndex, b.pageIndex);
            float at = a.boundsDoc != null ? a.boundsDoc.top : 0f;
            float bt = b.boundsDoc != null ? b.boundsDoc.top : 0f;
            if (Math.abs(at - bt) > 0.001f) return Float.compare(at, bt);
            float al = a.boundsDoc != null ? a.boundsDoc.left : 0f;
            float bl = b.boundsDoc != null ? b.boundsDoc.left : 0f;
            return Float.compare(al, bl);
        }
    }

    private static boolean isCommentType(@NonNull Annotation.Type type) {
        switch (type) {
            case FREETEXT:
            case TEXT:
            case HIGHLIGHT:
            case UNDERLINE:
            case STRIKEOUT:
            case SQUIGGLY:
            case INK:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static Bucket bucketFor(@NonNull Annotation.Type type) {
        switch (type) {
            case TEXT:
                return Bucket.NOTE;
            case FREETEXT:
                return Bucket.TEXT_BOX;
            case HIGHLIGHT:
            case UNDERLINE:
            case STRIKEOUT:
            case SQUIGGLY:
                return Bucket.MARKUP;
            case INK:
                return Bucket.INK;
            default:
                return Bucket.OTHER;
        }
    }

    @Nullable
    private static RectF quadUnion(@NonNull PointF[] quadPoints) {
        if (quadPoints.length < 4) return null;
        RectF union = null;
        int n = quadPoints.length - (quadPoints.length % 4);
        for (int i = 0; i < n; i += 4) {
            RectF r = quadRect(quadPoints, i);
            if (r == null) continue;
            if (union == null) union = new RectF(r);
            else union.union(r);
        }
        return union;
    }

    @Nullable
    private static RectF quadRect(@NonNull PointF[] points, int start) {
        if (points.length < start + 4) return null;
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < 4; j++) {
            PointF p = points[start + j];
            if (p == null) continue;
            if (p.x < left) left = p.x;
            if (p.y < top) top = p.y;
            if (p.x > right) right = p.x;
            if (p.y > bottom) bottom = p.y;
        }
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)) return null;
        return new RectF(left, top, right, bottom);
    }

    @Nullable
    private static RectF pointsBounds(@NonNull PointF[] points) {
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (PointF p : points) {
            if (p == null) continue;
            if (p.x < left) left = p.x;
            if (p.y < top) top = p.y;
            if (p.x > right) right = p.x;
            if (p.y > bottom) bottom = p.y;
        }
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(right) || !Float.isFinite(bottom)) return null;
        if (right - left < 0.5f) right = left + 0.5f;
        if (bottom - top < 0.5f) bottom = top + 0.5f;
        return new RectF(left, top, right, bottom);
    }

    private static final class CommentsAdapter extends BaseAdapter {
        private final AppCompatActivity activity;
        private final ArrayList<CommentEntry> all = new ArrayList<>();
        private final ArrayList<CommentEntry> filtered = new ArrayList<>();
        private Filter filter = Filter.ALL;
        private String query = "";

        CommentsAdapter(@NonNull AppCompatActivity activity) {
            this.activity = activity;
        }

        void setItems(@NonNull List<CommentEntry> items) {
            all.clear();
            all.addAll(items);
            applyFilter();
        }

        void setFilter(@NonNull Filter filter) {
            if (filter == null) filter = Filter.ALL;
            if (this.filter == filter) return;
            this.filter = filter;
            applyFilter();
        }

        void setQuery(@Nullable String query) {
            String next = query != null ? query : "";
            if (Objects.equals(this.query, next)) return;
            this.query = next;
            applyFilter();
        }

        @Nullable
        CommentEntry itemAt(int position) {
            if (position < 0 || position >= filtered.size()) return null;
            return filtered.get(position);
        }

        private void applyFilter() {
            filtered.clear();
            final String q = query != null ? query.trim().toLowerCase(Locale.US) : "";
            for (CommentEntry e : all) {
                if (e == null) continue;
                if (!matchesBucket(e, filter)) continue;
                if (!q.isEmpty()) {
                    String hay = e.searchText != null ? e.searchText : "";
                    hay = hay.toLowerCase(Locale.US);
                    if (!hay.contains(q)) continue;
                }
                filtered.add(e);
            }
            notifyDataSetChanged();
        }

        private static boolean matchesBucket(@NonNull CommentEntry e, @NonNull Filter f) {
            switch (f) {
                case NOTES:
                    return e.bucket == Bucket.NOTE;
                case TEXT_BOXES:
                    return e.bucket == Bucket.TEXT_BOX;
                case MARKUPS:
                    return e.bucket == Bucket.MARKUP;
                case INK:
                    return e.bucket == Bucket.INK;
                case ALL:
                default:
                    return true;
            }
        }

        @Override public int getCount() { return filtered.size(); }

        @Override public Object getItem(int position) { return itemAt(position); }

        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(activity).inflate(android.R.layout.simple_list_item_2, parent, false);
            }

            TextView t1 = row.findViewById(android.R.id.text1);
            TextView t2 = row.findViewById(android.R.id.text2);
            CommentEntry e = itemAt(position);
            if (e == null) return row;

            String type = typeLabel(activity, e.annotType);
            String header = activity.getString(R.string.comments_page_prefix) + " " + (e.pageIndex + 1) + " · " + type;
            t1.setText(header);

            String snippet = e.searchText != null ? e.searchText.trim() : "";
            String time = "";
            try {
                if (e.createdAtEpochMs > 0L) {
                    time = DateUtils.getRelativeTimeSpanString(
                            e.createdAtEpochMs,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS).toString();
                }
            } catch (Throwable ignore) {
                time = "";
            }
            String line2;
            if (!time.isEmpty() && !snippet.isEmpty()) {
                line2 = time + " · " + snippet;
            } else if (!time.isEmpty()) {
                line2 = time;
            } else {
                line2 = snippet;
            }
            t2.setText(truncate(line2, 120));

            return row;
        }
    }

    @NonNull
    private static String typeLabel(@NonNull AppCompatActivity activity, @Nullable Annotation.Type type) {
        if (type == null) return "";
        switch (type) {
            case TEXT:
                return activity.getString(R.string.comment_type_note);
            case FREETEXT:
                return activity.getString(R.string.comment_type_text_box);
            case HIGHLIGHT:
                return activity.getString(R.string.menu_highlight);
            case UNDERLINE:
                return activity.getString(R.string.menu_underline);
            case STRIKEOUT:
                return activity.getString(R.string.menu_strikeout);
            case SQUIGGLY:
                return activity.getString(R.string.comment_type_squiggly);
            case INK:
                return activity.getString(R.string.comment_type_ink);
            default:
                return type.name();
        }
    }

    @NonNull
    private static String truncate(@Nullable String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }
}
