package org.opendroidpdf.app.shell;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.opendroidpdf.R;

public final class PlaceholderTabFragment extends Fragment {
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";

    @NonNull
    public static PlaceholderTabFragment newInstance(@NonNull String title, @NonNull String message) {
        PlaceholderTabFragment f = new PlaceholderTabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_app_shell_placeholder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        String title = args != null ? args.getString(ARG_TITLE, "") : "";
        String message = args != null ? args.getString(ARG_MESSAGE, "") : "";

        TextView titleView = view.findViewById(R.id.app_shell_placeholder_title);
        TextView messageView = view.findViewById(R.id.app_shell_placeholder_message);
        if (titleView != null) titleView.setText(title);
        if (messageView != null) messageView.setText(message);
    }
}

