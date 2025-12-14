package org.opendroidpdf;

import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Handles link tap navigation so MuPDFReaderView stays smaller.
 */
final class LinkTapHandler {
    private LinkTapHandler() {}

    static void handle(final MuPDFReaderView host, LinkInfo link) {
        if (host == null || link == null) return;

        link.acceptVisitor(new LinkInfoVisitor() {
            @Override
            public void visitInternal(LinkInfoInternal li) {
                host.setDisplayedViewIndex(li.pageNumber);
                if (li.target == null) return;

                if ((li.targetFlags & LinkInfoInternal.fz_link_flag_l_valid) == LinkInfoInternal.fz_link_flag_l_valid)
                    host.setDocRelXScroll(li.target.left);
                if ((li.targetFlags & LinkInfoInternal.fz_link_flag_t_valid) == LinkInfoInternal.fz_link_flag_t_valid)
                    host.setDocRelYScroll(li.target.top);
                if ((li.targetFlags & LinkInfoInternal.fz_link_flag_r_is_zoom) == LinkInfoInternal.fz_link_flag_r_is_zoom
                        && (li.targetFlags & LinkInfoInternal.fz_link_flag_r_valid) == LinkInfoInternal.fz_link_flag_r_valid) {
                    if (li.target.right > 0 && li.target.right <= 1.0f) host.setScale(li.target.right);
                }
                if ((li.targetFlags & LinkInfoInternal.fz_link_flag_fit_h) == LinkInfoInternal.fz_link_flag_fit_h
                        && (li.targetFlags & LinkInfoInternal.fz_link_flag_fit_v) == LinkInfoInternal.fz_link_flag_fit_v) {
                    host.setScale(1.0f);
                } else if ((li.targetFlags & LinkInfoInternal.fz_link_flag_fit_h) == LinkInfoInternal.fz_link_flag_fit_h) {
                    // Fit width; already handled by normalized scroll + scale above
                } else if ((li.targetFlags & LinkInfoInternal.fz_link_flag_fit_v) == LinkInfoInternal.fz_link_flag_fit_v) {
                    // Fit height; no-op here
                }
                // FitR is intentionally not handled (unchanged legacy behavior).
            }

            @Override
            public void visitExternal(LinkInfoExternal li) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(li.url));
                    host.getContext().startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(
                            host.getContext(),
                            host.getContext().getString(R.string.error_opening_link, e.getMessage()),
                            Toast.LENGTH_SHORT)
                            .show();
                }
            }

            @Override
            public void visitRemote(LinkInfoRemote li) {
                // Clicked on a remote (GoToR) link; legacy behavior was no-op.
            }
        });
    }
}
