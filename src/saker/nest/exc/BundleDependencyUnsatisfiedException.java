package saker.nest.exc;

/**
 * Exception signaling that the dependencies failed to be satisfied.
 * <p>
 * The exact context of the dependency resolution is based on the executed operation.
 */
public class BundleDependencyUnsatisfiedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see RuntimeException#RuntimeException()
	 */
	public BundleDependencyUnsatisfiedException() {
		super();
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable, boolean, boolean)
	 */
	protected BundleDependencyUnsatisfiedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see RuntimeException#RuntimeException(String, Throwable)
	 */
	public BundleDependencyUnsatisfiedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see RuntimeException#RuntimeException(String)
	 */
	public BundleDependencyUnsatisfiedException(String message) {
		super(message);
	}

	/**
	 * @see RuntimeException#RuntimeException( Throwable)
	 */
	public BundleDependencyUnsatisfiedException(Throwable cause) {
		super(cause);
	}

}
