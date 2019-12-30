package saker.nest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.SubDirectoryClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.nest.bundle.storage.AbstractBundleStorage;
import saker.nest.bundle.storage.AbstractStorageKey;
import saker.nest.meta.Versions;

public final class NestRepositoryImpl implements SakerRepository, NestRepository {
	static final char CHAR_CL_IDENITIFER_SEPARATOR = '\n';

	private volatile boolean closed = false;

	private final RepositoryEnvironment repositoryEnvironment;
	private final Path classPathPath;
	private final byte[] repositoryHash;

	private final Map<AbstractStorageKey, Object> storageLoadLocks = Collections.synchronizedMap(new WeakHashMap<>());
	private final Map<AbstractStorageKey, AbstractBundleStorage> loadedStorages = new ConcurrentHashMap<>();

	public NestRepositoryImpl(RepositoryEnvironment environment) {
		this.repositoryEnvironment = environment;
		this.classPathPath = environment.getRepositoryClassPath();

		byte[] repohash;
		try {
			repohash = FileUtils.hashFiles(classPathPath);
		} catch (IOException e) {
			System.err.println("Failed to hash class path files for Nest repository at " + classPathPath + ": " + e);
			//failed to hash the repository files, use a random hash for the repository to ensure proper operation
			repohash = new byte[Long.BYTES * 2];
			UUID uuid = UUID.randomUUID();
			SerialUtils.writeLongToBuffer(uuid.getMostSignificantBits(), repohash, 0);
			SerialUtils.writeLongToBuffer(uuid.getLeastSignificantBits(), repohash, Long.BYTES);
		}
		this.repositoryHash = repohash;
	}

	private static class ActionClassLoaderHolder {
		static final ClassLoader INTERNAL_ACTION_CLASSLOADER;
		static final Method invokeMethod;
		static {
			SubDirectoryClassLoaderDataFinder subdirfinder = SubDirectoryClassLoaderDataFinder.create("internal/action",
					ActionClassLoaderHolder.class.getClassLoader());
			INTERNAL_ACTION_CLASSLOADER = new MultiDataClassLoader(ActionClassLoaderHolder.class.getClassLoader(),
					subdirfinder);
			try {
				Class<?> executeactionclass = Class.forName("saker.nest.action.main.ExecuteAction", false,
						INTERNAL_ACTION_CLASSLOADER);
				invokeMethod = executeactionclass.getMethod("invoke", NestRepositoryImpl.class, String[].class);
			} catch (Exception e) {
				throw new AssertionError("Failed to load repository action runtime.", e);
			}
		}

	}

	@Override
	public void executeAction(String... arguments) throws Exception {
		try {
			ActionClassLoaderHolder.invokeMethod.invoke(null, this, arguments);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception || cause instanceof Error) {
				throw ObjectUtils.sneakyThrow(cause);
			}
			throw e;
		}
	}

	@Override
	public RepositoryEnvironment getRepositoryEnvironment() {
		return repositoryEnvironment;
	}

	public Path getRepositoryStorageDirectory() {
		return repositoryEnvironment.getRepositoryStorageDirectory();
	}

	public byte[] getRepositoryHash() {
		return repositoryHash;
	}

	/**
	 * Returns the classpath where the repository was loaded from.
	 * <p>
	 * The classes for this repository are loaded from this file location. It will be a directory or a JAR file.
	 * 
	 * @return The classpath.
	 */
	public Path getClassPathPath() {
		return classPathPath;
	}

	@Override
	public BuildRepository createBuildRepository(RepositoryBuildEnvironment environment) {
		return NestBuildRepositoryImpl.create(this, environment);
	}

	@Override
	public void close() throws IOException {
		closed = true;
		IOException exc = null;

		//synchronize on a synchronized map
		synchronized (storageLoadLocks) {
			for (Entry<AbstractStorageKey, Object> lockentry : storageLoadLocks.entrySet()) {
				synchronized (lockentry.getValue()) {
					exc = IOUtils.closeExc(exc, loadedStorages.remove(lockentry.getKey()));
				}
			}
			storageLoadLocks.clear();
			//close all remaining and clear just in case
			exc = IOUtils.closeExc(exc, loadedStorages.values());
		}
		loadedStorages.clear();

		IOUtils.throwExc(exc);
	}

	protected AbstractBundleStorage loadStorage(AbstractStorageKey key) {
		synchronized (storageLoadLocks.computeIfAbsent(key, Functionals.objectComputer())) {
			if (closed) {
				throw new IllegalStateException("repository closed.");
			}
			return loadedStorages.computeIfAbsent(key, k -> k.getStorage(this));
		}
	}

	String getCoreClassLoaderResolverId(String buildrepositoryid) {
		return buildrepositoryid + "/" + NestRepositoryFactory.IDENTIFIER + ".repository."
				+ Versions.VERSION_STRING_FULL + "/" + StringUtils.toHexString(repositoryHash);
	}

}
