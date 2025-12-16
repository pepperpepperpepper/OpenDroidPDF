package org.opendroidpdf.app.annotation;

import android.content.Context;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuItemCompat;

import org.opendroidpdf.Annotation;
import org.opendroidpdf.MuPDFPageView;
import org.opendroidpdf.PageView;
import org.opendroidpdf.R;

/**
 * Centralizes toolbar/menu wiring for annotation-related interactions so the activity can
 * delegate per-feature configuration instead of hosting all bindings itself.
 */
public class AnnotationToolbarController {

    public interface Host {
        @NonNull Context getContext();
        void showAnnotationInfo(@NonNull String message);
        void showPenSizeDialog();
        void showInkColorDialog();
        void requestSaveDialog();
        boolean isSelectedAnnotationEditable();
        @Nullable PageView getActivePageView();
        boolean hasDocumentView();
        void notifyStrokeCountChanged(int strokeCount);
        void cancelAnnotationMode();
        void confirmAnnotationChanges();
    }

    private final Host host;
    private final AnnotationModeStore modeStore;

    public AnnotationToolbarController(@NonNull Host host,
                                       @NonNull AnnotationModeStore modeStore) {
        this.host = host;
        this.modeStore = modeStore;
    }

    public void inflateAnnotationMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.annot_menu, menu);
        final PageView pageView = host.getActivePageView();
        configurePenSizeItem(menu);
        configureUndo(menu.findItem(R.id.menu_undo), pageView);
        configureCancelButton(menu, pageView, AnnotationCancelBehavior.CANCEL_DRAW);
        configurePenShortcut(menu.findItem(R.id.menu_draw));
        configureEraseVisibility(menu);
    }

    public void inflateSelectionMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.selection_menu, menu);
    }

    public void inflateAddTextAnnotationMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.add_text_annot_menu, menu);
    }

    public void inflateEditMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.edit_menu, menu);
        if (!host.isSelectedAnnotationEditable()) {
            final MenuItem editButton = menu.findItem(R.id.menu_edit);
            if (editButton != null) {
                editButton.setEnabled(false);
                editButton.setVisible(false);
            }
        }
        final PageView pageView = host.getActivePageView();
        configureCancelButton(menu, pageView, AnnotationCancelBehavior.DELETE_ANNOTATION);
    }

    public void prepareMainMenuShortcuts(@NonNull Menu menu) {
        final PageView pageView = host.getActivePageView();
        configurePenShortcut(menu.findItem(R.id.menu_draw));
        configureMainUndo(menu.findItem(R.id.menu_undo), pageView);
    }

    /**
     * Handles annotation-related toolbar/menu actions. Returns true if the event was consumed.
     */
    public boolean handleOptionsItem(@NonNull MenuItem item) {
        final PageView pageView = host.getActivePageView();
        switch (item.getItemId()) {
            case R.id.menu_undo:
                if (pageView != null) {
                    pageView.undoDraw();
                    host.notifyStrokeCountChanged(pageView.getDrawingSize());
                }
                return true;
            case R.id.menu_edit:
                if (pageView instanceof MuPDFPageView) {
                    ((MuPDFPageView) pageView).editSelectedAnnotation();
                    modeStore.enterDrawingMode();
                }
                return true;
            case R.id.menu_add_text_annot:
                modeStore.enterAddingTextMode();
                host.showAnnotationInfo(host.getContext().getString(R.string.tap_to_add_annotation));
                return true;
            case R.id.menu_erase:
                modeStore.enterErasingMode();
                return true;
            case R.id.menu_draw:
                modeStore.enterDrawingMode();
                return true;
            case R.id.menu_pen_size:
                host.showPenSizeDialog();
                return true;
            case R.id.menu_ink_color:
                host.showInkColorDialog();
                return true;
            case R.id.menu_save:
                host.requestSaveDialog();
                return true;
            case R.id.menu_highlight:
                return markupSelection(pageView, Annotation.Type.HIGHLIGHT);
            case R.id.menu_underline:
                return markupSelection(pageView, Annotation.Type.UNDERLINE);
            case R.id.menu_strikeout:
                return markupSelection(pageView, Annotation.Type.STRIKEOUT);
            case R.id.menu_copytext:
                if (pageView != null) {
                    if (pageView.hasSelection()) {
                        boolean success = pageView.copySelection();
                        host.showAnnotationInfo(success
                                ? host.getContext().getString(R.string.copied_to_clipboard)
                                : host.getContext().getString(R.string.no_text_selected));
                        modeStore.enterViewingMode();
                    } else {
                        host.showAnnotationInfo(host.getContext().getString(R.string.select_text));
                    }
                }
                return true;
            case R.id.menu_cancel:
                host.cancelAnnotationMode();
                return true;
            case R.id.menu_accept:
                host.confirmAnnotationChanges();
                return true;
            default:
                return false;
        }
    }

    public void configurePenShortcut(@Nullable MenuItem drawItem) {
        if (drawItem == null) {
            return;
        }
        final View actionView = MenuItemCompat.getActionView(drawItem);
        if (actionView == null) {
            return;
        }
        ImageButton drawButton = actionView.findViewById(R.id.draw_image_button);
        if (drawButton == null) {
            return;
        }
        final Context context = host.getContext();
        final GestureDetector.SimpleOnGestureListener gestures =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    modeStore.enterDrawingMode();
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    modeStore.enterDrawingMode();
                    host.showPenSizeDialog();
                    return true;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    modeStore.enterDrawingMode();
                    host.showPenSizeDialog();
                }
            };
        final GestureDetector detector = new GestureDetector(context, gestures);
        detector.setOnDoubleTapListener(gestures);
        drawButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean handled = detector.onTouchEvent(event);
                if (!handled && event.getAction() == MotionEvent.ACTION_UP) {
                    v.performClick();
                }
                return handled;
            }
        });
        drawButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modeStore.enterDrawingMode();
            }
        });
    }

    private void configurePenSizeItem(@NonNull Menu menu) {
        final MenuItem penSizeItem = menu.findItem(R.id.menu_pen_size);
        final MenuItem inkColorItem = menu.findItem(R.id.menu_ink_color);
        final boolean drawing = modeStore.isDrawingModeActive();
        if (penSizeItem != null) {
            penSizeItem.setVisible(drawing);
            penSizeItem.setEnabled(drawing);
        }
        if (inkColorItem != null) {
            inkColorItem.setVisible(drawing);
            inkColorItem.setEnabled(drawing);
        }
    }

    private void configureUndo(@Nullable MenuItem undoItem, @Nullable PageView pageView) {
        if (undoItem == null) {
            return;
        }
        boolean canUndo = pageView != null && pageView.canUndo();
        undoItem.setVisible(canUndo);
        undoItem.setEnabled(canUndo);
        if (undoItem.getIcon() != null) {
            undoItem.getIcon().mutate().setAlpha(canUndo ? 255 : 100);
        }
    }

    private void configureMainUndo(@Nullable MenuItem undoItem, @Nullable PageView pageView) {
        if (undoItem == null) {
            return;
        }
        boolean canUndo = pageView != null && pageView.canUndo();
        undoItem.setVisible(true);
        undoItem.setEnabled(canUndo);
        if (undoItem.getIcon() != null) {
            undoItem.getIcon().mutate().setAlpha(canUndo ? 255 : 100);
        }
    }

    private void configureEraseVisibility(@NonNull Menu menu) {
        final MenuItem drawButton = menu.findItem(R.id.menu_draw);
        final MenuItem eraseButton = menu.findItem(R.id.menu_erase);
        if (!host.hasDocumentView()) {
            if (drawButton != null) {
                drawButton.setEnabled(false);
                drawButton.setVisible(false);
            }
            if (eraseButton != null) {
                eraseButton.setEnabled(false);
                eraseButton.setVisible(false);
            }
            return;
        }
        if (modeStore.isDrawingModeActive()) {
            if (drawButton != null) {
                drawButton.setEnabled(false);
                drawButton.setVisible(false);
            }
            if (eraseButton != null) {
                eraseButton.setEnabled(true);
                eraseButton.setVisible(true);
            }
        } else if (modeStore.isErasingModeActive()) {
            if (eraseButton != null) {
                eraseButton.setEnabled(false);
                eraseButton.setVisible(false);
            }
            if (drawButton != null) {
                drawButton.setEnabled(true);
                drawButton.setVisible(true);
            }
        } else {
            if (drawButton != null) {
                drawButton.setEnabled(true);
                drawButton.setVisible(true);
            }
            if (eraseButton != null) {
                eraseButton.setEnabled(true);
                eraseButton.setVisible(true);
            }
        }
    }

    private void configureCancelButton(@NonNull Menu menu,
                                       @Nullable PageView pageView,
                                       @NonNull AnnotationCancelBehavior behavior) {
        final MenuItem cancelItem = menu.findItem(R.id.menu_cancel);
        if (cancelItem == null) {
            return;
        }
        final View actionView = MenuItemCompat.getActionView(cancelItem);
        if (actionView == null) {
            return;
        }
        final ImageButton cancelButton = actionView.findViewById(R.id.cancel_image_button);
        if (cancelButton == null) {
            return;
        }
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.showAnnotationInfo(host.getContext().getString(R.string.long_press_to_delete));
            }
        });
        cancelButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (pageView != null) {
                    switch (behavior) {
                        case CANCEL_DRAW:
                            pageView.deselectText();
                            pageView.cancelDraw();
                            break;
                        case DELETE_ANNOTATION:
                            pageView.deleteSelectedAnnotation();
                            break;
                    }
                }
                modeStore.enterViewingMode();
                return true;
            }
        });
    }

    private boolean markupSelection(@Nullable PageView pageView, @NonNull Annotation.Type type) {
        if (pageView == null) {
            return false;
        }
        if (pageView.hasSelection()) {
            pageView.markupSelection(type);
            modeStore.enterViewingMode();
        } else {
            host.showAnnotationInfo(host.getContext().getString(R.string.select_text));
        }
        return true;
    }

    private enum AnnotationCancelBehavior {
        CANCEL_DRAW,
        DELETE_ANNOTATION
    }
}
