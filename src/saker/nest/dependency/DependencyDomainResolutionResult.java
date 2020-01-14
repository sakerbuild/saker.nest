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

import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleIdentifierHolder;

/**
 * Interface providing access to the result of a dependency resolution.
 * <p>
 * The interface allows accessing the direct dependencies of an associated bundle. The each dependency may also have
 * additional transitive dependencies. The dependencies may be recursive and circular.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @param <BK>
 *            The bundle key type used during resolution.
 * @param <BC>The
 *            bundle context type used during resolution.
 * @see DependencyUtils#satisfyDependencyDomain
 * @since saker.nest 0.8.1
 */
public interface DependencyDomainResolutionResult<BK extends BundleIdentifierHolder, BC> {
	/**
	 * Gets the direct dependencies of the associated bundle.
	 * <p>
	 * The returned map contains the resolved bundles mapped to their own dependency domains. The map has a
	 * deterministic iteration order that is defined by the {@link BundleDependencyInformation} of the bundle.
	 * <p>
	 * <b>Note:</b> the returned map may containg circular transitive dependencies and can cause stack overflows if not
	 * handled properly.
	 * 
	 * @return An immutable map of resolved direct dependencies.
	 */
	public Map<Entry<? extends BK, ? extends BC>, ? extends DependencyDomainResolutionResult<BK, BC>> getDirectDependencies();

	/**
	 * Gets the hash code for this dependency domain.
	 * <p>
	 * The hash code consists of the key entries of the {@linkplain #getDirectDependencies() direct dependencies}. The
	 * hash code is non-transitive.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode();

	/**
	 * Checks if this dependency domain equals the argument.
	 * <p>
	 * The equality checks if this domain has the same direct dependencies as the argument, and the domains of those
	 * dependencies equal as well. The equality is checked recursively for transitive dependencies, but will not cause
	 * stack overflow.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj);
}
