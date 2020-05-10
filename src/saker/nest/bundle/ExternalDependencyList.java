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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * List of external dependency specifications for a given resource.
 * <p>
 * The dependency list is an immutable information object used with {@link ExternalDependencyInformation} that holds the
 * dependency data for a given external reosurce dependency.
 * <p>
 * Use {@link #builder()} to create a new instance.
 * 
 * @since saker.nest 0.8.5
 * @see ExternalDependencyInformation
 */
public final class ExternalDependencyList implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * A singleton instance that contains no properties.
	 */
	public static final ExternalDependencyList EMPTY = new ExternalDependencyList();
	static {
		EMPTY.sourceAttachments = Collections.emptyMap();
		EMPTY.documentationAttachments = Collections.emptyMap();
		EMPTY.dependencies = Collections.emptySet();
	}
	private String sha256Hash;
	private String sha1Hash;
	private String md5Hash;
	private Set<? extends ExternalDependency> dependencies;
	private Map<URI, ExternalAttachmentInformation> sourceAttachments;
	private Map<URI, ExternalAttachmentInformation> documentationAttachments;

	/**
	 * For {@link Externalizable}.
	 */
	public ExternalDependencyList() {
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
	 * Gets the source attachments.
	 * 
	 * @return An unmodifiable map of source attachment informations.
	 */
	public Map<URI, ExternalAttachmentInformation> getSourceAttachments() {
		return sourceAttachments;
	}

	/**
	 * Gets the documentation attachments.
	 * 
	 * @return An unmodifiable map of documentation attachment informations.
	 */
	public Map<URI, ExternalAttachmentInformation> getDocumentationAttachments() {
		return documentationAttachments;
	}

	/**
	 * Gets the dependencies contained in this object.
	 * 
	 * @return An unmodifiable set of dependencies.
	 */
	public Set<? extends ExternalDependency> getDependencies() {
		return dependencies;
	}

	/**
	 * Checks if this dependency object is empty.
	 * <p>
	 * Note that even if this method returns <code>true</code>, the object may still contain other values such as hashes
	 * and attachments. It just checks if {@link #getDependencies()} contains any elements.
	 * 
	 * @return <code>true</code> if there are no declared dependencies in this information object.
	 */
	public boolean isEmpty() {
		return ObjectUtils.isNullOrEmpty(dependencies);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(sha256Hash);
		out.writeObject(sha1Hash);
		out.writeObject(md5Hash);
		SerialUtils.writeExternalCollection(out, dependencies);
		SerialUtils.writeExternalMap(out, sourceAttachments);
		SerialUtils.writeExternalMap(out, documentationAttachments);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		sha256Hash = SerialUtils.readExternalObject(in);
		sha1Hash = SerialUtils.readExternalObject(in);
		md5Hash = SerialUtils.readExternalObject(in);
		dependencies = SerialUtils.readExternalImmutableLinkedHashSet(in);
		sourceAttachments = SerialUtils.readExternalImmutableLinkedHashMap(in);
		documentationAttachments = SerialUtils.readExternalImmutableLinkedHashMap(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dependencies == null) ? 0 : dependencies.hashCode());
		result = prime * result + ((documentationAttachments == null) ? 0 : documentationAttachments.hashCode());
		result = prime * result + ((md5Hash == null) ? 0 : md5Hash.hashCode());
		result = prime * result + ((sha1Hash == null) ? 0 : sha1Hash.hashCode());
		result = prime * result + ((sha256Hash == null) ? 0 : sha256Hash.hashCode());
		result = prime * result + ((sourceAttachments == null) ? 0 : sourceAttachments.hashCode());
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
		ExternalDependencyList other = (ExternalDependencyList) obj;
		if (dependencies == null) {
			if (other.dependencies != null)
				return false;
		} else if (!dependencies.equals(other.dependencies))
			return false;
		if (documentationAttachments == null) {
			if (other.documentationAttachments != null)
				return false;
		} else if (!documentationAttachments.equals(other.documentationAttachments))
			return false;
		if (md5Hash == null) {
			if (other.md5Hash != null)
				return false;
		} else if (!md5Hash.equals(other.md5Hash))
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
		if (sourceAttachments == null) {
			if (other.sourceAttachments != null)
				return false;
		} else if (!sourceAttachments.equals(other.sourceAttachments))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (sha256Hash != null ? "sha256Hash=" + sha256Hash + ", " : "")
				+ (sha1Hash != null ? "sha1Hash=" + sha1Hash + ", " : "")
				+ (md5Hash != null ? "md5Hash=" + md5Hash + ", " : "")
				+ (!ObjectUtils.isNullOrEmpty(dependencies) ? "dependencies=" + dependencies + ", " : "")
				+ (!ObjectUtils.isNullOrEmpty(sourceAttachments) ? "sourceAttachments=" + sourceAttachments + ", " : "")
				+ (!ObjectUtils.isNullOrEmpty(documentationAttachments)
						? "documentationAttachments=" + documentationAttachments
						: "")
				+ "]";
	}

	/**
	 * Creates a new {@link ExternalDependencyList} builder.
	 * 
	 * @return The builder.
	 */
	public static ExternalDependencyList.Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for {@link ExternalDependencyList}.
	 */
	public static final class Builder {
		private String sha256Hash;
		private String sha1Hash;
		private String md5Hash;
		private Map<URI, ExternalAttachmentInformation> sourceAttachments = new LinkedHashMap<>();
		private Map<URI, ExternalAttachmentInformation> documentationAttachments = new LinkedHashMap<>();

		private Set<ExternalDependency> dependencies = new LinkedHashSet<>();

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
		 * @see ExternalDependencyList#getSha256Hash()
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
		 * @see ExternalDependencyList#getSha1Hash()
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
		 * @see ExternalDependencyList#getMd5Hash()
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
		 * Adds the argument dependency to the list of dependencies.
		 * 
		 * @param dep
		 *            The dependency.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If the argument is <code>null</code>
		 * @see ExternalDependencyList#getDependencies()
		 */
		public Builder addDepdendency(ExternalDependency dep) throws NullPointerException {
			Objects.requireNonNull(dep, "dependency");
			this.dependencies.add(dep);
			return this;
		}

		/**
		 * Checks if a source attachment is already added for the given {@link URI}.
		 * 
		 * @param uri
		 *            The {@link URI} to check for.
		 * @return <code>true</code> if a source attachment is already present for the given resource identifier.
		 */
		public boolean hasSourceAttachment(URI uri) {
			return uri != null && sourceAttachments.containsKey(uri);
		}

		/**
		 * Adds a source attachment.
		 * 
		 * @param uri
		 *            The {@link URI} of the attachment.
		 * @param info
		 *            The attachment information.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 * @see ExternalDependencyList#getSourceAttachments()
		 */
		public Builder addSourceAttachment(URI uri, ExternalAttachmentInformation info) throws NullPointerException {
			Objects.requireNonNull(uri, "uri");
			Objects.requireNonNull(info, "external attachment info");
			this.sourceAttachments.put(uri, info);
			return this;
		}

		/**
		 * Checks if a documentation attachment is already added for the given {@link URI}.
		 * 
		 * @param uri
		 *            The {@link URI} to check for.
		 * @return <code>true</code> if a documentation attachment is already present for the given resource identifier.
		 */
		public boolean hasDocumentationAttachment(URI uri) {
			return uri != null && documentationAttachments.containsKey(uri);
		}

		/**
		 * Adds a documentation attachment.
		 * 
		 * @param uri
		 *            The {@link URI} of the attachment.
		 * @param info
		 *            The attachment information.
		 * @return <code>this</code>
		 * @throws NullPointerException
		 *             If any of the arguments are <code>null</code>.
		 * @see ExternalDependencyList#getDocumentationAttachments()
		 */
		public Builder addDocumentationAttachment(URI uri, ExternalAttachmentInformation info)
				throws NullPointerException {
			Objects.requireNonNull(uri, "uri");
			Objects.requireNonNull(info, "external attachment info");
			this.documentationAttachments.put(uri, info);
			return this;
		}

		/**
		 * Creates an {@link ExternalDependencyList} object from the contents of this builder.
		 * <p>
		 * The builder can be reused after this call.
		 * 
		 * @return The constructed dependency list.
		 */
		public ExternalDependencyList build() {
			ExternalDependencyList res = new ExternalDependencyList();
			res.sha256Hash = this.sha256Hash;
			res.sha1Hash = this.sha1Hash;
			res.md5Hash = this.md5Hash;
			res.sourceAttachments = ImmutableUtils.makeImmutableLinkedHashMap(this.sourceAttachments);
			res.documentationAttachments = ImmutableUtils.makeImmutableLinkedHashMap(this.documentationAttachments);
			res.dependencies = ImmutableUtils.makeImmutableLinkedHashSet(this.dependencies);
			return res;
		}
	}

}
