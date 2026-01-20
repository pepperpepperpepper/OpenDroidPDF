package org.opendroidpdf.app.comments;

import android.graphics.RectF;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;

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
import org.opendroidpdf.core.MuPdfRepository;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import kotlinx.coroutines.Job;

/**
 * Minimal “Comments list” UI for jumping between comment-style annotations.
 *
 * <p>Scope: embedded PDF annotations (MuPDF) + sidecar annotations (EPUB / read-only PDFs).</p>
 */
public final class CommentsListController {

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
        final View empty = root.findViewById(R.id.comments_empty);

        final CommentsListUi.CommentsAdapter adapter = new CommentsListUi.CommentsAdapter(activity);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        final ArrayList<String> filterLabels = new ArrayList<>();
        for (CommentsListUi.Filter f : CommentsListUi.Filter.values()) {
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
            CommentsListOps.CommentEntry item = adapter.itemAt(position);
            if (item == null) return;
            dialog.dismiss();
            jumpTo(activity, docView, item);
        });

        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                CommentsListUi.Filter next = CommentsListUi.Filter.ALL;
                try {
                    CommentsListUi.Filter[] vals = CommentsListUi.Filter.values();
                    if (position >= 0 && position < vals.length) next = vals[position];
                } catch (Throwable ignore) {
                }
                adapter.setFilter(next);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {
                adapter.setFilter(CommentsListUi.Filter.ALL);
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
            final java.util.List<CommentsListOps.CommentEntry> loaded = CommentsListOps.loadComments(repo, sidecarProvider);
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
                               @NonNull CommentsListOps.CommentEntry entry) {
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
                                                  @NonNull CommentsListOps.CommentEntry entry,
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
}
