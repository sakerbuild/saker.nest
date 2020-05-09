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
import java.util.function.BiConsumer;

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
import saker.nest.exc.BundleDependencyUnsatisfiedException;
import saker.nest.exc.ExternalArchiveLoadingFailedException;
import saker.nest.meta.Versions;

public final class NestRepositoryImpl implements SakerRepository, NestRepository {
	static final char CHAR_CL_IDENITIFER_SEPARATOR = '\n';

	private static final String STORAGE_DIRECTORY_NAME_EXTERNAL_ARCHIVES = "external";

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

	private static class ExternalArchiveEntrySpec {
		private Set<WildcardPath> entries;
		private boolean includesMainArchive;

		public ExternalArchiveEntrySpec(ExternalDependency dep) {
			this.entries = dep.getEntries();
			this.includesMainArchive = dep.isIncludesMainArchive();
		}

		public ExternalArchiveEntrySpec(ExternalAttachmentInformation info) {
			this.entries = info.getEntries();
			this.includesMainArchive = info.isIncludesMainArchive();
		}

		public Set<WildcardPath> getEntries() {
			return entries;
		}

		public boolean isIncludesMainArchive() {
			return includesMainArchive;
		}
	}

	private static boolean specHasNonEmptyEntriesDependency(Iterable<? extends ExternalArchiveEntrySpec> deps) {
		for (ExternalArchiveEntrySpec dep : deps) {
			if (!ObjectUtils.isNullOrEmpty(dep.getEntries())) {
				return true;
			}
		}
		return false;
	}

	private static boolean specIsMainArchiveIncluded(Iterable<? extends ExternalArchiveEntrySpec> deps) {
		for (ExternalArchiveEntrySpec dep : deps) {
			if (dep.isIncludesMainArchive()) {
				return true;
			}
		}
		return false;
	}

