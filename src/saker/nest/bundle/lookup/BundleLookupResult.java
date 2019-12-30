package saker.nest.bundle.lookup;

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.storage.BundleStorageView;

/**
 * Represents the result of a bundle lookup request.
 * <p>
 * The interface provides access to the {@linkplain #getBundle() loaded bundle}, the {@linkplain #getStorageView()
 * storage view} that contains it, and the {@linkplain #getRelativeLookup() relative lookup} that can be used to look up
 * further bundles in relation to this.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleLookup#lookupBundle(BundleIdentifier)
 */
@PublicApi
public interface BundleLookupResult {
	/**
	 * Gets the loaded bundle that is the result of the lookup operation.
	 * 
	 * @return The loaded bundle.
	 */
	public NestRepositoryBundle getBundle();

	/**
	 * Gets the relative lookup in relation to the found bundle.
	 * <p>
	 * The lookup can be used to look up other bundles and information that is accessible in relation to the loaded
	 * bundle.
	 * 
	 * @return The relative bundle lookup.
	 */
	public BundleLookup getRelativeLookup();

	/**
	 * Gets the storage view that contains the loaded bundle.
	 * 
	 * @return The storage view.
	 */
	public BundleStorageView getStorageView();
}
