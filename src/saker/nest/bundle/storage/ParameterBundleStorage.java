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
package saker.nest.bundle.storage;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import saker.build.file.content.ContentDescriptor;
import saker.build.file.content.FileAttributesContentDescriptor;
import saker.build.file.content.HashContentDescriptor;
import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimplePathKey;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.path.WildcardPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.FileHashResult;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryImpl;
import saker.nest.bundle.AbstractNestRepositoryBundle;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleUtils;
import saker.nest.bundle.ExternalArchive;
import saker.nest.bundle.ExternalArchiveKey;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.JarNestRepositoryBundleImpl;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.exc.BundleLoadingFailedException;
import saker.nest.exc.BundleStorageInitializationException;
import saker.nest.exc.ExternalArchiveLoadingFailedException;
import saker.nest.exc.InvalidNestBundleException;
import testing.saker.nest.TestFlag;

public class ParameterBundleStorage extends AbstractBundleStorage {
	private static final char BUNDLES_PARAMETER_SEPARATOR = ';';

	public static class ParameterStorageKey extends AbstractStorageKey implements Externalizable {
		private static final long serialVersionUID = 1L;

		protected SakerPath storageDirectory;

		/**
		 * For {@link Externalizable}.
		 */
		public ParameterStorageKey() {
		}

		private ParameterStorageKey(Path storageDirectory) {
			this.storageDirectory = SakerPath.valueOf(storageDirectory);
		}

		public static AbstractStorageKey create(NestRepositoryImpl repository,
				@SuppressWarnings("unused") Map<String, String> userparams) {
			//user param unused warning is suppressed, the parameter is present to keep consistency with the other kind of storage key
			//    create methods
			return new ParameterStorageKey(repository.getRepositoryStorageDirectory()
					.resolve(ParameterBundleStorageView.DEFAULT_STORAGE_NAME));
		}

		@Override
		public AbstractBundleStorage getStorage(NestRepositoryImpl repository) {
			return new ParameterBundleStorage(this, repository);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(storageDirectory);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			storageDirectory = (SakerPath) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((storageDirectory == null) ? 0 : storageDirectory.hashCode());
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
			ParameterStorageKey other = (ParameterStorageKey) obj;
			if (storageDirectory == null) {
				if (other.storageDirectory != null)
					return false;
			} else if (!storageDirectory.equals(other.storageDirectory))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + storageDirectory + "]";
		}
	}

	private final ParameterStorageKey storageKey;
	private final Path storageDirectory;
	private final Path bundlesDir;

	private final ConcurrentHashMap<Path, Object> bundleLoadLocks = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Path, JarNestRepositoryBundleImpl> loadedBundles = new ConcurrentHashMap<>();
	private final NestRepositoryImpl repository;

	public ParameterBundleStorage(ParameterStorageKey storageKey, NestRepositoryImpl repository) {
		this.storageKey = storageKey;
		this.repository = repository;
		this.storageDirectory = LocalFileProvider.toRealPath(storageKey.storageDirectory);
		this.bundlesDir = storageDirectory.resolve("bundles");
	}

	@Override
	public String getType() {
		return ConfiguredRepositoryStorage.STORAGE_TYPE_PARAMETER;
	}

	@Override
	public AbstractStorageKey getStorageKey() {
		return storageKey;
	}

	@Override
	public Path getBundleStoragePath(NestRepositoryBundle bundle)
			throws NullPointerException, IllegalArgumentException {
		BundleIdentifier bundleid = bundle.getBundleIdentifier();
		return storageDirectory.resolve("storage").resolve(bundleid.toString())
				.resolve(StringUtils.toHexString(bundle.getHash()));
	}

	@Override
	public Path getBundleLibStoragePath(NestRepositoryBundle bundle)
			throws NullPointerException, IllegalArgumentException {
		BundleIdentifier bundleid = bundle.getBundleIdentifier();
		return storageDirectory.resolve("lib_storage").resolve(bundleid.toString())
				.resolve(StringUtils.toHexString(bundle.getHash()));
	}

	@Override
	public void close() throws IOException {
		IOException exc = null;
		exc = IOUtils.closeExc(loadedBundles.values());
		IOUtils.throwExc(exc);
	}

