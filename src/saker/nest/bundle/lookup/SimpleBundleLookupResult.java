package saker.nest.bundle.lookup;

import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.storage.BundleStorageView;

public class SimpleBundleLookupResult implements BundleLookupResult {
	protected final NestRepositoryBundle bundle;
	protected final AbstractBundleLookup lookupConfiguration;
	protected final BundleStorageView storageView;

	public SimpleBundleLookupResult(NestRepositoryBundle bundle, AbstractBundleLookup lookupConfiguration,
			BundleStorageView storageView) {
		this.bundle = bundle;
		this.lookupConfiguration = lookupConfiguration;
		this.storageView = storageView;
	}

	@Override
	public NestRepositoryBundle getBundle() {
		return bundle;
	}

	@Override
	public AbstractBundleLookup getRelativeLookup() {
		return lookupConfiguration;
	}

	@Override
	public BundleStorageView getStorageView() {
		return storageView;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bundle == null) ? 0 : bundle.hashCode());
		result = prime * result + ((lookupConfiguration == null) ? 0 : lookupConfiguration.hashCode());
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
		SimpleBundleLookupResult other = (SimpleBundleLookupResult) obj;
		if (bundle == null) {
			if (other.bundle != null)
				return false;
		} else if (!bundle.equals(other.bundle))
			return false;
		if (lookupConfiguration == null) {
			if (other.lookupConfiguration != null)
				return false;
		} else if (!lookupConfiguration.equals(other.lookupConfiguration))
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
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append("[bundle=");
		sb.append(bundle.getBundleIdentifier());
		sb.append(", storage=");
		sb.append(storageView.getStorageViewKey());
		sb.append("]");
		return sb.toString();
	}

}