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
package saker.nest.bundle;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Holds an immutable set of {@linkplain BundleDependency bundle dependencies}.
 * <p>
 * This class servers as an enclosing collection to bundle dependencies. It is used by
 * {@link BundleDependencyInformation} to represent possibly multiple bundle dependencies for a given bundle identifier.
 * <p>
 * The class contains multiple {@link BundleDependency} instances in an ordered {@link Set} meaning that there are no
 * duplicate dependencies, and the order remains the same between serialization and deserialization.
 * <p>
 * Use {@link #create(Collection)} to create a new instance.
 * 
 * @see BundleDependencyInformation
 * @see BundleInformation#ENTRY_BUNDLE_DEPENDENCIES
 */
public final class BundleDependencyList implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Singleton instance contanining no dependencies.
	 */
	public static final BundleDependencyList EMPTY = new BundleDependencyList();
	static {
		EMPTY.dependencies = Collections.emptySet();
	}
	private Set<? extends BundleDependency> dependencies;

	/**
	 * For {@link Externalizable}.
	 */
	public BundleDependencyList() {
	}

	private BundleDependencyList(Set<? extends BundleDependency> dependencies) {
		this.dependencies = dependencies;
	}

	/**
	 * Creates a new dependency list with the argument bundle dependencies.
	 * 
	 * @param dependencies
	 *            The dependencies.
	 * @return The created dependency list.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static BundleDependencyList create(Collection<? extends BundleDependency> dependencies)
			throws NullPointerException {
		Objects.requireNonNull(dependencies, "dependencies");
		if (dependencies.isEmpty()) {
			return EMPTY;
		}
		return new BundleDependencyList(ImmutableUtils.makeImmutableLinkedHashSet(dependencies));
	}

	/**
	 * Checks if this dependency list contains any dependencies.
	 * 
	 * @return <code>true</code> if there is at least one {@link BundleDependency} present in this instance.
	 */
	public boolean isEmpty() {
		return dependencies.isEmpty();
	}

	/**
	 * Checks if there are any {@linkplain BundleDependency#isOptional() optional} dependencies in this instance.
	 * 
	 * @return <code>true</code> if there was at least one {@link BundleDependency} that reported to be
	 *             {@linkplain BundleDependency#isOptional() optional}.
	 */
	public boolean hasOptional() {
		for (BundleDependency dep : dependencies) {
			if (dep.isOptional()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the bundle dependencies enclosed in this dependency list.
	 * 
	 * @return An unmodifiable and ordered set of dependencies.
	 */
	public Set<? extends BundleDependency> getDependencies() {
		return dependencies;
	}

	/**
	 * Gets all the dependency kinds which are present in any of the enclosed bundle dependency.
	 * <p>
	 * This method returns an empty list if and only if {@link #isEmpty()} is <code>true</code>.
	 * 
	 * @return A set of dependency kinds.
	 */
	public NavigableSet<String> getAllPresentKinds() {
		NavigableSet<String> result = new TreeSet<>();
		for (BundleDependency dep : dependencies) {
			result.addAll(dep.getKinds());
		}
		return result;
	}

	/**
	 * Gets a dependency list that contains all the dependencies as <code>this</code>, but doesn't contain any
	 * {@linkplain BundleDependency#isOptional() optional} dependencies.
	 * <p>
	 * If <code>this</code> instance contains no optionals, <code>this</code> is returned, without constructing a new
	 * instance.
	 * 
	 * @return A dependency list without the optionals in <code>this</code> instance.
	 */
	public BundleDependencyList withoutOptionals() {
		boolean hadoptional = false;
		Set<BundleDependency> ndeps = new LinkedHashSet<>();
		for (BundleDependency dep : this.dependencies) {
			if (dep.isOptional()) {
				hadoptional = true;
				continue;
			}
			ndeps.add(dep);
		}
		if (!hadoptional) {
			return this;
		}
		return new BundleDependencyList(ImmutableUtils.unmodifiableSet(ndeps));
	}

	/**
	 * Creates a new dependency list by filtering the dependencies in <code>this</code> instance.
	 * <p>
	 * The dependencies in <code>this</code> will be enumerated and the transformation function will be applied to it.
	 * If it returns <code>null</code>, it will be omitted.
	 * <p>
	 * This function can be used to transform the dependency list by filtering the dependencies in a custom manner.
	 * 
	 * @param dependencytransformation
	 *            The dependency transformation function.
	 * @return The dependency list result of the filtering.
	 * @throws NullPointerException
	 *             If the transformation function is <code>null</code>, and <code>this</code> is not
	 *             {@linkplain #isEmpty() empty}.
	 */
	public BundleDependencyList filter(
			Function<? super BundleDependency, ? extends BundleDependency> dependencytransformation)
			throws NullPointerException {
		if (this.isEmpty()) {
			return this;
		}
		Objects.requireNonNull(dependencytransformation, "dependency transformation function");
		Set<BundleDependency> ndeps = new LinkedHashSet<>();
		boolean changed = false;
		for (BundleDependency dep : this.dependencies) {
			BundleDependency replace = dependencytransformation.apply(dep);
			if (!changed && !dep.equals(replace)) {
				changed = true;
			}
			if (replace == null) {
				continue;
			}
			ndeps.add(replace);
		}
		if (!changed) {
			return this;
		}
		if (ndeps.isEmpty()) {
			return EMPTY;
		}
		return new BundleDependencyList(ImmutableUtils.unmodifiableSet(ndeps));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, dependencies);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		dependencies = SerialUtils.readExternalImmutableLinkedHashSet(in);
	}

	@Override
	public int hashCode() {
		return dependencies.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BundleDependencyList other = (BundleDependencyList) obj;
		if (dependencies == null) {
			if (other.dependencies != null)
				return false;
		} else if (!dependencies.equals(other.dependencies))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[dependencies=" + dependencies + "]";
	}

}
