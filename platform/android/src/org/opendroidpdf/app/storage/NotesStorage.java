package org.opendroidpdf.app.storage;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Centralizes notes storage paths and legacy migration so the Activity can shrink.
 */
public final class NotesStorage {

    private NotesStorage() {}

    public static File ensureNotesDir(Context context, String notesDirName, String legacyNotesDirName) {
        File notesDir = new File(Environment.getExternalStorageDirectory(), notesDirName);
        if (!notesDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            notesDir.mkdirs();
        }
        migrateLegacyPrivateNotes(context, notesDir);
        migrateLegacyExternalNotes(notesDir, legacyNotesDirName, notesDirName);
        return notesDir;
    }

    private static void migrateLegacyPrivateNotes(Context context, File notesDir) {
        try {
            File oldNotesDir = context.getDir("notes", Context.MODE_WORLD_READABLE);
            File[] listOfFiles = oldNotesDir.listFiles();
            if (listOfFiles != null && listOfFiles.length > 0) {
                boolean migratedAny = false;
                for (File child : listOfFiles) {
                    File targetFile = new File(notesDir, child.getName());
                    if (child.isFile() && !targetFile.exists()) {
                        copyFile(child, targetFile);
                        //noinspection ResultOfMethodCallIgnored
                        child.delete();
                        migratedAny = true;
                    }
                }
                if (migratedAny) {
                    deleteDirIfEmpty(oldNotesDir);
                }
            }
        } catch (Exception ignored) {
            // Best-effort migration only
        }
    }

    private static void migrateLegacyExternalNotes(File notesDir, String legacyNotesDirName, String notesDirName) {
        File legacyDir = new File(Environment.getExternalStorageDirectory(), legacyNotesDirName);
        if (!legacyDir.exists() || notesDirName.equals(legacyNotesDirName)) {
            return;
        }
        File[] legacyFiles = legacyDir.listFiles();
        if (legacyFiles == null || legacyFiles.length == 0) {
            return;
        }
        for (File child : legacyFiles) {
            File target = new File(notesDir, child.getName());
            if (child.isFile() && !target.exists()) {
                if (!child.renameTo(target)) {
                    try {
                        copyFile(child, target);
                        //noinspection ResultOfMethodCallIgnored
                        child.delete();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        deleteDirIfEmpty(legacyDir);
    }

    private static void deleteDirIfEmpty(File directory) {
        if (directory == null) {
            return;
        }
        String[] remaining = directory.list();
        if (remaining == null || remaining.length == 0) {
            //noinspection ResultOfMethodCallIgnored
            directory.delete();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target);
        byte[] buf = new byte[8192];
        int len;
        try {
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            in.close();
            out.close();
        }
    }
}

