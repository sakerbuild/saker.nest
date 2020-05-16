package saker.nest.exc;

/**
 * An exception was raised as part of the signature verification mechanism
 * 
 * @since saker.nest 0.8.5
 */
public class NestSignatureVerificationException extends Exception {
	private static final long serialVersionUID = 1L;

	/**
	 * @see Exception#Exception()
	 */
	public NestSignatureVerificationException() {
		super();
	}

	/**
	 * @see Exception#Exception(String, Throwable, boolean, boolean)
	 */
	protected NestSignatureVerificationException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see Exception#Exception(String, Throwable)
	 */
	public NestSignatureVerificationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see Exception#Exception(String)
	 */
	public NestSignatureVerificationException(String message) {
		super(message);
	}

	/**
	 * @see Exception#Exception(Throwable)
	 */
	public NestSignatureVerificationException(Throwable cause) {
		super(cause);
	}
}
