package org.opendroidpdf.app.document;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import org.opendroidpdf.R;
import org.opendroidpdf.TextDrawable;
import org.opendroidpdf.OpenDroidPDFCore;
import org.opendroidpdf.OpenDroidPDFActivity;

import java.io.File;
import java.util.concurrent.Callable;

import androidx.appcompat.app.AlertDialog;

/**
 * Handles open/new/save navigation flows so the Activity can delegate.
 */
public class DocumentNavigationController {

    public interface Host {
        boolean hasUnsavedChanges();
        boolean canSaveToCurrentUri();
        void saveInBackground(Callable<?> success, Callable<?> failure);
        void saveAsInBackground(Uri uri, Callable<?> success, Callable<?> failure);
        void callInBackgroundAndShowDialog(String message, Callable<Exception> saveCallable, Callable<?> success, Callable<?> failure);
        void commitPendingInkToCoreBlocking();
        void showInfo(String message);
        AlertDialog.Builder alertBuilder();
        void startActivityForResult(Intent intent, int requestCode);
        void overridePendingTransition(int enterAnim, int exitAnim);
        void hideDashboard();
        OpenDroidPDFCore getCore();
        void setCoreInstance(OpenDroidPDFCore core);
        void finish();
        void checkSaveThenCall(Callable<?> callable);
        void setTitle();
        File getNotesDir();
        void openNewDocument(String filename) throws java.io.IOException;
        void setupCore();
        void setupDocView();
        void setupSearchSession();
        void tryToTakePersistablePermissions(Intent intent);
        void rememberTemporaryUriPermission(Intent intent);
        void recordRecent(Uri uri);
        void runAutotestIfNeeded(Intent intent);
        OpenDroidPDFActivity getActivity();
    }

    private final Host host;

    private final int EDIT_REQUEST;
    private final int SAVEAS_REQUEST;
    private final OpenDroidPDFActivity activity;

    public DocumentNavigationController(OpenDroidPDFActivity activity, Host host, int editRequest, int saveAsRequest) {
        this.activity = activity;
        this.host = host;
        this.EDIT_REQUEST = editRequest;
        this.SAVEAS_REQUEST = saveAsRequest;
    }

    public void openDocument() {
        host.checkSaveThenCall(new Callable<Void>() {
            @Override
            public Void call() {
                showOpenDocumentDialog();
                return null;
            }
        });
    }

    /**
     * Prompt to save if dirty, then invoke the callable. Mirrors the legacy activity helper.
     */
    public void checkSaveThenCall(final Callable<?> callable) {
        host.checkSaveThenCall(callable);
    }

    public void showOpenDocumentDialog() {
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT < 19) {
            intent = new Intent(activity, org.opendroidpdf.OpenDroidPDFFileChooser.class);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setAction(Intent.ACTION_EDIT);
        } else {
            intent = DocumentAccessIntents.newOpenDocumentIntent();
        }

