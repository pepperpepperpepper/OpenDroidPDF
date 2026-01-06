package org.opendroidpdf.app.fillsign;

import android.content.Context;
import android.graphics.PointF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple local persistence for Fill & Sign user assets (signature/initials templates + name).
 *
 * <p>Stored in app-private internal storage so smokes can inject fixtures via `run-as` without
 * requiring external storage permissions.</p>
 */
public final class FillSignStore {
    private static final String FILE_SIGNATURE = "fill_sign_signature.json";
    private static final String FILE_INITIALS = "fill_sign_initials.json";
    private static final String FILE_NAME = "fill_sign_name.txt";

    private final Context appContext;

    public FillSignStore(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean hasSignature() { return file(FILE_SIGNATURE).exists(); }
    public boolean hasInitials() { return file(FILE_INITIALS).exists(); }

    @Nullable
    public SignatureTemplate loadSignature() { return loadTemplate(FILE_SIGNATURE); }

    @Nullable
    public SignatureTemplate loadInitials() { return loadTemplate(FILE_INITIALS); }

    public void saveSignature(@NonNull SignatureTemplate template) { saveTemplate(FILE_SIGNATURE, template); }
    public void saveInitials(@NonNull SignatureTemplate template) { saveTemplate(FILE_INITIALS, template); }

    @Nullable
    public String loadName() {
        File f = file(FILE_NAME);
        if (!f.exists()) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String s = r.readLine();
            if (s == null) return null;
            s = s.trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    public void saveName(@NonNull String name) {
        String n = name != null ? name.trim() : "";
        try (FileOutputStream out = new FileOutputStream(file(FILE_NAME))) {
            out.write(n.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignore) {
        }
    }

    @Nullable
    private SignatureTemplate loadTemplate(@NonNull String fname) {
        File f = file(fname);
        if (!f.exists()) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            float aspectRatio = (float) root.optDouble("aspectRatio", 1.0);
            JSONArray strokesJson = root.optJSONArray("strokes");
            if (strokesJson == null) return null;
            List<List<PointF>> strokes = new ArrayList<>();
            for (int i = 0; i < strokesJson.length(); i++) {
                JSONArray strokeJson = strokesJson.optJSONArray(i);
                if (strokeJson == null) continue;
                List<PointF> pts = new ArrayList<>();
                for (int j = 0; j < strokeJson.length(); j++) {
                    JSONArray pt = strokeJson.optJSONArray(j);
                    if (pt == null || pt.length() < 2) continue;
                    float x = (float) pt.optDouble(0, Double.NaN);
                    float y = (float) pt.optDouble(1, Double.NaN);
                    if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
                    pts.add(new PointF(x, y));
                }
                if (pts.size() >= 2) strokes.add(pts);
            }
            if (strokes.isEmpty()) return null;
            return new SignatureTemplate(aspectRatio, strokes);
        } catch (Throwable t) {
            return null;
        }
    }

    private void saveTemplate(@NonNull String fname, @NonNull SignatureTemplate template) {
        if (template == null || template.strokes == null || template.strokes.isEmpty()) return;
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("aspectRatio", template.aspectRatio);
            JSONArray strokes = new JSONArray();
            for (List<PointF> stroke : template.strokes) {
                if (stroke == null || stroke.size() < 2) continue;
                JSONArray strokeJson = new JSONArray();
                for (PointF p : stroke) {
                    if (p == null) continue;
                    JSONArray pt = new JSONArray();
                    pt.put(p.x);
                    pt.put(p.y);
                    strokeJson.put(pt);
                }
                if (strokeJson.length() >= 2) strokes.put(strokeJson);
            }
            root.put("strokes", strokes);

            byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(file(fname))) {
                out.write(bytes);
            }
        } catch (Throwable ignore) {
        }
    }

    @NonNull
    private File file(@NonNull String name) {
        return new File(appContext.getFilesDir(), name);
    }
}

