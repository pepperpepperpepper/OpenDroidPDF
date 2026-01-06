package org.opendroidpdf.app.fillsign;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.PointF;
import android.graphics.RectF;
import android.text.InputType;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements "Fill & Sign" convenience workflows:
 * - reusable signature + initials placement (Ink annotations)
 * - quick stamps (check/X/date/name)
 *
 * <p>Placement consumes touch events while active so page scroll/fling/zoom does not interfere.</p>
 */
public final class FillSignController {

    public interface PageViewProvider {
        @Nullable MuPDFPageView currentPageView();
    }

    private enum Mode {
        NONE,
        PLACE_SIGNATURE,
        PLACE_INITIALS,
        PLACE_CHECK,
        PLACE_CROSS,
        PLACE_DATE,
        PLACE_NAME
    }

    private final Activity activity;
    private final PageViewProvider pageViewProvider;
    private final FillSignStore store;

    private Mode mode = Mode.NONE;
    @Nullable private SignatureTemplate template;

    // Placement state (doc coords).
    private int activePageNumber = -1;
    @Nullable private RectF boundsDoc;
    private float rotationRad = 0f;

    // Pointer tracking.
    private int primaryPointerId = -1;
    private int secondaryPointerId = -1;
    private float lastDocX = 0f;
    private float lastDocY = 0f;

    // Two-pointer transform baseline.
    @Nullable private RectF startBoundsDoc;
    private float startRotationRad = 0f;
    private float gestureStartCenterX = 0f;
    private float gestureStartCenterY = 0f;
    private float rectStartCenterX = 0f;
    private float rectStartCenterY = 0f;
    private float gestureStartDist = 0f;
    private float gestureStartAngle = 0f;

    public FillSignController(@NonNull Activity activity, @NonNull PageViewProvider pageViewProvider) {
        this.activity = activity;
        this.pageViewProvider = pageViewProvider;
        this.store = new FillSignStore(activity);
    }

    public boolean isActive() { return mode != Mode.NONE; }

    public void requestAction(@NonNull FillSignAction action) {
        if (action == null) return;
        switch (action) {
            case SIGNATURE:
                requestSignature(false);
                return;
            case INITIALS:
                requestSignature(true);
                return;
            case CHECKMARK:
                begin(Mode.PLACE_CHECK, builtinCheckTemplate());
                return;
            case CROSS:
                begin(Mode.PLACE_CROSS, builtinCrossTemplate());
                return;
            case DATE:
                begin(Mode.PLACE_DATE, null);
                return;
            case NAME:
                requestNameStamp();
                return;
            default:
                break;
        }
    }

