package org.opendroidpdf.app.navigation;

import androidx.annotation.Nullable;

import org.opendroidpdf.app.navigation.LinkBackDelegate;

/** Keeps link-back state plumbing out of the activity surface. */
public final class LinkBackHelper {
    private final LinkBackDelegate delegate;

    public LinkBackHelper(LinkBackDelegate delegate) {
        this.delegate = delegate;
    }

    public boolean isAvailable() { return delegate != null && delegate.isAvailable(); }
    public LinkBackState state() { return delegate != null ? delegate.state() : null; }
    public void clear() { if (delegate != null) delegate.clear(); }
    public void remember(int page, float scale, float x, float y) { if (delegate != null) delegate.remember(page, scale, x, y); }
}
