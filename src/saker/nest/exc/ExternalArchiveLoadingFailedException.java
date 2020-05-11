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
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException()
	 */
	public ExternalArchiveLoadingFailedException() {
		super();
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException(String, Throwable, boolean, boolean)
	 */
	protected ExternalArchiveLoadingFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException(String, Throwable)
	 */
	public ExternalArchiveLoadingFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException(String)
	 */
	public ExternalArchiveLoadingFailedException(String message) {
		super(message);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException(Throwable)
	 */
	public ExternalArchiveLoadingFailedException(Throwable cause) {
		super(cause);
	}

}
