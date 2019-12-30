package saker.nest.bundle.storage;

import saker.apiextract.api.PublicApi;

/**
 * Interface providing access to a bundle storage.
 * <p>
 * The {@link BundleStorage} interface generally manages the structure and synchronization of a bundle storage location.
 * <p>
 * Currently this interface serves no purpose for client usage, however, it may be extended in the future to provide
 * access to various bundle storage facilities.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleStorageView
 */
@PublicApi
public interface BundleStorage {
	/**
	 * Gets the storage key for this bundle storage.
	 * 
	 * @return The storage key.
	 */
	public StorageKey getStorageKey();
}
