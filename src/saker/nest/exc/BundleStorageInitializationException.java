package saker.nest.exc;

/**
 * Exception thrown when a bundle storage failed to be initialized.
 */
public class BundleStorageInitializationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public BundleStorageInitializationException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected BundleStorageInitializationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public BundleStorageInitializationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public BundleStorageInitializationException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException( Throwable)
	 */
	public BundleStorageInitializationException(Throwable cause) {
		super(cause);
	}

}
