package saker.nest.bundle.storage;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Map;

import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.nest.bundle.NestRepositoryBundle;

public abstract class AbstractBundleStorage implements BundleStorage, Closeable {
	public abstract AbstractBundleStorageView newStorageView(Map<String, String> userparameters,
			ExecutionPathConfiguration pathconfig);

	public abstract Path getBundleLibStoragePath(NestRepositoryBundle bundle)
			throws NullPointerException, IllegalArgumentException;

	public abstract Path getBundleStoragePath(NestRepositoryBundle bundle)
			throws NullPointerException, IllegalArgumentException;

	@Override
	public abstract AbstractStorageKey getStorageKey();

	public abstract String getType();

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getStorageKey() + "]";
	}
}
