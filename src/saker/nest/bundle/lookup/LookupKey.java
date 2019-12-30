package saker.nest.bundle.lookup;

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.NestBundleStorageConfiguration;

/**
 * An unique key identifier for {@linkplain BundleLookup bundle lookups}.
 * <p>
 * Instances of this interface can be compared by equality and serialized.
 * <p>
 * They can be retrieved using {@link BundleLookup#getLookupKey()}.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleStorageConfiguration#getBundleLookupForKey(LookupKey)
 */
@PublicApi
public interface LookupKey {
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
