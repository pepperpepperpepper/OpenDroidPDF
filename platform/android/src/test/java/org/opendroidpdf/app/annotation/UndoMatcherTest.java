package org.opendroidpdf.app.annotation;

import org.junit.Test;

import static org.junit.Assert.*;

public class UndoMatcherTest {

    private static float[][][] arcs(float... xy) {
        // Build a single-stroke arc from flat x,y pairs
        if (xy.length % 2 != 0) throw new IllegalArgumentException("xy must be pairs");
        float[][] stroke = new float[xy.length / 2][2];
        for (int i = 0; i < xy.length; i += 2) {
            stroke[i / 2][0] = xy[i];
            stroke[i / 2][1] = xy[i + 1];
        }
        return new float[][][] { stroke };
    }

    @Test
    public void choosePushSignature_prefersMatchingInk() {
        UndoMatcher.SimpleAnnotation[] ann = new UndoMatcher.SimpleAnnotation[] {
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_OTHER, -1, 0,0,10,10, null),
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_INK,  42, 1,1, 5,5, arcs(0,0, 1,1)),
        };
        float[][][] committed = arcs(0,0, 1,1);
        UndoMatcher.UndoItem item = UndoMatcher.choosePushSignature(ann, committed);
        assertNotNull(item);
        assertEquals(1, item.annotationIndex);
        assertEquals(42, item.objectNumber);
        assertTrue(UndoMatcher.arcsApproximatelyEqual(item.arcsSignature, ann[1].arcs));
    }

    @Test
    public void choosePushSignature_fallbackToLastInk() {
        UndoMatcher.SimpleAnnotation[] ann = new UndoMatcher.SimpleAnnotation[] {
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_INK,  21, 0,0, 1,1, arcs(5,5, 6,6)),
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_OTHER,-1, 0,0, 1,1, null)
        };
        float[][][] committed = arcs(0,0, 1,1);
        UndoMatcher.UndoItem item = UndoMatcher.choosePushSignature(ann, committed);
        assertNotNull(item);
        assertEquals(0, item.annotationIndex);
        assertEquals(21, item.objectNumber);
    }

    @Test
    public void choosePushSignature_fallbackToPopup() {
        UndoMatcher.SimpleAnnotation[] ann = new UndoMatcher.SimpleAnnotation[] {
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_OTHER, -1, 0,0, 1,1, null),
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_POPUP, 33, 2,2, 3,3, null)
        };
        UndoMatcher.UndoItem item = UndoMatcher.choosePushSignature(ann, null);
        assertNotNull(item);
        assertEquals(1, item.annotationIndex);
        assertEquals(33, item.objectNumber);
    }

    @Test
    public void findMatchingIndex_usesPrimaryThenScans() {
        UndoMatcher.SimpleAnnotation[] ann = new UndoMatcher.SimpleAnnotation[] {
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_INK,  10, 9,9, 10,10, arcs(5,5)),
                new UndoMatcher.SimpleAnnotation(UndoMatcher.TYPE_INK,  11, 0,0, 1,1,     arcs(7,7)),
        };
        UndoMatcher.UndoItem item = new UndoMatcher.UndoItem(1, 11, 0,0,1,1, arcs(7,7));
        assertEquals(1, UndoMatcher.findMatchingIndex(ann, item));

        // Break primary; expect scan to find by arcs
        UndoMatcher.UndoItem brokenPrimary = new UndoMatcher.UndoItem(0, 11, 0,0,1,1, arcs(7,7));
        assertEquals(1, UndoMatcher.findMatchingIndex(ann, brokenPrimary));
    }
}
