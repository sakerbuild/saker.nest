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
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import saker.build.file.path.WildcardPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Immutable class holding information about an external dependency.
 * <p>
 * An external dependency contains information about the dependency kinds, meta-data, and associated entries for an
 * external dependency.
 * <p>
 * The class is immutable.
 * <p>
 * Use {@link #builder()} to create a new instance.
 * 
 * @since saker.nest 0.8.5
 * @see ExternalDependencyInformation
 * @see ExternalDependencyList
 */
public final class ExternalDependency implements Externalizable {
	private static final long serialVersionUID = 1L;

	private NavigableSet<String> kinds;
	private Map<String, String> metaData;
	private NavigableSet<WildcardPath> entries;
	private boolean includesMainArchive;

	/**
	 * For {@link Externalizable}.
	 */
	public ExternalDependency() {
	}

	/**
	 * Gets the kinds of this external dependency.
	 * 
	 * @return An unmodifiable set of dependency kinds. (Never empty.)
	 */
	public Set<String> getKinds() {
		return kinds;
	}

	/**
	 * Gets the meta-data entries of this external dependency.
	 * 
	 * @return An unmodifiable set of meta-data entries.
	 */
	public Map<String, String> getMetaData() {
		return metaData;
	}

	/**
	 * Gets the entries that this dependency is associated with.
	 * <p>
	 * The entries are retrieved from the {@link URI} of the dependency declaration by interpreting it as a ZIP archive
	 * and finding the entries that match any of the returned wildcards.
	 * 
	 * @return An unmodifiable set of wildcards specifying the entries.
	 */
	public Set<WildcardPath> getEntries() {
		return entries;
	}

	/**
	 * Checks if the main archive is included by this dependency.
	 * <p>
	 * The main archive is the file that the {@link URI} of the dependency declaration points to.
	 * 
	 * @return <code>true</code> if the main archive is included.
	 */
	public boolean isIncludesMainArchive() {
		return includesMainArchive;
	}

	/**
	 * Checks if this dependency has private scope.
	 * <p>
	 * External dependencies are <b>private by default</b>. They are made public if and only if the <code>private</code>
	 * meta-data is declared and associated with <code>false</code>.
	 * 
	 * @return <code>true</code> if the dependency is private.
	 */
	public boolean isPrivate() {
		String mdvalue = metaData.get(BundleInformation.DEPENDENCY_META_PRIVATE);
		//external dependencies are private by default
		//a dependency is not private if any only if the value is false
		if ("false".equalsIgnoreCase(mdvalue)) {
			return false;
		}
		return true;
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

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, kinds);
		SerialUtils.writeExternalCollection(out, entries);
		SerialUtils.writeExternalMap(out, metaData);
		out.writeBoolean(includesMainArchive);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		kinds = SerialUtils.readExternalImmutableNavigableSet(in);
		entries = SerialUtils.readExternalImmutableNavigableSet(in);
		metaData = SerialUtils.readExternalImmutableLinkedHashMap(in);
		includesMainArchive = in.readBoolean();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entries == null) ? 0 : entries.hashCode());
		result = prime * result + (includesMainArchive ? 1231 : 1237);
		result = prime * result + ((kinds == null) ? 0 : kinds.hashCode());
		result = prime * result + ((metaData == null) ? 0 : metaData.hashCode());
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
		ExternalDependency other = (ExternalDependency) obj;
		if (entries == null) {
			if (other.entries != null)
				return false;
		} else if (!entries.equals(other.entries))
			return false;
		if (includesMainArchive != other.includesMainArchive)
			return false;
		if (kinds == null) {
			if (other.kinds != null)
				return false;
		} else if (!kinds.equals(other.kinds))
			return false;
		if (metaData == null) {
			if (other.metaData != null)
				return false;
		} else if (!metaData.equals(other.metaData))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (!ObjectUtils.isNullOrEmpty(kinds) ? "kinds=" + kinds + ", " : "")
				+ (!ObjectUtils.isNullOrEmpty(metaData) ? "metaData=" + metaData + ", " : "")
				+ (!ObjectUtils.isNullOrEmpty(entries) ? "entries=" + entries + ", " : "") + "includesMainArchive="
				+ includesMainArchive + "]";
	}

	/**
	 * Creates a new {@link ExternalDependency} builder.
	 * 
	 * @return The builder.
	 */
	public static ExternalDependency.Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for {@link ExternalDependency}.
	 * <p>
	 * At least one {@linkplain #addKind(String) dependency kind} must be added before calling {@link #build()}.
	 */
	public static final class Builder {
		private NavigableSet<String> kinds = new TreeSet<>();
		private Map<String, String> metaData;
		private NavigableSet<WildcardPath> entries;
		private boolean includesMainArchive;

		Builder() {
		}

		/**
		 * Adds a dependency kind.
		 * 
		 * @param kind
		 *            The dependency kind.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>.
		 * @throws IllegalArgumentException
		 *             If the format of the kind is inappropriate. See {@link BundleDependency#isValidKind(String)}.
		 * @see ExternalDependency#getKinds()
		 */
		public Builder addKind(String kind) throws NullPointerException, IllegalArgumentException {
			Objects.requireNonNull(kind, "kind");
			if (!BundleDependency.isValidKind(kind)) {
				throw new IllegalArgumentException("Invalid dependency kind format: " + kind);
			}
			kinds.add(kind);
			return this;
		}

		/**
		 * Adda a meta-data entry.
		 * 
		 * @param name
		 *            The name of the meta-data.
		 * @param content
		 *            The value of the meta-data.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 * @throws IllegalArgumentException
		 *             If the format of the meta-data name is inappropriate. See
		 *             {@link BundleDependency#isValidMetaDataName(String)}.
		 * @see ExternalDependency#getMetaData()
		 */
		public Builder addMetaData(String name, String content) throws NullPointerException, IllegalArgumentException {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(content, "content");
			if (metaData == null) {
				metaData = new LinkedHashMap<>();
			}
			if (!BundleDependency.isValidMetaDataName(name)) {
				throw new IllegalArgumentException("Invalid dependency meta-data name format: " + name);
			}
			metaData.put(name, content);
			return this;
		}

		/**
		 * Checks if a meta-data with the given name is already present.
		 * 
		 * @param name
		 *            The meta-data name.
		 * @return <code>true</code> if the meta-data with the given name is already set.
		 */
		public boolean hasMetaData(String name) {
			return name != null && ObjectUtils.containsKey(metaData, name);
		}

		/**
		 * Adds an entry wildcard.
		 * 
		 * @param wildcard
		 *            The wildcard.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>.
		 * @see ExternalDependency#getEntries()
		 */
		public Builder addEntry(WildcardPath wildcard) throws NullPointerException {
			Objects.requireNonNull(wildcard, "wildcard");
			if (this.entries == null) {
				this.entries = new TreeSet<>();
			}
			this.entries.add(wildcard);
			return this;
		}

		/**
		 * Sets if the main archive is included or not.
		 * <p>
		 * Note that the actual value of this property in the {@linkplain #build() built} object will be set to
		 * <code>true</code> if there are no {@linkplain ExternalDependency#getEntries() entries}.
		 * 
		 * @param includesMainArchive
		 *            <code>true</code> to include the main archive.
		 * @return <code>this</code>
		 * @see ExternalDependency#isIncludesMainArchive()
		 */
		public Builder setIncludesMainArchive(boolean includesMainArchive) {
			this.includesMainArchive = includesMainArchive;
			return this;
		}

		/**
		 * Builds the external dependency.
		 * <p>
		 * The builder can be reused after this call.
		 * 
		 * @return The constructed {@link ExternalDependency}.
		 * @throws IllegalStateException
		 *             If no {@linkplain #addKind(String) kinds} were specified.
		 */
		public ExternalDependency build() throws IllegalStateException {
			if (kinds.isEmpty()) {
				throw new IllegalStateException("No kinds specified.");
			}
			ExternalDependency result = new ExternalDependency();
			result.kinds = ImmutableUtils.makeImmutableNavigableSet(kinds);
			result.metaData = metaData == null ? Collections.emptyMap()
					: ImmutableUtils.makeImmutableLinkedHashMap(metaData);
			result.entries = entries == null ? Collections.emptyNavigableSet()
					: ImmutableUtils.makeImmutableNavigableSet(entries);
			result.includesMainArchive = includesMainArchive || ObjectUtils.isNullOrEmpty(result.entries);
			return result;
		}
	}

}
