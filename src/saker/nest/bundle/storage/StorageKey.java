package saker.nest.bundle.storage;

import saker.apiextract.api.PublicApi;

/**
 * Key identifier interface for identifying a given bundle storage.
 * <p>
 * Storage keys are used to differentiate various {@link BundleStorage} objects. If two storage keys equal, then they
 * provide the storage to the same location. However, storage key equality doesn't mean that they provide access to the
 * same bundles.
 * <p>
 * Instances of this interface can be compared by equality and serialized.
 * <p>
 * They can be retrieved using {@link BundleStorage#getStorageKey()}.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleStorage
 * @see BundleStorageView
 * @see StorageViewKey
 */
@PublicApi
public interface StorageKey {
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
