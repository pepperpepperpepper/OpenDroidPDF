package org.opendroidpdf.app.annotation;

import org.opendroidpdf.MuPDFPageView;

final class TextAnnotationStyleApplier {
    private TextAnnotationStyleApplier() {
    }

    static float selectedFontSizeOrDefault(MuPDFPageView pageView, float defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationFontSizeOrDefault(defaultValue);
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static int selectedAlignmentOrDefault(MuPDFPageView pageView, int defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationAlignmentOrDefault();
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static boolean selectedLockPositionSizeOrDefault(MuPDFPageView pageView, boolean defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationLockPositionSizeOrDefault();
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static boolean selectedLockContentsOrDefault(MuPDFPageView pageView, boolean defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationLockContentsOrDefault();
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static int selectedRotationDegOrDefault(MuPDFPageView pageView, int defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationRotationDegOrDefault();
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static int selectedFontFamilyOrDefault(MuPDFPageView pageView, int defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationFontFamilyOrDefault(defaultValue);
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static int selectedStyleFlagsOrDefault(MuPDFPageView pageView, int defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationStyleFlagsOrDefault(defaultValue);
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static float selectedLineHeightOrDefault(MuPDFPageView pageView, float defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationLineHeightOrDefault(defaultValue);
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static float selectedTextIndentPtOrDefault(MuPDFPageView pageView, float defaultValue) {
        try {
            return pageView.textAnnotationDelegate().selectedTextAnnotationTextIndentPtOrDefault(defaultValue);
        } catch (Throwable ignore) {
            return defaultValue;
        }
    }

    static boolean applyTextStyle(MuPDFPageView pageView, float fontSize, int colorIndex) {
        try {
            return pageView.textAnnotationDelegate().applyTextStyleToSelectedTextAnnotation(fontSize, colorIndex);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyParagraph(MuPDFPageView pageView, float lineHeight, float textIndentPt) {
        try {
            return pageView.textAnnotationDelegate().applyTextParagraphToSelectedTextAnnotation(lineHeight, textIndentPt);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyBackground(MuPDFPageView pageView, int colorIndex, float opacity01) {
        try {
            return pageView.textAnnotationDelegate().applyTextBackgroundToSelectedTextAnnotation(colorIndex, opacity01);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyBorder(MuPDFPageView pageView, int colorIndex, float widthPt, boolean dashed, float radiusPt) {
        try {
            return pageView.textAnnotationDelegate().applyTextBorderToSelectedTextAnnotation(colorIndex, widthPt, dashed, radiusPt);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyAlignment(MuPDFPageView pageView, int alignValue) {
        try {
            return pageView.textAnnotationDelegate().applyTextAlignmentToSelectedTextAnnotation(alignValue);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean fitToText(MuPDFPageView pageView) {
        try {
            return pageView.textAnnotationDelegate().fitSelectedTextAnnotationToText();
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyFontFamily(MuPDFPageView pageView, int fontFamily) {
        try {
            return pageView.textAnnotationDelegate().applyTextFontFamilyToSelectedTextAnnotation(fontFamily);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyStyleFlags(MuPDFPageView pageView, int flags) {
        try {
            return pageView.textAnnotationDelegate().applyTextStyleFlagsToSelectedTextAnnotation(flags);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyRotation(MuPDFPageView pageView, int rotationDeg) {
        try {
            return pageView.textAnnotationDelegate().applyTextRotationToSelectedTextAnnotation(rotationDeg);
        } catch (Throwable ignore) {
            return false;
        }
    }

    static boolean applyLocks(MuPDFPageView pageView, boolean lockPositionSize, boolean lockContents) {
        try {
            return pageView.textAnnotationDelegate().applyTextLocksToSelectedTextAnnotation(lockPositionSize, lockContents);
        } catch (Throwable ignore) {
            return false;
        }
    }
}

