package org.opendroidpdf.app.sidecar;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

/**
 * SQLite schema for sidecar annotations. Designed to be compact and simple for v1:
 * separate tables per annotation type.
 */
final class SidecarDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "sidecar_annotations.db";
    private static final int DB_VERSION = 2;

    SidecarDbHelper(@NonNull Context context) {
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
                "CREATE TABLE IF NOT EXISTS ink_strokes (" +
                        "id TEXT PRIMARY KEY," +
                        "doc_id TEXT NOT NULL," +
                        "page_index INTEGER NOT NULL," +
                        "layout_profile_id TEXT," +
                        "color INTEGER NOT NULL," +
                        "thickness REAL NOT NULL," +
                        "created_at_ms INTEGER NOT NULL," +
                        "points BLOB NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ink_doc_page ON ink_strokes(doc_id, page_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ink_doc_page_layout ON ink_strokes(doc_id, page_index, layout_profile_id)");

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS highlights (" +
                        "id TEXT PRIMARY KEY," +
                        "doc_id TEXT NOT NULL," +
                        "page_index INTEGER NOT NULL," +
                        "layout_profile_id TEXT," +
                        "type_ordinal INTEGER NOT NULL," +
                        "color INTEGER NOT NULL," +
                        "opacity REAL NOT NULL," +
                        "created_at_ms INTEGER NOT NULL," +
                        "quad_points BLOB NOT NULL," +
                        "quote TEXT," +
                        "doc_progress REAL" +
                        ")"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hl_doc_page ON highlights(doc_id, page_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hl_doc_page_layout ON highlights(doc_id, page_index, layout_profile_id)");

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS notes (" +
                        "id TEXT PRIMARY KEY," +
                        "doc_id TEXT NOT NULL," +
                        "page_index INTEGER NOT NULL," +
                        "layout_profile_id TEXT," +
                        "left REAL NOT NULL," +
                        "top REAL NOT NULL," +
                        "right REAL NOT NULL," +
                        "bottom REAL NOT NULL," +
                        "text TEXT," +
                        "created_at_ms INTEGER NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_note_doc_page ON notes(doc_id, page_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_note_doc_page_layout ON notes(doc_id, page_index, layout_profile_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE highlights ADD COLUMN quote TEXT");
            } catch (Throwable ignore) {
            }
            try {
                db.execSQL("ALTER TABLE highlights ADD COLUMN doc_progress REAL");
            } catch (Throwable ignore) {
            }
        }
    }
}
