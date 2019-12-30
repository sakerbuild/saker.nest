package saker.nest.bundle.storage;

import saker.apiextract.api.PublicApi;

/**
 * An unique key identifier for {@linkplain BundleStorageView bundle storage views}.
 * <p>
 * Instances of this interface can be compared by equality and serialized.
 * <p>
 * Storage view keys uniquely identify the backing storage and the configuration used to modify the view behaviour. They
 * can be compared for equality, however, if two storage view keys equal, then they still may provide access to
 * different bundles. The equality of storage view keys only ensure that the given views were configured semantically
 * the same way.
 * <p>
 * A bundle addition to a backing storage may still result in equality of storage view keys.
 * <p>
 * Instances can be retrieved using {@link BundleStorageView#getStorageViewKey()}.
 * <p>
 * This interface is not to be implemented by clients.
 */
@PublicApi
public interface StorageViewKey {
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
