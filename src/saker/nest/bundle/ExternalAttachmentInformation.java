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
 * Contains information about an external dependency attachment.
 * <p>
 * The class is an immutable information container about an attachment for a given external dependency. The class
 * contains hash information, meta-data, and entry specification for the attachment.
 * <p>
 * Use {@link #builder()} to create a new instance.
 * 
 * @since saker.nest 0.8.5
 * @see ExternalDependencyInformation
 * @see ExternalDependencyList
 * @see ExternalDependencyList#getSourceAttachments()
 * @see ExternalDependencyList#getDocumentationAttachments()
 */
public final class ExternalAttachmentInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * A singleton instance that contains no properties.
	 */
	public static final ExternalAttachmentInformation EMPTY = new ExternalAttachmentInformation();
	static {
		EMPTY.entries = Collections.emptyNavigableSet();
		EMPTY.targetEntries = Collections.emptyNavigableSet();
		EMPTY.metaData = Collections.emptyMap();
	}
	private String sha256Hash;
	private String sha1Hash;
	private String md5Hash;
	private NavigableSet<WildcardPath> entries;
	private NavigableSet<WildcardPath> targetEntries;
	private Map<String, String> metaData;
	private boolean includesMainArchive;
	private boolean targetsMainArchive;

	/**
	 * For {@link Externalizable}.
	 */
	public ExternalAttachmentInformation() {
	}

	/**
	 * Gets the expected SHA-256 hash.
	 * 
	 * @return The SHA-256 hash in lowercase hexa format or <code>null</code> if none.
	 */
	public String getSha256Hash() {
		return sha256Hash;
	}

	/**
	 * Gets the expected SHA-1 hash.
	 * 
	 * @return The SHA-1 hash in lowercase hexa format or <code>null</code> if none.
	 */
	public String getSha1Hash() {
		return sha1Hash;
	}

	/**
	 * Gets the expected MD5 hash.
	 * 
	 * @return The MD5 hash in lowercase hexa format or <code>null</code> if none.
	 */
	public String getMd5Hash() {
		return md5Hash;
	}

	/**
	 * Gets the entries that this attachment is associated with.
	 * <p>
	 * The entries are retrieved from the {@link URI} of the attachment declaration by interpreting it as a ZIP archive
	 * and finding the entries that match any of the returned wildcards.
	 * 
	 * @return An unmodifiable set of wildcards specifying the entries.
	 */
	public Set<WildcardPath> getEntries() {
		return entries;
	}

	/**
	 * Gets the entries that this attachment is defined for.
	 * <p>
	 * The target entries are the ones that the attachment is attached to. The entries are retrieved from the
	 * {@link URI} of the dependency declaration by interpreting it as a ZIP archive and finding the entries that match
	 * any of the returned wildcards.
	 * 
	 * @return An unmodifiable set of wildcards specifying the target entries.
	 */
	public NavigableSet<WildcardPath> getTargetEntries() {
		return targetEntries;
	}

	/**
	 * Checks if the main archive is included by this attachment.
	 * <p>
	 * The main archive is the file that the {@link URI} of the attachment declaration points to.
	 * 
	 * @return <code>true</code> if the main archive is included.
	 */
	public boolean isIncludesMainArchive() {
		return includesMainArchive;
	}

	/**
	 * Checks if the attachment is defined for the main archive.
	 * <p>
	 * The main archive is the file that the {@link URI} of the attachment declaration points to.
	 * 
	 * @return <code>true</code> if the main archive is included.
	 */
	public boolean isTargetsMainArchive() {
		return targetsMainArchive;
	}

	/**
	 * Gets the meta-data entries of this external attachment.
	 * 
	 * @return An unmodifiable set of meta-data entries.
	 */
	public Map<String, String> getMetaData() {
		return metaData;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(sha256Hash);
		out.writeObject(sha1Hash);
		out.writeObject(md5Hash);
		SerialUtils.writeExternalCollection(out, entries);
		SerialUtils.writeExternalCollection(out, targetEntries);
		SerialUtils.writeExternalMap(out, metaData);
		out.writeBoolean(includesMainArchive);
		out.writeBoolean(targetsMainArchive);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		sha256Hash = SerialUtils.readExternalObject(in);
		sha1Hash = SerialUtils.readExternalObject(in);
		md5Hash = SerialUtils.readExternalObject(in);
		entries = SerialUtils.readExternalImmutableNavigableSet(in);
		targetEntries = SerialUtils.readExternalImmutableNavigableSet(in);
		metaData = SerialUtils.readExternalImmutableLinkedHashMap(in);
		includesMainArchive = in.readBoolean();
		targetsMainArchive = in.readBoolean();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entries == null) ? 0 : entries.hashCode());
		result = prime * result + (includesMainArchive ? 1231 : 1237);
		result = prime * result + ((md5Hash == null) ? 0 : md5Hash.hashCode());
		result = prime * result + ((metaData == null) ? 0 : metaData.hashCode());
		result = prime * result + ((sha1Hash == null) ? 0 : sha1Hash.hashCode());
		result = prime * result + ((sha256Hash == null) ? 0 : sha256Hash.hashCode());
		result = prime * result + ((targetEntries == null) ? 0 : targetEntries.hashCode());
		result = prime * result + (targetsMainArchive ? 1231 : 1237);
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
		if (includesMainArchive != other.includesMainArchive)
			return false;
		if (md5Hash == null) {
			if (other.md5Hash != null)
				return false;
		} else if (!md5Hash.equals(other.md5Hash))
			return false;
		if (metaData == null) {
			if (other.metaData != null)
				return false;
		} else if (!metaData.equals(other.metaData))
			return false;
		if (sha1Hash == null) {
			if (other.sha1Hash != null)
				return false;
		} else if (!sha1Hash.equals(other.sha1Hash))
			return false;
		if (sha256Hash == null) {
			if (other.sha256Hash != null)
				return false;
		} else if (!sha256Hash.equals(other.sha256Hash))
			return false;
		if (targetEntries == null) {
			if (other.targetEntries != null)
				return false;
		} else if (!targetEntries.equals(other.targetEntries))
			return false;
		if (targetsMainArchive != other.targetsMainArchive)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (ObjectUtils.isNullOrEmpty(entries) ? "entries=" + entries + ", " : "")
				+ (ObjectUtils.isNullOrEmpty(targetEntries) ? "targetEntries=" + targetEntries + ", " : "")
				+ (ObjectUtils.isNullOrEmpty(metaData) ? "metaData=" + metaData + ", " : "") + "includesMainArchive="
				+ includesMainArchive + ", targetsMainArchive=" + targetsMainArchive + "]";
	}

	/**
	 * Creates a new {@link ExternalAttachmentInformation} builder.
	 * 
	 * @return The builder.
	 */
	public static ExternalAttachmentInformation.Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for {@link ExternalAttachmentInformation}.
	 */
	public static final class Builder {
		private String sha256Hash;
		private String sha1Hash;
		private String md5Hash;
		private NavigableSet<WildcardPath> entries;
		private NavigableSet<WildcardPath> targetEntries;
		private Map<String, String> metaData;
		private boolean includesMainArchive;
		private boolean targetsMainArchive;

		Builder() {
		}

		/**
		 * Gets the SHA-256 hash previously set using {@link #setSha256Hash(String)}.
		 * 
		 * @return The hash or <code>null</code> if none.
		 */
		public String getSha256Hash() {
			return sha256Hash;
		}

		/**
		 * Gets the SHA-1 hash previously set using {@link #setSha1Hash(String)}.
		 * 
		 * @return The hash or <code>null</code> if none.
		 */
		public String getSha1Hash() {
			return sha1Hash;
		}

		/**
		 * Gets the MD5 hash previously set using {@link #setMd5Hash(String)}.
		 * 
		 * @return The hash or <code>null</code> if none.
		 */
		public String getMd5Hash() {
			return md5Hash;
		}

		/**
		 * Sets the SHA-256 hash.
		 * <p>
		 * The argument will be converted to lowercase hexa representation.
		 * 
		 * @param sha256Hash
		 *            The hash or <code>null</code> to unset.
		 * @return <code>this</code>
		 * @throws IllegalArgumentException
		 *             If the argument is not a valid SHA-256 hash in hexa format.
		 * @see ExternalAttachmentInformation#getSha256Hash()
		 */
		public Builder setSha256Hash(String sha256Hash) throws IllegalArgumentException {
			if (sha256Hash == null) {
				this.sha256Hash = null;
				return this;
			}
			this.sha256Hash = ExternalDependencyInformation.validateSha256(sha256Hash);
			return this;
		}

		/**
		 * Sets the SHA-1 hash.
		 * <p>
		 * The argument will be converted to lowercase hexa representation.
		 * 
		 * @param sha1Hash
		 *            The hash or <code>null</code> to unset.
		 * @return <code>this</code>
		 * @throws IllegalArgumentException
		 *             If the argument is not a valid SHA-1 hash in hexa format.
		 * @see ExternalAttachmentInformation#getSha1Hash()
		 */
		public Builder setSha1Hash(String sha1Hash) throws IllegalArgumentException {
			if (sha1Hash == null) {
				this.sha1Hash = null;
				return this;
			}
			this.sha1Hash = ExternalDependencyInformation.validateSha1(sha1Hash);
			return this;
		}

		/**
		 * Sets the MD5 hash.
		 * <p>
		 * The argument will be converted to lowercase hexa representation.
		 * 
		 * @param md5Hash
		 *            The hash or <code>null</code> to unset.
		 * @return <code>this</code>
		 * @throws IllegalArgumentException
		 *             If the argument is not a valid MD5 hash in hexa format.
		 * @see ExternalAttachmentInformation#getMd5Hash()
		 */
		public Builder setMd5Hash(String md5Hash) throws IllegalArgumentException {
			if (md5Hash == null) {
				this.md5Hash = null;
				return this;
			}
			this.md5Hash = ExternalDependencyInformation.validateMd5(md5Hash);
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
		 * @see ExternalAttachmentInformation#getMetaData()
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
		 * @see ExternalAttachmentInformation#getEntries()
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
		 * Adds a target entry wildcard.
		 * 
		 * @param wildcard
		 *            The wildcard.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>.
		 * @see ExternalAttachmentInformation#getTargetEntries()
		 */
		public Builder addTargetEntry(WildcardPath wildcard) throws NullPointerException {
			Objects.requireNonNull(wildcard, "wildcard");
			if (this.targetEntries == null) {
				this.targetEntries = new TreeSet<>();
			}
			this.targetEntries.add(wildcard);
			return this;
		}

		/**
		 * Sets if the main archive is included or not.
		 * <p>
		 * Note that the actual value of this property in the {@linkplain #build() built} object will be set to
		 * <code>true</code> if there are no {@linkplain ExternalAttachmentInformation#getEntries() entries}.
		 * 
		 * @param includesMainArchive
		 *            <code>true</code> to include the main archive.
		 * @return <code>this</code>
		 * @see ExternalAttachmentInformation#isIncludesMainArchive()
		 */
		public void setIncludesMainArchive(boolean includesMainArchive) {
			this.includesMainArchive = includesMainArchive;
		}

		/**
		 * Sets if the main archive is targeted or not.
		 * <p>
		 * Note that the actual value of this property in the {@linkplain #build() built} object will be set to
		 * <code>true</code> if there are no {@linkplain ExternalAttachmentInformation#getTargetEntries() target
		 * entries}.
		 * 
		 * @param targetsMainArchive
		 *            <code>true</code> to target the main archive.
		 * @return <code>this</code>
		 * @see ExternalAttachmentInformation#isTargetsMainArchive()
		 */
		public void setTargetsMainArchive(boolean targetsMainArchive) {
			this.targetsMainArchive = targetsMainArchive;
		}

		/**
		 * Builds the external attachment information.
		 * <p>
		 * The builder can be reused after this call.
		 * 
		 * @return The constructed {@link ExternalAttachmentInformation}.
		 */
		public ExternalAttachmentInformation build() {
			ExternalAttachmentInformation result = new ExternalAttachmentInformation();
			result.sha256Hash = this.sha256Hash;
			result.sha1Hash = this.sha1Hash;
			result.md5Hash = this.md5Hash;
			result.metaData = metaData == null ? Collections.emptyMap()
					: ImmutableUtils.makeImmutableLinkedHashMap(metaData);
			result.entries = entries == null ? Collections.emptyNavigableSet()
					: ImmutableUtils.makeImmutableNavigableSet(entries);
			result.targetEntries = targetEntries == null ? Collections.emptyNavigableSet()
					: ImmutableUtils.makeImmutableNavigableSet(targetEntries);
			result.includesMainArchive = includesMainArchive || ObjectUtils.isNullOrEmpty(result.entries);
			result.targetsMainArchive = targetsMainArchive || ObjectUtils.isNullOrEmpty(result.targetEntries);
			return result;
		}
	}

}
