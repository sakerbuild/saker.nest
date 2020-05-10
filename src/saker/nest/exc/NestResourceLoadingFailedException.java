package saker.nest.exc;

/**
 * Exception signaling that a resource failed to be properly loaded by the repository runtime.
 * 
 * @since saker.nest 0.8.5
 */
public class NestResourceLoadingFailedException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * @see Exception#Exception()
	 */
	public NestResourceLoadingFailedException() {
		super();
	}

	/**
	 * @see Exception#Exception(String, Throwable, boolean, boolean)
	 */
	protected NestResourceLoadingFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see Exception#Exception(String, Throwable)
	 */
	public NestResourceLoadingFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see Exception#Exception(String)
	 */
	public NestResourceLoadingFailedException(String message) {
		super(message);
	}

	/**
	 * @see Exception#Exception( Throwable)
	 */
	public NestResourceLoadingFailedException(Throwable cause) {
		super(cause);
	}

}
