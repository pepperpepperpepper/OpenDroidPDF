package org.opendroidpdf.app.sidecar;

import android.graphics.PointF;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.TextWord;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Lightweight helpers for turning extracted page text into "text quote" anchors and re-deriving
 * highlight geometry from an anchored text match.
 *
 * <p>This intentionally avoids EPUB/DOM-specific anchors for now and implements the common
 * "exact + prefix + suffix" text-quote selector pattern with whitespace normalization.</p>
 */
final class TextAnchorUtils {
    static final int DEFAULT_CONTEXT_CHARS = 64;

    private TextAnchorUtils() {}

    static final class WordRef {
        final int lineIndex;
        @NonNull final RectF bounds;
        @NonNull final String text;

        WordRef(int lineIndex, @NonNull RectF bounds, @NonNull String text) {
            this.lineIndex = lineIndex;
            this.bounds = bounds;
            this.text = text;
        }
    }

    static final class PageTextIndex {
        @NonNull final String text;
        @NonNull final int[] charToWordIndex;
        @NonNull final WordRef[] words;

        PageTextIndex(@NonNull String text, @NonNull int[] charToWordIndex, @NonNull WordRef[] words) {
            this.text = text;
            this.charToWordIndex = charToWordIndex;
            this.words = words;
        }
    }

    static final class QuoteMatch {
        final int start;
        final int end;
        final int score;
        @NonNull final PointF[] quadPoints;
        @NonNull final RectF bounds;

        QuoteMatch(int start, int end, int score, @NonNull PointF[] quadPoints, @NonNull RectF bounds) {
            this.start = start;
            this.end = end;
            this.score = score;
            this.quadPoints = quadPoints;
            this.bounds = bounds;
        }
    }

    @Nullable
    static String normalizeWhitespace(@Nullable String s) {
        if (s == null) return null;
        String normalized = s.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @NonNull
    static PageTextIndex buildIndex(@Nullable TextWord[][] textLines) {
        if (textLines == null || textLines.length == 0) {
            return new PageTextIndex("", new int[0], new WordRef[0]);
        }

        ArrayList<WordRef> words = new ArrayList<>();
        StringBuilder sb = new StringBuilder(2048);
        int[] map = new int[2048];
        int mapLen = 0;
        boolean lastWasSpace = true;

        for (int li = 0; li < textLines.length; li++) {
            TextWord[] line = textLines[li];
            if (line == null) continue;
            for (TextWord w : line) {
                if (w == null) continue;
                String text = (w.w != null) ? w.w : "";
                int wordIndex = words.size();
                words.add(new WordRef(li, new RectF(w), text));
                for (int ci = 0; ci < text.length(); ci++) {
                    char c = text.charAt(ci);
                    if (Character.isWhitespace(c)) {
                        if (!lastWasSpace) {
                            sb.append(' ');
                            if (mapLen == map.length) map = Arrays.copyOf(map, map.length * 2);
                            map[mapLen++] = -1;
                            lastWasSpace = true;
                        }
                    } else {
                        sb.append(c);
                        if (mapLen == map.length) map = Arrays.copyOf(map, map.length * 2);
                        map[mapLen++] = wordIndex;
                        lastWasSpace = false;
                    }
                }
            }
            // Treat line breaks as whitespace.
            if (!lastWasSpace) {
                sb.append(' ');
                if (mapLen == map.length) map = Arrays.copyOf(map, map.length * 2);
                map[mapLen++] = -1;
                lastWasSpace = true;
            }
        }

        // Strip trailing space.
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
            sb.deleteCharAt(sb.length() - 1);
            if (mapLen > 0) mapLen--;
        }

        String text = sb.toString();
        int[] finalMap = mapLen == map.length ? map : Arrays.copyOf(map, mapLen);
        return new PageTextIndex(text, finalMap, words.toArray(new WordRef[0]));
    }

    @Nullable
    static QuoteMatch bestMatchByBounds(@NonNull PageTextIndex index,
                                        @NonNull String quote,
                                        @NonNull RectF selectionBounds) {
        if (quote.isEmpty() || index.text.isEmpty()) return null;

        int bestScore = Integer.MIN_VALUE;
        QuoteMatch best = null;

        int from = 0;
        while (true) {
            int pos = index.text.indexOf(quote, from);
            if (pos < 0) break;
            int end = pos + quote.length();
            PointF[] quads = quadPointsForRange(index, pos, end);
            RectF bounds = boundsFromQuads(quads);
            if (bounds == null || bounds.isEmpty()) {
                from = pos + 1;
                continue;
            }
            int score = overlapScore(selectionBounds, bounds);
            if (score > bestScore) {
                bestScore = score;
                best = new QuoteMatch(pos, end, score, quads, bounds);
            }
            from = pos + 1;
        }

        return best;
    }

