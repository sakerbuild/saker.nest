package saker.nest.exc;

import saker.nest.bundle.ExternalArchive;

/**
 * The repository runtime failed to load an external archive for some reason.
 * 
 * @since saker.nest 0.8.5
 * @see ExternalArchive
 */
public class ExternalArchiveLoadingFailedException extends NestResourceLoadingFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingFailedException()
	 */
	public ExternalArchiveLoadingFailedException() {
		super();
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingFailedException(String, Throwable, boolean, boolean)
	 */
	protected ExternalArchiveLoadingFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingFailedException(String, Throwable)
	 */
	public ExternalArchiveLoadingFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingFailedException(String)
	 */
	public ExternalArchiveLoadingFailedException(String message) {
		super(message);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingFailedException(Throwable)
	 */
	public ExternalArchiveLoadingFailedException(Throwable cause) {
		super(cause);
	}

}
