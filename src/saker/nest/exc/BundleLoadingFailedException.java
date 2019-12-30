package saker.nest.exc;

/**
 * Exception representing that a given bundle loading request failed.
 * <p>
 * It may be either due to the bundle having an invalid format, accessing the bundle failed due to I/O error, the bundle
 * not being found, or other reasons.
 * <p>
 * The exception may also be thrown if bundle related data failed to load. That is, not the bundle loading itself
 * failed, but the bundle related data caused the failure.
 */
public class BundleLoadingFailedException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * @see Exception#Exception()
	 */
	public BundleLoadingFailedException() {
		super();
	}

	/**
	 * @see Exception#Exception(String, Throwable, boolean, boolean)
	 */
	protected BundleLoadingFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see Exception#Exception(String, Throwable)
	 */
	public BundleLoadingFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see Exception#Exception(String)
	 */
	public BundleLoadingFailedException(String message) {
		super(message);
	}

	/**
	 * @see Exception#Exception( Throwable)
	 */
	public BundleLoadingFailedException(Throwable cause) {
		super(cause);
	}

}
