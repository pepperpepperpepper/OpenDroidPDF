package org.opendroidpdf;

import org.opendroidpdf.MuPDFCore.Cookie;
import org.opendroidpdf.core.AnnotationCallback;
import org.opendroidpdf.core.AnnotationController;
import org.opendroidpdf.core.AnnotationController.AnnotationJob;
import org.opendroidpdf.core.DocumentContentController;
import org.opendroidpdf.core.MuPdfController;
import org.opendroidpdf.core.SignatureBooleanCallback;
import org.opendroidpdf.core.SignatureController;
import org.opendroidpdf.core.SignatureController.SignatureJob;
import org.opendroidpdf.core.SignatureStringCallback;
import org.opendroidpdf.core.WidgetBooleanCallback;
import org.opendroidpdf.core.WidgetCompletionCallback;
import org.opendroidpdf.core.WidgetController;
import org.opendroidpdf.core.WidgetController.WidgetJob;
import org.opendroidpdf.core.WidgetAreasCallback;
import org.opendroidpdf.core.WidgetPassClickCallback;
import org.opendroidpdf.app.annotation.InkUndoController;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import java.util.Objects;
import android.text.method.PasswordTransformationMethod;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.ArrayList;


public class MuPDFPageView extends PageView implements MuPDFView {
    private static final String TAG = "MuPDFPageView";
    private static final boolean LOG_UNDO = true; // temporary instrumentation

private int lastHitAnnotation = 0;
    
private final FilePicker.FilePickerSupport mFilePickerSupport;
private final MuPdfController muPdfController;
private final AnnotationController annotationController;
private final WidgetController widgetController;
private final SignatureController signatureController;
private final InkUndoController inkUndoController;
    private WidgetJob mPassClickJob;
	private RectF mWidgetAreas[];
	private int mSelectedAnnotationIndex = -1;
    private WidgetJob mLoadWidgetAreas; //Should be moved to PageView!
	private AlertDialog.Builder mTextEntryBuilder;
	private AlertDialog.Builder mChoiceEntryBuilder;
	private AlertDialog.Builder mSigningDialogBuilder;
	private AlertDialog.Builder mSignatureReportBuilder;
	private AlertDialog.Builder mPasswordEntryBuilder;
	private EditText mPasswordText;
	private AlertDialog mTextEntry;
	private AlertDialog mPasswordEntry;
	private EditText mEditText;
    private WidgetJob mSetWidgetText;
    private WidgetJob mSetWidgetChoice;
    private AnnotationJob mAddMarkupAnnotationJob;
    private AnnotationJob mAddTextAnnotationJob;
    private AnnotationJob mDeleteAnnotationJob;
    private SignatureJob mCheckSignature;
    private SignatureJob mSign;
	private Runnable changeReporter;
    
public MuPDFPageView(Context context, FilePicker.FilePickerSupport filePickerSupport, MuPdfController controller, ViewGroup parent) {
        super(context, parent, new DocumentContentController(Objects.requireNonNull(controller, "MuPdfController required")));
		mFilePickerSupport = filePickerSupport;
		muPdfController = controller;
        annotationController = new AnnotationController(muPdfController);
		widgetController = new WidgetController(muPdfController);
		signatureController = new SignatureController(muPdfController);
        inkUndoController = new InkUndoController(new InkUndoHost(), muPdfController, TAG, LOG_UNDO);
		mTextEntryBuilder = new AlertDialog.Builder(context);
		mTextEntryBuilder.setTitle(getContext().getString(R.string.fill_out_text_field));
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View dialogView = inflater.inflate(R.layout.dialog_text_input, null);
		mEditText = dialogView.findViewById(R.id.dialog_text_input);
		mTextEntryBuilder.setView(dialogView);
		mTextEntryBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
		mTextEntryBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (mSetWidgetText != null) {
                        mSetWidgetText.cancel();
                    }
                    final String contents = mEditText.getText().toString();
					mSetWidgetText = widgetController.setWidgetTextAsync(mPageNumber, contents, new WidgetBooleanCallback() {
						@Override
						public void onResult(boolean result) {
                                if (changeReporter != null) {
                                    changeReporter.run();
                                }
                                if (!result) {
                                    invokeTextDialog(contents);
                                }
                            }
                        });
                }
            });
		mTextEntry = mTextEntryBuilder.create();

		mChoiceEntryBuilder = new AlertDialog.Builder(context);
		mChoiceEntryBuilder.setTitle(getContext().getString(R.string.choose_value));

		mSigningDialogBuilder = new AlertDialog.Builder(context);
		mSigningDialogBuilder.setTitle(R.string.signature_dialog_title);
		mSigningDialogBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
		mSigningDialogBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    FilePicker picker = new FilePicker(mFilePickerSupport) {
                            @Override
                            void onPick(Uri uri) {
                                signWithKeyFile(uri);
                            }
                        };

                    picker.pick();
                }
            });

		mSignatureReportBuilder = new AlertDialog.Builder(context);
		mSignatureReportBuilder.setTitle(R.string.signature_report_title);
		mSignatureReportBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

		mPasswordText = new EditText(context);
		mPasswordText.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
		mPasswordText.setTransformationMethod(new PasswordTransformationMethod());

		mPasswordEntryBuilder = new AlertDialog.Builder(context);
		mPasswordEntryBuilder.setTitle(R.string.enter_password);
		mPasswordEntryBuilder.setView(mPasswordText);
		mPasswordEntryBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

		mPasswordEntry = mPasswordEntryBuilder.create();
	}

    private class InkUndoHost implements InkUndoController.Host {
        @Override
        public int pageNumber() {
            return mPageNumber;
        }

        @Override
        public void onInkStackMutated() {
            requestFullRedrawAfterNextAnnotationLoad();
            loadAnnotations();
            discardRenderedPage();
            redraw(false);
        }
    }

	private void signWithKeyFile(final Uri uri) {
		mPasswordEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		mPasswordEntry.setButton(AlertDialog.BUTTON_POSITIVE, getContext().getString(R.string.signature_sign_action), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    signWithKeyFileAndPassword(uri, mPasswordText.getText().toString());
                }
            });

		mPasswordEntry.show();
	}

	private void signWithKeyFileAndPassword(final Uri uri, final String password) {
		if (mSign != null) {
			mSign.cancel();
		}
		mSign = signatureController.signFocusedSignatureAsync(Uri.decode(uri.getEncodedPath()), password, new SignatureBooleanCallback() {
			@Override
			public void onResult(boolean result) {
				if (result) {
					if (changeReporter != null) {
						changeReporter.run();
					}
				} else {
					mPasswordText.setText("");
					signWithKeyFile(uri);
				}
			}
		});
	}

	public LinkInfo hitLink(float x, float y) {
		float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
		float docRelX = (x - getLeft())/scale;
		float docRelY = (y - getTop())/scale;

		for (LinkInfo l: mLinks)
			if (l.rect.contains(docRelX, docRelY))
				return l;

		return null;
	}

	private void invokeTextDialog(String text) {
		mEditText.setText(text);
		mTextEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		mTextEntry.show();
	}

	private void invokeChoiceDialog(final String [] options) {
		mChoiceEntryBuilder.setItems(options, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (mSetWidgetChoice != null) {
                        mSetWidgetChoice.cancel();
                    }
                    final String selection = options[which];
                    mSetWidgetChoice = widgetController.setWidgetChoiceAsync(new String[]{selection}, new WidgetCompletionCallback() {
                            @Override
                            public void onComplete() {
                                if (changeReporter != null) {
                                    changeReporter.run();
                                }
                            }
                        });
                }
            });
		AlertDialog dialog = mChoiceEntryBuilder.create();
		dialog.show();
	}

	private void invokeSignatureCheckingDialog() {
		if (mCheckSignature != null) {
			mCheckSignature.cancel();
		}
		mCheckSignature = signatureController.checkFocusedSignatureAsync(new SignatureStringCallback() {
			@Override
			public void onResult(String result) {
				AlertDialog report = mSignatureReportBuilder.create();
				report.setMessage(result);
				report.show();
			}
		});
	}

	private void invokeSigningDialog() {
		AlertDialog dialog = mSigningDialogBuilder.create();
		dialog.show();
	}

	private void warnNoSignatureSupport() {
		AlertDialog dialog = mSignatureReportBuilder.create();
		dialog.setTitle(R.string.signature_not_supported_title);
		dialog.show();
	}

	public void setChangeReporter(Runnable reporter) {
        changeReporter = reporter;
	}

	public Hit passClickEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
		final float docRelX = (x - getLeft())/scale;
		final float docRelY = (y - getTop())/scale;
		boolean hit = false;
		int i;

		if (mLinks != null)
            for (LinkInfo l: mLinks)
                if (l.rect.contains(docRelX, docRelY))
                {
                    deselectAnnotation();
                    switch(l.type())
                    {
                        case Internal:
                            return Hit.LinkInternal;
                        case External:
                            return Hit.LinkExternal;
                        case Remote:
                            return Hit.LinkRemote;
                    }
                }
                
		if (mAnnotations != null) {
            for (i = 0; i < mAnnotations.length; i++)
            {
                    //If multiple annotations overlap, make sure we
                    //return a different annotation as hit each
                    //time we are called 
                int j = (i+lastHitAnnotation+1) % mAnnotations.length;
                if (mAnnotations[j].contains(docRelX, docRelY))
                {
                    hit = true;
                    i = lastHitAnnotation = j;
                    break;
                }
            }
            if (hit) {
                switch (mAnnotations[i].type) {
                    case HIGHLIGHT:
                    case UNDERLINE:
                    case SQUIGGLY:
                    case STRIKEOUT:
                        mSelectedAnnotationIndex = i;
                        setItemSelectBox(mAnnotations[i]);
                        return Hit.Annotation;
                    case INK:
                        mSelectedAnnotationIndex = i;
                        setItemSelectBox(mAnnotations[i]);
                        return Hit.InkAnnotation;
                    case TEXT:
                    case FREETEXT:
                        mSelectedAnnotationIndex = i;
                        setItemSelectBox(mAnnotations[i]);
                        ((MuPDFReaderView)mParent).addTextAnnotFromUserInput(mAnnotations[i]);
                        return Hit.TextAnnotation;
                }
            }
		}
		deselectAnnotation();
		
		if (!widgetController.javascriptSupported())
			return Hit.Nothing;
		
		if (mWidgetAreas != null) {
			for (i = 0; i < mWidgetAreas.length && !hit; i++)
				if (mWidgetAreas[i].contains(docRelX, docRelY))
					hit = true;
		}

		if (hit) {
            if (mPassClickJob != null) {
                mPassClickJob.cancel();
            }
            mPassClickJob = widgetController.passClickAsync(
                    mPageNumber,
                    docRelX,
                    docRelY,
                    new WidgetPassClickCallback() {
                        @Override
                        public void onResult(PassClickResult result) {
                            if (result.changed && changeReporter != null) {
                                changeReporter.run();
                            }

                            result.acceptVisitor(new PassClickResultVisitor() {
                                @Override
                                public void visitText(PassClickResultText result) {
                                    invokeTextDialog(result.text);
                                }

                                @Override
                                public void visitChoice(PassClickResultChoice result) {
                                    invokeChoiceDialog(result.options);
                                }

                                @Override
                                public void visitSignature(PassClickResultSignature result) {
                                    switch (result.state) {
                                        case NoSupport:
                                            warnNoSignatureSupport();
                                            break;
                                        case Unsigned:
                                            invokeSigningDialog();
                                            break;
                                        case Signed:
                                            invokeSignatureCheckingDialog();
                                            break;
                                    }
                                }
                            });
                        }
                    });
			return Hit.Widget;
		}

		return Hit.Nothing;
	}


    public Hit clickWouldHit(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
		final float docRelX = (x - getLeft())/scale;
		final float docRelY = (y - getTop())/scale;
		boolean hit = false;
		int i;

		if (mLinks != null)
            for (LinkInfo l: mLinks)
                if (l.rect.contains(docRelX, docRelY))
                {
                    switch(l.type())
                    {
                        case Internal:
                            return Hit.LinkInternal;
                        case External:
                            return Hit.LinkExternal;
                        case Remote:
                            return Hit.LinkRemote;
                    }
                }
                
		if (mAnnotations != null) {
            for (i = 0; i < mAnnotations.length; i++)
            {
                    //If multiple annotations overlap, make sure we
                    //return a different annotation as hit each
                    //time we are called 
                int j = (i+lastHitAnnotation) % mAnnotations.length;
                if (mAnnotations[j].contains(docRelX, docRelY))
                {
                    hit = true;
                    break;
                }
            }
            if (hit) {
                switch (mAnnotations[i].type) {
                    case HIGHLIGHT:
                    case UNDERLINE:
                    case SQUIGGLY:
                    case STRIKEOUT:
                        return Hit.Annotation;
                    case INK:
                        return Hit.InkAnnotation;
                    case TEXT:
                    case FREETEXT:
                        return Hit.TextAnnotation;
                }
            }
		}
                
		if (!widgetController.javascriptSupported())
			return Hit.Nothing;
		if (mWidgetAreas != null) {
			for (i = 0; i < mWidgetAreas.length && !hit; i++)
				if (mWidgetAreas[i].contains(docRelX, docRelY))
					hit = true;
		}
		if (hit) {
			return Hit.Widget;
		}
		return Hit.Nothing;
	}
    


	@TargetApi(11)
	public boolean copySelection() {
		final StringBuilder text = new StringBuilder();

		processSelectedText(new TextProcessor() {
                StringBuilder line;

                public void onStartLine() {
                    line = new StringBuilder();
                }

                public void onWord(TextWord word) {
                    line.append(word.w);
                }

                public void onEndLine() {
                    if (text.length() > 0)
                        text.append('\n');
                    text.append(line);
                }

                public void onEndText() {};
            });

		if (text.length() == 0)
			return false;

		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		if (currentApiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			android.content.ClipboardManager cm = (android.content.ClipboardManager)mContext.getSystemService(Context.CLIPBOARD_SERVICE);

		cm.setPrimaryClip(ClipData.newPlainText(getContext().getString(R.string.app_name), text));
		} else {
			android.text.ClipboardManager cm = (android.text.ClipboardManager)mContext.getSystemService(Context.CLIPBOARD_SERVICE);
			cm.setText(text);
		}

		deselectText();

		return true;
	}

	public boolean markupSelection(final Annotation.Type type) {
		final ArrayList<PointF> quadPoints = new ArrayList<PointF>();
		processSelectedText(new TextProcessor() {
                RectF rect;

                public void onStartLine() {
                    rect = new RectF();
                }

                public void onWord(TextWord word) {
                    rect.union(word);
                }

                public void onEndLine() {
                    if (!rect.isEmpty()) {
                        quadPoints.add(new PointF(rect.left, rect.bottom));
                        quadPoints.add(new PointF(rect.right, rect.bottom));
                        quadPoints.add(new PointF(rect.right, rect.top));
                        quadPoints.add(new PointF(rect.left, rect.top));
                    }
                }
                        
                public void onEndText() {};
            });

		if (quadPoints.size() == 0)
			return false;

        if (mAddMarkupAnnotationJob != null) {
            mAddMarkupAnnotationJob.cancel();
        }
        PointF[] quadArray = quadPoints.toArray(new PointF[quadPoints.size()]);
        mAddMarkupAnnotationJob = annotationController.addMarkupAnnotationAsync(
                mPageNumber,
                quadArray,
                type,
                new AnnotationCallback() {
                    @Override
                    public void onComplete() {
                        loadAnnotations();
                    }
                });

		deselectText();

		return true;
	}

    @Override
    public void deleteSelectedAnnotation() {
        if (mSelectedAnnotationIndex != -1) {
            if (mDeleteAnnotationJob != null) {
                mDeleteAnnotationJob.cancel();
            }
            final int targetIndex = mSelectedAnnotationIndex;
            mDeleteAnnotationJob = annotationController.deleteAnnotationAsync(
                    mPageNumber,
                    targetIndex,
                    new AnnotationCallback() {
                        @Override
                        public void onComplete() {
                            requestFullRedrawAfterNextAnnotationLoad();
                            loadAnnotations();
                            discardRenderedPage();
                            redraw(false);
                        }
                    });

            deselectAnnotation();
		}
	}


    public void editSelectedAnnotation() {
        Annotation annot;
        if (mSelectedAnnotationIndex != -1 && (annot = mAnnotations[mSelectedAnnotationIndex]) != null) {
            
            switch(annot.type){
                case INK:
                    PointF[][] arcs = mAnnotations[mSelectedAnnotationIndex].arcs;
                    if(arcs != null)
                    {
                        setDraw(arcs);
                        ((MuPDFReaderView)mParent).setMode(MuPDFReaderView.Mode.Drawing);
                        deleteSelectedAnnotation();
                    }
                    break;
            }
        }
    }


    public Annotation.Type selectedAnnotationType() {
        return mAnnotations[mSelectedAnnotationIndex].type; 
    }
    
    
    public boolean selectedAnnotationIsEditable() {
        if (mSelectedAnnotationIndex != -1) {
            if(mAnnotations[mSelectedAnnotationIndex].arcs != null || mAnnotations[mSelectedAnnotationIndex].text != null)
                return true;
            else
                return false;
        }
        else
            return false;
    }
    
    
    public void deselectAnnotation() {
		mSelectedAnnotationIndex = -1;
		setItemSelectBox(null);
	}

    private static int countPoints(PointF[][] arcs) {
        if (arcs == null) {
            return 0;
        }
        int count = 0;
        for (PointF[] arc : arcs) {
            if (arc == null) {
                continue;
            }
            count += arc.length;
        }
        return count;
    }


    @Override
    public boolean saveDraw() { 
		PointF[][] path = getDraw();
		if (path == null)
            return false;
        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw begin page=" + mPageNumber
                    + " viewHash=" + System.identityHashCode(this)
                    + " pendingPoints=" + countPoints(path));
        }

            //Copy the overlay to the Hq view to prevent flickering,
            //the Hq view is then anyway rendered again...
        super.saveDraw();

        cancelDraw();

        // Synchronously commit the ink annotation so that immediate export/print
        // includes the accepted strokes without racing an async task.
        try {
            muPdfController.addInkAnnotation(mPageNumber, path);
            muPdfController.markDocumentDirty();
            // Force a tiny render to update annotation appearance streams prior to any export.
            try {
                android.graphics.Bitmap onePx = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                MuPDFCore.Cookie cookie = muPdfController.newRenderCookie();
                muPdfController.drawPage(onePx, mPageNumber, /*pageW*/1, /*pageH*/1, /*patchX*/0, /*patchY*/0, /*patchW*/1, /*patchH*/1, cookie);
                cookie.destroy();
            } catch (Throwable ignoreInner) {}
            loadAnnotations();
            // Snapshot after MuPDF normalises the annotation so undo matches committed strokes.
            inkUndoController.recordCommittedInkForUndo(path);
        } catch (Throwable ignore) {
        }
        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw end page=" + mPageNumber
                    + " stackSize=" + inkUndoController.stackSize()
                    + " viewHash=" + System.identityHashCode(this));
        }

        return true;
    }

    @Override
    public void undoDraw() {
        if (super.canUndo()) {
            super.undoDraw();
            return;
        }
        if (inkUndoController.undoLast()) {
            return;
        }
    }

    @Override
    public boolean canUndo() {
        return super.canUndo() || inkUndoController.hasUndo();
    }

    public void recordCommittedInkForUndo(PointF[][] arcs) {
        inkUndoController.recordCommittedInkForUndo(arcs);
    }


    private void drawPage(Bitmap bm, int sizeX, int sizeY,
                          int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        muPdfController.drawPage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
    }

    // Wait (best-effort) for the asynchronous ink-commit task to finish so that
    // subsequent export/print includes the accepted stroke. Safe to call off the UI thread.
    public void awaitInkCommit(long timeoutMs) {
        // Ink commits run synchronously; retained for legacy callers that awaited AsyncTasks.
    }
    
    private void updatePage(Bitmap bm, int sizeX, int sizeY,
                            int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        muPdfController.updatePage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
    }
	
	@Override
	protected CancellableTaskDefinition<PatchInfo, PatchInfo> getRenderTask(PatchInfo patchInfo) {
		return new MuPDFCancellableTaskDefinition<PatchInfo, PatchInfo>(muPdfController.rawRepository()) {
            @Override
			public PatchInfo doInBackground(MuPDFCore.Cookie cookie, PatchInfo... v) {
				PatchInfo patchInfo = v[0];
					// Workaround bug in Android Honeycomb 3.x, where the bitmap generation count
					// is not incremented when drawing.
					//Careful: We must not let the native code draw to a bitmap that is alreay set to the view. The view might redraw itself (this can even happen without draw() or onDraw() beeing called) and then immediately appear with the new content of the bitmap. This leads to flicker if the view would have to be moved before showing the new content. This is avoided by the ReaderView providing one of two bitmaps in a smart way such that v[0].patchBm is always set to the one not currently set.		
				if (patchInfo.completeRedraw) {
					patchInfo.patchBm.eraseColor(0xFFFFFFFF);
					drawPage(patchInfo.patchBm, patchInfo.viewArea.width(), patchInfo.viewArea.height(),
							 patchInfo.patchArea.left, patchInfo.patchArea.top,
							 patchInfo.patchArea.width(), patchInfo.patchArea.height(),
							 cookie);
				} else {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
						Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
						patchInfo.patchBm.eraseColor(0);
					}
					updatePage(patchInfo.patchBm, patchInfo.viewArea.width(), patchInfo.viewArea.height(),
							   patchInfo.patchArea.left, patchInfo.patchArea.top,
							   patchInfo.patchArea.width(), patchInfo.patchArea.height(),
							   cookie);
				}
				return patchInfo;
			}			
		};
	}
	
    
	@Override
	protected LinkInfo[] getLinkInfo() {
		return muPdfController.links(mPageNumber);
	}

	@Override
	protected TextWord[][] getText() {
        TextWord[][] lines = muPdfController.textLines(mPageNumber);
        return lines != null ? lines : new TextWord[0][];
	}

	@Override
	protected Annotation[] getAnnotations() {
        return muPdfController.annotations(mPageNumber);
	}
    
	@Override
	protected void addMarkup(PointF[] quadPoints, Annotation.Type type) {
		muPdfController.addMarkupAnnotation(mPageNumber, quadPoints, type);
	}

    @Override
	protected void addTextAnnotation(final Annotation annot) {
            
        if (mAddTextAnnotationJob != null) {
            mAddTextAnnotationJob.cancel();
        }
        PointF start = new PointF(annot.left, getHeight()/getScale()-annot.top);
        PointF end = new PointF(annot.right, getHeight()/getScale()-annot.bottom);
        PointF[] quadPoints = new PointF[]{start, end};
        mAddTextAnnotationJob = annotationController.addTextAnnotationAsync(
                mPageNumber,
                quadPoints,
                annot.text,
                new AnnotationCallback() {
                    @Override
                    public void onComplete() {
                        loadAnnotations();
                    }
                });
	}

	@Override
	public void setPage(final int page, PointF size) {
        inkUndoController.clear();

		if (mLoadWidgetAreas != null) {
			mLoadWidgetAreas.cancel();
		}
		mLoadWidgetAreas = widgetController.loadWidgetAreasAsync(page, new WidgetAreasCallback() {
			@Override
			public void onResult(RectF[] areas) {
				mWidgetAreas = areas;
			}
		});

		super.setPage(page, size);
        loadAnnotations();//Must be done after super.setPage() otherwise page number is wrong!
	}

	public void setScale(float scale) {
            // This type of view scales automatically to fit the size
            // determined by the parent view groups during layout
	}

	@Override
	public void releaseResources() {
        if (mPassClickJob != null) {
            mPassClickJob.cancel();
            mPassClickJob = null;
        }

		if (mLoadWidgetAreas != null) {
			mLoadWidgetAreas.cancel();
			mLoadWidgetAreas = null;
		}

		if (mSetWidgetText != null) {
			mSetWidgetText.cancel();
			mSetWidgetText = null;
		}

		if (mSetWidgetChoice != null) {
			mSetWidgetChoice.cancel();
			mSetWidgetChoice = null;
		}

		if (mCheckSignature != null) {
			mCheckSignature.cancel();
			mCheckSignature = null;
		}

		if (mSign != null) {
			mSign.cancel();
			mSign = null;
		}

        if (mAddMarkupAnnotationJob != null) {
            mAddMarkupAnnotationJob.cancel();
            mAddMarkupAnnotationJob = null;
        }

        if (mAddTextAnnotationJob != null) {
            mAddTextAnnotationJob.cancel();
            mAddTextAnnotationJob = null;
        }

        if (mDeleteAnnotationJob != null) {
            mDeleteAnnotationJob.cancel();
            mDeleteAnnotationJob = null;
        }

		super.releaseResources();
	}
}
