package org.opendroidpdf.app.content;

import android.graphics.RectF;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.LinkInfo;
import org.opendroidpdf.SearchResult;
import org.opendroidpdf.TextWord;

/**
 * Narrow interface PageView implements so controllers can update overlay/content without a fat dependency.
 */
public interface PageContentHost {
    int getPageNumber();
    void setText(TextWord[][] text);
    void setLinks(LinkInfo[] links);
    void setAnnotations(Annotation[] annotations);
    void invalidateOverlay();
    boolean consumeForceFullRedrawFlag();
    void requestRedraw(boolean update);
    void setSelectBox(RectF box);
    RectF getSelectBox();
}
