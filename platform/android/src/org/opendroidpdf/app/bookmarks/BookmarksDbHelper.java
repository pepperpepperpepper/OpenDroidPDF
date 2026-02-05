package org.opendroidpdf.app.bookmarks;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

final class BookmarksDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "reader_bookmarks.db";
    private static final int DB_VERSION = 1;

    BookmarksDbHelper(@NonNull Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.enableWriteAheadLogging();
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS bookmarks (" +
                        "id TEXT PRIMARY KEY," +
                        "doc_id TEXT NOT NULL," +
                        "page_index INTEGER NOT NULL," +
                        "title TEXT NOT NULL," +
                        "created_at_ms INTEGER NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_doc_page ON bookmarks(doc_id, page_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_doc_created ON bookmarks(doc_id, created_at_ms)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1: initial schema.
    }
}

