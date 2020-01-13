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

import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;

import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleIdentifierHolder;

/**
 * Interface providing access to the result of a dependency resolution.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @param <BK>
 *            The bundle key type used during resolution.
 * @param <BC>The
 *            bundle context type used during resolution.
 * @see DependencyUtils#satisfyDependencyRequirements
 * @deprecated Use {@link DependencyUtils#satisfyDependencyDomain(BundleIdentifierHolder, Object, BundleDependencyInformation, BiFunction, BiFunction, DependencyResolutionLogger)
 *                 DependencyUtils.satisfyDependencyDomain} instead.
 */
@Deprecated
public interface DependencyResolutionResult<BK extends BundleIdentifierHolder, BC> {
	/**
	 * Gets the dependency resolution result with undetermined iteration order.
	 * <p>
	 * The returned map contains bundle dependency entries mapped to their resolved bundles. The iteration order of the
	 * returned map is implementation dependent, and may be different for different invocations of the dependency
	 * resolution.
	 * <p>
	 * The bundle identifiers in the key entries don't contain {@linkplain BundleIdentifier#getVersionQualifier()
	 * version qualifiers}.
	 * 
	 * @return The dependency resolution result.
	 */
	public Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> getResultInAnyOrder();

	/**
	 * Gets the dependency resolution result in declaration order.
	 * <p>
	 * The returned map contains bundle dependency entries mapped to their resolved bundles. The iteration order of the
	 * returned map is always the same, that is the declaration order in which the bundles and dependencies are
	 * encountered by the resolution algorithm. The declaration order is considered to be breadth-first considering
	 * transitive dependencies.
	 * <p>
	 * The bundle identifiers in the key entries don't contain {@linkplain BundleIdentifier#getVersionQualifier()
	 * version qualifiers}.
	 * 
	 * @return The dependency resolution result in declaration order.
	 */
	public Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> getResultInDeclarationOrder();

	/**
	 * Gets the dependency domains of the resolved bundles.
	 * <p>
	 * The returned map contains the resolved bundles mapped to their resolved <b>direct</b> dependencies. The values in
	 * the map doesn't contain transitive dependencies of the key bundles.
	 * <p>
	 * The iteration order of the returned map and the value maps is the same as {@link #getResultInDeclarationOrder()}.
	 * 
	 * @return The dependency domains of the resolved bundles.
	 */
	public Map<Entry<? extends BK, ? extends BC>, ? extends Map<? extends BK, ? extends BC>> getDependencyDomainResult();
}
