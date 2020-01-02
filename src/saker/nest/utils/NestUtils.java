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
package saker.nest.utils;

import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.NestBundleClassLoader;

/**
 * Contains utility functions that help working with the Nest repository.
 */
@PublicApi
public class NestUtils {
	private NestUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Gets the bundle identifier that contains the argument class.
	 * <p>
	 * The argument class must be loaded by the Nest repository runtime. (That is, the {@link ClassLoader} of the class
	 * must be an instance of {@link NestBundleClassLoader}.)
	 * 
	 * @param c
	 *            The class to get the enclosing bundle identifier of.
	 * @return The bundle identifier of the enclosing bundle.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the class wasn't loaded by the Nest repository runtime.
	 */
	public static BundleIdentifier getClassBundleIdentifier(Class<?> c)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(c, "class");
		ClassLoader cl = c.getClassLoader();
		if (!(cl instanceof NestBundleClassLoader)) {
			throw new IllegalArgumentException("Class is not part of a Nest bundle. (" + c + ")");
		}
		NestBundleClassLoader nestcl = (NestBundleClassLoader) cl;
		return nestcl.getBundle().getBundleIdentifier();
	}
}
