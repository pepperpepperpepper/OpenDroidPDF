package com.cgogolin.penandpdf;

import android.graphics.PointF;
import android.graphics.RectF;

public class Annotation extends RectF {
    enum Type {
        TEXT,
        LINK,
        FREETEXT,
        LINE,
        SQUARE,
        CIRCLE,
        POLYGON,
        POLYLINE,
        HIGHLIGHT,
        UNDERLINE,
        SQUIGGLY,
        STRIKEOUT,
        REDACT,
        STAMP,
        CARET,
        INK,
        POPUP,
        FILEATTACHMENT,
        SOUND,
        MOVIE,
        RICHMEDIA,
        WIDGET,
        SCREEN,
        PRINTERMARK,
        TRAPNET,
        WATERMARK,
        A3D,
        PROJECTION,
        UNKNOWN;

        static Type fromNative(int rawType) {
            if (rawType < 0) {
                return UNKNOWN;
            }
            Type[] entries = Type.values();
            int unknownOrdinal = UNKNOWN.ordinal();
            if (rawType >= unknownOrdinal) {
                return UNKNOWN;
            }
            return entries[rawType];
        }
    }

    public final Type type;
    public final int rawType;
    public final PointF[][] arcs;
    public String text;
    public final long objectNumber;

    public Annotation(float x0, float y0, float x1, float y1, Type type, PointF[][] arcs, String text) {
        this(x0, y0, x1, y1, type, arcs, text, -1L);
    }

    public Annotation(float x0, float y0, float x1, float y1, Type type, PointF[][] arcs, String text, long objectNumber) {
        super(x0, y0, x1, y1);
        this.type = type;
        this.rawType = type != null ? type.ordinal() : -1;
        this.arcs = arcs;
        this.text = text;
        this.objectNumber = objectNumber;
    }
    
        //This is for convenience in mupdf.c
    public Annotation(float x0, float y0, float x1, float y1, int type, PointF[][] arcs, String text) {
        this(x0, y0, x1, y1, Type.fromNative(type), arcs, text, type, -1L);
    }

    public Annotation(float x0, float y0, float x1, float y1, int type, PointF[][] arcs, String text, long objectNumber) {
        this(x0, y0, x1, y1, Type.fromNative(type), arcs, text, type, objectNumber);
    }

    private Annotation(float x0, float y0, float x1, float y1, Type resolvedType, PointF[][] arcs, String text, int rawType, long objectNumber) {
        super(x0, y0, x1, y1);
        this.type = resolvedType;
        this.rawType = rawType;
        this.arcs = arcs;
        this.text = text;
        this.objectNumber = objectNumber;
    }
}
