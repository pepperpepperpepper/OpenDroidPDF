package org.opendroidpdf.app.services.recent;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.PdfThumbnailManager;
import org.opendroidpdf.RecentFile;
import org.opendroidpdf.RecentFilesList;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * SharedPreferences-backed store using the legacy schema so existing data
 * migrates seamlessly.
 */
public final class SharedPreferencesRecentFilesStore implements RecentFilesStore {
    private final Context appContext;
    private final SharedPreferences prefs;

    public SharedPreferencesRecentFilesStore(@NonNull Context context,
                                             @NonNull SharedPreferences prefs) {
        this.appContext = context.getApplicationContext();
        this.prefs = prefs;
    }

    @Override
    public List<RecentEntry> loadRecents() {
        RecentFilesList list = new RecentFilesList(appContext, prefs);
        List<RecentEntry> entries = new ArrayList<>(list.size());
        for (RecentFile rf : list) {
            String docId = rf.getFileString();
            ViewportSnapshot vp = loadViewport(docId);
            int lastPage = vp != null ? vp.page() : 0;
            entries.add(new RecentEntry(
                    docId,
                    rf.getFileString(),
                    rf.getDisplayName(),
                    rf.getLastOpened(),
                    lastPage,
                    vp,
                    rf.getThumbnailString()));
        }
        return entries;
    }

    @Override
    public void persistRecents(List<RecentEntry> entries) {
        RecentFilesList list = new RecentFilesList(appContext, prefs);
        list.clear();
        PdfThumbnailManager pdfThumbnailManager = new PdfThumbnailManager(appContext);
        // Build a new list respecting legacy duplicate-removal semantics.
        for (RecentEntry e : entries) {
            RecentFile rf = new RecentFile(e.uriString(), e.displayName(), e.lastOpenedEpochMs(), e.thumbnailString());
            list.push(rf);
        }
        SharedPreferences.Editor edit = prefs.edit();
        list.writeTo(edit);
        edit.apply();
    }

    @Override
    public void saveViewport(String docId, ViewportSnapshot snapshot) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt("page" + docId, snapshot.page());
        edit.putFloat("normalizedscale" + docId, snapshot.normalizedScale());
        edit.putFloat("normalizedxscroll" + docId, snapshot.normalizedXScroll());
        edit.putFloat("normalizedyscroll" + docId, snapshot.normalizedYScroll());
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
        return new ViewportSnapshot(page, scale, nx, ny);
    }
}