	@Override
	public AbstractBundleStorageView newStorageView(Map<String, String> userparameters,
			ExecutionPathConfiguration pathconfig) {
		return new ParameterBundleStorageViewImpl(userparameters, pathconfig);
	}

	private JarNestRepositoryBundleImpl getLoadBundle(Path jp)
			throws IOException, InvalidNestBundleException, BundleLoadingFailedException {
		JarNestRepositoryBundleImpl got = loadedBundles.get(jp);
		if (got != null) {
			return got;
		}
		synchronized (bundleLoadLocks.computeIfAbsent(jp, Functionals.objectComputer())) {
			got = loadedBundles.get(jp);
			if (got != null) {
				return got;
			}
			got = JarNestRepositoryBundleImpl.create(this, jp);
			//no need to check for bundle identifier mismatch, as we don't have a specific expectation about it.
			loadedBundles.put(jp, got);
			return got;
		}
	}

	private static BasicFileAttributes readAttributesOrNull(Path copyjarpath) {
		try {
			return Files.readAttributes(copyjarpath, BasicFileAttributes.class);
		} catch (IOException e) {
			return null;
		}
	}

	private static class LoadedViewBundleInfo {
		final JarNestRepositoryBundleImpl loaded;
		final SimplePathKey originalJarLocation;
		final FileEntry originalBundleAttributes;

		public LoadedViewBundleInfo(JarNestRepositoryBundleImpl loaded, SimplePathKey originalJarLocation,
				FileEntry sourcebundleattrs) {
			this.loaded = loaded;
			this.originalJarLocation = originalJarLocation;
			this.originalBundleAttributes = sourcebundleattrs;
		}
	}

	private static final class ParameterStorageViewKeyImpl implements StorageViewKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private NavigableMap<BundleIdentifier, ContentDescriptor> bundleContents;

		/**
		 * For {@link Externalizable}.
		 */
		public ParameterStorageViewKeyImpl() {
		}

