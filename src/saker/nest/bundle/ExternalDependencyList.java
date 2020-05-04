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

public final class ExternalDependencyList implements Externalizable {
	private static final long serialVersionUID = 1L;

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

	public String getSha256Hash() {
		return sha256Hash;
	}

	public String getSha1Hash() {
		return sha1Hash;
	}

	public String getMd5Hash() {
		return md5Hash;
	}

	public Map<URI, ExternalAttachmentInformation> getSourceAttachments() {
		return sourceAttachments;
	}

	public Map<URI, ExternalAttachmentInformation> getDocumentationAttachments() {
		return documentationAttachments;
	}

	public Set<? extends ExternalDependency> getDependencies() {
		return dependencies;
	}

	public boolean isEmpty() {
		return ObjectUtils.isNullOrEmpty(dependencies);
	}

	public static ExternalDependencyList.Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String sha256Hash;
		private String sha1Hash;
		private String md5Hash;
		private Map<URI, ExternalAttachmentInformation> sourceAttachments = new LinkedHashMap<>();
		private Map<URI, ExternalAttachmentInformation> documentationAttachments = new LinkedHashMap<>();

		private Set<ExternalDependency> dependencies = new LinkedHashSet<>();

		Builder() {
		}

		public String getSha256Hash() {
			return sha256Hash;
		}

		public String getSha1Hash() {
			return sha1Hash;
		}

		public String getMd5Hash() {
			return md5Hash;
		}

		public Builder setSha256Hash(String sha256Hash) {
			this.sha256Hash = sha256Hash;
			return this;
		}

		public Builder setSha1Hash(String sha1Hash) {
			this.sha1Hash = sha1Hash;
			return this;
		}

		public Builder setMd5Hash(String md5Hash) {
			this.md5Hash = md5Hash;
			return this;
		}

		public Builder addDepdendency(ExternalDependency dep) {
			this.dependencies.add(dep);
			return this;
		}

		public boolean hasSourceAttachment(URI uri) {
			return sourceAttachments.containsKey(uri);
		}

		public Builder addSourceAttachment(URI uri, ExternalAttachmentInformation info) {
			Objects.requireNonNull(uri, "uri");
			Objects.requireNonNull(info, "external attachment info");
			this.sourceAttachments.put(uri, info);
			return this;
		}

		public boolean hasDocumentationAttachment(URI uri) {
			return documentationAttachments.containsKey(uri);
		}

		public Builder addDocumentationAttachment(URI uri, ExternalAttachmentInformation info) {
			Objects.requireNonNull(uri, "uri");
			Objects.requireNonNull(info, "external attachment info");
			this.documentationAttachments.put(uri, info);
			return this;
		}

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

}
