/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.nest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

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
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.nest.bundle.AbstractExternalArchive;
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

	final ConcurrentSkipListMap<Path, Object> externalArchiveLoadLocks = new ConcurrentSkipListMap<>();
	final ConcurrentSkipListMap<Path, ExternalArchiveReference> externalArchives = new ConcurrentSkipListMap<>();

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
		for (Entry<Path, Object> lockentry : externalArchiveLoadLocks.entrySet()) {
			synchronized (lockentry.getValue()) {
				ExternalArchiveReference archiveref = externalArchives.remove(lockentry.getKey());
				if (archiveref != null) {
					exc = IOUtils.closeExc(exc, archiveref.archive);
				}
			}
		}

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

	public static class ExternalArchiveReference {
		public final AbstractExternalArchive archive;
		public final Hashes hashes;

		private final ConcurrentSkipListMap<String, Hashes> entryHashes = new ConcurrentSkipListMap<>();

		public ExternalArchiveReference(AbstractExternalArchive archive, Hashes hashes) {
			this.archive = archive;
			this.hashes = hashes;
		}

		public Hashes getEntryHash(String name) throws IOException {
			Hashes hashes = entryHashes.get(name);
			if (hashes != null) {
				return hashes;
			}
			MessageDigest sha1;
			MessageDigest sha256;
			MessageDigest md5;
			try {
				md5 = MessageDigest.getInstance("MD5");
				sha256 = MessageDigest.getInstance("SHA-256");
				sha1 = MessageDigest.getInstance("SHA-1");
			} catch (NoSuchAlgorithmException e) {
				throw new IOException("Failed to retrieve hashing algorithm.", e);
			}
			try (InputStream is = archive.openEntry(name)) {
				byte[] buffer = new byte[StreamUtils.DEFAULT_BUFFER_SIZE];
				for (int read; (read = is.read(buffer)) > 0;) {
					sha256.update(buffer, 0, read);
					sha1.update(buffer, 0, read);
					md5.update(buffer, 0, read);
				}
			}
			hashes = new Hashes(StringUtils.toHexString(sha256.digest()), StringUtils.toHexString(sha1.digest()),
					StringUtils.toHexString(md5.digest()));
			Hashes prev = entryHashes.putIfAbsent(name, hashes);
			if (prev != null) {
				return prev;
			}
			return hashes;
		}

		public void putEntryHash(String name, Hashes hashes) {
			entryHashes.putIfAbsent(name, hashes);
		}

	}

	public static class Hashes {
		public final String sha256;
		public final String sha1;
		public final String md5;

		public Hashes(String sha256, String sha1, String md5) {
			this.sha256 = sha256;
			this.sha1 = sha1;
			this.md5 = md5;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((md5 == null) ? 0 : md5.hashCode());
			result = prime * result + ((sha1 == null) ? 0 : sha1.hashCode());
			result = prime * result + ((sha256 == null) ? 0 : sha256.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Hashes other = (Hashes) obj;
			if (md5 == null) {
				if (other.md5 != null)
					return false;
			} else if (!md5.equals(other.md5))
				return false;
			if (sha1 == null) {
				if (other.sha1 != null)
					return false;
			} else if (!sha1.equals(other.sha1))
				return false;
			if (sha256 == null) {
				if (other.sha256 != null)
					return false;
			} else if (!sha256.equals(other.sha256))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Hashes[" + (sha256 != null ? "sha256=" + sha256 + ", " : "")
					+ (sha1 != null ? "sha1=" + sha1 + ", " : "") + (md5 != null ? "md5=" + md5 : "") + "]";
		}

	}

}
