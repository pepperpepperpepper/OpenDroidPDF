package org.opendroidpdf.app.sidecar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed implementation of {@link SidecarAnnotationStore}.
 *
 * <p>All methods are synchronous; callers should keep payloads small or move usage off the UI
 * thread if they observe jank.</p>
 */
public final class SQLiteSidecarAnnotationStore implements SidecarAnnotationStore {
    private final SidecarDbHelper helper;

    public SQLiteSidecarAnnotationStore(@NonNull Context context) {
        this.helper = new SidecarDbHelper(context.getApplicationContext());
    }

    @Override
    @NonNull
    public List<SidecarInkStroke> listInk(@NonNull String docId, int pageIndex, @Nullable String layoutProfileId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        ArrayList<SidecarInkStroke> out = new ArrayList<>();
        String selection = "doc_id=? AND page_index=? AND " + (layoutProfileId == null ? "layout_profile_id IS NULL" : "layout_profile_id=?");
        String[] args = layoutProfileId == null
                ? new String[]{docId, String.valueOf(pageIndex)}
                : new String[]{docId, String.valueOf(pageIndex), layoutProfileId};
        try (Cursor c = db.query("ink_strokes",
                new String[]{"id", "layout_profile_id", "color", "thickness", "created_at_ms", "points"},
                selection,
                args,
                null, null,
                "created_at_ms ASC")) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String layout = c.isNull(1) ? null : c.getString(1);
                int color = c.getInt(2);
                float thickness = c.getFloat(3);
                long createdAt = c.getLong(4);
                byte[] blob = c.getBlob(5);
                PointF[] points = SidecarPointCodec.decodePoints(blob);
                if (id == null || points == null) continue;
                out.add(new SidecarInkStroke(id, pageIndex, layout, color, thickness, createdAt, points));
            }
        }
        return out;
    }

    @Override
    public void insertInk(@NonNull String docId, @NonNull List<SidecarInkStroke> strokes) {
        if (strokes.isEmpty()) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (SidecarInkStroke s : strokes) {
                ContentValues v = new ContentValues();
                v.put("id", s.id);
                v.put("doc_id", docId);
                v.put("page_index", s.pageIndex);
                if (s.layoutProfileId != null) v.put("layout_profile_id", s.layoutProfileId);
                v.put("color", s.color);
                v.put("thickness", s.thickness);
                v.put("created_at_ms", s.createdAtEpochMs);
                v.put("points", SidecarPointCodec.encodePoints(s.points));
                db.insertWithOnConflict("ink_strokes", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void deleteInk(@NonNull String docId, @NonNull String strokeId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("ink_strokes", "doc_id=? AND id=?", new String[]{docId, strokeId});
    }

    @Override
    public boolean hasAnyInk(@NonNull String docId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT 1 FROM ink_strokes WHERE doc_id=? LIMIT 1", new String[]{docId})) {
            return c.moveToFirst();
        }
    }

    @Override
    @NonNull
    public List<SidecarHighlight> listHighlights(@NonNull String docId, int pageIndex, @Nullable String layoutProfileId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        ArrayList<SidecarHighlight> out = new ArrayList<>();
        String selection = "doc_id=? AND page_index=? AND " + (layoutProfileId == null ? "layout_profile_id IS NULL" : "layout_profile_id=?");
        String[] args = layoutProfileId == null
                ? new String[]{docId, String.valueOf(pageIndex)}
                : new String[]{docId, String.valueOf(pageIndex), layoutProfileId};
        try (Cursor c = db.query("highlights",
                new String[]{"id", "layout_profile_id", "type_ordinal", "color", "opacity", "created_at_ms", "quad_points", "quote", "quote_prefix", "quote_suffix", "doc_progress"},
                selection,
                args,
                null, null,
                "created_at_ms ASC")) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String layout = c.isNull(1) ? null : c.getString(1);
                int typeOrdinal = c.getInt(2);
                int color = c.getInt(3);
                float opacity = c.getFloat(4);
                long createdAt = c.getLong(5);
                byte[] blob = c.getBlob(6);
                String quote = c.getString(7);
                String quotePrefix = c.getString(8);
                String quoteSuffix = c.getString(9);
                float docProgress01 = c.isNull(10) ? -1f : c.getFloat(10);
                PointF[] points = SidecarPointCodec.decodePoints(blob);
                Annotation.Type type = (typeOrdinal >= 0 && typeOrdinal < Annotation.Type.values().length)
                        ? Annotation.Type.values()[typeOrdinal]
                        : null;
                if (id == null || type == null || points == null) continue;
                out.add(new SidecarHighlight(id, pageIndex, layout, type, color, opacity, createdAt, points, quote, quotePrefix, quoteSuffix, docProgress01));
            }
        }
        return out;
    }

    @Override
    @NonNull
    public List<SidecarHighlight> listAllHighlights(@NonNull String docId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        ArrayList<SidecarHighlight> out = new ArrayList<>();
        try (Cursor c = db.query("highlights",
                new String[]{"id", "page_index", "layout_profile_id", "type_ordinal", "color", "opacity", "created_at_ms", "quad_points", "quote", "quote_prefix", "quote_suffix", "doc_progress"},
                "doc_id=?",
                new String[]{docId},
                null, null,
                "created_at_ms ASC")) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                int pageIndex = c.getInt(1);
                String layout = c.isNull(2) ? null : c.getString(2);
                int typeOrdinal = c.getInt(3);
                int color = c.getInt(4);
                float opacity = c.getFloat(5);
                long createdAt = c.getLong(6);
                byte[] blob = c.getBlob(7);
                String quote = c.getString(8);
                String quotePrefix = c.getString(9);
                String quoteSuffix = c.getString(10);
                float docProgress01 = c.isNull(11) ? -1f : c.getFloat(11);
                PointF[] points = SidecarPointCodec.decodePoints(blob);
                Annotation.Type type = (typeOrdinal >= 0 && typeOrdinal < Annotation.Type.values().length)
                        ? Annotation.Type.values()[typeOrdinal]
                        : null;
                if (id == null || type == null || points == null) continue;
                out.add(new SidecarHighlight(id, pageIndex, layout, type, color, opacity, createdAt, points, quote, quotePrefix, quoteSuffix, docProgress01));
            }
        }
        return out;
    }

    @Override
    public void insertHighlight(@NonNull String docId, @NonNull SidecarHighlight highlight) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", highlight.id);
        v.put("doc_id", docId);
        v.put("page_index", highlight.pageIndex);
        if (highlight.layoutProfileId != null) v.put("layout_profile_id", highlight.layoutProfileId);
        v.put("type_ordinal", highlight.type.ordinal());
        v.put("color", highlight.color);
        v.put("opacity", highlight.opacity);
        v.put("created_at_ms", highlight.createdAtEpochMs);
        v.put("quad_points", SidecarPointCodec.encodePoints(highlight.quadPoints));
        if (highlight.quote != null) v.put("quote", highlight.quote);
        if (highlight.quotePrefix != null) v.put("quote_prefix", highlight.quotePrefix);
        if (highlight.quoteSuffix != null) v.put("quote_suffix", highlight.quoteSuffix);
        if (highlight.docProgress01 >= 0f) v.put("doc_progress", highlight.docProgress01);
        db.insertWithOnConflict("highlights", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void deleteHighlight(@NonNull String docId, @NonNull String highlightId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("highlights", "doc_id=? AND id=?", new String[]{docId, highlightId});
    }

    @Override
    @NonNull
    public List<SidecarNote> listNotes(@NonNull String docId, int pageIndex, @Nullable String layoutProfileId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        ArrayList<SidecarNote> out = new ArrayList<>();
        String selection = "doc_id=? AND page_index=? AND " + (layoutProfileId == null ? "layout_profile_id IS NULL" : "layout_profile_id=?");
        String[] args = layoutProfileId == null
                ? new String[]{docId, String.valueOf(pageIndex)}
                : new String[]{docId, String.valueOf(pageIndex), layoutProfileId};
        try (Cursor c = db.query("notes",
                new String[]{"id", "layout_profile_id", "left", "top", "right", "bottom", "text", "created_at_ms"},
                selection,
                args,
                null, null,
                "created_at_ms ASC")) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                String layout = c.isNull(1) ? null : c.getString(1);
                float left = c.getFloat(2);
                float top = c.getFloat(3);
                float right = c.getFloat(4);
                float bottom = c.getFloat(5);
                String text = c.isNull(6) ? null : c.getString(6);
                long createdAt = c.getLong(7);
                out.add(new SidecarNote(id, pageIndex, layout, new RectF(left, top, right, bottom), text, createdAt));
            }
        }
        return out;
    }

    @Override
    public void insertNote(@NonNull String docId, @NonNull SidecarNote note) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", note.id);
        v.put("doc_id", docId);
        v.put("page_index", note.pageIndex);
        if (note.layoutProfileId != null) v.put("layout_profile_id", note.layoutProfileId);
        v.put("left", note.bounds.left);
        v.put("top", note.bounds.top);
        v.put("right", note.bounds.right);
        v.put("bottom", note.bounds.bottom);
        v.put("text", note.text);
        v.put("created_at_ms", note.createdAtEpochMs);
        db.insertWithOnConflict("notes", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void deleteNote(@NonNull String docId, @NonNull String noteId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("notes", "doc_id=? AND id=?", new String[]{docId, noteId});
    }

    @Override
    public boolean hasAnyAnnotationsInLayout(@NonNull String docId, @Nullable String layoutProfileId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String where = "doc_id=? AND " + (layoutProfileId == null ? "layout_profile_id IS NULL" : "layout_profile_id=?");
        String[] args = layoutProfileId == null ? new String[]{docId} : new String[]{docId, layoutProfileId};
        try (Cursor c = db.rawQuery("SELECT 1 FROM ink_strokes WHERE " + where + " LIMIT 1", args)) {
            if (c.moveToFirst()) return true;
        }
        try (Cursor c = db.rawQuery("SELECT 1 FROM highlights WHERE " + where + " LIMIT 1", args)) {
            if (c.moveToFirst()) return true;
        }
        try (Cursor c = db.rawQuery("SELECT 1 FROM notes WHERE " + where + " LIMIT 1", args)) {
            return c.moveToFirst();
        }
    }

    @Override
    public boolean hasAnyAnnotationsOutsideLayout(@NonNull String docId, @NonNull String layoutProfileId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String[] args = new String[]{docId, layoutProfileId};
        String where = "doc_id=? AND (layout_profile_id IS NULL OR layout_profile_id<>?)";
        try (Cursor c = db.rawQuery("SELECT 1 FROM ink_strokes WHERE " + where + " LIMIT 1", args)) {
            if (c.moveToFirst()) return true;
        }
        try (Cursor c = db.rawQuery("SELECT 1 FROM highlights WHERE " + where + " LIMIT 1", args)) {
            if (c.moveToFirst()) return true;
        }
        try (Cursor c = db.rawQuery("SELECT 1 FROM notes WHERE " + where + " LIMIT 1", args)) {
            return c.moveToFirst();
        }
    }

    @Override
    public void migrateDocId(@NonNull String fromDocId, @NonNull String toDocId) {
        if (fromDocId.equals(toDocId)) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("doc_id", toDocId);
            db.update("ink_strokes", v, "doc_id=?", new String[]{fromDocId});
            db.update("highlights", v, "doc_id=?", new String[]{fromDocId});
            db.update("notes", v, "doc_id=?", new String[]{fromDocId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
