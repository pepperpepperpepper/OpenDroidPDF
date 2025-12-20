package org.opendroidpdf.app.services.recent;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.PdfThumbnailManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SharedPreferences-backed store using the legacy schema so existing data
 * migrates seamlessly.
 */
public final class SharedPreferencesRecentFilesStore implements RecentFilesStore {
    private static final int MAX_RECENT_FILES = 100;
    private static final String KEY_RECENTFILE = "recentfile";
    private static final String KEY_RECENTFILE_LAST_OPENED = "recentfile_lastModified";
    private static final String KEY_RECENTFILE_DISPLAY_NAME = "recentfile_displayName";
    private static final String KEY_RECENTFILE_THUMBNAIL = "recentfile_thumbnailString";

    private final Context appContext;
    private final SharedPreferences prefs;

    public SharedPreferencesRecentFilesStore(@NonNull Context context,
                                             @NonNull SharedPreferences prefs) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
    }

    @Override
    public List<RecentEntry> loadRecents() {
        List<RecentEntry> entries = new ArrayList<>();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            final String uriString = prefs.getString(KEY_RECENTFILE + i, null);
            if (uriString == null) continue;

            if (android.os.Build.VERSION.SDK_INT < 19) {
                // On pre-KitKat devices the legacy code only recorded readable files.
                try {
                    Uri uri = Uri.parse(uriString);
                    File file = uri != null ? new File(Uri.decode(uri.getEncodedPath())) : null;
                    if (file == null || !file.isFile() || !file.canRead()) continue;
                } catch (Throwable ignore) {
                    continue;
                }
            }

            final long lastOpened = prefs.getLong(KEY_RECENTFILE_LAST_OPENED + i, 0L);
            final String displayName = prefs.getString(KEY_RECENTFILE_DISPLAY_NAME + i, null);
            final String thumbnailString = prefs.getString(KEY_RECENTFILE_THUMBNAIL + i, null);

            // Compat: docId is historically the uriString.
            final String docId = uriString;
            final ViewportSnapshot vp = loadViewport(docId);
            final int lastPage = vp != null ? vp.page() : 0;

            entries.add(new RecentEntry(
                    docId,
                    uriString,
                    displayName,
                    lastOpened,
                    lastPage,
                    vp,
                    thumbnailString));
        }
        return entries;
    }

    @Override
    public void persistRecents(List<RecentEntry> entries) {
        if (entries == null) return;

        // Preserve legacy behavior: if a recent is re-recorded without a thumbnail,
        // keep any previously stored thumbnail for that docId.
        Map<String, String> previousThumbnailByDocId = new HashMap<>();
        Set<String> previousThumbnails = new HashSet<>();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            String uriString = prefs.getString(KEY_RECENTFILE + i, null);
            if (uriString == null) continue;
            String thumb = prefs.getString(KEY_RECENTFILE_THUMBNAIL + i, null);
            if (thumb != null) {
                previousThumbnails.add(thumb);
                if (!previousThumbnailByDocId.containsKey(uriString)) {
                    previousThumbnailByDocId.put(uriString, thumb);
                }
            }
        }

        List<RecentEntry> normalized = new ArrayList<>();
        Set<String> newThumbnails = new HashSet<>();
        for (RecentEntry e : entries) {
            if (e == null || e.uriString() == null) continue;
            if (normalized.size() >= MAX_RECENT_FILES) break;

            String docId = e.docId() != null ? e.docId() : e.uriString();
            String thumb = e.thumbnailString();
            if (thumb == null) {
                thumb = previousThumbnailByDocId.get(docId);
            }
            if (thumb != null) newThumbnails.add(thumb);

            normalized.add(new RecentEntry(
                    docId,
                    e.uriString(),
                    e.displayName(),
                    e.lastOpenedEpochMs(),
                    e.lastPage(),
                    e.viewport(),
                    thumb));
        }

        // Delete thumbnails that are no longer referenced by any stored recent.
        PdfThumbnailManager pdfThumbnailManager = new PdfThumbnailManager(appContext);
        for (String oldThumb : previousThumbnails) {
            if (oldThumb == null) continue;
            if (!newThumbnails.contains(oldThumb)) {
                try {
                    pdfThumbnailManager.delete(oldThumb);
                } catch (Throwable ignore) {
                }
            }
        }

        SharedPreferences.Editor edit = prefs.edit();
        for (int i = 0; i < MAX_RECENT_FILES; i++) {
            if (i < normalized.size()) {
                RecentEntry e = normalized.get(i);
                edit.putString(KEY_RECENTFILE + i, e.uriString());
                edit.putLong(KEY_RECENTFILE_LAST_OPENED + i, e.lastOpenedEpochMs());
                if (e.displayName() != null) {
                    edit.putString(KEY_RECENTFILE_DISPLAY_NAME + i, e.displayName());
                } else {
                    edit.remove(KEY_RECENTFILE_DISPLAY_NAME + i);
                }
                if (e.thumbnailString() != null) {
                    edit.putString(KEY_RECENTFILE_THUMBNAIL + i, e.thumbnailString());
                } else {
                    edit.remove(KEY_RECENTFILE_THUMBNAIL + i);
                }
            } else {
                // Clear any stale trailing entries so removed recents don't reappear.
                edit.remove(KEY_RECENTFILE + i);
                edit.remove(KEY_RECENTFILE_LAST_OPENED + i);
                edit.remove(KEY_RECENTFILE_DISPLAY_NAME + i);
                edit.remove(KEY_RECENTFILE_THUMBNAIL + i);
            }
        }
        edit.apply();
    }

    @Override
    public void saveViewport(String docId, ViewportSnapshot snapshot) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt("page" + docId, snapshot.page());
        edit.putFloat("normalizedscale" + docId, snapshot.normalizedScale());
        edit.putFloat("normalizedxscroll" + docId, snapshot.normalizedXScroll());
        edit.putFloat("normalizedyscroll" + docId, snapshot.normalizedYScroll());
        float docProgress01 = snapshot.docProgress01();
        if (docProgress01 >= 0f) {
            edit.putFloat("docprogress" + docId, docProgress01);
        } else {
            edit.remove("docprogress" + docId);
        }
        String layoutProfileId = snapshot.layoutProfileId();
        if (layoutProfileId != null) {
            edit.putString("layoutProfileId" + docId, layoutProfileId);
        } else {
            edit.remove("layoutProfileId" + docId);
        }
        edit.apply();
    }

    @Nullable
    @Override
    public ViewportSnapshot loadViewport(String docId) {
        String pageKey = "page" + docId;
        if (!prefs.contains(pageKey)) return null;
        int page = prefs.getInt(pageKey, 0);
        float scale = prefs.getFloat("normalizedscale" + docId, 0f);
        float nx = prefs.getFloat("normalizedxscroll" + docId, 0f);
        float ny = prefs.getFloat("normalizedyscroll" + docId, 0f);
        float docProgress01 = prefs.getFloat("docprogress" + docId, -1f);
        String layoutProfileId = prefs.getString("layoutProfileId" + docId, null);
        return new ViewportSnapshot(page, scale, nx, ny, docProgress01, layoutProfileId);
    }
}
