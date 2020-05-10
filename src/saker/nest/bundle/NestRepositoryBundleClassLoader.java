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
package saker.nest.bundle;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.TransformingMap;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryImpl;
import saker.nest.bundle.lookup.BundleLookup;
import testing.saker.nest.TestFlag;

public final class NestRepositoryBundleClassLoader extends MultiDataClassLoader implements NestBundleClassLoader {

	public static final class DependentClassLoader<T extends ClassLoader> {
		public final T classLoader;
		public final boolean privateScope;

		public DependentClassLoader(T classLoader, boolean privateScope) {
			this.classLoader = classLoader;
			this.privateScope = privateScope;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classLoader == null) ? 0 : classLoader.hashCode());
			result = prime * result + (privateScope ? 1231 : 1237);
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
			DependentClassLoader<?> other = (DependentClassLoader<?>) obj;
			if (classLoader == null) {
				if (other.classLoader != null)
					return false;
			} else if (!classLoader.equals(other.classLoader))
				return false;
			if (privateScope != other.privateScope)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "DependentClassLoader[" + (classLoader != null ? "classLoader=" + classLoader + ", " : "")
					+ "privateScope=" + privateScope + "]";
		}

	}

	static {
		registerAsParallelCapable();
	}

	private final ConfiguredRepositoryStorage configuredStorage;
	private final BundleKey bundleKey;
	private final AbstractNestRepositoryBundle bundle;
	/**
	 * Unmodifiable map of dependency class loaders.
	 */
	private final Map<BundleKey, DependentClassLoader<? extends NestRepositoryBundleClassLoader>> dependencyClassLoaders;
	private final Map<SimpleExternalArchiveKey, DependentClassLoader<? extends NestRepositoryExternalArchiveClassLoader>> externalDependencyClassLoaders;
	private final BundleLookup relativeBundleLookup;

	private final ConcurrentSkipListMap<String, Class<?>> bundleLoadedClasses = new ConcurrentSkipListMap<>();

	private final LazySupplier<byte[]> hashWithClassPathDependencies = LazySupplier
			.of(this::computeHashWithClassPathDependencies);

	public NestRepositoryBundleClassLoader(ClassLoader parent, ConfiguredRepositoryStorage configuredStorage,
			BundleKey bundlekey, AbstractNestRepositoryBundle bundle,
			Map<BundleKey, ? extends DependentClassLoader<? extends NestRepositoryBundleClassLoader>> dependencyClassLoaders,
			BundleLookup relativeBundleLookup,
			Map<SimpleExternalArchiveKey, DependentClassLoader<? extends NestRepositoryExternalArchiveClassLoader>> externalDependencyClassLoaders) {
		super(parent, new NestBundleClassLoaderDataFinder(bundle));
		this.configuredStorage = configuredStorage;
		this.bundleKey = bundlekey;
		this.bundle = bundle;
		this.relativeBundleLookup = relativeBundleLookup;
		this.dependencyClassLoaders = ImmutableUtils.unmodifiableMap(dependencyClassLoaders);
		this.externalDependencyClassLoaders = ImmutableUtils.unmodifiableMap(externalDependencyClassLoaders);
	}

	public Map<SimpleExternalArchiveKey, DependentClassLoader<? extends NestRepositoryExternalArchiveClassLoader>> getExternalDependencyClassLoaders() {
		return externalDependencyClassLoaders;
	}

	@Override
	public BundleLookup getRelativeBundleLookup() {
		return relativeBundleLookup;
	}

	@Override
	public NestRepositoryImpl getRepository() {
		return configuredStorage.getRepository();
	}

	@Override
	public AbstractNestRepositoryBundle getBundle() {
		return bundle;
	}

	@Override
	public NestBundleStorageConfiguration getBundleStorageConfiguration() {
		return configuredStorage;
	}

	@Override
	public Map<? extends BundleKey, ? extends NestBundleClassLoader> getClassPathDependencies() {
		return ImmutableUtils.makeImmutableLinkedHashMap(
				new TransformingMap<BundleKey, DependentClassLoader<? extends NestRepositoryBundleClassLoader>, BundleKey, NestBundleClassLoader>(
						dependencyClassLoaders) {
					@Override
					protected Entry<BundleKey, NestBundleClassLoader> transformEntry(BundleKey key,
							DependentClassLoader<? extends NestRepositoryBundleClassLoader> value) {
						return ImmutableUtils.makeImmutableMapEntry(key, value.classLoader);
					}
				});
	}

	@Override
	public byte[] getBundleHashWithClassPathDependencies() {
		return getSharedBundleHashWithClassPathDependencies().clone();
	}

	public byte[] getSharedBundleHashWithClassPathDependencies() {
		return hashWithClassPathDependencies.get();
	}

	public Map<BundleKey, DependentClassLoader<? extends NestRepositoryBundleClassLoader>> getDependencyClassLoaders() {
		return dependencyClassLoaders;
	}

	@Override
	public BundleKey getBundleKey() {
		return bundleKey;
	}

	/**
	 * Must be locked on {@link #getClassLoadingLock(String)}.
	 * <p>
	 * We can't use {@link #findLoadedClass(String)} as that may return classes that weren't defined by this
	 * classloader.
	 */
	private Class<?> getAlreadyLoadedClassByThisBundle(String name) {
		return bundleLoadedClasses.get(name);
	}

	//doc: this method doesn't search any classpath bundles, only the current one
	public Class<?> loadClassFromBundle(String name) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = getAlreadyLoadedClassByThisBundle(name);
			if (c != null) {
				return c;
			}
			return loadDefineClassFromBundle(name);
		}
	}

	/**
	 * Must be locked on {@link #getClassLoadingLock(String)}.
	 */
	private Class<?> loadDefineClassFromBundle(String name) throws ClassNotFoundException {
		Class<?> result = super.findClass(name);
		Class<?> prev = bundleLoadedClasses.putIfAbsent(name, result);
		if (prev != null) {
			throw new AssertionError("Loaded class multiple times: " + name);
		}
		return result;
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		ClassNotFoundException e = null;
		Class<?> c;
		synchronized (getClassLoadingLock(name)) {
			c = getAlreadyLoadedClassByThisBundle(name);
			if (c == null) {
				try {
					c = Class.forName(name, false, getParent());
				} catch (ClassNotFoundException e2) {
					e = IOUtils.addExc(e, e2);
					try {
						c = loadDefineClassFromBundle(name);
					} catch (ClassNotFoundException e3) {
						e = IOUtils.addExc(e, e3);
					}
				}
			}
		}
		if (c == null) {
			Set<NestBundleClassLoader> triedcls = new HashSet<>();
			triedcls.add(this);
			c = findClassRecursively(triedcls, name, e, true);
			if (c == null) {
				triedcls.clear();
				triedcls.add(this);
				c = findExternalClassRecursively(triedcls, name, e, true);
			}
		}
		if (c != null) {
			if (resolve) {
				resolveClass(c);
			}
			return c;
		}
		throw e;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		throw new ClassNotFoundException("This should never be called. (" + name + ")");
	}

	@Override
	protected String findLibrary(String libname) {
		String libparentpath;
		String libfilenamepart;
		int lastdot = libname.lastIndexOf('.');
		if (lastdot >= 0) {
			//convert a name of some.package.SomeLibName to some/package/libSomeLibName.so (lib.so is mapped by the system based on OS)
			libfilenamepart = libname.substring(lastdot + 1);
			libparentpath = libname.substring(0, lastdot).replace('.', '/') + "/";
		} else {
			libparentpath = "";
			libfilenamepart = libname;
		}
		String osarch = configuredStorage.getDependencyConstraintConfiguration().getNativeArchitecture();
		if (osarch != null) {
			String arcitecturedlibfilename = libfilenamepart + "." + osarch;
			String fullpath = libparentpath + System.mapLibraryName(arcitecturedlibfilename);
			if (bundle.hasEntry(fullpath)) {
				return exportLib(fullpath);
			}
		}
		String fullpath = libparentpath + System.mapLibraryName(libfilenamepart);
		if (bundle.hasEntry(fullpath)) {
			return exportLib(fullpath);
		}
		return null;
	}

	private Class<?> loadClassRecursively(Set<NestBundleClassLoader> triedcls, String name, ClassNotFoundException e) {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = getAlreadyLoadedClassByThisBundle(name);
			if (c != null) {
				return c;
			}
			try {
				return Class.forName(name, false, getParent());
			} catch (ClassNotFoundException e2) {
				e.addSuppressed(e2);
			}

			try {
				return loadDefineClassFromBundle(name);
			} catch (ClassNotFoundException e2) {
				e.addSuppressed(e2);
			}
		}
		return findClassRecursively(triedcls, name, e, false);
	}

	private Class<?> findClassRecursively(Set<NestBundleClassLoader> triedcls, String name, ClassNotFoundException e,
			boolean allowprivate) {
		if (dependencyClassLoaders.isEmpty()) {
			return null;
		}
		for (DependentClassLoader<? extends NestRepositoryBundleClassLoader> depcl : dependencyClassLoaders.values()) {
			if (depcl.privateScope && !allowprivate) {
				continue;
			}
			NestRepositoryBundleClassLoader cl = depcl.classLoader;
			if (!triedcls.add(cl)) {
				continue;
			}
			Class<?> found = cl.loadClassRecursively(triedcls, name, e);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private Class<?> findExternalClassRecursively(Set<NestBundleClassLoader> triedcls, String name,
			ClassNotFoundException e, boolean allowprivate) {
		if (!externalDependencyClassLoaders.isEmpty()) {
			for (DependentClassLoader<? extends NestRepositoryExternalArchiveClassLoader> depcl : externalDependencyClassLoaders
					.values()) {
				if (depcl.privateScope && !allowprivate) {
					continue;
				}
				try {
					return depcl.classLoader.loadClassFromArchive(name);
				} catch (ClassNotFoundException e1) {
					e.addSuppressed(e1);
				}
			}
		}
		if (!dependencyClassLoaders.isEmpty()) {
			for (DependentClassLoader<? extends NestRepositoryBundleClassLoader> depcl : dependencyClassLoaders
					.values()) {
				if (depcl.privateScope && !allowprivate) {
					continue;
				}
				NestRepositoryBundleClassLoader cl = depcl.classLoader;
				if (!triedcls.add(cl)) {
					continue;
				}
				Class<?> found = cl.findExternalClassRecursively(triedcls, name, e, false);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private String exportLib(String libentrynamename) {
		Path libdirpath = bundle.getStorage().getBundleLibStoragePath(bundle);
		if (libdirpath == null) {
			return null;
		}
		try {
			ByteArrayRegion bytes = bundle.getEntryBytes(libentrynamename);
			MessageDigest digest = FileUtils.getDefaultFileHasher();
			digest.update(bytes.getArray(), bytes.getOffset(), bytes.getLength());
			byte[] entryhash = digest.digest();
			String hashstr = StringUtils.toHexString(entryhash);
			Path libpath = libdirpath.resolve(System.mapLibraryName(hashstr));
			String originallibpathstr = libpath.toString();
			if (originallibpathstr.length() > 240) {
				//Windows cannot load libraries which have a longer than 260 character path length
				//use 240 for comparison with a little threshold

				//we fall back to the temp directory
				String tmpdir = System.getProperty("java.io.tmpdir");
				if (tmpdir != null) {
					//the new lib path is calculated based on the hash of the original path and the entry
					Path tmpdirpath = Paths.get(tmpdir);
					MessageDigest tmplibdigest = FileUtils.getDefaultFileHasher();
					tmplibdigest.update(entryhash);
					tmplibdigest.update(originallibpathstr.getBytes(StandardCharsets.UTF_8));
					libpath = tmpdirpath.resolve(System.mapLibraryName(StringUtils.toHexString(tmplibdigest.digest())));
				}
				//else if the temp dir is null, we still export the library to the original path, and hope for the best for loading
				//    failing is acceptable in that case
			}
			if (TestFlag.ENABLED) {
				libpath = TestFlag.metric().overrideNativeLibraryPath(this, libpath);
				libdirpath = libpath.getParent();
			}
			//lock on a vm-common string for the library path to avoid concurrent loading
			//XXX maybe we should do file-system level locking to deal with concurrent processes as well
			synchronized (("nest-lib-load-lock:" + libpath).intern()) {
				Files.createDirectories(libdirpath);
				if (getFileSizeOrNegative(libpath) != bytes.getLength()) {
					Path temppath = libpath.resolveSibling(UUID.randomUUID() + ".templib");
					try {
						try (OutputStream os = Files.newOutputStream(temppath, StandardOpenOption.CREATE_NEW,
								StandardOpenOption.WRITE)) {
							bytes.writeTo(os);
						}
						Files.move(temppath, libpath, StandardCopyOption.REPLACE_EXISTING);
					} catch (Throwable e) {
						try {
							Files.deleteIfExists(temppath);
						} catch (IOException ignored) {
							e.addSuppressed(ignored);
						}
						throw e;
					}
				}
			}
			return libpath.toString();
		} catch (IOException e) {
			//the entry failed to read, of the lib failed to write
			System.err.println("Failed to export native library: " + libentrynamename + " : " + e);
		}
		return null;
	}

	private static long getFileSizeOrNegative(Path libpath) {
		try {
			return Files.size(libpath);
		} catch (IOException e) {
		}
		return -1;
	}

	private byte[] computeHashWithClassPathDependencies() {
		Map<BundleKey, byte[]> hashes = collectBundleHashes();
		MessageDigest hasher = FileUtils.getDefaultFileHasher();
		for (byte[] hash : hashes.values()) {
			hasher.update(hash);
		}
		for (DependentClassLoader<? extends NestRepositoryExternalArchiveClassLoader> extdcl : externalDependencyClassLoaders
				.values()) {
			hasher.update(extdcl.classLoader.getArchive().getSharedHash());
		}
		return hasher.digest();
	}

	private Map<BundleKey, byte[]> collectBundleHashes() {
		Map<BundleKey, byte[]> result = new LinkedHashMap<>();
		collectBundleHashesImpl(this, result);
		return result;
	}

	private static void collectBundleHashesImpl(NestRepositoryBundleClassLoader cl, Map<BundleKey, byte[]> result) {
		AbstractNestRepositoryBundle bundle = cl.getBundle();
		if (result.put(cl.bundleKey, bundle.getSharedHash()) != null) {
			//already added
			return;
		}
		Map<BundleKey, DependentClassLoader<? extends NestRepositoryBundleClassLoader>> deps = cl.dependencyClassLoaders;
		if (!deps.isEmpty()) {
			for (DependentClassLoader<? extends NestRepositoryBundleClassLoader> depcl : deps.values()) {
				collectBundleHashesImpl(depcl.classLoader, result);
			}
		}
	}
}