    /**
     * Consume touch events while placing, update the overlay, and commit on ACTION_UP.
     */
    public boolean onTouchEvent(@Nullable MotionEvent event) {
        if (event == null) return false;
        if (mode == Mode.NONE) return false;

        final MuPDFPageView pageView = pageViewProvider.currentPageView();
        if (pageView == null) {
            cancelPlacement();
            return true;
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                handleDown(pageView, event);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                handlePointerDown(pageView, event);
                return true;
            case MotionEvent.ACTION_MOVE:
                handleMove(pageView, event);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                handlePointerUp(pageView, event);
                return true;
            case MotionEvent.ACTION_UP:
                handleUp(pageView, event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelPlacement();
                return true;
            default:
                return true;
        }
    }

    private void requestSignature(boolean initials) {
        SignatureTemplate existing = initials ? store.loadInitials() : store.loadSignature();
        if (existing != null) {
            begin(initials ? Mode.PLACE_INITIALS : Mode.PLACE_SIGNATURE, existing);
            return;
        }
        showCaptureDialog(initials);
    }

    private void requestNameStamp() {
        String existing = store.loadName();
        if (existing != null) {
            begin(Mode.PLACE_NAME, null);
            return;
        }

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.fill_sign_name_prompt_hint);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.fill_sign_name_prompt_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String v = input.getText() != null ? input.getText().toString().trim() : "";
                    if (v.isEmpty()) {
                        toast(activity.getString(R.string.fill_sign_name_prompt_hint));
                        return;
                    }
                    store.saveName(v);
                    begin(Mode.PLACE_NAME, null);
                })
                .show();
    }

    private void showCaptureDialog(boolean initials) {
        final SignatureCaptureView capture = new SignatureCaptureView(activity);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(initials ? R.string.fill_sign_capture_initials_title : R.string.fill_sign_capture_signature_title)
                .setView(capture)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.fill_sign_action_clear, null)
                .setPositiveButton(R.string.fill_sign_action_save, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> capture.clear());

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                SignatureTemplate template = capture.buildTemplate();
                if (template == null || template.strokes.isEmpty()) {
                    toast(activity.getString(R.string.fill_sign_capture_empty));
                    return;
                }
                if (initials) store.saveInitials(template);
                else store.saveSignature(template);
                dialog.dismiss();
                begin(initials ? Mode.PLACE_INITIALS : Mode.PLACE_SIGNATURE, template);
            });
        });

        dialog.show();
    }

    private void begin(@NonNull Mode newMode, @Nullable SignatureTemplate tpl) {
        cancelPlacement();
        mode = newMode;
        template = tpl;
        toast(activity.getString(R.string.fill_sign_place_hint));
    }

    private void handleDown(@NonNull MuPDFPageView pageView, @NonNull MotionEvent event) {
        activePageNumber = pageView.getPageNumber();
        primaryPointerId = event.getPointerId(0);
        secondaryPointerId = -1;
        startBoundsDoc = null;

        if (mode == Mode.PLACE_DATE || mode == Mode.PLACE_NAME) {
            // Wait for ACTION_UP to place.
            return;
        }

        SignatureTemplate tpl = template;
        if (tpl == null) {
            cancelPlacement();
            return;
        }

        float[] doc = docPoint(pageView, event, 0);
        if (doc == null) return;
        float docX = doc[0];
        float docY = doc[1];
        lastDocX = docX;
        lastDocY = docY;

        boundsDoc = defaultBoundsForTemplate(pageView, tpl, docX, docY);
        rotationRad = 0f;
        updateOverlay(pageView);
    }

    private void handlePointerDown(@NonNull MuPDFPageView pageView, @NonNull MotionEvent event) {
        if (mode == Mode.PLACE_DATE || mode == Mode.PLACE_NAME) return;
        RectF b = boundsDoc;
        if (b == null) return;
        if (secondaryPointerId != -1) return;
        if (event.getPointerCount() < 2) return;

        int idx = event.getActionIndex();
        secondaryPointerId = event.getPointerId(idx);

        int i0 = event.findPointerIndex(primaryPointerId);
        int i1 = event.findPointerIndex(secondaryPointerId);
        if (i0 < 0 || i1 < 0) return;

        float[] p0 = docPoint(pageView, event, i0);
        float[] p1 = docPoint(pageView, event, i1);
        if (p0 == null || p1 == null) return;

        gestureStartCenterX = (p0[0] + p1[0]) * 0.5f;
        gestureStartCenterY = (p0[1] + p1[1]) * 0.5f;
        rectStartCenterX = (b.left + b.right) * 0.5f;
        rectStartCenterY = (b.top + b.bottom) * 0.5f;
        startBoundsDoc = new RectF(b);
        startRotationRad = rotationRad;

        float dx = p1[0] - p0[0];
        float dy = p1[1] - p0[1];
        gestureStartDist = (float) Math.hypot(dx, dy);
        gestureStartAngle = (float) Math.atan2(dy, dx);
    }

    private void handleMove(@NonNull MuPDFPageView pageView, @NonNull MotionEvent event) {
        if (activePageNumber >= 0 && pageView.getPageNumber() != activePageNumber) {
            cancelPlacement();
            return;
        }
        if (mode == Mode.PLACE_DATE || mode == Mode.PLACE_NAME) {
            return;
        }
        RectF b = boundsDoc;
        if (b == null) return;

        if (secondaryPointerId != -1 && event.getPointerCount() >= 2 && startBoundsDoc != null && gestureStartDist > 0f) {
            int i0 = event.findPointerIndex(primaryPointerId);
            int i1 = event.findPointerIndex(secondaryPointerId);
            if (i0 < 0 || i1 < 0) return;

            float[] p0 = docPoint(pageView, event, i0);
            float[] p1 = docPoint(pageView, event, i1);
            if (p0 == null || p1 == null) return;

            float dx = p1[0] - p0[0];
            float dy = p1[1] - p0[1];
            float dist = (float) Math.hypot(dx, dy);
            float angle = (float) Math.atan2(dy, dx);

            float scaleFactor = dist / gestureStartDist;
            scaleFactor = Math.max(0.2f, Math.min(8.0f, scaleFactor));

            float centerX = (p0[0] + p1[0]) * 0.5f;
            float centerY = (p0[1] + p1[1]) * 0.5f;
            float deltaCenterX = centerX - gestureStartCenterX;
            float deltaCenterY = centerY - gestureStartCenterY;

            float newW = startBoundsDoc.width() * scaleFactor;
            float newH = startBoundsDoc.height() * scaleFactor;
            float newCenterX = rectStartCenterX + deltaCenterX;
            float newCenterY = rectStartCenterY + deltaCenterY;

            rotationRad = startRotationRad + (angle - gestureStartAngle);
            boundsDoc = new RectF(newCenterX - newW * 0.5f, newCenterY - newH * 0.5f,
                    newCenterX + newW * 0.5f, newCenterY + newH * 0.5f);
        } else {
            int i0 = event.findPointerIndex(primaryPointerId);
            if (i0 < 0) return;
            float[] p = docPoint(pageView, event, i0);
            if (p == null) return;
            float docX = p[0];
            float docY = p[1];
            float dx = docX - lastDocX;
            float dy = docY - lastDocY;
            lastDocX = docX;
            lastDocY = docY;
            boundsDoc = new RectF(b);
            boundsDoc.offset(dx, dy);
        }

        boundsDoc = clampToDoc(pageView, boundsDoc);
        updateOverlay(pageView);
    }

    private void handlePointerUp(@NonNull MuPDFPageView pageView, @NonNull MotionEvent event) {
        int idx = event.getActionIndex();
        int upId = event.getPointerId(idx);
        if (upId == secondaryPointerId) {
            secondaryPointerId = -1;
            startBoundsDoc = null;
            // Reset single-pointer baseline.
            int i0 = event.findPointerIndex(primaryPointerId);
            if (i0 >= 0) {
                float[] p = docPoint(pageView, event, i0);
                if (p != null) {
                    lastDocX = p[0];
                    lastDocY = p[1];
                }
            }
        } else if (upId == primaryPointerId) {
            // Promote the secondary pointer if present.
            if (secondaryPointerId != -1) {
                primaryPointerId = secondaryPointerId;
                secondaryPointerId = -1;
                startBoundsDoc = null;
            }
        }
    }

    private void handleUp(@NonNull MuPDFPageView pageView, @NonNull MotionEvent event) {
        if (mode == Mode.PLACE_DATE || mode == Mode.PLACE_NAME) {
            float[] doc = docPoint(pageView, event, event.findPointerIndex(primaryPointerId >= 0 ? primaryPointerId : 0));
            if (doc != null) {
                placeTextStamp(pageView, doc[0], doc[1], mode == Mode.PLACE_DATE);
            }
            cancelPlacement();
            return;
        }

        RectF b = boundsDoc;
        SignatureTemplate tpl = template;
        if (b != null && tpl != null) {
            PointF[][] arcs = transformTemplate(tpl, b, rotationRad);
            pageView.addInkAnnotationFromUi(arcs);
        }
        cancelPlacement();
    }

    private void placeTextStamp(@NonNull MuPDFPageView pageView, float docX, float docY, boolean isDate) {
        String text;
        if (isDate) {
            java.text.DateFormat fmt = android.text.format.DateFormat.getDateFormat(activity);
            text = fmt.format(new java.util.Date());
        } else {
            String name = store.loadName();
            if (name == null) return;
            text = name;
        }

        float width = 220f + (Math.min(40f, text.length() * 6f));
        float height = 64f;
        RectF r = new RectF(docX - width * 0.5f, docY - height * 0.5f, docX + width * 0.5f, docY + height * 0.5f);
        r = clampToDoc(pageView, r);

        Annotation annot = new Annotation(r.left, r.top, r.right, r.bottom, Annotation.Type.FREETEXT, null, text);
        pageView.addTextAnnotationFromUi(annot);
    }

    private void updateOverlay(@NonNull MuPDFPageView pageView) {
        RectF b = boundsDoc;
        SignatureTemplate tpl = template;
        if (b == null || tpl == null) {
            pageView.setFillSignPlacementOverlay(null);
            return;
        }
        PointF[][] arcs = transformTemplate(tpl, b, rotationRad);
        pageView.setFillSignPlacementOverlay(new FillSignPlacementOverlay(b, rotationRad, arcs));
    }

    private void cancelPlacement() {
        MuPDFPageView pageView = pageViewProvider.currentPageView();
        if (pageView != null) {
            try { pageView.setFillSignPlacementOverlay(null); } catch (Throwable ignore) {}
        }
        mode = Mode.NONE;
        template = null;
        activePageNumber = -1;
        boundsDoc = null;
        rotationRad = 0f;
        primaryPointerId = -1;
        secondaryPointerId = -1;
        startBoundsDoc = null;
    }

    @Nullable
    private static float[] docPoint(@NonNull MuPDFPageView pageView, @NonNull MotionEvent e, int pointerIndex) {
        float scale = pageView.getScale();
        if (scale <= 0f) return null;
        if (pointerIndex < 0 || pointerIndex >= e.getPointerCount()) return null;
        float x = (e.getX(pointerIndex) - pageView.getLeft()) / scale;
        float y = (e.getY(pointerIndex) - pageView.getTop()) / scale;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return null;
        return new float[] { x, y };
    }

    @NonNull
    private static RectF defaultBoundsForTemplate(@NonNull MuPDFPageView pageView,
                                                 @NonNull SignatureTemplate tpl,
                                                 float centerX,
                                                 float centerY) {
        float scale = pageView.getScale();
        float docW = pageView.getWidth() / (scale > 0f ? scale : 1f);
        float docH = pageView.getHeight() / (scale > 0f ? scale : 1f);
        float targetW = Math.min(0.62f * docW, 360f);
        targetW = Math.max(160f, targetW);
        float targetH = targetW / Math.max(0.3f, Math.min(3.5f, tpl.aspectRatio));
        RectF r = new RectF(centerX - targetW * 0.5f, centerY - targetH * 0.5f, centerX + targetW * 0.5f, centerY + targetH * 0.5f);
        return clampToDoc(pageView, r);
    }

    @NonNull
    private static RectF clampToDoc(@NonNull MuPDFPageView pageView, @NonNull RectF r) {
        float scale = pageView.getScale();
        float docW = pageView.getWidth() / (scale > 0f ? scale : 1f);
        float docH = pageView.getHeight() / (scale > 0f ? scale : 1f);

        float left = Math.min(r.left, r.right);
        float right = Math.max(r.left, r.right);
        float top = Math.min(r.top, r.bottom);
        float bottom = Math.max(r.top, r.bottom);

        float minEdge = 24f;
        if ((right - left) < minEdge) {
            float cx = (left + right) * 0.5f;
            left = cx - minEdge * 0.5f;
            right = cx + minEdge * 0.5f;
        }
        if ((bottom - top) < minEdge) {
            float cy = (top + bottom) * 0.5f;
            top = cy - minEdge * 0.5f;
            bottom = cy + minEdge * 0.5f;
        }

        // Clamp to doc bounds by shifting the rect back inside.
        if (left < 0f) {
            right -= left;
            left = 0f;
        }
        if (top < 0f) {
            bottom -= top;
            top = 0f;
        }
        if (right > docW) {
            float overflow = right - docW;
            left -= overflow;
            right = docW;
        }
        if (bottom > docH) {
            float overflow = bottom - docH;
            top -= overflow;
            bottom = docH;
        }

        left = Math.max(0f, Math.min(left, docW));
        right = Math.max(0f, Math.min(right, docW));
        top = Math.max(0f, Math.min(top, docH));
        bottom = Math.max(0f, Math.min(bottom, docH));

        return new RectF(left, top, right, bottom);
    }

    @NonNull
    private static PointF[][] transformTemplate(@NonNull SignatureTemplate tpl, @NonNull RectF boundsDoc, float rotationRad) {
        float w = boundsDoc.width();
        float h = boundsDoc.height();
        float cx = (boundsDoc.left + boundsDoc.right) * 0.5f;
        float cy = (boundsDoc.top + boundsDoc.bottom) * 0.5f;
        float cos = (float) Math.cos(rotationRad);
        float sin = (float) Math.sin(rotationRad);

        List<List<PointF>> strokes = tpl.strokes;
        ArrayList<PointF[]> out = new ArrayList<>(strokes.size());
        for (List<PointF> stroke : strokes) {
            if (stroke == null || stroke.size() < 2) continue;
            PointF[] pts = new PointF[stroke.size()];
            int n = 0;
            for (PointF p : stroke) {
                if (p == null) continue;
                float x = boundsDoc.left + (p.x * w);
                float y = boundsDoc.top + (p.y * h);
                float dx = x - cx;
                float dy = y - cy;
                float rx = (dx * cos) - (dy * sin) + cx;
                float ry = (dx * sin) + (dy * cos) + cy;
                if (!Float.isFinite(rx) || !Float.isFinite(ry)) continue;
                pts[n++] = new PointF(rx, ry);
            }
            if (n >= 2) {
                if (n != pts.length) {
                    PointF[] trimmed = new PointF[n];
                    System.arraycopy(pts, 0, trimmed, 0, n);
                    pts = trimmed;
                }
                out.add(pts);
            }
        }
        return out.toArray(new PointF[0][]);
    }

    @NonNull
    private static SignatureTemplate builtinCheckTemplate() {
        List<PointF> stroke = new ArrayList<>();
        stroke.add(new PointF(0.10f, 0.55f));
        stroke.add(new PointF(0.38f, 0.86f));
        stroke.add(new PointF(0.92f, 0.18f));
        List<List<PointF>> strokes = new ArrayList<>();
        strokes.add(stroke);
        return new SignatureTemplate(1.0f, strokes);
    }

    @NonNull
    private static SignatureTemplate builtinCrossTemplate() {
        List<List<PointF>> strokes = new ArrayList<>();
        List<PointF> a = new ArrayList<>();
        a.add(new PointF(0.12f, 0.12f));
        a.add(new PointF(0.88f, 0.88f));
        strokes.add(a);
        List<PointF> b = new ArrayList<>();
        b.add(new PointF(0.88f, 0.12f));
        b.add(new PointF(0.12f, 0.88f));
        strokes.add(b);
        return new SignatureTemplate(1.0f, strokes);
    }

    private void toast(@NonNull String msg) {
        try {
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }
}
