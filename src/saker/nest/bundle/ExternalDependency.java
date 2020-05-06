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

import saker.build.file.path.WildcardPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

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

	public Set<WildcardPath> getEntries() {
		return entries;
	}

	public boolean isIncludesMainArchive() {
		return includesMainArchive;
	}

	public boolean isPrivate() {
		String mdvalue = metaData.get(BundleInformation.DEPENDENCY_META_PRIVATE);
		//external dependencies are private by default
		//a dependency is not private if any only if the value is false
		if ("false".equalsIgnoreCase(mdvalue)) {
			return false;
		}
		return true;
	}

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

	public static ExternalDependency.Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private NavigableSet<String> kinds = new TreeSet<>();
		private Map<String, String> metaData;
		private NavigableSet<WildcardPath> entries;
		private boolean includesMainArchive;

		Builder() {
		}

		public Builder addKind(String kind) throws NullPointerException {
			Objects.requireNonNull(kind, "kind");
			if (!BundleDependency.isValidKind(kind)) {
				throw new IllegalArgumentException("Invalid dependency kind format: " + kind);
			}
			kinds.add(kind);
			return this;
		}

		public Builder addMetaData(String name, String content) throws NullPointerException {
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

		public boolean hasMetaData(String name) {
			return ObjectUtils.containsKey(metaData, name);
		}

		public Builder addEntry(WildcardPath wildcard) throws NullPointerException {
			Objects.requireNonNull(wildcard, "wildcard");
			if (this.entries == null) {
				this.entries = new TreeSet<>();
			}
			this.entries.add(wildcard);
			return this;
		}

		public void setIncludesMainArchive(boolean includesMainArchive) {
			this.includesMainArchive = includesMainArchive;
		}

		public ExternalDependency build() {
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
