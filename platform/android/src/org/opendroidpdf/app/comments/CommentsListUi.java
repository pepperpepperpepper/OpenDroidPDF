package org.opendroidpdf.app.comments;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

final class CommentsListUi {

    enum Filter {
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

    static final class CommentsAdapter extends BaseAdapter {
        private final AppCompatActivity activity;
        private final ArrayList<CommentsListOps.CommentEntry> all = new ArrayList<>();
        private final ArrayList<CommentsListOps.CommentEntry> filtered = new ArrayList<>();
        private Filter filter = Filter.ALL;
        private String query = "";

        CommentsAdapter(@NonNull AppCompatActivity activity) {
            this.activity = activity;
        }

        void setItems(@NonNull List<CommentsListOps.CommentEntry> items) {
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
        CommentsListOps.CommentEntry itemAt(int position) {
            if (position < 0 || position >= filtered.size()) return null;
            return filtered.get(position);
        }

        private void applyFilter() {
            filtered.clear();
            final String q = query != null ? query.trim().toLowerCase(Locale.US) : "";
            for (CommentsListOps.CommentEntry e : all) {
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

        private static boolean matchesBucket(@NonNull CommentsListOps.CommentEntry e, @NonNull Filter f) {
            switch (f) {
                case NOTES:
                    return e.bucket == CommentsListOps.Bucket.NOTE;
                case TEXT_BOXES:
                    return e.bucket == CommentsListOps.Bucket.TEXT_BOX;
                case MARKUPS:
                    return e.bucket == CommentsListOps.Bucket.MARKUP;
                case INK:
                    return e.bucket == CommentsListOps.Bucket.INK;
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
            CommentsListOps.CommentEntry e = itemAt(position);
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

