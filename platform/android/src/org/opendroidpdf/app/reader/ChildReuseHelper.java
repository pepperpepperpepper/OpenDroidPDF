package org.opendroidpdf.app.reader;

import android.view.View;
import android.widget.Adapter;

import org.opendroidpdf.MuPDFView;

import java.util.LinkedList;

/**
 * Handles removing/adding/reusing child PageViews to slim ReaderView.
 */
public final class ChildReuseHelper {
    private ChildReuseHelper() {}

    public interface Host {
        Adapter adapter();
        View childAtIndex(int index);
        int childKeyAt(int position);
        int childCount();
        void removeViewInLayout(View v);
        void removeChildKey(int key);
        void appendChild(int key, View view);
        LinkedList<View> viewCache();
        void onChildSetup(int index, View v);
        void onScaleChild(View v, float scale);
        int currentIndex();
        View getCached();
        void addViewInLayout(View v);
        float scale();
        void onRestoreIfNeeded(int index, View v);
    }

    public static void removeSuperfluous(Host h) {
        int numChildren = h.childCount();
        int[] keys = new int[numChildren];
        for (int i = 0; i < numChildren; i++) keys[i] = h.childKeyAt(i);

        int maxCount = h.adapter() != null ? h.adapter().getCount() : 0;
        for (int key : keys) {
            if (key < h.currentIndex() - 1 || key > h.currentIndex() + 1 || key < 0 || key >= maxCount) {
                View v = h.childAtIndex(key);
                ((MuPDFView) v).releaseResources();
                h.viewCache().add(v);
                h.removeViewInLayout(v);
                h.removeChildKey(key);
            }
        }
    }

    public static View getOrCreateChild(Host h, int index) {
        View v = h.childAtIndex(index);
        if (v != null) return v;

        v = h.adapter().getView(index, h.getCached(), null);
        h.onChildSetup(index, v);
        h.onScaleChild(v, h.scale());
        h.addViewInLayout(v);
        h.appendChild(index, v);
        h.onRestoreIfNeeded(index, v);
        return v;
    }
}