		public ParameterStorageViewKeyImpl(NavigableMap<BundleIdentifier, ContentDescriptor> bundleContents) {
			this.bundleContents = bundleContents;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			SerialUtils.writeExternalMap(out, bundleContents);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			bundleContents = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((bundleContents == null) ? 0 : bundleContents.hashCode());
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
			ParameterStorageViewKeyImpl other = (ParameterStorageViewKeyImpl) obj;
			if (bundleContents == null) {
				if (other.bundleContents != null)
					return false;
			} else if (!bundleContents.equals(other.bundleContents))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ParameterStorageViewKeyImpl[" + bundleContents + "]";
		}
	}

	private final class ParameterBundleStorageViewImpl extends AbstractBundleStorageView
			implements ParameterBundleStorageView {
		private StorageViewKey storageViewKey;

		private final NavigableMap<BundleIdentifier, LoadedViewBundleInfo> bundles = new TreeMap<>();
		private final Map<SimplePathKey, LoadedViewBundleInfo> pathKeyBundles = new HashMap<>();
		private final NavigableMap<String, String> taskNamePackageNames = new TreeMap<>();
		private final NavigableMap<String, NavigableMap<BundleIdentifier, LoadedViewBundleInfo>> taskNameBundles = new TreeMap<>();

		private final Object detectChangeLock = new Object();

		private Map<String, String> userParameters;

		public ParameterBundleStorageViewImpl(Map<String, String> userparameters,
				ExecutionPathConfiguration pathconfig) {
			this.userParameters = userparameters;
			Map<ProviderHolderPathKey, BasicFileAttributes> paramjarpaths = getParameterJarPaths(pathconfig);

			NavigableMap<BundleIdentifier, ContentDescriptor> bundlehashes = new TreeMap<>();
			if (!paramjarpaths.isEmpty()) {
				try {
					Files.createDirectories(bundlesDir);
				} catch (IOException e) {
					throw new BundleStorageInitializationException("Failed to create storage directory: " + bundlesDir,
							e);
				}
				for (ProviderHolderPathKey pathkey : paramjarpaths.keySet()) {
					LoadedViewBundleInfo loadinfo = addBundle(pathkey);
					bundlehashes.put(loadinfo.loaded.getBundleIdentifier(),
							HashContentDescriptor.createWithHash(loadinfo.loaded.getHash()));
				}
			}
			this.storageViewKey = new ParameterStorageViewKeyImpl(bundlehashes);
		}

		private Map<ProviderHolderPathKey, BasicFileAttributes> getParameterJarPaths(
				ExecutionPathConfiguration pathconfig) {
			String additionalbundlesparam = this.userParameters.get(PARAMETER_NEST_REPOSITORY_BUNDLES);
			if (ObjectUtils.isNullOrEmpty(additionalbundlesparam)) {
				return Collections.emptyMap();
			}
			Iterator<? extends CharSequence> it = StringUtils.splitCharSequenceIterator(additionalbundlesparam,
					BUNDLES_PARAMETER_SEPARATOR);
			Map<ProviderHolderPathKey, BasicFileAttributes> bundlepathattrs = new HashMap<>();
			do {
				String path = it.next().toString().trim();
				if (path.isEmpty()) {
					continue;
				}
				if (path.startsWith("//")) {
					String actualpathstr = path.substring(2);
					WildcardPath wc = WildcardPath.valueOf(actualpathstr);
					NavigableMap<SakerPath, ? extends BasicFileAttributes> files = wc
							.getFiles(LocalFileProvider.getInstance());
					if (!ObjectUtils.isNullOrEmpty(files)) {
						for (Entry<SakerPath, ? extends BasicFileAttributes> entry : files.entrySet()) {
							bundlepathattrs.put(LocalFileProvider.getInstance().getPathKey(entry.getKey()),
									entry.getValue());
						}
					}
				} else {
					WildcardPath wc = WildcardPath.valueOf(path);
					NavigableMap<SakerPath, ? extends BasicFileAttributes> files = wc.getFiles(pathconfig);
					if (!ObjectUtils.isNullOrEmpty(files)) {
						for (Entry<SakerPath, ? extends BasicFileAttributes> entry : files.entrySet()) {
							bundlepathattrs.put(pathconfig.getPathKey(entry.getKey()), entry.getValue());
						}
					}
				}
			} while (it.hasNext());
			return bundlepathattrs;
		}

		@Override
		public Map<? extends ExternalArchiveKey, ? extends ExternalArchive> loadExternalArchives(
				ExternalDependencyInformation depinfo)
				throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException {
			return repository.loadExternalArchives(depinfo, this);
		}

		@Override
		public void updateStorageViewHash(MessageDigest digest) {
			digest.update(ConfiguredRepositoryStorage.STORAGE_TYPE_PARAMETER.getBytes(StandardCharsets.UTF_8));
			for (Entry<BundleIdentifier, LoadedViewBundleInfo> entry : bundles.entrySet()) {

				//we dont need to path in the hash, the hash of the bundle itself should be enough as its unique per bundle
				digest.update(entry.getKey().toString().getBytes(StandardCharsets.UTF_8));
//				PathKey bundlepathkey = entry.getValue().originalJarLocation;
//				digest.update(bundlepathkey.getFileProviderKey().getUUID().toString().getBytes(StandardCharsets.UTF_8));
//				digest.update(bundlepathkey.getPath().toString().getBytes(StandardCharsets.UTF_8));
				digest.update(entry.getValue().loaded.getHash());
			}
		}

		@Override
		public StorageViewKey getStorageViewKey() {
			return storageViewKey;
		}

		@Override
		public void appendConfigurationUserParameters(Map<String, String> userparameters, String repositoryid,
				String storagename) {
			StringBuilder bundlessb = new StringBuilder();
			for (LoadedViewBundleInfo binfo : bundles.values()) {
				PathKey loc = binfo.originalJarLocation;
				String bundlepath;
				if (loc.getFileProviderKey().equals(LocalFileProvider.getProviderKeyStatic())) {
					bundlepath = "//" + loc.getPath();
				} else {
					bundlepath = "//" + binfo.loaded.getJarPath();
				}
				if (bundlessb.length() > 0) {
					bundlessb.append(BUNDLES_PARAMETER_SEPARATOR);
				}
				bundlessb.append(bundlepath);
			}
			userparameters.put(repositoryid + "." + storagename + "." + PARAMETER_NEST_REPOSITORY_BUNDLES,
					bundlessb.toString());
		}

		@Override
		public NestRepositoryBundle lookupTaskBundle(TaskName taskname) throws TaskNotFoundException {
			NavigableMap<BundleIdentifier, LoadedViewBundleInfo> bundles = taskNameBundles.get(taskname.getName());
			if (ObjectUtils.isNullOrEmpty(bundles)) {
				throw new TaskNotFoundException("Bundle not found for task.", taskname);
			}
			BundleIdentifier bundleid = BundleUtils.selectAppropriateBundleIdentifierForTask(taskname,
					bundles.navigableKeySet());
			if (bundleid == null) {
				throw new TaskNotFoundException("Bundle not found for task.", taskname);
			}
			LoadedViewBundleInfo foundbundle = bundles.get(bundleid);
			if (foundbundle == null) {
				throw new TaskNotFoundException("Bundle not found: " + bundleid, taskname);
			}
			return foundbundle.loaded;
		}

		@Override
		public Set<BundleIdentifier> lookupBundleVersions(BundleIdentifier bundleid) {
			Objects.requireNonNull(bundleid, "bundle identifier");
			NavigableMap<String, BundleIdentifier> lookupres = new TreeMap<>(
					Collections.reverseOrder(BundleIdentifier::compareVersionNumbers));
			for (BundleIdentifier b : bundles.keySet()) {
				if (b.getName().equals(bundleid.getName())
						&& b.getBundleQualifiers().equals(bundleid.getBundleQualifiers())) {
					String vnum = b.getVersionNumber();
					if (vnum != null) {
						lookupres.put(vnum, b);
					}
				}
			}
			return new LinkedHashSet<>(lookupres.values());
		}

		@Override
		public Map<String, ? extends Set<? extends BundleIdentifier>> lookupBundleIdentifiers(String bundlename)
				throws NullPointerException, IllegalArgumentException {
			Objects.requireNonNull(bundlename, "bundle name");
			if (!BundleIdentifier.isValidBundleName(bundlename)) {
				throw new IllegalArgumentException("Invalid bundle name: " + bundlename);
			}
			Map<String, Set<BundleIdentifier>> result = new TreeMap<>(
					Collections.reverseOrder(BundleIdentifier::compareVersionNumbers));
			for (BundleIdentifier b : bundles.keySet()) {
				if (b.getName().equals(bundlename)) {
					String vnum = b.getVersionNumber();
					if (vnum != null) {
						result.computeIfAbsent(vnum, Functionals.treeSetComputer()).add(b);
					}
				}
			}
			return result;
		}

		@Override
		public AbstractBundleStorage getStorage() {
			return ParameterBundleStorage.this;
		}

		@Override
		public AbstractNestRepositoryBundle getBundle(BundleIdentifier bundleid) throws BundleLoadingFailedException {
			Objects.requireNonNull(bundleid, "bundleid");
			LoadedViewBundleInfo result = bundles.get(bundleid);
			if (result == null) {
				throw new BundleLoadingFailedException("Bundle not found with identifier: " + bundleid);
			}
			return result.loaded;
		}

		@Override
		public Object detectChanges(ExecutionPathConfiguration pathconfig) {
			synchronized (detectChangeLock) {
				Map<ProviderHolderPathKey, BasicFileAttributes> paramjarpaths = getParameterJarPaths(pathconfig);
				Set<SimplePathKey> invalidatedbundles = new HashSet<>();
				for (Entry<ProviderHolderPathKey, BasicFileAttributes> entry : paramjarpaths.entrySet()) {
					SimplePathKey simplebundlepathkey = new SimplePathKey(entry.getKey());
					LoadedViewBundleInfo present = pathKeyBundles.get(simplebundlepathkey);
					if (present == null || FileAttributesContentDescriptor.isChanged(present.originalBundleAttributes,
							entry.getValue())) {
						invalidatedbundles.add(simplebundlepathkey);
					}
				}
//				for (Entry<BundleIdentifier, LoadedViewBundleInfo> entry : bundles.entrySet()) {
//					LoadedViewBundleInfo bundleinfo = entry.getValue();
//
//					PathKey jarpk = entry.getValue().originalJarLocation;
//					try {
//						RootFileProviderKey providerkey = jarpk.getFileProviderKey();
//						boolean changed = getFileProviderForKey(pathconfig, providerkey).isChanged(jarpk.getPath(),
//								bundleinfo.originalBundleAttributes.getSize(),
//								bundleinfo.originalBundleAttributes.getLastModifiedMillis());
//						if (!changed) {
//							continue;
//						}
//					} catch (RMIRuntimeException e) {
//						//in case if the file provider is remote, and the call fails through RMI
//					}
//					//if we reach here, then the bundle changed
//					invalidatedbundles.put(entry.getKey(), entry.getValue());
//				}
				return invalidatedbundles.isEmpty() ? null : invalidatedbundles;
			}
		}

		@Override
		public void handleChanges(ExecutionPathConfiguration pathconfig, Object detectedchanges) {
			if (detectedchanges == null) {
				return;
			}
			synchronized (detectChangeLock) {
				@SuppressWarnings("unchecked")
				Set<SimplePathKey> invalidatedbundles = (Set<SimplePathKey>) detectedchanges;

				for (SimplePathKey bundlepathkey : invalidatedbundles) {
					LoadedViewBundleInfo present = pathKeyBundles.get(bundlepathkey);
					if (present != null) {
						removeBundle(present);
					}
				}

				NavigableMap<BundleIdentifier, ContentDescriptor> bundlecontents = new TreeMap<>();
//				@SuppressWarnings("unchecked")
//				Map<BundleIdentifier, LoadedViewBundleInfo> invalidatebundles = (Map<BundleIdentifier, LoadedViewBundleInfo>) detectedchanges;
//				for (Entry<BundleIdentifier, LoadedViewBundleInfo> entry : invalidatebundles.entrySet()) {
//					LoadedViewBundleInfo bundleinfo = entry.getValue();
//					result.add(entry.getKey());
//					removeBundle(bundleinfo);
//				}
				for (SimplePathKey bundlepathkey : invalidatedbundles) {
					RootFileProviderKey fpkey = bundlepathkey.getFileProviderKey();
					SakerFileProvider fp = getFileProviderForKey(pathconfig, fpkey);
					LoadedViewBundleInfo loaded = addBundle(
							new SimpleProviderHolderPathKey(bundlepathkey.getPath(), fp, fpkey));
					BundleIdentifier bundleid = loaded.loaded.getBundleIdentifier();
					bundlecontents.put(bundleid, HashContentDescriptor.createWithHash(loaded.loaded.getHash()));
				}
//				for (Entry<BundleIdentifier, LoadedViewBundleInfo> entry : invalidatebundles.entrySet()) {
//					PathKey originalloc = entry.getValue().originalJarLocation;
//					RootFileProviderKey providerKey = originalloc.getFileProviderKey();
//					SakerFileProvider fp = getFileProviderForKey(pathconfig, providerKey);
//					LoadedViewBundleInfo loaded = addBundle(
//							new SimpleProviderHolderPathKey(originalloc.getPath(), fp, providerKey));
//					bundlecontents.put(loaded.loaded.getBundleIdentifier(),
//							HashContentDescriptor.createWithHash(loaded.loaded.getHash()));
//					result.add(entry.getKey());
//				}
				this.storageViewKey = new ParameterStorageViewKeyImpl(bundlecontents);
			}
		}

		@Override
		public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
			NavigableSet<TaskName> result = new TreeSet<>();
			NavigableSet<String> qualifierbuffer = new TreeSet<>();
			for (Entry<String, NavigableMap<BundleIdentifier, LoadedViewBundleInfo>> entry : taskNameBundles
					.entrySet()) {
				String tn = entry.getKey();
				for (BundleIdentifier bundle : entry.getValue().keySet()) {
					qualifierbuffer.clear();
					qualifierbuffer.addAll(bundle.getBundleQualifiers());
					qualifierbuffer.addAll(bundle.getMetaQualifiers());
					result.add(TaskName.valueOf(tn, qualifierbuffer));
				}
			}
			return result;
		}

		@Override
		public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
			return ImmutableUtils.unmodifiableNavigableSet(bundles.navigableKeySet());
		}

		private LoadedViewBundleInfo addBundle(ProviderHolderPathKey pathkey) {
			RootFileProviderKey rootfpk = pathkey.getFileProviderKey();
			SakerFileProvider fp = pathkey.getFileProvider();
			SakerPath fpath = pathkey.getPath();
			String locationhashstring = StringUtils.toHexString(FileUtils.hashString(rootfpk.getUUID() + "/" + fpath));
			FileEntry sourcebundleattrs;
			Path jp;
			try {
				if (rootfpk.equals(LocalFileProvider.getProviderKeyStatic())
						&& fpath.startsWith(SakerPath.valueOf(bundlesDir))) {
					//the bundle to use is already under the bundles dir. no additional exporting required
					jp = LocalFileProvider.toRealPath(fpath);
					sourcebundleattrs = fp.getFileAttributes(fpath);
				} else {
					FileHashResult hres = getParameterBundleHash(fp, fpath);
					sourcebundleattrs = fp.getFileAttributes(fpath);
					if (hres.getCount() != sourcebundleattrs.getSize()) {
						throw new ConcurrentModificationException(
								"Parameter bundle was concurrently modified during examination: " + fpath);
					}
					Path bundleexportdir = bundlesDir.resolve(locationhashstring);
					String bundlehashstring = StringUtils.toHexString(hres.getHash());
					jp = bundleexportdir.resolve(bundlehashstring + ".jar");

					BasicFileAttributes jattrs = readAttributesOrNull(jp);
					//don't check modification time, as a bundle with same contents may be re-exported unnecessarily
					//if the name hash and size is the same, we expect the bundle contents to be the same
					//it is not a secure solution, but we don't aim to be
					//if the bundle at the location is corrupted, the user manually has to fix it.
					if (jattrs == null || jattrs.size() != sourcebundleattrs.size()) {
						try {
							Files.createDirectories(bundleexportdir);
						} catch (IOException e) {
							throw new BundleStorageInitializationException(
									"Failed to create directory to export parameter bundle: " + fpath + " at "
											+ bundleexportdir,
									e);
						}

						Path tempjarpath = jp.resolveSibling(UUID.randomUUID() + ".jar_temp");
						try {
							try (OutputStream out = Files.newOutputStream(tempjarpath, StandardOpenOption.CREATE_NEW,
									StandardOpenOption.WRITE)) {
								long c = fp.writeTo(fpath, ByteSink.valueOf(out));
								if (c != sourcebundleattrs.getSize()) {
									throw new ConcurrentModificationException(
											"Parameter bundle was concurrently modified during examination: " + fpath);
								}
							}
							//this can throw FileSystemException if the target jar is loaded by others
							try {
								Files.move(tempjarpath, jp, StandardCopyOption.REPLACE_EXISTING);
							} catch (Exception e) {
								jattrs = readAttributesOrNull(jp);
								if (jattrs == null) {
									//we failed to move, yet the file doesn't exist.
									System.err.println("Failed to move parameter bundle while loading: " + e);
									throw e;
								}
								if (jattrs.size() != sourcebundleattrs.size()) {
									//we failed to move, and the jar has different sizes still.
									//this can happen if the jar is already loaded by others
									//it may be either to hash collision which should be very rare,
									//or the bundle at the location was overwritten with different contents, and is currently loaded
									//or there is a directory at the location
									//in either case, we can ignore the exception, and continue with loading the bundle
									//print the exception just in case
									System.err.println("Failed to move parameter bundle while loading: " + e);
								}
							}
						} catch (Throwable e) {
							try {
								//only attempt to delete the temp jar if we failed
								// not in finally { }
								//if we succeeded, then the file moving from the temp jar was successful.
								Files.deleteIfExists(tempjarpath);
							} catch (IOException ignored) {
								e.addSuppressed(ignored);
							}
							throw e;
						}
					} //else already exists jar at the path, as a file, and with same length. 
				}
			} catch (IOException e) {
				throw new BundleStorageInitializationException("Failed to load bundle from: " + fpath, e);
			} catch (NoSuchAlgorithmException e) {
				throw new BundleStorageInitializationException("MD5 message digest algorithm not found.", e);
			}
			JarNestRepositoryBundleImpl jarbundle;
			try {
				jarbundle = getLoadBundle(jp);
			} catch (InvalidNestBundleException e) {
				throw new BundleStorageInitializationException("Invalid bundle specified: " + fpath, e);
			} catch (Exception e) {
				throw new BundleStorageInitializationException(
						"Failed to load bundle at path: " + jp + " from " + fpath, e);
			}
			LoadedViewBundleInfo loadedinfo = new LoadedViewBundleInfo(jarbundle, new SimplePathKey(pathkey),
					sourcebundleattrs);
			BundleIdentifier bundleid = jarbundle.getBundleIdentifier();
			LoadedViewBundleInfo prev = bundles.putIfAbsent(bundleid, loadedinfo);
			if (prev != null) {
				throw new BundleStorageInitializationException("Multiple bundles specified with identifier: " + bundleid
						+ ": " + jp + " and " + prev.loaded.getJarPath());
			}
			pathKeyBundles.put(new SimplePathKey(pathkey), loadedinfo);
			String bundlepackagename = bundleid.getName();
			NavigableMap<TaskName, String> taskclassnames = jarbundle.getInformation().getTaskClassNames();
			if (!taskclassnames.isEmpty()) {
				for (TaskName tn : taskclassnames.keySet()) {
					String tasknamestr = tn.getName();
					String tasknameprevpackagename = taskNamePackageNames.putIfAbsent(tasknamestr, bundlepackagename);
					if (tasknameprevpackagename != null && !tasknameprevpackagename.equals(bundlepackagename)) {
						throw new BundleStorageInitializationException("Multiple packages contain tasks with name: "
								+ tasknamestr + " in: " + bundlepackagename + " and " + tasknameprevpackagename);
					}
					taskNameBundles.computeIfAbsent(tasknamestr, Functionals.treeMapComputer()).put(bundleid,
							loadedinfo);
				}
			}

			return loadedinfo;

			//XXX redo maintenance of the bundles with concurrent processes in mind
//			try {
//				//exceptions can be ignored, as this delete call is only for maintenance to remove no longer used leftover jars.
//				LocalFileProvider.getInstance().deleteChildrenRecursivelyIfNotIn(jp.getParent(), Collections.singleton(jp.getFileName().toString()));
//			} catch (PartiallyDeletedChildrenException ignored) {
//				//this is probably thrown, as the opened jar cannot be deleted
//			} catch (IOException ignored) {
//				//this shouldn't be thrown generally
//			}
		}

		private void removeBundle(LoadedViewBundleInfo bundleinfo) {
			BundleIdentifier bundleid = bundleinfo.loaded.getBundleIdentifier();
			bundles.remove(bundleid, bundleinfo);
			pathKeyBundles.remove(bundleinfo.originalJarLocation, bundleinfo);

			NavigableMap<TaskName, String> taskclassnames = bundleinfo.loaded.getInformation().getTaskClassNames();
			if (!taskclassnames.isEmpty()) {
				for (TaskName tn : taskclassnames.keySet()) {
					String tasknamestr = tn.getName();
					NavigableMap<BundleIdentifier, LoadedViewBundleInfo> tasknamebundlemap = taskNameBundles
							.get(tasknamestr);
					if (!tasknamebundlemap.remove(bundleid, bundleinfo)) {
						//XXX different kind of error?
						throw new AssertionError(
								"Internal consistency error when removing bundle task: " + bundleid + " - " + tn);
					}
					if (tasknamebundlemap.isEmpty()) {
						taskNamePackageNames.remove(tasknamestr, bundleid.getName());
					}
				}
			}
		}
	}

	private static FileHashResult getParameterBundleHash(SakerFileProvider fp, SakerPath fpath)
			throws NoSuchAlgorithmException, IOException {
		if (TestFlag.ENABLED) {
			FileHashResult override = TestFlag.metric().overrideParameterBundlePerceivedHash(fp, fpath, "MD5");
			if (override != null) {
				return override;
			}
		}
		return fp.hash(fpath, "MD5");
	}

	private static SakerFileProvider getFileProviderForKey(ExecutionPathConfiguration pathconfig,
			RootFileProviderKey providerkey) {
		if (LocalFileProvider.getProviderKeyStatic().equals(providerkey)) {
			return LocalFileProvider.getInstance();
		}
		return pathconfig.getFileProvider(providerkey);
	}
}
