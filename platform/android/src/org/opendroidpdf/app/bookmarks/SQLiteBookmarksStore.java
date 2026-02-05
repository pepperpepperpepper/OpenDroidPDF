package org.opendroidpdf.app.bookmarks;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SQLiteBookmarksStore implements BookmarksStore {
    private final BookmarksDbHelper helper;

    public SQLiteBookmarksStore(@NonNull Context context) {
        this.helper = new BookmarksDbHelper(context.getApplicationContext());
    }

    @NonNull
    @Override
    public List<DocumentBookmark> list(@NonNull String docId) {
        if (docId == null || docId.trim().isEmpty()) return new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        ArrayList<DocumentBookmark> out = new ArrayList<>();
        try (Cursor c = db.query(
                "bookmarks",
                new String[]{"id", "page_index", "title", "created_at_ms"},
                "doc_id=?",
                new String[]{docId},
                null,
                null,
                "page_index ASC, created_at_ms ASC")) {
            while (c.moveToNext()) {
                String id = c.getString(0);
                int pageIndex = c.getInt(1);
                String title = c.getString(2);
                long createdAt = c.getLong(3);
                if (id == null || title == null) continue;
                out.add(new DocumentBookmark(id, pageIndex, title, createdAt));
            }
        }
        return out;
    }

    @NonNull
    @Override
    public DocumentBookmark insert(@NonNull String docId, int pageIndex, @NonNull String title) {
        if (docId == null || docId.trim().isEmpty()) throw new IllegalArgumentException("docId required");
        if (title == null) throw new IllegalArgumentException("title required");
        SQLiteDatabase db = helper.getWritableDatabase();
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("doc_id", docId);
        v.put("page_index", Math.max(0, pageIndex));
        v.put("title", title);
        v.put("created_at_ms", now);
        db.insertWithOnConflict("bookmarks", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return new DocumentBookmark(id, pageIndex, title, now);
    }

    @Override
    public void rename(@NonNull String docId, @NonNull String bookmarkId, @NonNull String newTitle) {
        if (docId == null || docId.trim().isEmpty()) return;
        if (bookmarkId == null || bookmarkId.trim().isEmpty()) return;
        if (newTitle == null || newTitle.trim().isEmpty()) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("title", newTitle);
        db.update("bookmarks", v, "doc_id=? AND id=?", new String[]{docId, bookmarkId});
    }

    @Override
    public void delete(@NonNull String docId, @NonNull String bookmarkId) {
        if (docId == null || docId.trim().isEmpty()) return;
        if (bookmarkId == null || bookmarkId.trim().isEmpty()) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete("bookmarks", "doc_id=? AND id=?", new String[]{docId, bookmarkId});
    }

    @Override
    public void migrateDocId(@NonNull String legacyDocId, @NonNull String newDocId) {
        if (legacyDocId == null || legacyDocId.trim().isEmpty()) return;
        if (newDocId == null || newDocId.trim().isEmpty()) return;
        if (legacyDocId.equals(newDocId)) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("doc_id", newDocId);
        db.update("bookmarks", v, "doc_id=?", new String[]{legacyDocId});
    }
}

