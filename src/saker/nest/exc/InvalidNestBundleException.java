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
