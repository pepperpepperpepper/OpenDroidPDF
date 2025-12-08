package org.opendroidpdf;

import org.opendroidpdf.MuPDFCore.Cookie;
import org.opendroidpdf.core.MuPdfRepository;

/**
 * Helper wrapper that obtains a {@link Cookie} from the shared {@link MuPdfRepository}
 * so background tasks can render/update safely and be cancelled by aborting the cookie.
 */
public abstract class MuPDFCancellableTaskDefinition<Params, Result> implements CancellableTaskDefinition<Params, Result> {
	private final MuPdfRepository repository;
	private Cookie cookie;

	public MuPDFCancellableTaskDefinition(MuPdfRepository repository) {
		this.repository = repository;
		this.cookie = repository != null ? repository.newRenderCookie() : null;
	}

	protected final MuPdfRepository getRepository() {
		return repository;
	}

	@Override
	public void doCancel() {
		if (cookie == null)
			return;

		cookie.abort();
	}

	@Override
	public void doCleanup() {
		if (cookie == null)
			return;

		cookie.destroy();
		cookie = null;
	}

	@Override
	public final Result doInBackground(Params ... params) {
		return doInBackground(cookie, params);
	}

	public abstract Result doInBackground(Cookie cookie, Params ... params);
}
