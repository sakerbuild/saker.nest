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
package saker.nest.bundle.lookup;

import java.util.Set;

import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.storage.BundleStorageView;

public class SimpleBundleVersionLookupResult implements BundleVersionLookupResult {
	protected final Set<? extends BundleIdentifier> bundles;
	protected final BundleStorageView storage;
	protected final BundleLookup lookupConfiguration;

	public SimpleBundleVersionLookupResult(Set<? extends BundleIdentifier> bundles, BundleStorageView storage,
			BundleLookup relativelookup) {
		this.bundles = bundles;
		this.storage = storage;
		this.lookupConfiguration = relativelookup;
	}

	@Override
	public Set<? extends BundleIdentifier> getBundles() {
		return bundles;
	}

	@Override
	public BundleLookup getRelativeLookup() {
		return lookupConfiguration;
	}

	@Override
	public BundleStorageView getStorageView() {
		return storage;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bundles == null) ? 0 : bundles.hashCode());
		result = prime * result + ((lookupConfiguration == null) ? 0 : lookupConfiguration.hashCode());
		result = prime * result + ((storage == null) ? 0 : storage.hashCode());
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
		SimpleBundleVersionLookupResult other = (SimpleBundleVersionLookupResult) obj;
		if (bundles == null) {
			if (other.bundles != null)
				return false;
		} else if (!bundles.equals(other.bundles))
			return false;
		if (lookupConfiguration == null) {
			if (other.lookupConfiguration != null)
				return false;
		} else if (!lookupConfiguration.equals(other.lookupConfiguration))
			return false;
		if (storage == null) {
			if (other.storage != null)
				return false;
		} else if (!storage.equals(other.storage))
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("[bundles=");
		sb.append(bundles);
		sb.append(", storage=");
		sb.append(storage.getStorageViewKey());
		sb.append("]");
		return sb.toString();
	}

}