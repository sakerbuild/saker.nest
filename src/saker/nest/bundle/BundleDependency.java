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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.nest.version.VersionRange;

/**
 * Encloses immutable information about a bundle dependency.
 * <p>
 * Dependencies are associated with dependency kinds and are defined with a specific {@linkplain VersionRange version
 * range}. Additionally, dependencies can contain meta-data which are arbitrary key-value string interpreted in an
 * implementation dependent manner.
 * <p>
 * The dependency kinds are arbitrary strings that specifies in what context should a given dependency applied to the
 * declaring entity. E.g. a dependency with <code>classpath</code> kind will be used to determine the bundles required
 * on the classpath for a given bundle. See {@link BundleInformation#DEPENDENCY_KIND_CLASSPATH}.
 * <p>
 * The version range specifies the version requirements for a given dependency. When the dependencies are resolved, the
 * version range attribute will determine if a given bundle can be used or not.
 * <p>
 * The meta-data key-value parts can be used by the dependency resolution algorithm in an implementation dependent way.
 * These entries are to be interpreted in the context of the bundle dependency kind. <br>
 * E.g. The <code>optional</code> meta-data is interpreted as a <code>boolean</code> by the classpath resolver of the
 * Nest repository to signal that a given bundle may be omitted of not available.
 * <p>
 * Both dependency kinds and meta-data names are case sensitive and can only contain alphabetic, numeric, underscore,
 * and dash characters.
 * <p>
 * New instances can be constructed by using the {@link #builder()} method(s).
 * 
 * @see BundleDependencyInformation
 * @see BundleInformation#ENTRY_BUNDLE_DEPENDENCIES
 */
public final class BundleDependency implements Externalizable {
	private static final long serialVersionUID = 1L;

	private static final Pattern PATTERN_DEPENDENCY_KIND = Pattern.compile("[a-zA-Z_\\-0-9]+");
	private static final Pattern PATTERN_METADATA_NAME = Pattern.compile("[a-zA-Z_\\-0-9]+");

	private NavigableSet<String> kinds;
	private VersionRange range;
	private Map<String, String> metaData;

	/**
	 * For {@link Externalizable}.
	 */
	public BundleDependency() {
	}

	private BundleDependency(NavigableSet<String> kinds, VersionRange range, Map<String, String> metaData) {
		this.kinds = kinds;
		this.range = range;
		this.metaData = metaData;
	}

	/**
	 * Gets the kinds of this bundle dependency.
	 * 
	 * @return An unmodifiable set of dependency kinds. (Never empty.)
	 */
	public Set<String> getKinds() {
		return kinds;
	}

	/**
	 * Gets the version range of this bundle dependency.
	 * 
	 * @return The version range which specifies the allowed versions.
	 */
	public VersionRange getRange() {
		return range;
	}

	/**
	 * Gets the meta-data entries of this bundle dependency.
	 * 
	 * @return An unmodifiable set of meta-data entries.
	 */
	public Map<String, String> getMetaData() {
		return metaData;
	}

	/**
	 * Checks if the argument kind is present in this bundle dependency.
	 * 
	 * @param kind
	 *            The kind.
	 * @return <code>true</code> if the argument is non-<code>null</code> and is contained in this dependency.
	 */
	public boolean hasKind(String kind) {
		return kind != null && kinds.contains(kind);
	}

	/**
	 * Checks if the dependency contains the <code>optional</code> meta-data and is associated with a <code>true</code>
	 * boolean value.
	 * 
	 * @return <code>true</code> if the dependency was declared to be optional in the meta-datas.
	 * @see BundleInformation#DEPENDENCY_META_OPTIONAL
	 */
	public boolean isOptional() {
		return Boolean.parseBoolean(metaData.get(BundleInformation.DEPENDENCY_META_OPTIONAL));
	}

	/**
	 * Checks if the dependency contains the <code>private</code> meta-data and is associated with a <code>true</code>
	 * boolean value.
	 * 
	 * @return <code>true</code> if the dependency was declared to be private in the meta-datas.
	 * @see BundleInformation#DEPENDENCY_META_PRIVATE
	 * @since saker.nest 0.8.1
	 */
	public boolean isPrivate() {
		return Boolean.parseBoolean(metaData.get(BundleInformation.DEPENDENCY_META_PRIVATE));
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, kinds);
		out.writeObject(range);
		SerialUtils.writeExternalMap(out, metaData);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		kinds = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		range = (VersionRange) in.readObject();
		metaData = SerialUtils.readExternalImmutableLinkedHashMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((kinds == null) ? 0 : kinds.hashCode());
		result = prime * result + ((metaData == null) ? 0 : metaData.hashCode());
		result = prime * result + ((range == null) ? 0 : range.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BundleDependency other = (BundleDependency) obj;
		if (!ObjectUtils.iterablesOrderedEquals(this.kinds, other.kinds)) {
			return false;
		}
		if (!range.equals(other.range))
			return false;
		if (!metaData.equals(other.metaData))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[kinds=" + kinds + ", range=" + range + ", metaData=" + metaData + "]";
	}

