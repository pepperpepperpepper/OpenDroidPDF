package com.cgogolin.penandpdf;

import com.cgogolin.penandpdf.MuPDFCore.Cookie;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;
import android.text.method.PasswordTransformationMethod;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;


/* This enum should be kept in line with the cooresponding C enum in mupdf.c */
enum SignatureState {
	NoSupport,
	Unsigned,
	Signed
}

abstract class PassClickResultVisitor {
	public abstract void visitText(PassClickResultText result);
	public abstract void visitChoice(PassClickResultChoice result);
	public abstract void visitSignature(PassClickResultSignature result);
}

class PassClickResult {
	public final boolean changed;

	public PassClickResult(boolean _changed) {
		changed = _changed;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
	}
}

class PassClickResultText extends PassClickResult {
	public final String text;

	public PassClickResultText(boolean _changed, String _text) {
		super(_changed);
		text = _text;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitText(this);
	}
}

class PassClickResultChoice extends PassClickResult {
	public final String [] options;
	public final String [] selected;

	public PassClickResultChoice(boolean _changed, String [] _options, String [] _selected) {
		super(_changed);
		options = _options;
		selected = _selected;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitChoice(this);
	}
}

class PassClickResultSignature extends PassClickResult {
	public final SignatureState state;

	public PassClickResultSignature(boolean _changed, int _state) {
		super(_changed);
		state = SignatureState.values()[_state];
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitSignature(this);
	}
}

public class MuPDFPageView extends PageView implements MuPDFView {
    private static final String TAG = "MuPDFPageView";
    private static final boolean LOG_UNDO = true; // temporary instrumentation

    private int lastHitAnnotation = 0;
    
	final private FilePicker.FilePickerSupport mFilePickerSupport;
	private final MuPDFCore mCore;
	private AsyncTask<Void,Void,PassClickResult> mPassClick;
	private RectF mWidgetAreas[];
	private int mSelectedAnnotationIndex = -1;
    private AsyncTask<Void,Void,RectF[]> mLoadWidgetAreas; //Should be moved to PageView!
	private AlertDialog.Builder mTextEntryBuilder;
	private AlertDialog.Builder mChoiceEntryBuilder;
	private AlertDialog.Builder mSigningDialogBuilder;
	private AlertDialog.Builder mSignatureReportBuilder;
	private AlertDialog.Builder mPasswordEntryBuilder;
	private EditText mPasswordText;
	private AlertDialog mTextEntry;
	private AlertDialog mPasswordEntry;
	private EditText mEditText;
	private AsyncTask<String,Void,Boolean> mSetWidgetText;
	private AsyncTask<String,Void,Void> mSetWidgetChoice;
	private AsyncTask<PointF[],Void,Void> mAddMarkupAnnotation;
    private AsyncTask<PointF[],Void,Void> mAddTextAnnotation;
    private AsyncTask<PointF[][],Void,Void> mAddInkAnnotation;
	private AsyncTask<Integer,Void,Void> mDeleteAnnotation;
	private AsyncTask<Void,Void,String> mCheckSignature;
    private AsyncTask<Void,Void,Boolean> mSign;
	private Runnable changeReporter;
    
