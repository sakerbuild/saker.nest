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

import saker.nest.bundle.storage.StorageViewKey;

public final class SimpleBundleKey implements BundleKey, Externalizable {
	private static final long serialVersionUID = 1L;

	private BundleIdentifier bundleIdentifier;
	private StorageViewKey storageViewKey;

	/**
	 * For {@link Externalizable}.
	 */
	public SimpleBundleKey() {
	}

	public SimpleBundleKey(BundleIdentifier bundleIdentifier, StorageViewKey storageViewKey) {
		this.bundleIdentifier = bundleIdentifier;
		this.storageViewKey = storageViewKey;
	}

	@Override
	public BundleIdentifier getBundleIdentifier() {
		return bundleIdentifier;
	}

	@Override
	public StorageViewKey getStorageViewKey() {
		return storageViewKey;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(bundleIdentifier);
		out.writeObject(storageViewKey);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		bundleIdentifier = (BundleIdentifier) in.readObject();
		storageViewKey = (StorageViewKey) in.readObject();
	}

	@Override
	public int hashCode() {
		return bundleIdentifier.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleBundleKey other = (SimpleBundleKey) obj;
		if (bundleIdentifier == null) {
			if (other.bundleIdentifier != null)
				return false;
		} else if (!bundleIdentifier.equals(other.bundleIdentifier))
			return false;
		if (storageViewKey == null) {
			if (other.storageViewKey != null)
				return false;
		} else if (!storageViewKey.equals(other.storageViewKey))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + bundleIdentifier + ":" + storageViewKey + "]";
	}

}
