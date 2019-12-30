package saker.nest.bundle.lookup;

import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.storage.BundleStorageView;

/**
 * Represents the result of a bundle version lookup request.
 * <p>
 * The interface provides access to the {@linkplain #getBundles() found versions} of a bundle, the
 * {@linkplain #getStorageView() storage view} that contains them, and the {@linkplain #getRelativeLookup() relative
 * lookup} that can be used to look up further bundles in relation to them.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleLookup#lookupBundleVersions(BundleIdentifier)
 */
@PublicApi
public interface BundleVersionLookupResult {
	/**
	 * Gets the found bundle identifiers that match the lookup request bundle identifier.
	 * 
	 * @return A set of bundles. The iteration order of the returned collection is descending by the
	 *             {@linkplain BundleIdentifier#compareVersionNumbers(String, String) bundle version}.
	 */
	public Set<? extends BundleIdentifier> getBundles();

	/**
	 * Gets the relative lookup in relation to the found bundles.
	 * <p>
	 * The lookup can be used to look up other bundles and information that is accessible in relation to the bundles.
	 * 
	 * @return The relative bundle lookup.
	 */
	public BundleLookup getRelativeLookup();

	/**
	 * Gets the storage view that contains the found bundles.
	 * 
	 * @return The storage view.
	 */
	public BundleStorageView getStorageView();
}
