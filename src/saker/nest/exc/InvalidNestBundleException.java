package saker.nest.exc;

/**
 * Thrown when a bundle failed to be validated for the requirements associated with the given operation.
 * <p>
 * This is usually thrown when the bundle meta-data has an invalid format. It can also be thrown when a bundle that is
 * expected to be loaded for a given bundle identifier has an other bundle identifier declared in it.
 */
public class InvalidNestBundleException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String, Throwable)
	 */
	public InvalidNestBundleException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see IllegalArgumentException#IllegalArgumentException(String)
	 */
	public InvalidNestBundleException(String message) {
		super(message);
	}
}
