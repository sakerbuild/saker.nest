package saker.nest.bundle.storage;

import saker.nest.NestRepositoryImpl;

public abstract class AbstractStorageKey implements StorageKey {
	public abstract AbstractBundleStorage getStorage(NestRepositoryImpl repository);
}