	public MuPDFPageView(Context context, FilePicker.FilePickerSupport filePickerSupport, MuPDFCore core, ViewGroup parent) {
        super(context, parent);
		mFilePickerSupport = filePickerSupport;
		mCore = core;
		mTextEntryBuilder = new AlertDialog.Builder(context);
		mTextEntryBuilder.setTitle(getContext().getString(R.string.fill_out_text_field));
		LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mEditText = (EditText)inflater.inflate(R.layout.textentry, null);
		mTextEntryBuilder.setView(mEditText);
		mTextEntryBuilder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
		mTextEntryBuilder.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    mSetWidgetText = new AsyncTask<String,Void,Boolean> () {
                            @Override
                            protected Boolean doInBackground(String... arg0) {
                                return mCore.setFocusedWidgetText(mPageNumber, arg0[0]);
                            }
                            @Override
                            protected void onPostExecute(Boolean result) {
                                changeReporter.run();
                                if (!result)
                                    invokeTextDialog(mEditText.getText().toString());
                            }
                        };

                    mSetWidgetText.execute(mEditText.getText().toString());
                }
            });
		mTextEntry = mTextEntryBuilder.create();

		mChoiceEntryBuilder = new AlertDialog.Builder(context);
		mChoiceEntryBuilder.setTitle(getContext().getString(R.string.choose_value));

		mSigningDialogBuilder = new AlertDialog.Builder(context);
		mSigningDialogBuilder.setTitle("Select certificate and sign?");
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
		mSignatureReportBuilder.setTitle("Signature checked");
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

	private void signWithKeyFile(final Uri uri) {
		mPasswordEntry.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
		mPasswordEntry.setButton(AlertDialog.BUTTON_POSITIVE, "Sign", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    signWithKeyFileAndPassword(uri, mPasswordText.getText().toString());
                }
            });

		mPasswordEntry.show();
	}

	private void signWithKeyFileAndPassword(final Uri uri, final String password) {
		mSign = new AsyncTask<Void,Void,Boolean>() {
                @Override
                protected Boolean doInBackground(Void... params) {
                    return mCore.signFocusedSignature(Uri.decode(uri.getEncodedPath()), password);
                }
                @Override
                protected void onPostExecute(Boolean result) {
                    if (result)
                    {
                        changeReporter.run();
                    }
                    else
                    {
                        mPasswordText.setText("");
                        signWithKeyFile(uri);
                    }
                }

            };

		mSign.execute();
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
                    mSetWidgetChoice = new AsyncTask<String,Void,Void>() {
                            @Override
                            protected Void doInBackground(String... params) {
                                String [] sel = {params[0]};
                                mCore.setFocusedWidgetChoiceSelected(sel);
                                return null;
                            }

                            @Override
                            protected void onPostExecute(Void result) {
                                changeReporter.run();
                            }
                        };

                    mSetWidgetChoice.execute(options[which]);
                }
            });
		AlertDialog dialog = mChoiceEntryBuilder.create();
		dialog.show();
	}

	private void invokeSignatureCheckingDialog() {
		mCheckSignature = new AsyncTask<Void,Void,String> () {
                @Override
                protected String doInBackground(Void... params) {
                    return mCore.checkFocusedSignature();
                }
                @Override
                protected void onPostExecute(String result) {
                    AlertDialog report = mSignatureReportBuilder.create();
                    report.setMessage(result);
                    report.show();
                }
            };

		mCheckSignature.execute();
	}

	private void invokeSigningDialog() {
		AlertDialog dialog = mSigningDialogBuilder.create();
		dialog.show();
	}

	private void warnNoSignatureSupport() {
		AlertDialog dialog = mSignatureReportBuilder.create();
		dialog.setTitle("App built with no signature support");
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
		
		if (!mCore.javascriptSupported())
			return Hit.Nothing;
		
		if (mWidgetAreas != null) {
			for (i = 0; i < mWidgetAreas.length && !hit; i++)
				if (mWidgetAreas[i].contains(docRelX, docRelY))
					hit = true;
		}

		if (hit) {
			mPassClick = new AsyncTask<Void,Void,PassClickResult>() {
                    @Override
                    protected PassClickResult doInBackground(Void... arg0) {
                        return mCore.passClickEvent(mPageNumber, docRelX, docRelY);
                    }

                    @Override
                    protected void onPostExecute(PassClickResult result) {
                        if (result.changed) {
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
                };

			mPassClick.execute();
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
                
		if (!mCore.javascriptSupported())
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

			cm.setPrimaryClip(ClipData.newPlainText("MuPDF", text));
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

		mAddMarkupAnnotation = new AsyncTask<PointF[],Void,Void>() {
                @Override
                protected Void doInBackground(PointF[]... params) {
                    addMarkup(params[0], type);
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    loadAnnotations();
                }
            };

		mAddMarkupAnnotation.execute(quadPoints.toArray(new PointF[quadPoints.size()]));

		deselectText();

		return true;
	}

    @Override
    public void deleteSelectedAnnotation() {
        if (mSelectedAnnotationIndex != -1) {
			if (mDeleteAnnotation != null)
				mDeleteAnnotation.cancel(true);

			mDeleteAnnotation = new AsyncTask<Integer,Void,Void>() {
                    @Override
                    protected Void doInBackground(Integer... params) {
                        mCore.deleteAnnotation(mPageNumber, params[0]);
                        return null;
                    }

					@Override
					protected void onPostExecute(Void result) {
						requestFullRedrawAfterNextAnnotationLoad();
						loadAnnotations();
						discardRenderedPage();
						redraw(false);
					}
                };

			mDeleteAnnotation.execute(mSelectedAnnotationIndex);

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
    private static final class InkUndoItem {
        final int annotationIndex;
        final RectF bounds;
        final PointF[][] arcsSignature;
        final long objectNumber;

        InkUndoItem(int annotationIndex, Annotation annotation, PointF[][] sourceArcs) {
            this.annotationIndex = annotationIndex;
            this.bounds = annotation != null ? new RectF(annotation) : null;
            PointF[][] candidate = (annotation != null && annotation.arcs != null)
                    ? annotation.arcs
                    : sourceArcs;
            this.arcsSignature = cloneArcs(candidate);
            this.objectNumber = annotation != null ? annotation.objectNumber : -1L;
        }

        boolean matches(Annotation annotation) {
            if (annotation == null) {
                return false;
            }
            Annotation.Type type = annotation.type;
            if (type != Annotation.Type.INK && type != Annotation.Type.POPUP) {
                return false;
            }
            if (objectNumber >= 0 && annotation.objectNumber >= 0) {
                if (annotation.objectNumber == objectNumber) {
                    return true;
                }
            }
            if (type == Annotation.Type.POPUP && arcsSignature != null) {
                return true;
            }
            if (arcsSignature != null && annotation.arcs != null
                    && arcsApproximatelyEqual(arcsSignature, annotation.arcs)) {
                return true;
            }
            if (bounds == null) {
                return arcsSignature == null;
            }
            final float epsilon = 5e-1f;
            boolean match = Math.abs(annotation.left - bounds.left) < epsilon
                    && Math.abs(annotation.top - bounds.top) < epsilon
                    && Math.abs(annotation.right - bounds.right) < epsilon
                    && Math.abs(annotation.bottom - bounds.bottom) < epsilon;
            return match;
        }
    }

    private static PointF[][] cloneArcs(PointF[][] arcs) {
        if (arcs == null) {
            return null;
        }
        PointF[][] copy = new PointF[arcs.length][];
        for (int i = 0; i < arcs.length; i++) {
            PointF[] arc = arcs[i];
            if (arc == null) {
                copy[i] = null;
                continue;
            }
            PointF[] arcCopy = new PointF[arc.length];
            for (int j = 0; j < arc.length; j++) {
                PointF pt = arc[j];
                arcCopy[j] = pt == null ? null : new PointF(pt.x, pt.y);
            }
            copy[i] = arcCopy;
        }
        return copy;
    }

    private static boolean arcsApproximatelyEqual(PointF[][] expected, PointF[][] actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.length != actual.length) {
            return false;
        }
        final float epsilon = 5e-1f;
        for (int i = 0; i < expected.length; i++) {
            PointF[] expArc = expected[i];
            PointF[] actArc = actual[i];
            if (expArc == null || actArc == null) {
                if (expArc != actArc) {
                    return false;
                }
                continue;
            }
            if (expArc.length != actArc.length) {
                return false;
            }
            for (int j = 0; j < expArc.length; j++) {
                PointF expPt = expArc[j];
                PointF actPt = actArc[j];
                if (expPt == null || actPt == null) {
                    if (expPt != actPt) {
                        return false;
                    }
                    continue;
                }
                if (Math.abs(expPt.x - actPt.x) > epsilon || Math.abs(expPt.y - actPt.y) > epsilon) {
                    return false;
                }
            }
        }
        return true;
    }

    private static float computeMaxPointDelta(PointF[][] expected, PointF[][] actual) {
        if (expected == null || actual == null) {
            return Float.NaN;
        }
        if (expected.length != actual.length) {
            return Float.POSITIVE_INFINITY;
        }
        float max = 0f;
        for (int i = 0; i < expected.length; i++) {
            PointF[] expArc = expected[i];
            PointF[] actArc = actual[i];
            if (expArc == null || actArc == null) {
                if (expArc != actArc) {
                    return Float.POSITIVE_INFINITY;
                }
                continue;
            }
            if (expArc.length != actArc.length) {
                return Float.POSITIVE_INFINITY;
            }
            for (int j = 0; j < expArc.length; j++) {
                PointF expPt = expArc[j];
                PointF actPt = actArc[j];
                if (expPt == null || actPt == null) {
                    if (expPt != actPt) {
                        return Float.POSITIVE_INFINITY;
                    }
                    continue;
                }
                float dx = Math.abs(expPt.x - actPt.x);
                float dy = Math.abs(expPt.y - actPt.y);
                if (dx > max) {
                    max = dx;
                }
                if (dy > max) {
                    max = dy;
                }
            }
        }
        return max;
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

    private void logAnnotationGeometry(String stage, Annotation annotation, PointF[][] referenceArcs) {
        if (!LOG_UNDO) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(stage)
                .append(" page=").append(mPageNumber)
                .append(" stack=").append(mCommittedInkUndoStack.size())
                .append(" viewHash=").append(System.identityHashCode(this));
        if (annotation == null) {
            sb.append(" annotation=null");
            Log.d(TAG, sb.toString());
            return;
        }
        sb.append(" type=").append(annotation.type)
                .append(" rawType=").append(annotation.rawType)
                .append(" obj=").append(annotation.objectNumber)
                .append(" bounds=").append(String.format(Locale.US, "[%.2f,%.2f,%.2f,%.2f]", annotation.left, annotation.top, annotation.right, annotation.bottom))
                .append(" points=").append(countPoints(annotation.arcs));
        if (referenceArcs != null && annotation.arcs != null) {
            float maxDelta = computeMaxPointDelta(referenceArcs, annotation.arcs);
            sb.append(" maxDelta=").append(maxDelta);
        }
        Log.d(TAG, sb.toString());
    }

    private void logInkUndoItem(String stage, InkUndoItem item) {
        if (!LOG_UNDO) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(stage)
                .append(" page=").append(mPageNumber)
                .append(" stack=").append(mCommittedInkUndoStack.size())
                .append(" viewHash=").append(System.identityHashCode(this));
        if (item == null) {
            sb.append(" item=null");
            Log.d(TAG, sb.toString());
            return;
        }
        sb.append(" index=").append(item.annotationIndex)
                .append(" obj=").append(item.objectNumber);
        if (item.bounds != null) {
            sb.append(" bounds=").append(String.format(Locale.US, "[%.2f,%.2f,%.2f,%.2f]", item.bounds.left, item.bounds.top, item.bounds.right, item.bounds.bottom));
        } else {
            sb.append(" bounds=null");
        }
        sb.append(" signaturePoints=").append(countPoints(item.arcsSignature));
        Log.d(TAG, sb.toString());
    }

    private String describeAnnotations(Annotation[] annotations) {
        StringBuilder sb = new StringBuilder("[");
        if (annotations != null) {
            for (int i = 0; i < annotations.length; i++) {
                Annotation annot = annotations[i];
                if (annot == null) {
                    sb.append(i).append(":null");
                } else {
                    sb.append(i).append(":").append(annot.type)
                            .append("(raw=").append(annot.rawType)
                            .append(",obj=").append(annot.objectNumber).append(")");
                }
                if (i + 1 < annotations.length) {
                    sb.append(", ");
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }

    private final ArrayDeque<InkUndoItem> mCommittedInkUndoStack = new ArrayDeque<>();

    private void pushInkUndoSnapshotFromDocument(PointF[][] committedArcs) {
        if (LOG_UNDO) {
            Log.d(TAG, "[undo] push start page=" + mPageNumber + " stackSize=" + mCommittedInkUndoStack.size()
                    + " committedPoints=" + countPoints(committedArcs)
                    + " viewHash=" + System.identityHashCode(this));
        }
        try {
            Annotation[] annotations = mCore.getAnnoations(mPageNumber);
            if (LOG_UNDO) {
                Log.d(TAG, "[undo] push annotations=" + (annotations == null ? "null" : annotations.length));
                if (annotations != null) {
                    Log.d(TAG, "[undo] push annotation types " + describeAnnotations(annotations));
                }
            }
            if (annotations == null || annotations.length == 0) {
                return;
            }
            if (committedArcs != null && committedArcs.length > 0) {
                for (int i = annotations.length - 1; i >= 0; i--) {
                    Annotation candidate = annotations[i];
                    if (candidate == null || candidate.type != Annotation.Type.INK) {
                        continue;
                    }
                    if (arcsApproximatelyEqual(committedArcs, candidate.arcs)) {
                        InkUndoItem item = new InkUndoItem(i, candidate, committedArcs);
                        mCommittedInkUndoStack.push(item);
                        if (LOG_UNDO) {
                            logAnnotationGeometry("[undo] push matched INK idx=" + i, candidate, committedArcs);
                            logInkUndoItem("[undo] stack push", item);
                        }
                        return;
                    }
                }
            }
            Annotation fallback = null;
            int fallbackIndex = -1;
            for (int i = annotations.length - 1; i >= 0; i--) {
                Annotation candidate = annotations[i];
                if (candidate == null) {
                    continue;
                }
                if (candidate.type == Annotation.Type.INK) {
                    InkUndoItem item = new InkUndoItem(i, candidate, committedArcs);
                    mCommittedInkUndoStack.push(item);
                    if (LOG_UNDO) {
                        logAnnotationGeometry("[undo] push fallback INK idx=" + i, candidate, committedArcs);
                        logInkUndoItem("[undo] stack push", item);
                    }
                    return;
                }
                if (candidate.type == Annotation.Type.POPUP && fallback == null) {
                    fallback = candidate;
                    fallbackIndex = i;
                    if (LOG_UNDO) {
                        logAnnotationGeometry("[undo] potential POPUP fallback idx=" + i, candidate, committedArcs);
                    }
                } else if (LOG_UNDO && candidate.type != Annotation.Type.INK) {
                    logAnnotationGeometry("[undo] potential non-ink candidate idx=" + i, candidate, committedArcs);
                }
            }
            if (fallback != null) {
                InkUndoItem item = new InkUndoItem(fallbackIndex, fallback, committedArcs);
                mCommittedInkUndoStack.push(item);
                if (LOG_UNDO) {
                    logAnnotationGeometry("[undo] push fallback POPUP idx=" + fallbackIndex, fallback, committedArcs);
                    logInkUndoItem("[undo] stack push", item);
                }
            } else if (LOG_UNDO) {
                Log.d(TAG, "[undo] push failed to locate candidate");
            }
        } catch (Throwable t) {
            if (LOG_UNDO) {
                Log.e(TAG, "[undo] push exception", t);
            }
        }
    }

    private boolean undoCommittedInk() {
        InkUndoItem item = mCommittedInkUndoStack.peek();
        if (LOG_UNDO) {
            logInkUndoItem("[undo] attempt", item);
        }
        if (item == null) {
            return false;
        }
        try {
            Annotation[] annotations = mCore.getAnnoations(mPageNumber);
            if (LOG_UNDO) {
                Log.d(TAG, "[undo] annotations on page=" + (annotations == null ? "null" : annotations.length));
                if (annotations != null) {
                    Log.d(TAG, "[undo] undo annotation types " + describeAnnotations(annotations));
                }
            }
            if (annotations == null || annotations.length == 0) {
                return false;
            }
            int index = item.annotationIndex;
            if (index >= 0 && index < annotations.length) {
                Annotation candidate = annotations[index];
                if (LOG_UNDO) {
                    logAnnotationGeometry("[undo] primary candidate idx=" + index, candidate, item.arcsSignature);
                }
                if (candidate != null && item.matches(candidate)) {
                    mCore.deleteAnnotation(mPageNumber, index);
                    mCore.setHasAdditionalChanges(true);
					requestFullRedrawAfterNextAnnotationLoad();
					loadAnnotations();
					discardRenderedPage();
					redraw(false);
					mCommittedInkUndoStack.pop();
                    if (LOG_UNDO) {
                        Log.d(TAG, "[undo] success via primary index; new stack size=" + mCommittedInkUndoStack.size());
                    }
                    return true;
                }
                if (LOG_UNDO) {
                    float maxDelta = Float.NaN;
                    if (candidate != null && candidate.arcs != null) {
                        maxDelta = computeMaxPointDelta(item.arcsSignature, candidate.arcs);
                    }
                    Log.d(TAG, "[undo] primary candidate mismatch type=" + (candidate == null ? "null" : candidate.type)
                            + " rawType=" + (candidate == null ? "null" : candidate.rawType)
                            + " obj=" + (candidate == null ? "null" : candidate.objectNumber)
                            + " maxDelta=" + maxDelta);
                }
            }
            for (int i = annotations.length - 1; i >= 0; i--) {
                Annotation annot = annotations[i];
                if (LOG_UNDO) {
                    logAnnotationGeometry("[undo] scanning idx=" + i, annot, item.arcsSignature);
                }
                if (!item.matches(annot)) {
                    if (LOG_UNDO && annot != null) {
                        float maxDelta = annot.arcs != null ? computeMaxPointDelta(item.arcsSignature, annot.arcs) : Float.NaN;
                        Log.d(TAG, "[undo] scan mismatch idx=" + i + " type=" + annot.type
                                + " rawType=" + annot.rawType + " obj=" + annot.objectNumber
                                + " maxDelta=" + maxDelta);
                    }
                    continue;
                }
                mCore.deleteAnnotation(mPageNumber, i);
                mCore.setHasAdditionalChanges(true);
				requestFullRedrawAfterNextAnnotationLoad();
				loadAnnotations();
				discardRenderedPage();
				redraw(false);
				mCommittedInkUndoStack.pop();
                if (LOG_UNDO) {
                    Log.d(TAG, "[undo] success via scan idx=" + i + "; new stack size=" + mCommittedInkUndoStack.size());
                }
                return true;
            }
            if (LOG_UNDO) {
                Log.d(TAG, "[undo] no matching annotation found");
            }
        } catch (Throwable t) {
            if (LOG_UNDO) {
                Log.e(TAG, "[undo] undoCommittedInk exception", t);
            }
        }
        return false;
    }

    private boolean hasCommittedInkUndo() {
        return !mCommittedInkUndoStack.isEmpty();
    }

    public void recordCommittedInkForUndo(PointF[][] arcs) {
        pushInkUndoSnapshotFromDocument(arcs);
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
            mCore.addInkAnnotation(mPageNumber, path);
            mCore.setHasAdditionalChanges(true);
            // Force a tiny render to update annotation appearance streams prior to any export.
            try {
                android.graphics.Bitmap onePx = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
                MuPDFCore.Cookie cookie = mCore.new Cookie();
                mCore.drawPage(onePx, mPageNumber, /*pageW*/1, /*pageH*/1, /*patchX*/0, /*patchY*/0, /*patchW*/1, /*patchH*/1, cookie);
                cookie.destroy();
            } catch (Throwable ignoreInner) {}
            loadAnnotations();
            // Snapshot after MuPDF normalises the annotation so undo matches committed strokes.
            pushInkUndoSnapshotFromDocument(path);
        } catch (Throwable ignore) {
        }
        if (LOG_UNDO) {
            Log.d(TAG, "[undo] saveDraw end page=" + mPageNumber
                    + " stackSize=" + mCommittedInkUndoStack.size()
                    + " viewHash=" + System.identityHashCode(this));
        }

        return true;
    }

    @Override
    public void undoDraw() {
        if (!mDrawingHistory.isEmpty()) {
            super.undoDraw();
            return;
        }
        if (undoCommittedInk()) {
            return;
        }
    }

    @Override
    public boolean canUndo() {
        return !mDrawingHistory.isEmpty() || hasCommittedInkUndo();
    }


    private void drawPage(Bitmap bm, int sizeX, int sizeY,
                          int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        mCore.drawPage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
    }

    // Wait (best-effort) for the asynchronous ink-commit task to finish so that
    // subsequent export/print includes the accepted stroke. Safe to call off the UI thread.
    public void awaitInkCommit(long timeoutMs) {
        AsyncTask<PointF[][],Void,Void> task = mAddInkAnnotation;
        if (task == null) return;
        try {
            if (task.getStatus() != AsyncTask.Status.FINISHED && !task.isCancelled()) {
                task.get(timeoutMs, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable ignore) {
        }
    }
    
    private void updatePage(Bitmap bm, int sizeX, int sizeY,
                            int patchX, int patchY, int patchWidth, int patchHeight, MuPDFCore.Cookie cookie) {
        mCore.updatePage(bm, mPageNumber, sizeX, sizeY, patchX, patchY, patchWidth, patchHeight, cookie);
    }
	
	@Override
	protected CancellableTaskDefinition<PatchInfo, PatchInfo> getRenderTask(PatchInfo patchInfo) {
		return new MuPDFCancellableTaskDefinition<PatchInfo, PatchInfo>(mCore) {
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
		return mCore.getPageLinks(mPageNumber);
	}

	@Override
	protected TextWord[][] getText() {
        return mCore.textLines(mPageNumber);
	}

	@Override
	protected Annotation[] getAnnotations() {
        return mCore.getAnnoations(mPageNumber);
	}
    
	@Override
	protected void addMarkup(PointF[] quadPoints, Annotation.Type type) {
		mCore.addMarkupAnnotation(mPageNumber, quadPoints, type);
	}

    @Override
	protected void addTextAnnotation(final Annotation annot) {
            
        mAddTextAnnotation = new AsyncTask<PointF[],Void,Void>() {
                @Override
                protected Void doInBackground(PointF[]... params) {
                    mCore.addTextAnnotation(mPageNumber, params[0], annot.text);
                    loadAnnotations();
                    return null;
                }
            };
        mAddTextAnnotation.execute(new PointF[]{new PointF(annot.left, getHeight()/getScale()-annot.top), new PointF(annot.right, getHeight()/getScale()-annot.bottom)});
	}

	@Override
	public void setPage(final int page, PointF size) {
        mCommittedInkUndoStack.clear();

		mLoadWidgetAreas = new AsyncTask<Void,Void,RectF[]> () {
                @Override
                protected RectF[] doInBackground(Void... arg0) {
                    return mCore.getWidgetAreas(page);
                }

                @Override
                protected void onPostExecute(RectF[] result) {
                    mWidgetAreas = result;
                }
            };

		mLoadWidgetAreas.execute();

		super.setPage(page, size);
        loadAnnotations();//Must be done after super.setPage() otherwise page number is wrong!
	}

	public void setScale(float scale) {
            // This type of view scales automatically to fit the size
            // determined by the parent view groups during layout
	}

	@Override
	public void releaseResources() {
		if (mPassClick != null) {
			mPassClick.cancel(true);
			mPassClick = null;
		}

		if (mLoadWidgetAreas != null) {
			mLoadWidgetAreas.cancel(true);
			mLoadWidgetAreas = null;
		}

		if (mSetWidgetText != null) {
			mSetWidgetText.cancel(true);
			mSetWidgetText = null;
		}

		if (mSetWidgetChoice != null) {
			mSetWidgetChoice.cancel(true);
			mSetWidgetChoice = null;
		}

		if (mAddMarkupAnnotation != null) {
			mAddMarkupAnnotation.cancel(true);
			mAddMarkupAnnotation = null;
		}

        if (mAddInkAnnotation != null) {
			mAddInkAnnotation.cancel(true);
			mAddInkAnnotation = null;
		}

		if (mDeleteAnnotation != null) {
			mDeleteAnnotation.cancel(true);
			mDeleteAnnotation = null;
		}

		super.releaseResources();
	}
}
