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
package saker.nest.meta;

import saker.apiextract.api.DefaultableBoolean;
import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;

/**
 * Meta-data class holding information about the release version of the Nest repository.
 * <p>
 * Some constants declared in this class are final, but they are not final in the API release JAR. Meaning, that users
 * can use them in if conditions and compare them against constant values without worrying about dead code elimination.
 * Make sure to use the API release JAR in your classpath when compiling classes against the repository runtime.
 */
public class Versions {
	/**
	 * The major version of the Nest repository release.
	 * <p>
	 * The major version will change when there are backward incompatible changes between releases.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_MAJOR = 0;
	/**
	 * The minor version of the Nest repository release.
	 * <p>
	 * The minor version changes when changes are made to the repository implementation, but they are backward
	 * compatible.
	 * <p>
	 * The minor version is also changed when features are deprecated but not yet removed from the implementation.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_MINOR = 8;
	/**
	 * The patch version of the Nest repository release.
	 * <p>
	 * The patch verison changes when bugfixes are added to the implementation.
	 * 
	 * @see <a href="https://semver.org/">https://semver.org/</a>
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final int VERSION_PATCH = 0;

	/**
	 * The full version string in the format of
	 * <code>&lt;{@link #VERSION_MAJOR major}&gt;.&lt;{@link #VERSION_MINOR minor}&gt;.&lt;{@link #VERSION_PATCH patch}&gt;</code>
	 * 
	 * @see BundleIdentifier#compareVersionNumbers(String, String)
	 */
	@PublicApi(unconstantize = DefaultableBoolean.TRUE)
	public static final String VERSION_STRING_FULL = VERSION_MAJOR + "." + VERSION_MINOR + "." + VERSION_PATCH;

	private Versions() {
		throw new UnsupportedOperationException();
	}
}
