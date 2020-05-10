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
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.classloader.SubDirectoryClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.function.IOBiFunction;
import saker.build.thirdparty.saker.util.io.function.IOSupplier;
import saker.build.thirdparty.saker.util.thread.ParallelExecutionException;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.nest.bundle.AbstractExternalArchive;
import saker.nest.bundle.BundleUtils;
import saker.nest.bundle.ExternalAttachmentInformation;
import saker.nest.bundle.ExternalDependency;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.ExternalDependencyList;
import saker.nest.bundle.Hashes;
import saker.nest.bundle.JarExternalArchiveImpl;
import saker.nest.bundle.SimpleExternalArchiveKey;
import saker.nest.bundle.storage.AbstractBundleStorage;
import saker.nest.bundle.storage.AbstractBundleStorageView;
import saker.nest.bundle.storage.AbstractStorageKey;
import saker.nest.exc.ExternalArchiveLoadingFailedException;
import saker.nest.meta.Versions;
import testing.saker.nest.TestFlag;

public final class NestRepositoryImpl implements SakerRepository, NestRepository {

	static final char CHAR_CL_IDENITIFER_SEPARATOR = '\n';

	private static final String STORAGE_DIRECTORY_NAME_EXTERNAL_ARCHIVES = "external";
	private static final String EXTERNAL_ARCHIVES_SUBDIRECTORY_ENTRIES = "entries";

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

