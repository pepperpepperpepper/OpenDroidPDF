package org.opendroidpdf;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

// Lightweight replacement for AsyncTask that keeps cancellation semantics while routing callbacks
// through the main thread.
public class CancellableAsyncTask<Params, Result>
{
	private static final String TAG = "CancellableAsyncTask";
	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
	private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

	private final CancellableTaskDefinition<Params, Result> ourTask;
	private final AtomicBoolean cancelled = new AtomicBoolean(false);
	private final AtomicBoolean completionPosted = new AtomicBoolean(false);
	private final AtomicBoolean cleanedUp = new AtomicBoolean(false);
	private volatile Future<Result> future;

	protected void onPreExecute()
	{

	}

	protected void onPostExecute(Result result)
	{

	}

	protected void onCanceled()
	{
	}

	public CancellableAsyncTask(final CancellableTaskDefinition<Params, Result> task)
	{
		if (task == null)
			throw new IllegalArgumentException("task == null");
		this.ourTask = task;
	}

	public synchronized void execute(final Params ... params)
	{
		if (future != null)
			throw new IllegalStateException("Task already executed");
		onPreExecute();
		future = EXECUTOR.submit(() -> {
			Result result = null;
			try {
				result = ourTask.doInBackground(params);
			} catch (CancellationException ignore) {
				cancelled.set(true);
			} catch (Exception e) {
				Log.e(TAG, "Background task failed", e);
			} finally {
				dispatchCompletion(result);
			}
			return result;
		});
	}

	public void cancel()
	{
		if (!cancelled.compareAndSet(false, true))
			return;
		Future<Result> localFuture = future;
		if (localFuture != null)
			localFuture.cancel(true);
		ourTask.doCancel();
		dispatchCancellation();
	}

	public void cancelAndWait()
	{
		cancel();
		Future<Result> localFuture = future;
		if (localFuture != null)
			try {
				localFuture.get();
			} catch (Exception ignore) {
			}
	}

	private void dispatchCompletion(final Result result)
	{
		if (!completionPosted.compareAndSet(false, true))
			return;
		if (cancelled.get()) {
			MAIN_HANDLER.post(() -> {
				onCanceled();
				cleanup();
			});
		} else {
			MAIN_HANDLER.post(() -> {
				onPostExecute(result);
				cleanup();
			});
		}
	}

	private void dispatchCancellation()
	{
		if (!completionPosted.compareAndSet(false, true))
			return;
		MAIN_HANDLER.post(() -> {
			onCanceled();
			cleanup();
		});
	}

	private void cleanup()
	{
		if (cleanedUp.compareAndSet(false, true))
			ourTask.doCleanup();
	}
}
