package saker.nest.exc;

import java.io.IOException;

/**
 * Exception signaling that a given operation cannot be completed due to the bundle storage being configured for offline
 * use.
 */
public class OfflineStorageIOException extends IOException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see IOException#IOException()
	 */
	public OfflineStorageIOException() {
		super();
	}

	/**
	 * @see IOException#IOException(String, Throwable)
	 */
	public OfflineStorageIOException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see IOException#IOException(String)
	 */
	public OfflineStorageIOException(String message) {
		super(message);
	}

	/**
	 * @see IOException#IOException( Throwable)
	 */
	public OfflineStorageIOException(Throwable cause) {
		super(cause);
	}

}