	/**
	 * Creates a new builder.
	 * 
	 * @return A new bundle dependency builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a new builder and initializes it with data from the argument bundle dependency.
	 * 
	 * @param copy
	 *            The dependency to copy.
	 * @return The new builder.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Builder builder(BundleDependency copy) throws NullPointerException {
		Objects.requireNonNull(copy, "copy dependency");
		return new Builder(copy);
	}

	/**
	 * Checks if the argument is a valid dependency kind.
	 * 
	 * @param kind
	 *            The kind to check its format.
	 * @return <code>true</code> if the argument is accepted as a dependency kind.
	 */
	public static boolean isValidKind(String kind) {
		return kind != null && PATTERN_DEPENDENCY_KIND.matcher(kind).matches();
	}

	/**
	 * Checks if the argument is a valid meta-data name.
	 * 
	 * @param name
	 *            The name to check its format.
	 * @return <code>true</code> if the argument is accepted as a meta-data name.
	 */
	public static boolean isValidMetaDataName(String name) {
		return name != null && PATTERN_METADATA_NAME.matcher(name).matches();
	}

	/**
	 * {@link BundleDependency} builder class.
	 */
	public static final class Builder {
		private NavigableSet<String> kinds;
		private VersionRange range;
		private Map<String, String> metaData;

		Builder() {
			kinds = new TreeSet<>();
		}

		Builder(BundleDependency copy) {
			this.kinds = new TreeSet<>(copy.kinds);
			this.range = copy.range;
			this.metaData = new LinkedHashMap<>(copy.metaData);
		}

		/**
		 * Sets the version range.
		 * 
		 * @param range
		 *            The allowed version range.
		 * @return <code>this</code>
		 * @see BundleDependency#getRange()
		 */
		public Builder setRange(VersionRange range) {
			this.range = range;
			return this;
		}

		/**
		 * Adds a bundle dependency kind.
		 * 
		 * @param kind
		 *            The dependency kind.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>.
		 * @see BundleDependency#getKinds()
		 */
		public Builder addKind(String kind) throws NullPointerException {
			Objects.requireNonNull(kind, "kind");
			if (!isValidKind(kind)) {
				throw new IllegalArgumentException("Invalid dependency kind format: " + kind);
			}
			kinds.add(kind);
			return this;
		}

		/**
		 * Adds a new meta-data entry.
		 * 
		 * @param name
		 *            The name of the meta-data.
		 * @param content
		 *            The value of the meta-data.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If any the arguments are <code>null</code>.
		 */
		public Builder addMetaData(String name, String content) throws NullPointerException {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(content, "content");
			if (metaData == null) {
				metaData = new LinkedHashMap<>();
			}
			if (!isValidMetaDataName(name)) {
				throw new IllegalArgumentException("Invalid dependency meta-data name format: " + name);
			}
			metaData.put(name, content);
			return this;
		}

		/**
		 * Checks if the argument meta-data is already present in this builder.
		 * 
		 * @param name
		 *            The meta-data name.
		 * @return <code>true</code> if it was already set.
		 */
		public boolean hasMetaData(String name) {
			return ObjectUtils.containsKey(metaData, name);
		}

		/**
		 * Clears the previously set kinds.
		 * 
		 * @return <code>this</code>
		 */
		public Builder clearKinds() {
			kinds.clear();
			return this;
		}

		/**
		 * Clears the previously set meta-datas.
		 * 
		 * @return <code>this</code>
		 */
		public Builder clearMetaData() {
			metaData = null;
			return this;
		}

		/**
		 * Creates the bundle dependency based on the assigned data.
		 * 
		 * @return The created dependency.
		 * @throws IllegalStateException
		 *             If no {@linkplain #setRange(VersionRange) version range} was set, or no
		 *             {@linkplain #addKind(String) kinds} were specified.
		 */
		public BundleDependency build() throws IllegalStateException {
			if (range == null) {
				throw new IllegalStateException("Version range not set.");
			}
			if (kinds.isEmpty()) {
				throw new IllegalStateException("No kinds specified.");
			}
			return new BundleDependency(ImmutableUtils.makeImmutableNavigableSet(kinds), range,
					metaData == null ? Collections.emptyMap() : ImmutableUtils.makeImmutableLinkedHashMap(metaData));
		}
	}

}