    @Nullable
    static QuoteMatch bestMatchByContext(@NonNull PageTextIndex index,
                                         @NonNull String quote,
                                         @Nullable String quotePrefix,
                                         @Nullable String quoteSuffix) {
        if (quote.isEmpty() || index.text.isEmpty()) return null;

        String prefix = quotePrefix;
        String suffix = quoteSuffix;

        int bestScore = Integer.MIN_VALUE;
        QuoteMatch best = null;

        int from = 0;
        while (true) {
            int pos = index.text.indexOf(quote, from);
            if (pos < 0) break;
            int end = pos + quote.length();

            int score = 0;
            if (prefix != null && !prefix.isEmpty()) {
                int start = Math.max(0, pos - prefix.length());
                String before = index.text.substring(start, pos);
                score += Math.max(
                        commonSuffixLength(prefix, before),
                        commonSuffixLength(prefix.trim(), before.trim()));
            }
            if (suffix != null && !suffix.isEmpty()) {
                int endLimit = Math.min(index.text.length(), end + suffix.length());
                String after = index.text.substring(end, endLimit);
                score += Math.max(
                        commonPrefixLength(suffix, after),
                        commonPrefixLength(suffix.trim(), after.trim()));
            }

            PointF[] quads = quadPointsForRange(index, pos, end);
            RectF bounds = boundsFromQuads(quads);
            if (bounds == null || bounds.isEmpty()) {
                from = pos + 1;
                continue;
            }

            if (score > bestScore) {
                bestScore = score;
                best = new QuoteMatch(pos, end, score, quads, bounds);
            }
            from = pos + 1;
        }

        return best;
    }

    @Nullable
    static String prefixContext(@NonNull PageTextIndex index, int start, int maxChars) {
        if (maxChars <= 0) return null;
        int s = Math.max(0, start - maxChars);
        if (s >= start) return null;
        String prefix = index.text.substring(s, start);
        return prefix.isEmpty() ? null : prefix;
    }

    @Nullable
    static String suffixContext(@NonNull PageTextIndex index, int end, int maxChars) {
        if (maxChars <= 0) return null;
        int e = Math.min(index.text.length(), end + maxChars);
        if (end >= e) return null;
        String suffix = index.text.substring(end, e);
        return suffix.isEmpty() ? null : suffix;
    }

    @NonNull
    static PointF[] quadPointsForRange(@NonNull PageTextIndex index, int start, int end) {
        if (start < 0 || end <= start || start >= index.text.length()) return new PointF[0];
        end = Math.min(end, index.text.length());

        int minWord = Integer.MAX_VALUE;
        int maxWord = -1;
        for (int i = start; i < end && i < index.charToWordIndex.length; i++) {
            int wi = index.charToWordIndex[i];
            if (wi >= 0) {
                if (wi < minWord) minWord = wi;
                if (wi > maxWord) maxWord = wi;
            }
        }
        if (maxWord < 0 || minWord == Integer.MAX_VALUE) return new PointF[0];

        int maxLine = 0;
        for (int wi = minWord; wi <= maxWord && wi < index.words.length; wi++) {
            maxLine = Math.max(maxLine, index.words[wi].lineIndex);
        }

        RectF[] lineRects = new RectF[maxLine + 1];
        for (int wi = minWord; wi <= maxWord && wi < index.words.length; wi++) {
            WordRef w = index.words[wi];
            RectF r = lineRects[w.lineIndex];
            if (r == null) {
                lineRects[w.lineIndex] = new RectF(w.bounds);
            } else {
                r.union(w.bounds);
            }
        }

        ArrayList<PointF> out = new ArrayList<>(lineRects.length * 4);
        for (RectF r : lineRects) {
            if (r == null || r.isEmpty()) continue;
            out.add(new PointF(r.left, r.bottom));
            out.add(new PointF(r.right, r.bottom));
            out.add(new PointF(r.right, r.top));
            out.add(new PointF(r.left, r.top));
        }

        return out.toArray(new PointF[0]);
    }

    @Nullable
    static RectF boundsFromQuads(@Nullable PointF[] quads) {
        if (quads == null || quads.length < 4) return null;
        RectF out = new RectF();
        boolean set = false;
        for (PointF p : quads) {
            if (p == null) continue;
            if (!set) {
                out.set(p.x, p.y, p.x, p.y);
                set = true;
            } else {
                out.union(p.x, p.y);
            }
        }
        return set ? out : null;
    }

    private static int overlapScore(@NonNull RectF a, @NonNull RectF b) {
        RectF inter = new RectF(a);
        boolean ok = inter.intersect(b);
        if (!ok) {
            float dx = a.centerX() - b.centerX();
            float dy = a.centerY() - b.centerY();
            float dist2 = dx * dx + dy * dy;
            return (int) Math.max(-1_000_000, -dist2);
        }
        return (int) (inter.width() * inter.height());
    }

    private static int commonSuffixLength(@NonNull String a, @NonNull String b) {
        int i = a.length() - 1;
        int j = b.length() - 1;
        int count = 0;
        while (i >= 0 && j >= 0) {
            if (a.charAt(i) != b.charAt(j)) break;
            count++;
            i--;
            j--;
        }
        return count;
    }

    private static int commonPrefixLength(@NonNull String a, @NonNull String b) {
        int max = Math.min(a.length(), b.length());
        int count = 0;
        for (int i = 0; i < max; i++) {
            if (a.charAt(i) != b.charAt(i)) break;
            count++;
        }
        return count;
    }
}
