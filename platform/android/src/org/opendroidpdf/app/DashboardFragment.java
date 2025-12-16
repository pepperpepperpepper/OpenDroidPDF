package org.opendroidpdf.app;

import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.Animator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.cardview.widget.CardView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.opendroidpdf.OpenDroidPDFActivity;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.R;
import org.opendroidpdf.RecentFile;
import org.opendroidpdf.RecentFilesList;
import org.opendroidpdf.SettingsActivity;
import org.opendroidpdf.app.AppCoroutines;
import org.opendroidpdf.PdfThumbnailManager;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Hosts the entry/dashboard UI so it can be managed independently of the document view.
 * Logic is now owned here to slim the Activity and centralize navigation actions.
 */
public class DashboardFragment extends Fragment {
    public interface DashboardHost {
        void onOpenDocumentRequested();
        void onCreateNewDocumentRequested();
        void onOpenSettingsRequested();
        void onRecentFileRequested(RecentFile recentFile);
        boolean isMemoryLow();
        int maxRecentFiles();
    }

    private ScrollView scrollView;
    private LinearLayout entryLayout;
    private DashboardHost host;
    private final Executor thumbnailExecutor = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_dashboard, container, false);
        scrollView = root.findViewById(R.id.entry_screen_scroll_view);
        entryLayout = root.findViewById(R.id.entry_screen_layout);
        return root;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof DashboardHost) {
            host = (DashboardHost) context;
        } else {
            throw new IllegalStateException("Activity must implement DashboardHost");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        host = null;
    }

    @Nullable
    public ScrollView getScrollView() {
        return scrollView;
    }

    @Nullable
    public LinearLayout getEntryLayout() {
        return entryLayout;
    }

    /**
     * Populate the dashboard cards and animate them into view.
     */
    public void renderDashboard() {
        if (host == null || scrollView == null || entryLayout == null) return;

        entryLayout.removeAllViews();
        scrollView.scrollTo(0, 0);

        entryLayout.setLayoutTransition(new LayoutTransition());

        scrollView.setVisibility(View.VISIBLE);
        startBackgroundTransition(entryLayout, true);

        final OpenDroidPDFActivity activity = (OpenDroidPDFActivity) requireActivity();

        // Fixed cards
        int elevation = 5;
        int elevationInc = 5;
        addFixedCard(R.drawable.ic_open, R.string.entry_screen_open_document, R.string.entry_screen_open_document_summ, elevation, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onOpenDocumentRequested();
            }
        });
        elevation += elevationInc;

        addFixedCard(R.drawable.ic_new, R.string.entry_screen_new_document, R.string.entry_screen_new_document_summ, elevation, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onCreateNewDocumentRequested();
            }
        });
        elevation += elevationInc;

        addFixedCard(R.drawable.ic_settings, R.string.entry_screen_settings, R.string.entry_screen_settings_summ, elevation, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.onOpenSettingsRequested();
            }
        });
        elevation += elevationInc;

        // Recent files list
        SharedPreferences prefs = requireContext().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        RecentFilesList recentFilesList = new RecentFilesList(requireContext().getApplicationContext(), prefs);
        boolean beforeFirstCard = true;
        int cardNumber = 0;
        for (final RecentFile recentFile : recentFilesList) {
            cardNumber++;
            if (cardNumber > host.maxRecentFiles()) break;
            Uri uri = recentFile.getUri();
            if (!OpenDroidPDFCore.canReadFromUri(activity, uri)) continue;

            if (beforeFirstCard) {
                final CardView heading = (CardView) getLayoutInflater().inflate(R.layout.dashboard_recent_files_list_heading, entryLayout, false);
                entryLayout.addView(heading);
                beforeFirstCard = false;
            }

            final CardView card = (CardView) getLayoutInflater().inflate(R.layout.dashboard_card_recent_file, entryLayout, false);
            card.setCardElevation(elevation);
            ((TextView) card.findViewById(R.id.title)).setText(recentFile.getDisplayName());
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    host.onRecentFileRequested(recentFile);
                }
            });

            enqueueThumbnailLoad(card, recentFile);
            entryLayout.addView(card);
            elevation += elevationInc;
        }
    }

    public void clearDashboard() {
        if (scrollView == null || entryLayout == null) return;
        if (entryLayout.getChildCount() > 0) {
            entryLayout.removeViews(0, entryLayout.getChildCount());
        }
        startBackgroundTransition(entryLayout, false);
        scrollView.setVisibility(View.INVISIBLE);
    }

    private void startBackgroundTransition(LinearLayout layout, boolean forward) {
        if (scrollView == null) return;
        final Drawable background = scrollView.getBackground();
        if (!(background instanceof TransitionDrawable)) return;

        TransitionDrawable transition = (TransitionDrawable) background;
        int animationTime = (int) layout.getLayoutTransition().getDuration(LayoutTransition.DISAPPEARING);
        if (forward) {
            transition.startTransition(animationTime);
        } else {
            transition.reverseTransition(animationTime);
            AppCoroutines.launchMainDelayed(AppCoroutines.mainScope(), animationTime, new Runnable() {
                @Override
                public void run() {
                    scrollView.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    private void addFixedCard(int iconRes, int titleRes, int subtitleRes, int elevation, View.OnClickListener onClickListener) {
        CardView card = (CardView) getLayoutInflater().inflate(R.layout.dashboard_card, entryLayout, false);
        ImageView icon = card.findViewById(R.id.image);
        TextView title = card.findViewById(R.id.title);
        TextView subtitle = card.findViewById(R.id.subtitle);
        icon.setImageResource(iconRes);
        title.setText(titleRes);
        subtitle.setText(subtitleRes);
        card.setOnClickListener(onClickListener);
        card.setCardElevation(elevation);
        entryLayout.addView(card);
    }

    private void enqueueThumbnailLoad(final CardView card, final RecentFile recentFile) {
        thumbnailExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (host == null) return;
                if (host.isMemoryLow()) {
                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Add card without image
                        }
                    });
                    return;
                }
                PdfThumbnailManager pdfThumbnailManager = new PdfThumbnailManager(card.getContext());
                final Drawable drawable = pdfThumbnailManager.getDrawable(getResources(), recentFile.getThumbnailString());
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ImageView imageView = card.findViewById(R.id.image);
                        if (drawable != null) {
                            imageView.setImageDrawable(drawable);
                            Matrix matrix = imageView.getImageMatrix();
                            float imageWidth = drawable.getIntrinsicWidth();
                            int screenWidth = entryLayout.getWidth();
                            float scaleRatio = screenWidth / imageWidth;
                            matrix.postScale(scaleRatio, scaleRatio);
                            imageView.setImageMatrix(matrix);
                        }
                    }
                });
            }
        });
    }
}