	private static boolean specIncludesEntryName(String name, Set<ExternalArchiveEntrySpec> deps) {
		for (ExternalArchiveEntrySpec dep : deps) {
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
		return path.resolve("archive.jar");
	}

	public Map<SimpleExternalArchiveKey, ? extends AbstractExternalArchive> loadExternalArchives(
			ExternalDependencyInformation depinfo, AbstractBundleStorageView domainbundlestorage)
			throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException {
		Objects.requireNonNull(depinfo, "dependency info");
		Map<SimpleExternalArchiveKey, ExternalArchiveReference> archiverefs = loadExternalArchivesImpl(depinfo,
				domainbundlestorage);
		LinkedHashMap<SimpleExternalArchiveKey, AbstractExternalArchive> result = new LinkedHashMap<>();
		for (Entry<SimpleExternalArchiveKey, ExternalArchiveReference> entry : archiverefs.entrySet()) {
			result.put(entry.getKey(), entry.getValue().archive);
		}
		return result;
	}

	private Map<SimpleExternalArchiveKey, ExternalArchiveReference> loadExternalArchivesImpl(
			ExternalDependencyInformation extdependencies, AbstractBundleStorageView domainbundlestorage)
			throws ExternalArchiveLoadingFailedException {
		if (extdependencies.isEmpty()) {
			return Collections.emptyMap();
		}

		//TODO load attachments, but only those which target included archives

		Map<URI, Hashes> urihashes = BundleUtils.getExternalDependencyInformationHashes(extdependencies);

		List<Entry<URI, Set<ExternalArchiveEntrySpec>>> loaddependencies = new ArrayList<>();

		List<List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>>> loadedarchiverefs = new ArrayList<>();

		for (Entry<URI, ? extends ExternalDependencyList> entry : extdependencies.getDependencies().entrySet()) {
			Set<ExternalArchiveEntrySpec> depbuffer = new LinkedHashSet<>();
			ExternalDependencyList deplist = entry.getValue();
			for (ExternalDependency dep : deplist.getDependencies()) {
				depbuffer.add(new ExternalArchiveEntrySpec(dep));
			}
			for (ExternalAttachmentInformation information : deplist.getSourceAttachments().values()) {
				depbuffer.add(new ExternalArchiveEntrySpec(information));
			}
			for (ExternalAttachmentInformation information : deplist.getDocumentationAttachments().values()) {
				depbuffer.add(new ExternalArchiveEntrySpec(information));
			}

			if (depbuffer.isEmpty()) {
				continue;
			}

			URI uri = entry.getKey();
			loaddependencies.add(ImmutableUtils.makeImmutableMapEntry(uri, depbuffer));

			//entry load list
			loadedarchiverefs.add(new ArrayList<>());
			//main load list
			loadedarchiverefs.add(new ArrayList<>());
		}
		if (loaddependencies.isEmpty()) {
			return Collections.emptyMap();
		}

		try (ThreadWorkPool loaderpool = ThreadUtils.newDynamicWorkPool(null, "ext-dep-loader-", null, true)) {
			int depidx = 0;
			for (Entry<URI, Set<ExternalArchiveEntrySpec>> entry : loaddependencies) {
				URI uri = entry.getKey();
				Set<ExternalArchiveEntrySpec> deps = entry.getValue();
				Hashes urihash = urihashes.get(uri);

				List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>> entryloadlist = loadedarchiverefs
						.get(depidx++);
				List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>> mainloadlist = loadedarchiverefs
						.get(depidx++);

				Path archivepath = getExternalArchivePath(uri, urihash);
				loadExternalArchive(loaderpool, uri, archivepath, urihash, domainbundlestorage, (extarchive, e) -> {
					if (e != null) {
						throw new BundleDependencyUnsatisfiedException("Failed to load external dependency: " + uri, e);
					}
					if (urihash.sha256 != null && !urihash.sha256.equals(extarchive.hashes.sha256)) {
						throw new BundleDependencyUnsatisfiedException(
								"Failed to load external dependency: " + uri + " (SHA-256 mismatch, expected: "
										+ urihash.sha256 + " actual: " + extarchive.hashes.sha256 + ")");
					}
					if (urihash.sha1 != null && !urihash.sha1.equals(extarchive.hashes.sha1)) {
						throw new BundleDependencyUnsatisfiedException(
								"Failed to load external dependency: " + uri + " (SHA-1 mismatch, expected: "
										+ urihash.sha1 + " actual: " + extarchive.hashes.sha1 + ")");
					}
					if (urihash.md5 != null && !urihash.md5.equals(extarchive.hashes.md5)) {
						throw new BundleDependencyUnsatisfiedException(
								"Failed to load external dependency: " + uri + " (MD5 mismatch, expected: "
										+ urihash.md5 + " actual: " + extarchive.hashes.md5 + ")");
					}

					if (specHasNonEmptyEntriesDependency(deps)) {
						List<String> includedentrynames = new ArrayList<>();
						for (String ename : extarchive.archive.getEntryNames()) {
							if (!specIncludesEntryName(ename, deps)) {
								continue;
							}
							includedentrynames.add(ename);
							entryloadlist.add(null);
						}
						int i = 0;
						for (String ename : includedentrynames) {
							int idx = i++;
							loadEmbeddedArchive(loaderpool, extarchive, ename, archivepath, uri,
									(embeddedarchive, ee) -> {
										if (ee != null) {
											throw new BundleDependencyUnsatisfiedException(
													"Failed to load external dependency entry: " + ename + " in " + uri,
													ee);
										}
										entryloadlist.set(idx, ImmutableUtils.makeImmutableMapEntry(
												new SimpleExternalArchiveKey(uri, ename), embeddedarchive));
									});

						}
					}
					if (specIsMainArchiveIncluded(deps)) {
						mainloadlist.add(
								ImmutableUtils.makeImmutableMapEntry(new SimpleExternalArchiveKey(uri), extarchive));
					}
				});

			}
		} catch (ParallelExecutionException e) {
			throw new ExternalArchiveLoadingFailedException(e);
		}
		Map<SimpleExternalArchiveKey, ExternalArchiveReference> result = new LinkedHashMap<>();
		for (List<Entry<SimpleExternalArchiveKey, ExternalArchiveReference>> depcllist : loadedarchiverefs) {
			for (Entry<SimpleExternalArchiveKey, ExternalArchiveReference> dcl : depcllist) {
				if (dcl == null) {
					//shouldn't happen, ever.
					throw new BundleDependencyUnsatisfiedException(
							"Failed to load external dependencies. (Internal consistency error)");
				}
				result.put(dcl.getKey(), dcl.getValue());
			}
		}
		return result;
	}

	private void loadEmbeddedArchive(ThreadWorkPool loaderpool, ExternalArchiveReference containingarchive,
			String ename, Path archivepath, URI archiveuri,
			BiConsumer<ExternalArchiveReference, Exception> resultconsumer) {
		Path entrypath = archivepath.resolveSibling("entries").resolve(ename);
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

	public void loadExternalArchive(ThreadWorkPool loaderpool, URI uri, Path archivepath, Hashes expectedhashes,
			AbstractBundleStorageView bundlestorage, BiConsumer<ExternalArchiveReference, Exception> resultconsumer) {
		ExternalArchiveReference extarchive = externalArchives.get(archivepath);
		if (extarchive != null) {
			resultconsumer.accept(extarchive, null);
			return;
		}
		loaderpool.offer(() -> {
			try {
				ExternalArchiveReference archive = loadExternalArchive(uri, archivepath, expectedhashes, bundlestorage);
				resultconsumer.accept(archive, null);
			} catch (Exception e) {
				resultconsumer.accept(null, e);
			}
		});
	}

	private ExternalArchiveReference loadExternalArchive(URI uri, Path archivepath, Hashes expectedhashes,
			AbstractBundleStorageView bundlestorage) throws IOException, ExternalArchiveLoadingFailedException {
		return loadExternalArchiveImpl(new SimpleExternalArchiveKey(uri), archivepath, expectedhashes, uri, () -> {
			return bundlestorage.openExternalDependencyURI(uri, expectedhashes);
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
