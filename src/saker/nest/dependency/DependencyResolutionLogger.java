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
package saker.nest.dependency;

import java.util.function.BiFunction;

import saker.nest.bundle.BundleDependency;
import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleIdentifierHolder;

/**
 * Interface accepting dependency resolution events.
 * <p>
 * This interface is used with the dependency resolution algorithm used in
 * {@link DependencyUtils#satisfyDependencyRequirements(BundleIdentifierHolder, Object, BundleDependencyInformation, BiFunction, BiFunction, DependencyResolutionLogger)
 * DependencyUtils.satisfyDependencyRequirements}. The methods in this interface is called to allow logging of various
 * dependency resolution events in order for analysis purposes.
 * <p>
 * This interface may and is recommended to be subclassed.
 * 
 * @param <BC>
 *            The bundle context type used in
 *            {@link DependencyUtils#satisfyDependencyRequirements(BundleIdentifierHolder, Object, BundleDependencyInformation, BiFunction, BiFunction, DependencyResolutionLogger)
 *            DependencyUtils.satisfyDependencyRequirements}.
 */
public interface DependencyResolutionLogger<BC> {

	/**
	 * Called when the possible bundles for a given bundle identifier is being resolved.
	 * <p>
	 * This method may be called multiple times for a given bundle identifier-bundle context pair. As the dependency
	 * resolution may backtrack in case of failed resolutions, entering a bundle multiple times is possible.
	 * <p>
	 * Each call to this function will be matched with a subsequenc
	 * {@link #exit(BundleIdentifier, Object, BundleIdentifier, Object)} call.
	 * 
	 * @param bundleid
	 *            The bundle identifier for which the dependencies are resolved. The bundle identifier doesn't contain
	 *            any {@linkplain BundleIdentifier#getVersionQualifier() version qualifiers}.
	 * @param bundlecontext
	 *            The bundle context.
	 */
	public default void enter(BundleIdentifier bundleid, BC bundlecontext) {
	}

	/**
	 * Called after {@link #enter(BundleIdentifier, Object)} when a specific version of a bundle is being considered.
	 * <p>
	 * This method is called after entering a given bundle name. The bundle with a possible version is being resolved by
	 * the algorithm.
	 * <p>
	 * Each call to this function will be matched with a subsequenc {@link #exitVersion(BundleIdentifier, Object)} call.
	 * 
	 * @param bundleid
	 *            The bundle identifier that is being considered. Contains a
	 *            {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}.
	 * @param bundlecontext
	 *            The bundle context.
	 */
	public default void enterVersion(BundleIdentifier bundleid, BC bundlecontext) {
	}

	/**
	 * Called after a specific version of a bundle has been visited.
	 * <p>
	 * It is not specified if the bundle with the given version was accepted or not for this function.
	 * 
	 * @param bundleid
	 *            The bundle identifier that was visited. Contains a {@linkplain BundleIdentifier#getVersionQualifier()
	 *            version qualifier}.
	 * @param bundlecontext
	 *            The bundle context.
	 */
	public default void exitVersion(BundleIdentifier bundleid, BC bundlecontext) {
	}

	/**
	 * Called when the bundles resolution for a given bundle identifier has ended.
	 * <p>
	 * The success or failure of bundle resolution is based on the <code>null</code>ness of the matched bundle
	 * identifier argument.
	 * 
	 * @param bundleid
	 *            The bundle identifier for which the versioned bundle was resolved.
	 * @param bundlecontext
	 *            The bundle context.
	 * @param matchedidentifier
	 *            The matched versioned bundle identifier. <code>null</code> if the resolution failed for the given
	 *            bundle identifier.
	 * @param matchedbundlecontext
	 *            The matched bundle context.
	 */
	public default void exit(BundleIdentifier bundleid, BC bundlecontext, BundleIdentifier matchedidentifier,
			BC matchedbundlecontext) {
	}

	/**
	 * Notifies the logger that the bundle dependency cannot be satisfied for the given bundle identifier because the
	 * version is out of the allowed range.
	 * 
	 * @param dependency
	 *            The dependency that is being satisfied.
	 * @param bundleid
	 *            The considered versioned bundle identifier.
	 * @param bundlecontext
	 *            The bundle context.
	 * @see BundleDependency#getRange()
	 */
	public default void dependencyVersionRangeMismatch(BundleDependency dependency, BundleIdentifier bundleid,
			BC bundlecontext) {
	}

	/**
	 * Notifies the logger that a dependency was satisfied using a previously pinned bundle.
	 * <p>
	 * When a bundle is resolved, it will be pinned for further resolution. This method is called when a bundle was
	 * previously pinned, and the given dependency was satisfied because this pinned bundle is already present. (Note
	 * that unpinning may also occurr in case of backtracking due to failures.)
	 * 
	 * @param dependency
	 *            The dependency that is being satisfied.
	 * @param bundlecontext
	 *            The bundle context.
	 * @param pinnedbundle
	 *            The found pinned bundle identifier.
	 * @param pinnedbundlecontext
	 *            The pinned bundle context.
	 */
	public default void dependencyFoundPinned(BundleIdentifier dependency, BC bundlecontext,
			BundleIdentifier pinnedbundle, BC pinnedbundlecontext) {
	}

	/**
	 * Notifies the logger that a dependency cannot be satisfied because a pinned bundle is not allowed for the
	 * specified range.
	 * <p>
	 * This method is called when a previously resolved bundle is out of range for the given dependency, therefore it
	 * cannot be used for the given dependency due to version conflicts.
	 * 
	 * @param dependency
	 *            The dependency being satisfied.
	 * @param pinnedbundleid
	 *            The pinned bundle identifier that caused the conflict.
	 * @param pinnedbundlecontext
	 *            The pinned bundle context.
	 */
	public default void dependencyVersionRangeMismatchForPinnedBundle(BundleDependency dependency,
			BundleIdentifier pinnedbundleid, BC pinnedbundlecontext) {
	}
}
