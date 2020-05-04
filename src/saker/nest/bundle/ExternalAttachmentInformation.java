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

public final class ExternalAttachmentInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	public static final ExternalAttachmentInformation EMPTY = new ExternalAttachmentInformation();
	static {
		EMPTY.entries = Collections.emptyNavigableSet();
		EMPTY.metaData = Collections.emptyMap();
	}
	private NavigableSet<WildcardPath> entries;
	private Map<String, String> metaData;

	/**
	 * For {@link Externalizable}.
	 */
	public ExternalAttachmentInformation() {
	}

	public String getSha256Hash() {
		return metaData.get("SHA-256");
	}

	public String getSha1Hash() {
		return metaData.get("SHA-1");
	}

	public String getMd5Hash() {
		return metaData.get("MD5");
	}

	public Set<WildcardPath> getEntries() {
		return entries;
	}

	public Map<String, String> getMetaData() {
		return metaData;
	}

	public static ExternalAttachmentInformation.Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private NavigableSet<WildcardPath> entries;
		private Map<String, String> metaData;

		Builder() {
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

		public ExternalAttachmentInformation build() {
			ExternalAttachmentInformation result = new ExternalAttachmentInformation();
			result.metaData = metaData == null ? Collections.emptyMap()
					: ImmutableUtils.makeImmutableLinkedHashMap(metaData);
			result.entries = entries == null ? Collections.emptyNavigableSet()
					: ImmutableUtils.makeImmutableNavigableSet(entries);
			return result;
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, entries);
		SerialUtils.writeExternalMap(out, metaData);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		entries = SerialUtils.readExternalImmutableNavigableSet(in);
		metaData = SerialUtils.readExternalImmutableLinkedHashMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entries == null) ? 0 : entries.hashCode());
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
		ExternalAttachmentInformation other = (ExternalAttachmentInformation) obj;
		if (entries == null) {
			if (other.entries != null)
				return false;
		} else if (!entries.equals(other.entries))
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
		return getClass().getSimpleName() + "["
				+ (!ObjectUtils.isNullOrEmpty(entries) ? "entries=" + entries + ", " : "")
				+ (!ObjectUtils.isNullOrEmpty(metaData) ? "metaData=" + metaData : "") + "]";
	}

}
