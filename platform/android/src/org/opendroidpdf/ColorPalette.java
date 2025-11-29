package org.opendroidpdf;

import android.graphics.Color;

public class ColorPalette {
    private static final int[][] DEFAULT_RGB = {
        {0,0,0},
        {0,34,110},
        {4,70,110},
        {75,28,99},
        {52,76,5},
        {136,131,0},
        {121,66,3},
        {98,4,4},

        {49,49,49},
        {0, 62, 204},
        {0, 153, 204},
        {153,  51, 204},
        {102, 153,   0},
        {255, 246,   1},
        {255, 136,   0},
        {204,   0,   0},

        {91,91,91},
        {52, 102, 228},
        {51, 181, 229},
        {170, 102, 204},
        {153, 204,  0},
        {238, 255,  51},
        {255, 187,  51},
        {255,  68,  68}
    };

    private static float[][] paletteRGB;

    public static float getR(int number) {
        float[][] palette = getPalette();
        int index = clamp(number, palette.length);
        return palette[index][0];
    }

    public static float getG(int number) {
        float[][] palette = getPalette();
        int index = clamp(number, palette.length);
        return palette[index][1];
    }

    public static float getB(int number) {
        float[][] palette = getPalette();
        int index = clamp(number, palette.length);
        return palette[index][2];
    }

    public static int getHex(int number) {
        float[][] palette = getPalette();
        int index = clamp(number, palette.length);
        int r = Math.round(palette[index][0] * 255f);
        int g = Math.round(palette[index][1] * 255f);
        int b = Math.round(palette[index][2] * 255f);
        return Color.argb(255, r, g, b);
    }

    public static CharSequence[] getColorNumbers(){
        float[][] palette = getPalette();
        CharSequence[] colorNumbers = new CharSequence[palette.length];
        for(int i = 0; i < palette.length; i++) {
            colorNumbers[i] = Integer.toString(i);
        }
        return colorNumbers;
    }

    private static float[][] getPalette() {
        if (paletteRGB == null) {
            paletteRGB = loadPalette();
        }
        return paletteRGB;
    }

    private static float[][] loadPalette() {
        if (OpenDroidPDFApp.getAppResources() != null) {
            try {
                int[] colors = OpenDroidPDFApp.getAppResources().getIntArray(R.array.pen_palette_colors);
                if (colors != null && colors.length > 0) {
                    float[][] palette = new float[colors.length][3];
                    for (int i = 0; i < colors.length; i++) {
                        int color = colors[i];
                        palette[i][0] = Color.red(color) / 255f;
                        palette[i][1] = Color.green(color) / 255f;
                        palette[i][2] = Color.blue(color) / 255f;
                    }
                    return palette;
                }
            } catch (Exception ignore) {
            }
        }
        return copyDefaultPalette();
    }

    private static float[][] copyDefaultPalette() {
        float[][] palette = new float[DEFAULT_RGB.length][3];
        for (int i = 0; i < DEFAULT_RGB.length; i++) {
            palette[i][0] = DEFAULT_RGB[i][0] / 255f;
            palette[i][1] = DEFAULT_RGB[i][1] / 255f;
            palette[i][2] = DEFAULT_RGB[i][2] / 255f;
        }
        return palette;
    }

    private static int clamp(int number, int length) {
        if (length <= 0) {
            return 0;
        }
        if (number < 0) {
            return 0;
        }
        if (number >= length) {
            return length - 1;
        }
        return number;
    }
}
