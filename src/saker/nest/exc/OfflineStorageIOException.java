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
	 * @see IOException#IOException(Throwable)
	 */
	public OfflineStorageIOException(Throwable cause) {
		super(cause);
	}

}
