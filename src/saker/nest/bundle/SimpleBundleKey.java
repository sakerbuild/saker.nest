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
