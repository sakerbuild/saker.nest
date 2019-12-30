package saker.nest.bundle;

import java.io.Closeable;

import saker.nest.bundle.storage.AbstractBundleStorage;

public abstract class AbstractNestRepositoryBundle implements NestRepositoryBundle, Closeable {
	@Override
	public abstract AbstractBundleStorage getStorage();

	public abstract byte[] getSharedHash();

}
