package org.opendroidpdf.app.ui;

import android.content.Context;
import android.app.Activity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/** Small adapter to hide the keyboard without bloating the activity. */
public final class KeyboardHostAdapter {
    private final Activity activity;

    public KeyboardHostAdapter(Activity activity) {
        this.activity = activity;
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (imm != null && view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
