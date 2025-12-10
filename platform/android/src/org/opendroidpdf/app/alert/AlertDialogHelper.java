package org.opendroidpdf.app.alert;

import android.content.DialogInterface;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import org.opendroidpdf.MuPDFAlert;
import org.opendroidpdf.R;
import org.opendroidpdf.core.AlertController;

/**
 * UI helper that renders MuPDF alerts and forwards replies back to the AlertController.
 * This keeps dialog wiring out of the activity and avoids leaking UI state.
 */
public final class AlertDialogHelper {

    public interface Host {
        @NonNull AlertDialog.Builder alertBuilder();
        boolean isFinishing();
        @NonNull String t(int resId);
    }

    private final Host host;
    private final AlertController controller;
    private boolean active = false;
    private AlertDialog dialog;

    public AlertDialogHelper(@NonNull Host host, @NonNull AlertController controller) {
        this.host = host;
        this.controller = controller;
    }

    public void start() {
        stop(); // ensure clean state
        active = true;
        controller.start(this::show);
    }

    public void stop() {
        active = false;
        if (dialog != null) {
            try { dialog.cancel(); } catch (Throwable ignore) {}
            dialog = null;
        }
        try { controller.stop(); } catch (Throwable ignore) {}
    }

    private void show(final MuPDFAlert result) {
        if (!active || result == null || host.isFinishing()) return;

        final MuPDFAlert.ButtonPressed[] pressed = new MuPDFAlert.ButtonPressed[3];
        for (int i = 0; i < pressed.length; i++) pressed[i] = MuPDFAlert.ButtonPressed.None;

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialogInterface, int which) {
                dialog = null;
                if (!active) return;
                int index = 0;
                switch (which) {
                    case AlertDialog.BUTTON1: index = 0; break;
                    case AlertDialog.BUTTON2: index = 1; break;
                    case AlertDialog.BUTTON3: index = 2; break;
                }
                result.buttonPressed = pressed[index];
                controller.reply(result);
            }
        };

        dialog = host.alertBuilder().create();
        dialog.setTitle(result.title);
        dialog.setMessage(result.message);

        switch (result.buttonGroupType) {
            case OkCancel:
                dialog.setButton(AlertDialog.BUTTON2, host.t(R.string.cancel), listener);
                pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
            case Ok:
                dialog.setButton(AlertDialog.BUTTON1, host.t(R.string.okay), listener);
                pressed[0] = MuPDFAlert.ButtonPressed.Ok;
                break;
            case YesNoCancel:
                dialog.setButton(AlertDialog.BUTTON3, host.t(R.string.cancel), listener);
                pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
            case YesNo:
                dialog.setButton(AlertDialog.BUTTON1, host.t(R.string.yes), listener);
                pressed[0] = MuPDFAlert.ButtonPressed.Yes;
                dialog.setButton(AlertDialog.BUTTON2, host.t(R.string.no), listener);
                pressed[1] = MuPDFAlert.ButtonPressed.No;
                break;
        }

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override public void onCancel(DialogInterface dialogInterface) {
                dialog = null;
                if (active) {
                    result.buttonPressed = MuPDFAlert.ButtonPressed.None;
                    controller.reply(result);
                }
            }
        });

        dialog.show();
    }
}