	private static boolean hasNonEmptyEntriesDependency(Iterable<? extends ExternalDependency> deps) {
		for (ExternalDependency dep : deps) {
			if (!ObjectUtils.isNullOrEmpty(dep.getEntries())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isMainArchiveIncluded(Iterable<? extends ExternalDependency> deps) {
		for (ExternalDependency dep : deps) {
			if (dep.isIncludesMainArchive()) {
				return true;
			}
		}
		return false;
	}

	private static boolean includesEntryName(String name, Iterable<? extends ExternalDependency> deps) {
		for (ExternalDependency dep : deps) {
			Set<WildcardPath> entries = dep.getEntries();
			if (ObjectUtils.isNullOrEmpty(entries)) {
				continue;
			}
			if (BundleUtils.isWildcardsInclude(name, entries)) {
				return true;
			}
		}
		return false;
	}

	private static boolean attachmentTargetsEntryName(String name, ExternalAttachmentInformation dep) {
		Set<WildcardPath> entries = dep.getTargetEntries();
		if (ObjectUtils.isNullOrEmpty(entries)) {
			return false;
		}
		if (BundleUtils.isWildcardsInclude(name, entries)) {
			return true;
		}
		return false;
	}

	private Path getExternalArchivePath(URI uri, Hashes hash) {
		Path path = getRepositoryStorageDirectory().resolve(STORAGE_DIRECTORY_NAME_EXTERNAL_ARCHIVES)
				.resolve(BundleUtils.sha256(uri));
		if (hash != null) {
			if (hash.sha256 != null) {
				path = path.resolve(hash.sha256);
			} else {
				if (hash.sha1 != null) {
					path = path.resolve("sha1-" + hash.sha256);
				} else {
					if (hash.md5 != null) {
						path = path.resolve("md5-" + hash.sha256);
					}
				}
			}
		}
		String uripath = uri.getPath();
		if (uripath != null) {
			try {
				SakerPath spath = SakerPath.valueOf(uripath);
				String fn = spath.getFileName();
				if (fn != null) {
					if (!EXTERNAL_ARCHIVES_SUBDIRECTORY_ENTRIES.equalsIgnoreCase(fn)) {
						return path.resolve(fn);
					}
				}
			} catch (IllegalArgumentException e) {
				//couldn't parse path. fallback to default
				//throw if testing, so we can fix it
				if (TestFlag.ENABLED) {
					throw e;
				}
			}
		}
		//fallback to default well know file name
		return path.resolve("archive.jar");
	}

	public Map<SimpleExternalArchiveKey, ? extends AbstractExternalArchive> loadExternalArchives(
			ExternalDependencyInformation depinfo, AbstractBundleStorageView domainbundlestorage)
			throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException {
		IOBiFunction<URI, Hashes, InputStream> inputsupplier = domainbundlestorage::openExternalDependencyURI;

		return loadExternalArchives(depinfo, inputsupplier);
	}

	public Map<SimpleExternalArchiveKey, ? extends AbstractExternalArchive> loadExternalArchives(
			ExternalDependencyInformation depinfo,
			IOBiFunction<? super URI, ? super Hashes, ? extends InputStream> inputsupplier)
			throws ExternalArchiveLoadingFailedException {
		Objects.requireNonNull(depinfo, "dependency info");
		Map<SimpleExternalArchiveKey, ExternalArchiveReference> archiverefs = loadExternalArchivesImpl(depinfo,
				inputsupplier);
		LinkedHashMap<SimpleExternalArchiveKey, AbstractExternalArchive> result = new LinkedHashMap<>();
		for (Entry<SimpleExternalArchiveKey, ExternalArchiveReference> entry : archiverefs.entrySet()) {
			result.put(entry.getKey(), entry.getValue().archive);
		}
		return result;
	}

	private static Map<URI, ExternalAttachmentInformation> getMainTargettingAttachments(
			ExternalDependencyList deplist) {
		Map<URI, ExternalAttachmentInformation> result = null;
		for (Entry<URI, ExternalAttachmentInformation> entry : deplist.getSourceAttachments().entrySet()) {
			ExternalAttachmentInformation attachmentinfo = entry.getValue();
			if (attachmentinfo.isTargetsMainArchive()) {
				if (result == null) {
					result = new LinkedHashMap<>();
				}
				result.put(entry.getKey(), attachmentinfo);
			}
		}
		for (Entry<URI, ExternalAttachmentInformation> entry : deplist.getDocumentationAttachments().entrySet()) {
			ExternalAttachmentInformation attachmentinfo = entry.getValue();
			if (attachmentinfo.isTargetsMainArchive()) {
				if (result == null) {
					result = new LinkedHashMap<>();
				}
				result.put(entry.getKey(), attachmentinfo);
			}
		}
		if (result == null) {
			return Collections.emptyMap();
		}
		return result;
	}

	private static Map<URI, ExternalAttachmentInformation> getEntryTargettingAttachments(String entryname,
			ExternalDependencyList deplist) {
		Map<URI, ExternalAttachmentInformation> result = null;
		for (Entry<URI, ExternalAttachmentInformation> entry : deplist.getSourceAttachments().entrySet()) {
			ExternalAttachmentInformation attachmentinfo = entry.getValue();
			if (attachmentTargetsEntryName(entryname, attachmentinfo)) {
				if (result == null) {
					result = new LinkedHashMap<>();
				}
				result.put(entry.getKey(), attachmentinfo);
			}
		}
		for (Entry<URI, ExternalAttachmentInformation> entry : deplist.getDocumentationAttachments().entrySet()) {
			ExternalAttachmentInformation attachmentinfo = entry.getValue();
			if (attachmentTargetsEntryName(entryname, attachmentinfo)) {
				if (result == null) {
					result = new LinkedHashMap<>();
				}
				result.put(entry.getKey(), attachmentinfo);
			}
		}
		if (result == null) {
			return Collections.emptyMap();
		}
		return result;
	}

	private static class ArchiveCollector {
		private List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>> archives = new ArrayList<>();
		private List<ArchiveCollector> subCollectors = new ArrayList<>();

		public ArchiveCollector addSubCollector() {
			ArchiveCollector collector = new ArchiveCollector();
			subCollectors.add(collector);
			return collector;
		}

		public int reserveArchives(int count) {
			int idx = archives.size();

			while (count-- > 0) {
				archives.add(null);
			}
			return idx;
		}

		public void setArchive(int idx, SimpleExternalArchiveKey key, ExternalArchiveReference ref) {
			archives.set(idx, ImmutableUtils.makeImmutableMapEntry(key, ref));
		}

		public List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>> flatten() {
			List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>> result = new ArrayList<>();
			flattenInto(result);
			return result;
		}

		public void flattenInto(List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>> result) {
			result.addAll(archives);
			for (ArchiveCollector sc : subCollectors) {
				sc.flattenInto(result);
			}
		}
	}

	private Map<SimpleExternalArchiveKey, ExternalArchiveReference> loadExternalArchivesImpl(
			ExternalDependencyInformation extdependencies,
			IOBiFunction<? super URI, ? super Hashes, ? extends InputStream> inputsupplier)
			throws ExternalArchiveLoadingFailedException {
		if (extdependencies.isEmpty()) {
			return Collections.emptyMap();
		}

		Map<URI, Hashes> urihashes = BundleUtils.getExternalDependencyInformationHashes(extdependencies);

		List<Entry<URI, ExternalDependencyList>> loaddependencies = new ArrayList<>();

		ArchiveCollector archivecollector = new ArchiveCollector();

		for (Entry<URI, ? extends ExternalDependencyList> entry : extdependencies.getDependencies().entrySet()) {
			ExternalDependencyList deplist = entry.getValue();
			if (deplist.isEmpty()) {
				continue;
			}

			URI uri = entry.getKey();
			loaddependencies.add(ImmutableUtils.makeImmutableMapEntry(uri, deplist));
		}
		if (loaddependencies.isEmpty()) {
			return Collections.emptyMap();
		}

		try (ThreadWorkPool loaderpool = ThreadUtils.newDynamicWorkPool(null, "ext-dep-loader-", null, true)) {
			for (Entry<URI, ExternalDependencyList> entry : loaddependencies) {
				ExternalDependencyList deplist = entry.getValue();

				boolean mainincluded;

				{
					URI uri = entry.getKey();
					Hashes urihash = urihashes.get(uri);
					Set<? extends ExternalDependency> dependencies = deplist.getDependencies();
					mainincluded = isMainArchiveIncluded(dependencies);
					Path archivepath = getExternalArchivePath(uri, urihash);

					ArchiveCollector deparchivecollector = archivecollector.addSubCollector();

					loadExternalArchive(loaderpool, uri, archivepath, urihash, inputsupplier, (extarchive, e) -> {
						if (e != null) {
							throw new ExternalArchiveLoadingFailedException(
									"Failed to load external dependency: " + uri, e);
						}
						checkHashes(uri, urihash, extarchive);

						if (mainincluded) {
							int mainidx = deparchivecollector.reserveArchives(1);
							deparchivecollector.setArchive(mainidx, new SimpleExternalArchiveKey(uri), extarchive);
						}

						if (hasNonEmptyEntriesDependency(dependencies)) {
							Collection<String> includedentrynames = new LinkedHashSet<>();
							for (String ename : extarchive.archive.getEntryNames()) {
								if (!includesEntryName(ename, dependencies)) {
									continue;
								}
								includedentrynames.add(ename);
								Map<URI, ExternalAttachmentInformation> attachments = getEntryTargettingAttachments(
										ename, deplist);
								if (!ObjectUtils.isNullOrEmpty(attachments)) {
									ArchiveCollector attachcollector = deparchivecollector.addSubCollector();
									loadExternalAttachmentsImpl(loaderpool, attachcollector, attachments, urihashes,
											inputsupplier);
								}
							}
							int i = deparchivecollector.reserveArchives(includedentrynames.size());
							for (String ename : includedentrynames) {
								int idx = i++;
								loadEmbeddedArchive(loaderpool, extarchive, ename, archivepath, uri,
										(embeddedarchive, ee) -> {
											if (ee != null) {
												throw new ExternalArchiveLoadingFailedException(
														"Failed to load external dependency entry: " + ename + " in "
																+ uri,
														ee);
											}
											deparchivecollector.setArchive(idx,
													new SimpleExternalArchiveKey(uri, ename), embeddedarchive);
										});

							}
						}
					});
				}
				if (mainincluded) {
					ArchiveCollector attachcollector = archivecollector.addSubCollector();
					Map<URI, ExternalAttachmentInformation> attachments = getMainTargettingAttachments(deplist);

					loadExternalAttachmentsImpl(loaderpool, attachcollector, attachments, urihashes, inputsupplier);
				}
			}
		} catch (ParallelExecutionException e) {
			throw new ExternalArchiveLoadingFailedException(e);
		}
		Map<SimpleExternalArchiveKey, ExternalArchiveReference> result = new LinkedHashMap<>();
		for (Entry<SimpleExternalArchiveKey, ExternalArchiveReference> dcl : archivecollector.flatten()) {
			if (dcl == null) {
				//shouldn't happen, ever.
				throw new ExternalArchiveLoadingFailedException(
						"Failed to load external dependencies. (Internal consistency error)");
			}
			result.putIfAbsent(dcl.getKey(), dcl.getValue());
		}
		return result;
	}

	private void loadExternalAttachmentsImpl(ThreadWorkPool loaderpool, ArchiveCollector attachcollector,
			Map<URI, ExternalAttachmentInformation> attachments, Map<URI, Hashes> urihashes,
			IOBiFunction<? super URI, ? super Hashes, ? extends InputStream> inputsupplier)
			throws ExternalArchiveLoadingFailedException {
		for (Entry<URI, ExternalAttachmentInformation> attachmententry : attachments.entrySet()) {
			URI attachmenturi = attachmententry.getKey();
			ExternalAttachmentInformation attachmentinfo = attachmententry.getValue();
			Hashes attachmenturihash = urihashes.get(attachmenturi);

			Path attachmentarchivepath = getExternalArchivePath(attachmenturi, attachmenturihash);

			loadExternalArchive(loaderpool, attachmenturi, attachmentarchivepath, attachmenturihash, inputsupplier,
					(extarchive, e) -> {
						if (e != null) {
							throw new ExternalArchiveLoadingFailedException(
									"Failed to load external attachment: " + attachmenturi, e);
						}
						checkHashes(attachmenturi, attachmenturihash, extarchive);

						if (attachmentinfo.isIncludesMainArchive()) {
							int mainidx = attachcollector.reserveArchives(1);
							attachcollector.setArchive(mainidx, new SimpleExternalArchiveKey(attachmenturi),
									extarchive);
						}
						if (!attachmentinfo.getEntries().isEmpty()) {
							Collection<String> includedentrynames = new LinkedHashSet<>();
							for (String ename : extarchive.archive.getEntryNames()) {
								if (!attachmentTargetsEntryName(ename, attachmentinfo)) {
									continue;
								}
								includedentrynames.add(ename);
							}
							int i = attachcollector.reserveArchives(includedentrynames.size());

							for (String ename : includedentrynames) {
								int idx = i++;
								loadEmbeddedArchive(loaderpool, extarchive, ename, attachmentarchivepath, attachmenturi,
										(embeddedarchive, ee) -> {
											if (ee != null) {
												throw new ExternalArchiveLoadingFailedException(
														"Failed to load external dependency attachment: " + ename
																+ " in " + attachmenturi,
														ee);
											}
											attachcollector.setArchive(idx,
													new SimpleExternalArchiveKey(attachmenturi, ename),
													embeddedarchive);
										});

							}
						}
					});
		}
	}

	private static void checkHashes(URI uri, Hashes urihash, ExternalArchiveReference extarchive)
			throws ExternalArchiveLoadingFailedException {
		if (urihash.sha256 != null && !urihash.sha256.equals(extarchive.hashes.sha256)) {
			throw new ExternalArchiveLoadingFailedException("Failed to load external dependency: " + uri
					+ " (SHA-256 mismatch, expected: " + urihash.sha256 + " actual: " + extarchive.hashes.sha256 + ")");
		}
		if (urihash.sha1 != null && !urihash.sha1.equals(extarchive.hashes.sha1)) {
			throw new ExternalArchiveLoadingFailedException("Failed to load external dependency: " + uri
					+ " (SHA-1 mismatch, expected: " + urihash.sha1 + " actual: " + extarchive.hashes.sha1 + ")");
		}
		if (urihash.md5 != null && !urihash.md5.equals(extarchive.hashes.md5)) {
			throw new ExternalArchiveLoadingFailedException("Failed to load external dependency: " + uri
					+ " (MD5 mismatch, expected: " + urihash.md5 + " actual: " + extarchive.hashes.md5 + ")");
		}
	}

	private interface ExternalArchiveLoadConsumer {
		public void accept(ExternalArchiveReference reference, Exception e)
				throws ExternalArchiveLoadingFailedException;
	}

	private void loadEmbeddedArchive(ThreadWorkPool loaderpool, ExternalArchiveReference containingarchive,
			String ename, Path archivepath, URI archiveuri, ExternalArchiveLoadConsumer resultconsumer)
			throws ExternalArchiveLoadingFailedException {
		Path entrypath = archivepath.resolveSibling(EXTERNAL_ARCHIVES_SUBDIRECTORY_ENTRIES).resolve(ename);
		ExternalArchiveReference extarchive = externalArchives.get(entrypath);
		if (extarchive != null) {
			resultconsumer.accept(extarchive, null);
			return;
		}
		loaderpool.offer(() -> {
			try {
				ExternalArchiveReference archive = loadEmbeddedArchive(containingarchive, ename, entrypath, archiveuri);
				resultconsumer.accept(archive, null);
			} catch (Exception e) {
				resultconsumer.accept(null, e);
			}
		});
	}

	private void loadExternalArchive(ThreadWorkPool loaderpool, URI uri, Path archivepath, Hashes expectedhashes,
			IOBiFunction<? super URI, ? super Hashes, ? extends InputStream> inputsupplier,
			ExternalArchiveLoadConsumer resultconsumer) throws ExternalArchiveLoadingFailedException {
		ExternalArchiveReference extarchive = externalArchives.get(archivepath);
		if (extarchive != null) {
			resultconsumer.accept(extarchive, null);
			return;
		}
		loaderpool.offer(() -> {
			try {
				ExternalArchiveReference archive = loadExternalArchive(uri, archivepath, expectedhashes, inputsupplier);
				resultconsumer.accept(archive, null);
			} catch (Exception e) {
				resultconsumer.accept(null, e);
			}
		});
	}

	private ExternalArchiveReference loadExternalArchive(URI uri, Path archivepath, Hashes expectedhashes,
			IOBiFunction<? super URI, ? super Hashes, ? extends InputStream> inputsupplier)
			throws IOException, ExternalArchiveLoadingFailedException {
		return loadExternalArchiveImpl(new SimpleExternalArchiveKey(uri), archivepath, expectedhashes, uri, () -> {
			return inputsupplier.apply(uri, expectedhashes);
		});
	}

	private ExternalArchiveReference loadEmbeddedArchive(ExternalArchiveReference containingarchive, String ename,
			Path entrypath, URI archiveuri) throws IOException, ExternalArchiveLoadingFailedException {

		HashingInputStream[] hashingin = { null };
		ExternalArchiveReference result = loadExternalArchiveImpl(
				new SimpleExternalArchiveKey(containingarchive.archive.getArchiveKey().getUri(), ename), entrypath,
				null, null, () -> {
					InputStream entryis = containingarchive.archive.openEntry(ename);
					hashingin[0] = new HashingInputStream(entryis);
					return hashingin[0];
				});
		Hashes entryhash;
		if (hashingin[0] != null) {
			Hashes hashes = hashingin[0].getHashes();
			if (hashes != null) {
				containingarchive.putEntryHash(ename, hashes);
				entryhash = hashes;
			} else {
				entryhash = containingarchive.getEntryHash(ename);
			}
		} else {
			entryhash = containingarchive.getEntryHash(ename);
		}
		if (!entryhash.equals(result.hashes)) {
			throw new ExternalArchiveLoadingFailedException(
					"External archive entry hash mismatch: " + ename + " in " + archiveuri);
		}
		return result;
	}

	private ExternalArchiveReference loadExternalArchiveImpl(SimpleExternalArchiveKey archivekey, Path archivepath,
			Hashes expectedhashes, Object loadexceptioninfo, IOSupplier<InputStream> archiveinputsupplier)
			throws IOException, ExternalArchiveLoadingFailedException {
		ExternalArchiveReference extarchive;
		synchronized (externalArchiveLoadLocks.computeIfAbsent(archivepath, Functionals.objectComputer())) {
			extarchive = externalArchives.get(archivepath);
			if (extarchive != null) {
				return extarchive;
			}
			Hashes loadhashes = loadExternalArchiveFromInputImpl(archivepath, expectedhashes, loadexceptioninfo,
					archiveinputsupplier);
			JarExternalArchiveImpl jararchive = JarExternalArchiveImpl.create(archivekey, archivepath);
			extarchive = createArchiveReference(jararchive);
			if (loadhashes != null && !loadhashes.equals(extarchive.hashes)) {
				IOException e = new IOException(
						"Hash mismatch. External archive was concurrently modified: " + archivepath);
				IOUtils.closeExc(e, jararchive);
				throw e;
			}
			for (String ename : jararchive.getEntryNames()) {
				//validate entries
				BundleUtils.checkArchiveEntryName(ename);
			}
			externalArchives.put(archivepath, extarchive);
		}
		return extarchive;
	}

	private static Hashes loadExternalArchiveFromInputImpl(Path archivepath, Hashes expectedhashes,
			Object loadexceptioninfo, IOSupplier<InputStream> archiveinputsupplier)
			throws IOException, ExternalArchiveLoadingFailedException {
		Hashes loadhashes = null;
		Path tempfile = archivepath.resolveSibling(UUID.randomUUID() + ".temp");
		try {
			synchronized (("nest-external-dep-load:" + archivepath).intern()) {
				if (!Files.isRegularFile(archivepath)) {
					Files.createDirectories(archivepath.getParent());
					try (InputStream is = archiveinputsupplier.get()) {
						try (OutputStream out = Files.newOutputStream(tempfile)) {
							if (is instanceof HashingInputStream) {
								StreamUtils.copyStream(is, out);
								loadhashes = ((HashingInputStream) is).getHashes();
							} else {
								try (HashingOutputStream hasher = new HashingOutputStream(out)) {
									StreamUtils.copyStream(is, hasher);
									loadhashes = hasher.getHashes();
								}
							}
						}
					}
					if (expectedhashes != null) {
						if (expectedhashes.sha256 != null && !expectedhashes.sha256.equals(loadhashes.sha256)) {
							throw new ExternalArchiveLoadingFailedException(
									"External archive load hash mismatch: " + loadexceptioninfo + " (SHA-256 expected: "
											+ expectedhashes.sha256 + " actual: " + loadhashes.sha256 + ")");
						}
						if (expectedhashes.sha1 != null && !expectedhashes.sha1.equals(loadhashes.sha1)) {
							throw new ExternalArchiveLoadingFailedException(
									"External archive load hash mismatch: " + loadexceptioninfo + " (SHA-1 expected: "
											+ expectedhashes.sha1 + " actual: " + loadhashes.sha1 + ")");
						}
						if (expectedhashes.md5 != null && !expectedhashes.md5.equals(loadhashes.md5)) {
							throw new ExternalArchiveLoadingFailedException(
									"External archive load hash mismatch: " + loadexceptioninfo + " (MD5 expected: "
											+ expectedhashes.md5 + " actual: " + loadhashes.md5 + ")");
						}
					}
					try {
						Files.move(tempfile, archivepath);
					} catch (IOException e) {
						if (!Files.isRegularFile(archivepath)) {
							throw e;
						}
						//continue, somebody loaded concurrently in other jvm?
					}
				}
			}
		} finally {
			Files.deleteIfExists(tempfile);
		}
		return loadhashes;
	}

	private static ExternalArchiveReference createArchiveReference(JarExternalArchiveImpl archive) throws IOException {
		SeekableByteChannel channel = archive.getChannel();
		MessageDigest sha1;
		MessageDigest sha256;
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
			sha256 = MessageDigest.getInstance("SHA-256");
			sha1 = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError("Failed to retrieve hashing algorithm.", e);
		}
		synchronized (channel) {
			channel.position(0);
			InputStream in = Channels.newInputStream(channel);
			byte[] buffer = new byte[StreamUtils.DEFAULT_BUFFER_SIZE];
			for (int read; (read = in.read(buffer)) > 0;) {
				sha256.update(buffer, 0, read);
				sha1.update(buffer, 0, read);
				md5.update(buffer, 0, read);
			}
		}
		Hashes hashes = new Hashes(StringUtils.toHexString(sha256.digest()), StringUtils.toHexString(sha1.digest()),
				StringUtils.toHexString(md5.digest()));
		return new ExternalArchiveReference(archive, hashes);
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

	private static class HashingInputStream extends InputStream {
		protected MessageDigest sha256;
		protected MessageDigest sha1;
		protected MessageDigest md5;

		protected Hashes hashes;

		private InputStream is;

		public HashingInputStream(InputStream is) {
			this.is = is;

			try {
				sha256 = MessageDigest.getInstance("SHA-256");
				sha1 = MessageDigest.getInstance("SHA-1");
				md5 = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError("Built-in hashing algorithm not found.", e);
			}
		}

		private void genHashes() {
			if (hashes == null) {
				return;
			}
			hashes = new Hashes(StringUtils.toHexString(sha256.digest()), StringUtils.toHexString(sha1.digest()),
					StringUtils.toHexString(md5.digest()));
		}

		@Override
		public int read() throws IOException {
			if (hashes != null) {
				return -1;
			}
			int b = is.read();
			if (b >= 0) {
				sha256.update((byte) b);
				sha1.update((byte) b);
				md5.update((byte) b);
			} else {
				genHashes();
			}
			return b;
		}

		@Override
		public int read(byte[] b) throws IOException {
			return this.read(b, 0, b.length);
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (hashes != null) {
				return -0;
			}
			int read = super.read(b, off, len);
			if (read > 0) {
				sha256.update(b, off, read);
				sha1.update(b, off, read);
				md5.update(b, off, read);
			} else {
				genHashes();
			}
			return read;
		}

		@Override
		public void close() throws IOException {
			is.close();
		}

		public Hashes getHashes() {
			return hashes;
		}
	}

	private static class HashingOutputStream extends OutputStream {
		protected MessageDigest sha256;
		protected MessageDigest sha1;
		protected MessageDigest md5;

		private OutputStream os;

		public HashingOutputStream(OutputStream os) {
			this.os = os;

			try {
				sha256 = MessageDigest.getInstance("SHA-256");
				sha1 = MessageDigest.getInstance("SHA-1");
				md5 = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError("Built-in hashing algorithm not found.", e);
			}
		}

		@Override
		public void write(int b) throws IOException {
			sha256.update((byte) b);
			sha1.update((byte) b);
			md5.update((byte) b);
			os.write(b);
		}

		@Override
		public void write(byte[] b) throws IOException {
			this.write(b, 0, b.length);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			sha256.update(b, off, len);
			sha1.update(b, off, len);
			md5.update(b, off, len);
			os.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			os.flush();
		}

		@Override
		public void close() throws IOException {
			os.close();
		}

		public Hashes getHashes() {
			return new Hashes(StringUtils.toHexString(sha256.digest()), StringUtils.toHexString(sha1.digest()),
					StringUtils.toHexString(md5.digest()));
		}
	}

}
