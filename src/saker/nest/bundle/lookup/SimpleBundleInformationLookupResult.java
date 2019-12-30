package saker.nest.bundle.lookup;

import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.storage.BundleStorageView;

public class SimpleBundleInformationLookupResult implements BundleInformationLookupResult {
	protected final BundleInformation bundleInformation;
	protected final AbstractBundleLookup lookupConfiguration;
	protected final BundleStorageView storageView;

	public SimpleBundleInformationLookupResult(BundleInformation bundleInformation,
			AbstractBundleLookup lookupConfiguration, BundleStorageView storageView) {
		this.bundleInformation = bundleInformation;
		this.lookupConfiguration = lookupConfiguration;
		this.storageView = storageView;
	}

	@Override
	public BundleInformation getBundleInformation() {
		return bundleInformation;
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
		result = prime * result + ((bundleInformation == null) ? 0 : bundleInformation.hashCode());
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
		SimpleBundleInformationLookupResult other = (SimpleBundleInformationLookupResult) obj;
		if (bundleInformation == null) {
			if (other.bundleInformation != null)
				return false;
		} else if (!bundleInformation.equals(other.bundleInformation))
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
		sb.append(bundleInformation.getBundleIdentifier());
		sb.append(", storage=");
		sb.append(storageView.getStorageViewKey());
		sb.append("]");
		return sb.toString();
	}

}