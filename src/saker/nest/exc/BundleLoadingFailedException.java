/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
public class BundleLoadingFailedException extends NestResourceLoadingFailedException {
	private static final long serialVersionUID = 1L;

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException()
	 */
	public BundleLoadingFailedException() {
		super();
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException(String, Throwable, boolean, boolean)
	 */
	protected BundleLoadingFailedException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException(String, Throwable)
	 */
	public BundleLoadingFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException(String)
	 */
	public BundleLoadingFailedException(String message) {
		super(message);
	}

	/**
	 * @see NestResourceLoadingFailedException#NestResourceLoadingException( Throwable)
	 */
	public BundleLoadingFailedException(Throwable cause) {
		super(cause);
	}

}
