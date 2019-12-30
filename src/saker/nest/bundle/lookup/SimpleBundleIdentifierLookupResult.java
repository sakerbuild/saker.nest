package saker.nest.bundle.lookup;

import java.util.Map;
import java.util.Set;

import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.storage.BundleStorageView;

public class SimpleBundleIdentifierLookupResult implements BundleIdentifierLookupResult {
	protected final Map<String, ? extends Set<? extends BundleIdentifier>> bundles;
	protected final BundleLookup relativeLookup;
	protected final BundleStorageView storageView;

	public SimpleBundleIdentifierLookupResult(Map<String, ? extends Set<? extends BundleIdentifier>> bundles,
			BundleLookup relativeLookup, BundleStorageView storageView) {
		this.bundles = bundles;
		this.relativeLookup = relativeLookup;
		this.storageView = storageView;
	}

	@Override
	public Map<String, ? extends Set<? extends BundleIdentifier>> getBundles() {
		return bundles;
	}

	@Override
	public BundleLookup getRelativeLookup() {
		return relativeLookup;
	}

	@Override
	public BundleStorageView getStorageView() {
		return storageView;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bundles == null) ? 0 : bundles.hashCode());
		result = prime * result + ((relativeLookup == null) ? 0 : relativeLookup.hashCode());
		result = prime * result + ((storageView == null) ? 0 : storageView.hashCode());
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
		SimpleBundleIdentifierLookupResult other = (SimpleBundleIdentifierLookupResult) obj;
		if (bundles == null) {
			if (other.bundles != null)
				return false;
		} else if (!bundles.equals(other.bundles))
			return false;
		if (relativeLookup == null) {
			if (other.relativeLookup != null)
				return false;
		} else if (!relativeLookup.equals(other.relativeLookup))
			return false;
		if (storageView == null) {
			if (other.storageView != null)
				return false;
		} else if (!storageView.equals(other.storageView))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[bundles=" + bundles + ", relativeLookup=" + relativeLookup
				+ ", storageView=" + storageView + "]";
	}

}
