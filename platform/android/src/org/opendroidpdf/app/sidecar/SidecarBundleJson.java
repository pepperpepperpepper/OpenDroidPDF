package org.opendroidpdf.app.sidecar;

import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Base64;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.opendroidpdf.Annotation;
import org.opendroidpdf.app.sidecar.model.SidecarHighlight;
import org.opendroidpdf.app.sidecar.model.SidecarInkStroke;
import org.opendroidpdf.app.sidecar.model.SidecarNote;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SidecarBundleJson {
    private SidecarBundleJson() {}

    /**
     * Exports all sidecar annotations for the provided document id across layouts as a JSON bundle.
     *
     * <p>Intended for backup/sync. This does not include pending (uncommitted) ink.</p>
     */
    public static void writeBundleJson(@NonNull String docId,
                                       @NonNull SidecarAnnotationStore store,
                                       @NonNull OutputStream outputStream) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "opendroidpdf-sidecar");
        root.put("version", 1);
        root.put("docId", docId);
        root.put("createdAtEpochMs", System.currentTimeMillis());

        JSONArray ink = new JSONArray();
        for (SidecarInkStroke s : store.listAllInk(docId)) {
            if (s == null || s.id == null || s.points == null) continue;
            JSONObject o = new JSONObject();
            o.put("id", s.id);
            o.put("pageIndex", s.pageIndex);
            if (s.layoutProfileId != null) o.put("layoutProfileId", s.layoutProfileId);
            o.put("color", s.color);
            o.put("thickness", (double) s.thickness);
            o.put("createdAtEpochMs", s.createdAtEpochMs);
            byte[] blob = SidecarPointCodec.encodePoints(s.points);
            o.put("pointsB64", Base64.encodeToString(blob, Base64.NO_WRAP));
            ink.put(o);
        }
        root.put("ink", ink);

        JSONArray highlights = new JSONArray();
        for (SidecarHighlight h : store.listAllHighlights(docId)) {
            if (h == null || h.id == null || h.quadPoints == null) continue;
            JSONObject o = new JSONObject();
            o.put("id", h.id);
            o.put("pageIndex", h.pageIndex);
            if (h.layoutProfileId != null) o.put("layoutProfileId", h.layoutProfileId);
            o.put("type", h.type != null ? h.type.name() : "HIGHLIGHT");
            o.put("color", h.color);
            o.put("opacity", (double) h.opacity);
            o.put("createdAtEpochMs", h.createdAtEpochMs);
            o.put("quadPointsB64", Base64.encodeToString(SidecarPointCodec.encodePoints(h.quadPoints), Base64.NO_WRAP));
            if (h.quote != null) o.put("quote", h.quote);
            if (h.quotePrefix != null) o.put("quotePrefix", h.quotePrefix);
            if (h.quoteSuffix != null) o.put("quoteSuffix", h.quoteSuffix);
            if (h.docProgress01 >= 0f) o.put("docProgress01", (double) h.docProgress01);
            if (h.reflowLocation != -1L) o.put("reflowLocation", h.reflowLocation);
            if (h.anchorStartWord >= 0) o.put("anchorStartWord", h.anchorStartWord);
            if (h.anchorEndWordExclusive >= 0) o.put("anchorEndWordExclusive", h.anchorEndWordExclusive);
            highlights.put(o);
        }
        root.put("highlights", highlights);

        JSONArray notes = new JSONArray();
        for (SidecarNote n : store.listAllNotes(docId)) {
            if (n == null || n.id == null || n.bounds == null) continue;
            JSONObject o = new JSONObject();
            o.put("id", n.id);
            o.put("pageIndex", n.pageIndex);
            if (n.layoutProfileId != null) o.put("layoutProfileId", n.layoutProfileId);
            JSONObject b = new JSONObject();
            b.put("left", (double) n.bounds.left);
            b.put("top", (double) n.bounds.top);
            b.put("right", (double) n.bounds.right);
            b.put("bottom", (double) n.bounds.bottom);
            o.put("bounds", b);
            if (n.text != null) o.put("text", n.text);
            o.put("createdAtEpochMs", n.createdAtEpochMs);
            o.put("color", n.color);
            if (n.fontFamily != SidecarNote.DEFAULT_FONT_FAMILY) o.put("fontFamily", n.fontFamily);
            if (n.fontStyleFlags != SidecarNote.DEFAULT_FONT_STYLE_FLAGS) o.put("fontStyleFlags", n.fontStyleFlags);
            o.put("fontSize", (double) n.fontSize);
            if (n.lineHeight != SidecarNote.DEFAULT_LINE_HEIGHT) o.put("lineHeight", (double) n.lineHeight);
            if (n.textIndentPt != SidecarNote.DEFAULT_TEXT_INDENT_PT) o.put("textIndentPt", (double) n.textIndentPt);
            if (n.backgroundColor != SidecarNote.DEFAULT_BACKGROUND_COLOR) o.put("backgroundColor", n.backgroundColor);
            if (n.backgroundOpacity != SidecarNote.DEFAULT_BACKGROUND_OPACITY) o.put("backgroundOpacity", (double) n.backgroundOpacity);
            if (n.borderColor != SidecarNote.DEFAULT_BORDER_COLOR) o.put("borderColor", n.borderColor);
            if (n.borderWidthPt != SidecarNote.DEFAULT_BORDER_WIDTH_PT) o.put("borderWidthPt", (double) n.borderWidthPt);
            if (n.borderStyle != SidecarNote.DEFAULT_BORDER_STYLE) o.put("borderStyle", n.borderStyle);
            if (n.borderRadiusPt != SidecarNote.DEFAULT_BORDER_RADIUS_PT) o.put("borderRadiusPt", (double) n.borderRadiusPt);
            if (n.lockPositionSize != SidecarNote.DEFAULT_LOCK_POSITION_SIZE) o.put("lockPositionSize", n.lockPositionSize);
            if (n.lockContents != SidecarNote.DEFAULT_LOCK_CONTENTS) o.put("lockContents", n.lockContents);
            if (n.rotationDeg != SidecarNote.DEFAULT_ROTATION_DEG) o.put("rotationDeg", n.rotationDeg);
            if (n.userResized) o.put("userResized", true);
            notes.put(o);
        }
        root.put("notes", notes);

        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        outputStream.write(bytes);
        outputStream.flush();
    }

    /** Sidecar annotations bundle (JSON) parsed from {@link #writeBundleJson(String, SidecarAnnotationStore, OutputStream)}. */
    public static final class SidecarBundle {
        @NonNull public final String docId;
        public final int version;
        @NonNull public final List<SidecarInkStroke> ink;
        @NonNull public final List<SidecarHighlight> highlights;
        @NonNull public final List<SidecarNote> notes;

        SidecarBundle(@NonNull String docId,
                      int version,
                      @NonNull List<SidecarInkStroke> ink,
                      @NonNull List<SidecarHighlight> highlights,
                      @NonNull List<SidecarNote> notes) {
            this.docId = docId;
            this.version = version;
            this.ink = ink;
            this.highlights = highlights;
            this.notes = notes;
        }
    }

    /** Summary of a bundle import operation. */
    public static final class ImportStats {
        public final int inkCount;
        public final int highlightCount;
        public final int noteCount;

        ImportStats(int inkCount, int highlightCount, int noteCount) {
            this.inkCount = inkCount;
            this.highlightCount = highlightCount;
            this.noteCount = noteCount;
        }

        public int total() { return inkCount + highlightCount + noteCount; }
    }

    /** Parses a JSON bundle created by {@link #writeBundleJson(String, SidecarAnnotationStore, OutputStream)}. */
    @NonNull
    public static SidecarBundle readBundleJson(@NonNull InputStream inputStream) throws Exception {
        byte[] bytes = readAllBytes(inputStream);
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        String format = root.optString("format", "");
        if (!"opendroidpdf-sidecar".equals(format)) {
            throw new IllegalArgumentException("unexpected sidecar bundle format: " + format);
        }
        int version = root.optInt("version", 0);
        if (version < 1) throw new IllegalArgumentException("unexpected sidecar bundle version: " + version);
        String docId = root.optString("docId", null);
        if (docId == null || docId.trim().isEmpty()) throw new IllegalArgumentException("missing bundle docId");

        ArrayList<SidecarInkStroke> ink = new ArrayList<>();
        JSONArray inkArr = root.optJSONArray("ink");
        if (inkArr != null) {
            for (int i = 0; i < inkArr.length(); i++) {
                JSONObject o = inkArr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", null);
                if (id == null || id.trim().isEmpty()) continue;
                int pageIndex = o.optInt("pageIndex", -1);
                if (pageIndex < 0) continue;
                String layout = o.has("layoutProfileId") ? o.optString("layoutProfileId", null) : null;
                int color = o.optInt("color", 0);
                float thickness = (float) o.optDouble("thickness", 1.0);
                long createdAt = o.optLong("createdAtEpochMs", 0L);
                String b64 = o.optString("pointsB64", null);
                if (b64 == null || b64.isEmpty()) continue;
                byte[] blob = Base64.decode(b64, Base64.DEFAULT);
                PointF[] points = SidecarPointCodec.decodePoints(blob);
                if (points == null || points.length < 2) continue;
                ink.add(new SidecarInkStroke(id, pageIndex, layout, color, thickness, createdAt, points));
            }
        }

        ArrayList<SidecarHighlight> highlights = new ArrayList<>();
        JSONArray hlArr = root.optJSONArray("highlights");
        if (hlArr != null) {
            for (int i = 0; i < hlArr.length(); i++) {
                JSONObject o = hlArr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", null);
                if (id == null || id.trim().isEmpty()) continue;
                int pageIndex = o.optInt("pageIndex", -1);
                if (pageIndex < 0) continue;
                String layout = o.has("layoutProfileId") ? o.optString("layoutProfileId", null) : null;
                String typeName = o.optString("type", "HIGHLIGHT");
                Annotation.Type type;
                try {
                    type = Annotation.Type.valueOf(typeName);
                } catch (Throwable t) {
                    type = Annotation.Type.HIGHLIGHT;
                }
                int color = o.optInt("color", 0);
                float opacity = (float) o.optDouble("opacity", 1.0);
                long createdAt = o.optLong("createdAtEpochMs", 0L);
                String b64 = o.optString("quadPointsB64", null);
                if (b64 == null || b64.isEmpty()) continue;
                byte[] blob = Base64.decode(b64, Base64.DEFAULT);
                PointF[] quadPoints = SidecarPointCodec.decodePoints(blob);
                if (quadPoints == null || quadPoints.length < 4) continue;
                String quote = o.has("quote") ? o.optString("quote", null) : null;
                String quotePrefix = o.has("quotePrefix") ? o.optString("quotePrefix", null) : null;
                String quoteSuffix = o.has("quoteSuffix") ? o.optString("quoteSuffix", null) : null;
                float docProgress01 = o.has("docProgress01") ? (float) o.optDouble("docProgress01", -1.0) : -1f;
                long reflowLocation = o.has("reflowLocation") ? o.optLong("reflowLocation", -1L) : -1L;
                int anchorStartWord = o.has("anchorStartWord") ? o.optInt("anchorStartWord", -1) : -1;
                int anchorEndWordExcl = o.has("anchorEndWordExclusive") ? o.optInt("anchorEndWordExclusive", -1) : -1;
                highlights.add(new SidecarHighlight(
                        id,
                        pageIndex,
                        layout,
                        type,
                        color,
                        opacity,
                        createdAt,
                        quadPoints,
                        quote,
                        quotePrefix,
                        quoteSuffix,
                        docProgress01,
                        reflowLocation,
                        anchorStartWord,
                        anchorEndWordExcl));
            }
        }

        ArrayList<SidecarNote> notes = new ArrayList<>();
        JSONArray noteArr = root.optJSONArray("notes");
        if (noteArr != null) {
            for (int i = 0; i < noteArr.length(); i++) {
                JSONObject o = noteArr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", null);
                if (id == null || id.trim().isEmpty()) continue;
                int pageIndex = o.optInt("pageIndex", -1);
                if (pageIndex < 0) continue;
                String layout = o.has("layoutProfileId") ? o.optString("layoutProfileId", null) : null;
                JSONObject b = o.optJSONObject("bounds");
                if (b == null) continue;
                float left = (float) b.optDouble("left", 0.0);
                float top = (float) b.optDouble("top", 0.0);
                float right = (float) b.optDouble("right", 0.0);
                float bottom = (float) b.optDouble("bottom", 0.0);
                RectF bounds = new RectF(left, top, right, bottom);
                String text = o.has("text") ? o.optString("text", null) : null;
                long createdAt = o.optLong("createdAtEpochMs", 0L);
                int color = o.has("color") ? o.optInt("color", SidecarNote.DEFAULT_COLOR) : SidecarNote.DEFAULT_COLOR;
                int fontFamily = o.has("fontFamily") ? o.optInt("fontFamily", SidecarNote.DEFAULT_FONT_FAMILY) : SidecarNote.DEFAULT_FONT_FAMILY;
                int fontStyleFlags = o.has("fontStyleFlags") ? o.optInt("fontStyleFlags", SidecarNote.DEFAULT_FONT_STYLE_FLAGS) : SidecarNote.DEFAULT_FONT_STYLE_FLAGS;
                float fontSize = o.has("fontSize") ? (float) o.optDouble("fontSize", SidecarNote.DEFAULT_FONT_SIZE) : SidecarNote.DEFAULT_FONT_SIZE;
                float lineHeight = o.has("lineHeight") ? (float) o.optDouble("lineHeight", SidecarNote.DEFAULT_LINE_HEIGHT) : SidecarNote.DEFAULT_LINE_HEIGHT;
                float textIndentPt = o.has("textIndentPt") ? (float) o.optDouble("textIndentPt", SidecarNote.DEFAULT_TEXT_INDENT_PT) : SidecarNote.DEFAULT_TEXT_INDENT_PT;
                int bgColor = o.has("backgroundColor") ? o.optInt("backgroundColor", SidecarNote.DEFAULT_BACKGROUND_COLOR) : SidecarNote.DEFAULT_BACKGROUND_COLOR;
                float bgOpacity = o.has("backgroundOpacity") ? (float) o.optDouble("backgroundOpacity", SidecarNote.DEFAULT_BACKGROUND_OPACITY) : SidecarNote.DEFAULT_BACKGROUND_OPACITY;
                int borderColor = o.has("borderColor") ? o.optInt("borderColor", SidecarNote.DEFAULT_BORDER_COLOR) : SidecarNote.DEFAULT_BORDER_COLOR;
                float borderWidthPt = o.has("borderWidthPt") ? (float) o.optDouble("borderWidthPt", SidecarNote.DEFAULT_BORDER_WIDTH_PT) : SidecarNote.DEFAULT_BORDER_WIDTH_PT;
                int borderStyle = o.has("borderStyle") ? o.optInt("borderStyle", SidecarNote.DEFAULT_BORDER_STYLE) : SidecarNote.DEFAULT_BORDER_STYLE;
                float borderRadiusPt = o.has("borderRadiusPt") ? (float) o.optDouble("borderRadiusPt", SidecarNote.DEFAULT_BORDER_RADIUS_PT) : SidecarNote.DEFAULT_BORDER_RADIUS_PT;
                boolean lockPositionSize = o.has("lockPositionSize") && o.optBoolean("lockPositionSize", SidecarNote.DEFAULT_LOCK_POSITION_SIZE);
                boolean lockContents = o.has("lockContents") && o.optBoolean("lockContents", SidecarNote.DEFAULT_LOCK_CONTENTS);
                int rotationDeg = o.has("rotationDeg") ? o.optInt("rotationDeg", SidecarNote.DEFAULT_ROTATION_DEG) : SidecarNote.DEFAULT_ROTATION_DEG;
                boolean userResized = o.has("userResized") && o.optBoolean("userResized", false);
                notes.add(new SidecarNote(id, pageIndex, layout, bounds, text, createdAt, color, fontFamily, fontStyleFlags, fontSize, lineHeight, textIndentPt, userResized, bgColor, bgOpacity, borderColor, borderWidthPt, borderStyle, borderRadiusPt, lockPositionSize, lockContents, rotationDeg));
            }
        }

        return new SidecarBundle(docId, version, ink, highlights, notes);
    }

    /** Inserts the bundle rows into the given doc id and returns counts. */
    @NonNull
    public static ImportStats importIntoDoc(@NonNull String docId,
                                           @NonNull SidecarAnnotationStore store,
                                           @NonNull SidecarBundle bundle) {
        if (bundle.ink.isEmpty() && bundle.highlights.isEmpty() && bundle.notes.isEmpty()) {
            return new ImportStats(0, 0, 0);
        }
        store.insertInk(docId, bundle.ink);
        store.insertHighlights(docId, bundle.highlights);
        store.insertNotes(docId, bundle.notes);
        return new ImportStats(bundle.ink.size(), bundle.highlights.size(), bundle.notes.size());
    }

    @NonNull
    private static byte[] readAllBytes(@NonNull InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}