        activity.startActivityForResult(intent, EDIT_REQUEST);
        activity.overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
    }

    public void openDocumentFromIntent(Intent intent) {
        host.setupCore();
        OpenDroidPDFCore core = host.getCore();
        if (core == null) return;

        host.tryToTakePersistablePermissions(intent);
        host.rememberTemporaryUriPermission(intent);
        host.setupDocView();
        host.setTitle();
        host.setupSearchSession();

        Uri uri = activity.currentDocumentState().uri();
        if (uri != null) host.recordRecent(uri);

        host.runAutotestIfNeeded(intent);
    }

    public void onActivityResultSaveAs(int resultCode, Intent intent) {
        if (resultCode != OpenDroidPDFActivity.RESULT_OK) return;
        final Uri uri = intent != null ? intent.getData() : null;
        File file = null;
        if (uri != null) file = new File(org.opendroidpdf.app.util.UriPathResolver.getActualPath(activity, uri));

        if (file != null && file.isFile() && file.length() > 0) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (which == AlertDialog.BUTTON_POSITIVE) {
                        host.saveAsInBackground(uri,
                                new Callable<Void>() {
                                    @Override
                                    public Void call() {
                                        host.setTitle();
                                        return null;
                                    }
                                },
                                new Callable<Void>() {
                                    @Override
                                    public Void call() {
                                        host.showInfo(activity.getString(R.string.error_saveing));
                                        return null;
                                    }
                                });
                    }
                }
            };
            AlertDialog alert = host.alertBuilder().create();
            alert.setTitle(R.string.overwrite_question);
            alert.setMessage(activity.getString(R.string.overwrite) + " " + uri.getPath() + " ?");
            alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getString(R.string.yes), listener);
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.no), listener);
            alert.show();
            return;
        }

        if (uri == null) {
            host.showInfo(activity.getString(R.string.error_saveing));
        } else {
            host.saveAsInBackground(uri,
                    new Callable<Void>() {
                        @Override
                        public Void call() {
                            host.setTitle();
                            return null;
                        }
                    },
                    new Callable<Void>() {
                        @Override
                        public Void call() {
                            host.showInfo(activity.getString(R.string.error_saveing));
                            return null;
                        }
                    });
        }
    }

    public void showSaveAsActivity() {
        if (host.getCore() == null) return;
        DocumentState docState = activity.currentDocumentState();
        Uri currentUri = docState.uri();
        String docTitle = docState.displayName();
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT < 19) {
            intent = new Intent(activity.getApplicationContext(), org.opendroidpdf.OpenDroidPDFFileChooser.class);
            if (currentUri != null) intent.setData(currentUri);
            intent.putExtra(Intent.EXTRA_TITLE, docTitle);
            intent.setAction(Intent.ACTION_PICK);
        } else {
            intent = DocumentAccessIntents.newCreatePdfDocumentIntent(docTitle);
        }
        activity.startActivityForResult(intent, SAVEAS_REQUEST);
        activity.overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
    }

    public void promptSaveOrSaveAs() {
        AlertDialog alert = host.alertBuilder().create();
        alert.setTitle(activity.getString(R.string.save));
        alert.setMessage(activity.getString(R.string.how_do_you_want_to_save));
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    if (host.canSaveToCurrentUri()) {
                        host.saveInBackground(new Callable<Void>() {
                                                  @Override
                                                  public Void call() {
                                                      host.setTitle();
                                                      return null;
                                                  }
                                              },
                                new Callable<Void>() {
                                    @Override
                                    public Void call() {
                                        host.showInfo(activity.getString(R.string.error_saveing));
                                        return null;
                                    }
                                });
                    } else {
                        showSaveAsActivity();
                    }
                }
            }
        };
        if (host.canSaveToCurrentUri()) {
            alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getString(R.string.save), listener);
        }
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.saveas), listener);
        alert.setButton(AlertDialog.BUTTON_NEUTRAL, activity.getString(R.string.cancel), listener);
        alert.show();
    }

    public void showOpenNewDocumentDialog() {
        final AlertDialog dialog = host.alertBuilder().create();
        final View editTextLayout = LayoutInflater.from(activity).inflate(R.layout.dialog_text_input, null, false);
        final EditText input = editTextLayout.findViewById(R.id.dialog_text_input);
        input.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine();
        input.setBackgroundDrawable(null);
        TextDrawable textDrawable = new TextDrawable(".pdf", input.getTextSize(), input.getCurrentTextColor());
        input.setCompoundDrawablesWithIntrinsicBounds(null, null, textDrawable, null);
        input.setFocusable(true);
        input.setGravity(android.view.Gravity.END);
        dialog.setTitle(R.string.dialog_newdoc_title);

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int which) {
                if (which == AlertDialog.BUTTON_POSITIVE) {
                    String filename = input.getText().toString();
                    try {
                        if (!"".equals(filename)) {
                            filename += ".pdf";
                            File dir = host.getNotesDir();
                            File file = new File(dir, filename);
                            if (file.isFile() && file.length() > 0) {
                                host.showInfo(String.format(activity.getString(R.string.file_alrady_exists), filename));
                            } else {
                                host.openNewDocument(filename);
                            }
                        }
                    } catch (java.io.IOException e) {
                        AlertDialog alert = host.alertBuilder().create();
                        alert.setTitle(R.string.cannot_open_document);
                        alert.setMessage(activity.getResources().getString(R.string.reason) + ": " + e.toString());
                        alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getString(R.string.dismiss), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                host.finish();
                            }
                        });
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            public void onDismiss(DialogInterface dialog) {
                                host.finish();
                            }
                        });
                        alert.show();
                    }
                }
            }
        };

        dialog.setView(editTextLayout);
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, activity.getString(R.string.okay), listener);
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, activity.getString(R.string.cancel), listener);
        dialog.show();
    }
}
