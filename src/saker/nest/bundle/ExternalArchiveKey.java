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

import saker.build.thirdparty.saker.util.io.SerialUtils;

//non-api class
public class ExternalArchiveKey implements Externalizable {
	private static final long serialVersionUID = 1L;

	private URI uri;
	private String entryName;

	/**
	 * For {@link Externalizable}.
	 */
	public ExternalArchiveKey() {
	}

	public ExternalArchiveKey(URI uri) {
		this.uri = uri;
	}

	public ExternalArchiveKey(URI uri, String entryName) {
		this.uri = uri;
		this.entryName = entryName;
	}

	public URI getUri() {
		return uri;
	}

	public String getEntryName() {
		return entryName;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(uri);
		out.writeObject(entryName);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		uri = SerialUtils.readExternalObject(in);
		entryName = SerialUtils.readExternalObject(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((entryName == null) ? 0 : entryName.hashCode());
		result = prime * result + ((uri == null) ? 0 : uri.hashCode());
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
		ExternalArchiveKey other = (ExternalArchiveKey) obj;
		if (entryName == null) {
			if (other.entryName != null)
				return false;
		} else if (!entryName.equals(other.entryName))
			return false;
		if (uri == null) {
			if (other.uri != null)
				return false;
		} else if (!uri.equals(other.uri))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + (uri != null ? "uri=" + uri + ", " : "")
				+ (entryName != null ? "entryName=" + entryName : "") + "]";
	}

}
