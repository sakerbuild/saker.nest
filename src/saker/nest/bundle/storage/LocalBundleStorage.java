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
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import saker.build.file.StreamWritable;
import saker.build.file.content.FileAttributesContentDescriptor;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.RootFileProviderKey;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskName;
import saker.build.task.identifier.TaskIdentifier;
import saker.build.thirdparty.saker.rmi.annot.invoke.RMICacheResult;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMISerialize;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWrap;
import saker.build.thirdparty.saker.rmi.annot.transfer.RMIWriter;
import saker.build.thirdparty.saker.rmi.io.RMIObjectInput;
import saker.build.thirdparty.saker.rmi.io.RMIObjectOutput;
import saker.build.thirdparty.saker.rmi.io.wrap.RMIWrapper;
import saker.build.thirdparty.saker.rmi.io.writer.RemoteRMIObjectWriteHandler;
import saker.build.thirdparty.saker.util.ConcurrentPrependAccumulator;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.MultiplexOutputStream;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncBufferedOutputStream;
import saker.build.thirdparty.saker.util.rmi.wrap.RMITreeSetSerializeElementWrapper;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryImpl;
import saker.nest.bundle.AbstractNestRepositoryBundle;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.BundleUtils;
import saker.nest.bundle.ExternalArchive;
import saker.nest.bundle.ExternalArchiveKey;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.JarNestRepositoryBundle;
import saker.nest.bundle.JarNestRepositoryBundleImpl;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.storage.LocalBundleStorageView.InstallResult;
import saker.nest.exc.BundleLoadingFailedException;
import saker.nest.exc.BundleStorageInitializationException;
import saker.nest.exc.ExternalArchiveLoadingFailedException;
import saker.nest.exc.InvalidNestBundleException;
import testing.saker.nest.TestFlag;

public class LocalBundleStorage extends AbstractBundleStorage {
	private static final Comparator<String> COMPARATOR_REVERSE_VERSION_NUMBER = Collections
			.reverseOrder(BundleIdentifier::compareVersionNumbers);

	public static class LocalStorageKey extends AbstractStorageKey implements Externalizable {
		private static final long serialVersionUID = 1L;

		//store the file provider key in order to detect changes when the build was moved between PCs
		protected RootFileProviderKey fileProviderKey;
		protected SakerPath storageDirectory;

		/**
		 * For {@link Externalizable}.
		 */
		public LocalStorageKey() {
		}

		private LocalStorageKey(SakerPath storageDirectory) {
			this.fileProviderKey = LocalFileProvider.getProviderKeyStatic();
			this.storageDirectory = storageDirectory;
		}

		public static AbstractStorageKey create(NestRepositoryImpl repository, Map<String, String> userparams) {
			String rootparam = userparams.get(LocalBundleStorageView.PARAMETER_ROOT);
			SakerPath storagedir;
			if (rootparam == null) {
				storagedir = SakerPath.valueOf(repository.getRepositoryStorageDirectory()
						.resolve(LocalBundleStorageView.DEFAULT_STORAGE_NAME));
			} else {
				Path rootpath = Paths.get(rootparam);
				if (!rootpath.isAbsolute()) {
					rootpath = repository.getRepositoryStorageDirectory().resolve(rootpath);
				}
				storagedir = SakerPath.valueOf(rootpath);
			}
			return new LocalStorageKey(storagedir);
		}

		@Override
		public AbstractBundleStorage getStorage(NestRepositoryImpl repository) {
			return new LocalBundleStorage(this, repository);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(fileProviderKey);
			out.writeObject(storageDirectory);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			fileProviderKey = SerialUtils.readExternalObject(in);
			storageDirectory = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fileProviderKey == null) ? 0 : fileProviderKey.hashCode());
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
			LocalStorageKey other = (LocalStorageKey) obj;
			if (fileProviderKey == null) {
				if (other.fileProviderKey != null)
					return false;
			} else if (!fileProviderKey.equals(other.fileProviderKey))
				return false;
			if (storageDirectory == null) {
				if (other.storageDirectory != null)
					return false;
			} else if (!storageDirectory.equals(other.storageDirectory))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "["
					+ (storageDirectory != null ? "storageDirectory=" + storageDirectory : "") + "]";
		}
	}

	private static final String BUNDLES_DIRECTORY_NAME = "bundles";
	private static final String TEMP_DIRECTORY_NAME = "temp";
	private static final String PENDING_DIRECTORY_NAME = "pending";
	private static final String BUNDLE_STORAGE_DIRECTORY_NAME = "bundle_storage";
	private static final String BUNDLE_LIB_STORAGE_DIRECTORY_NAME = "bundle_lib_storage";

	private static final int INFO_SERIALIZATION_VERSION = 1;

	private static final int READLOCK_REGION_LENGTH = 4;
	private static final int LOCKFILE_STATE_DATA_LENGTH = 1024 * 1024 * 1024;
	private static final Random LOCK_OFFSET_RANDOMER = new SecureRandom();

	private static final Pattern PATTERN_BUNDLE_PENDING_VERSION_FILE_NAME = Pattern
			.compile("(.*)\\.b([1-9a-fA-F][0-9a-fA-F]*)\\.jar");

	private final LocalStorageKey storageKey;
	private final Path storageDirectory;

	private final Path bundlesDirectory;
	private final Path pendingDirectory;
	private final Path tempDirectory;
	private final Path infoFile;

	private final FileChannel infoFileChannel;
	private FileLock infoFileReadLock;

	private final ConcurrentNavigableMap<BundleIdentifier, Object> bundleLoadLocks = new ConcurrentSkipListMap<>();
	private final ConcurrentNavigableMap<BundleIdentifier, AbstractNestRepositoryBundle> loadedBundles = new ConcurrentSkipListMap<>();

	private final ConcurrentNavigableMap<BundleHashKey, Object> remoteBundleLoadLocks = new ConcurrentSkipListMap<>();
	private final ConcurrentNavigableMap<BundleHashKey, AbstractNestRepositoryBundle> remoteLoadedBundles = new ConcurrentSkipListMap<>();

	//XXX make this use weak references, and run a collector GC thread for cleaning up
	private final ConcurrentPrependAccumulator<AbstractNestRepositoryBundle> allLoadedBundles = new ConcurrentPrependAccumulator<>();

	private ConcurrentNavigableMap<BundleIdentifier, BundleInfoState> bundleInfoStates = new ConcurrentSkipListMap<>();
	private final ConcurrentNavigableMap<BundleIdentifier, PendingBundleInfoState> pendingBundleInfoStates = new ConcurrentSkipListMap<>();

	private final Object detectChangeLock = new Object();
	private UUID storageStateIdentity = UUID.randomUUID();
	private final NestRepositoryImpl repository;

	public LocalBundleStorage(LocalStorageKey storagekey, NestRepositoryImpl repository) {
		this.storageKey = storagekey;
		this.repository = repository;
		this.storageDirectory = LocalFileProvider.toRealPath(storagekey.storageDirectory);

		pendingDirectory = storageDirectory.resolve(PENDING_DIRECTORY_NAME);
		bundlesDirectory = storageDirectory.resolve(BUNDLES_DIRECTORY_NAME);
		tempDirectory = storageDirectory.resolve(TEMP_DIRECTORY_NAME);
		Path infofiletmp = bundlesDirectory.resolve("storage.info");

		FileChannel channeltoclose = null;
		Throwable exc = null;
		try {
			Files.createDirectories(bundlesDirectory);
			channeltoclose = FileChannel.open(infofiletmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.READ);
			this.infoFile = infofiletmp.toRealPath();
			this.infoFileChannel = channeltoclose;
			initializeFromConstructor(channeltoclose);
			channeltoclose = null;
		} catch (IOException e) {
			BundleStorageInitializationException initexc = new BundleStorageInitializationException(
					"Failed to initialize storage at: " + storageDirectory, e);
			exc = initexc;
			throw initexc;
		} catch (Throwable e) {
			exc = e;
			throw e;
		} finally {
			//if the locking fails, we need to close the opened channel
			IOException closeexc = IOUtils.closeExc(channeltoclose);
			if (closeexc != null) {
				if (exc == null) {
					throw new BundleStorageInitializationException(
							"Failed to initialize storage at: " + storageDirectory, closeexc);
				} else {
					exc.addSuppressed(closeexc);
				}
			}
		}
	}

	@Override
	public String getType() {
		return ConfiguredRepositoryStorage.STORAGE_TYPE_LOCAL;
	}

	@Override
	public AbstractStorageKey getStorageKey() {
		return storageKey;
	}

	@Override
	public Path getBundleStoragePath(NestRepositoryBundle bundle) {
		return storageDirectory.resolve(BUNDLE_STORAGE_DIRECTORY_NAME).resolve(bundle.getBundleIdentifier().toString());
	}

	@Override
	public Path getBundleLibStoragePath(NestRepositoryBundle bundle) {
		return storageDirectory.resolve(BUNDLE_LIB_STORAGE_DIRECTORY_NAME)
				.resolve(bundle.getBundleIdentifier().toString());
	}

	@Override
	public AbstractBundleStorageView newStorageView(StorageViewEnvironment viewenvironment) {
		Object sharedaccesskey = null;
		boolean remotecluster = false;
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_015) {
			sharedaccesskey = getSharedAccessKey(repository, viewenvironment.getUserParameters());
			remotecluster = viewenvironment.isRemoteCluster();
			if (remotecluster) {
				LocalStorageSharedAccessor accessor = (LocalStorageSharedAccessor) viewenvironment
						.getSharedObject(sharedaccesskey);
				if (TestFlag.ENABLED && accessor == null) {
					throw new AssertionError(sharedaccesskey);
				}
				return new RemoteMirrorLocalBundleStorageViewImpl(viewenvironment, sharedaccesskey);
			}
		}
		LocalBundleStorageViewImpl result;
		synchronized (detectChangeLock) {
			discoverPendingBundles(reduceInterestedPendingBundles(getPendingBundles()));
			result = new LocalBundleStorageViewImpl();
		}
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_015) {
			if (!remotecluster) {
				viewenvironment.setSharedObject(sharedaccesskey, new LocalStorageSharedAccessorImpl(result));
			}
		}
		return result;
	}

	@Override
	public void close() throws IOException {
		IOException exc = null;
		exc = IOUtils.closeExc(exc, allLoadedBundles.clearAndIterator());
		exc = IOUtils.closeExc(exc, infoFileReadLock, infoFileChannel);
		IOUtils.throwExc(exc);
	}

	private void initializeFromConstructor(FileChannel infofilechannel) throws IOException {
		if (tryInitializeWithHandlingPending(infofilechannel)) {
			return;
		}
		this.infoFileReadLock = getRandomInfoFileReadLock(infofilechannel);
		boolean infofileused = readValidateInfoFile();
		if (!infofileused) {
			discoverBundlesInBundlesDirectory();
		}
		initializePendingBundles();
	}

	private void initializePendingBundles() throws IOException {
		NavigableMap<BundleIdentifier, NavigableMap<Integer, Path>> pendingbundles = getPendingBundles();
		if (pendingbundles.isEmpty()) {
			return;
		}
		for (Entry<BundleIdentifier, NavigableMap<Integer, Path>> entry : pendingbundles.entrySet()) {
			Path lastbundle = entry.getValue().lastEntry().getValue();
			BundleIdentifier entrybundleid = entry.getKey();
			BundleInformation bundleinfo;
			try (InputStream lastbundlein = Files.newInputStream(lastbundle);
					JarInputStream jaris = new JarInputStream(lastbundlein)) {
				bundleinfo = new BundleInformation(jaris);
			} catch (IOException | InvalidNestBundleException e) {
				pendingBundleInfoStates.put(entrybundleid, new PendingBundleInfoState(lastbundle, e));
				continue;
			}
			BundleIdentifier infobundleid = bundleinfo.getBundleIdentifier();
			if (!infobundleid.equals(entrybundleid)) {
				pendingBundleInfoStates.put(entrybundleid,
						new PendingBundleInfoState(lastbundle,
								new InvalidNestBundleException(
										"Bundle identifier mismatch between manifest and exported bundle id: "
												+ infobundleid + " - " + entrybundleid)));
				continue;
			}
			pendingBundleInfoStates.put(entrybundleid,
					new PendingBundleInfoState(lastbundle, getTaskStringNames(bundleinfo)));
			bundleInfoStates.remove(entrybundleid);
		}
	}

	private boolean tryInitializeWithHandlingPending(FileChannel infofilechannel) throws IOException {
		FileLock readlock;
		try (FileLock fulllock = infofilechannel.tryLock(0, Long.MAX_VALUE - READLOCK_REGION_LENGTH, false)) {
			if (fulllock == null) {
				return false;
			}
			readlock = infofilechannel.tryLock(Long.MAX_VALUE - READLOCK_REGION_LENGTH, READLOCK_REGION_LENGTH, false);
			if (readlock == null) {
				return false;
			}
		} catch (IOException | OverlappingFileLockException e) {
			//failed to lock the whole file
			return false;
		}
		try {
			this.infoFileReadLock = readlock;
			initializeWithFullLock();
			readlock = null;
			return true;
		} finally {
			if (readlock != null) {
				this.infoFileReadLock = null;
				IOUtils.close(readlock);
			}
		}
	}

	private void initializeWithFullLock() {
		if (!readValidateInfoFile()) {
			//failed to read the info file data, enumerate the jars in the bundle directory
			discoverBundlesInBundlesDirectory();
		}
		boolean installedany = installPendingBundles(getPendingBundles());
		if (installedany) {
			writeInfoFileFullLocked();
		}
	}

	private void discoverBundleVersionedDirectory(Path dir, BundleIdentifier bundlename,
			String expectedversionqualifier) {
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
			Iterator<Path> it = ds.iterator();
			while (it.hasNext()) {
				Path filepath = it.next();
				String fnamestr = filepath.getFileName().toString();
				if (!FileUtils.hasExtensionIgnoreCase(fnamestr, "jar")) {
					continue;
				}
				//remove the extension
				String bundlefnamepart = fnamestr.substring(0, fnamestr.length() - 4);
				BundleIdentifier jarbundleid;
				try {
					jarbundleid = BundleIdentifier.valueOf(bundlefnamepart);
				} catch (IllegalArgumentException e) {
					//invalid bundle id
					continue;
				}
				if (!bundlename.getName().equals(jarbundleid.getName())) {
					continue;
				}
				if (!Objects.equals(jarbundleid.getVersionQualifier(), expectedversionqualifier)) {
					continue;
				}
				//found a bundle
				try (InputStream is = Files.newInputStream(filepath);
						JarInputStream jaris = new JarInputStream(is)) {
					BundleInformation bundleinfo = new BundleInformation(jaris);
					BundleIdentifier infobundleid = bundleinfo.getBundleIdentifier();
					if (!infobundleid.equals(jarbundleid)) {
						//XXX notify user
						System.err.println("Bundle id mismatch: " + infobundleid + " - " + jarbundleid);
						continue;
					}
					BasicFileAttributes jarattrs = Files.readAttributes(filepath, BasicFileAttributes.class);
					BundleInfoState infostate = new BundleInfoState(new FileEntry(jarattrs),
							getTaskStringNames(bundleinfo));
					BundleInfoState prev = bundleInfoStates.putIfAbsent(jarbundleid, infostate);
					if (prev != null) {
						//this shouldn't normally happen, but can if the file system is case sensitive and 
						//has different capilatization of the extension 
						//XXX notify user
					}
					//TODO store the following exceptions and notify the user
				} catch (IOException e) {
					//failed to open the jar
					e.printStackTrace();
				} catch (InvalidNestBundleException e) {
					//illegally formatted bundle. shouldn't really happen but handle just in case
					e.printStackTrace();
				}
			}
		} catch (IOException | DirectoryIteratorException e) {
			//failed to enumerate
		}
	}

	private void discoverBundleDirectory(Path dir, BundleIdentifier bundlename) {
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
			Iterator<Path> it = ds.iterator();
			while (it.hasNext()) {
				Path filepath = it.next();
				String fnamestr = filepath.getFileName().toString();
				if (Files.isDirectory(filepath)) {
					if ("v".equals(fnamestr)) {
						//directory for non versioned bundles
						discoverBundleVersionedDirectory(filepath, bundlename, null);
					} else if (BundleIdentifier.isValidVersionQualifier(fnamestr)) {
						//directory that contains versioned bundles
						discoverBundleVersionedDirectory(filepath, bundlename, fnamestr);
					} //else not a valid version directory
				}
			}
		} catch (IOException | DirectoryIteratorException e) {
			//failed to enumerate
		}
	}

	private void discoverBundlesInBundlesDirectory() {
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(bundlesDirectory)) {
			Iterator<Path> it = ds.iterator();
			while (it.hasNext()) {
				Path filepath = it.next();
				String fnamestr = filepath.getFileName().toString();
				if (Files.isDirectory(filepath)) {
					BundleIdentifier directorybundleid;
					try {
						directorybundleid = BundleIdentifier.valueOf(fnamestr);
					} catch (IllegalArgumentException e) {
						continue;
					}
					discoverBundleDirectory(filepath, directorybundleid);
				}
			}
		} catch (IOException | DirectoryIteratorException e) {
			//failed to enumerate the directory
		}
	}

	private boolean readValidateInfoFile() {
		try {
			if (infoFileChannel.size() <= 0) {
				return false;
			}
			infoFileChannel.position(0);
			try (ObjectInputStream in = new ObjectInputStream(new UnsyncBufferedInputStream(
					StreamUtils.closeProtectedInputStream(Channels.newInputStream(infoFileChannel)))) {
				@Override
				protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
					try {
						return super.resolveClass(desc);
					} catch (ClassNotFoundException e) {
						return Class.forName(desc.getName(), false, LocalBundleStorage.class.getClassLoader());
					}
				}
			}) {
				int serialversion = in.readInt();
				if (serialversion != 1) {
					return false;
				}
				bundleInfoStates = SerialUtils.readExternalMap(new ConcurrentSkipListMap<>(), in,
						SerialUtils::readExternalObject, i -> {
							BundleInfoState bis = new BundleInfoState();
							bis.readExternal(i);
							return bis;
						});
			}
		} catch (IOException | ClassNotFoundException e) {
			return false;
		}
		for (Iterator<Entry<BundleIdentifier, BundleInfoState>> it = bundleInfoStates.entrySet().iterator(); it
				.hasNext();) {
			Entry<BundleIdentifier, BundleInfoState> entry = it.next();
			BundleIdentifier bid = entry.getKey();
			BundleInfoState state = entry.getValue();
			Path bundlejarpath = getInstalledBundleJarPath(bid);
			try {
				BasicFileAttributes attrs = Files.readAttributes(bundlejarpath, BasicFileAttributes.class);
				if (!FileAttributesContentDescriptor.isChanged(attrs, state.jarAttrs)) {
					//the jar stayed the same as expected based on the information
					continue;
				}
				//the bundle JAR was changed meanwhile

				bundle_reloader:
				try (InputStream bundleis = Files.newInputStream(bundlejarpath);
						JarInputStream jis = new JarInputStream(bundleis)) {
					BundleInformation binfo = new BundleInformation(jis);
					if (!binfo.getBundleIdentifier().equals(bid)) {
						break bundle_reloader;
					}
					state.set(new FileEntry(attrs), getTaskStringNames(binfo));
					continue;
				} catch (InvalidNestBundleException e) {
					break bundle_reloader;
				}
				// in case of IO exception, the enclosing catch { } catches is
			} catch (IOException e) {
			}
			it.remove();
			continue;
		}
		return true;
	}

	private void writeInfoFileFullLocked() {
		try {
			infoFileChannel.position(0);
			try (ObjectOutputStream out = new ObjectOutputStream(new UnsyncBufferedOutputStream(
					StreamUtils.closeProtectedOutputStream(Channels.newOutputStream(infoFileChannel))))) {
				out.writeInt(INFO_SERIALIZATION_VERSION);
				SerialUtils.writeExternalMap(out, bundleInfoStates, ObjectOutput::writeObject,
						(o1, bis) -> bis.writeExternal(o1));
			}
		} catch (IOException ignored) {
			//exception is ignored. we can handle the absence of the info file somewhat gracefully
		}
	}

	private static long randomReadLockOffset() {
		while (true) {
			long offset = LOCK_OFFSET_RANDOMER.nextLong();
			if (offset >= LOCKFILE_STATE_DATA_LENGTH && offset <= Long.MAX_VALUE - READLOCK_REGION_LENGTH) {
				return offset;
			}
		}
	}

	@SuppressWarnings("try")
	private FileLock getRandomInfoFileReadLock(FileChannel channel) throws IOException {
		try {
			FileLock tried = channel.tryLock(randomReadLockOffset(), READLOCK_REGION_LENGTH, false);
			if (tried != null) {
				return tried;
			}
		} catch (OverlappingFileLockException e) {
		}
		//cross-classloader synchronize on an interned string
		synchronized (getInfoFileLockingSynchronizeObject()) {
			try (FileLock startlock = channel.lock(0, READLOCK_REGION_LENGTH, false)) {
				//start lock was acquired, we must be able to lock the random region, as no writing is done right now
				for (int i = 0; i < 3; i++) {
					try {
						FileLock tried = channel.tryLock(randomReadLockOffset(), READLOCK_REGION_LENGTH, false);
						if (tried != null) {
							return tried;
						}
					} catch (OverlappingFileLockException e) {
					}
				}
			} catch (OverlappingFileLockException e) {
			}
		}
		throw new BundleStorageInitializationException("Failed to lock info file for storage at: " + storageDirectory);
	}

	private Object getInfoFileLockingSynchronizeObject() {
		return (getClass().getName() + "/" + infoFile).intern();
	}

	private boolean installPendingBundles(NavigableMap<BundleIdentifier, NavigableMap<Integer, Path>> pendingbundles) {
		int installedcount = 0;
		outer:
		for (Entry<BundleIdentifier, NavigableMap<Integer, Path>> entry : pendingbundles.entrySet()) {
			BundleIdentifier entrybundleid = entry.getKey();
			pendingBundleInfoStates.remove(entrybundleid);

			//remove all pending bundles except the latest
			for (Iterator<Path> it = entry.getValue().values().iterator(); it.hasNext();) {
				Path bundlepath = it.next();
				if (it.hasNext()) {
					try {
						Files.deleteIfExists(bundlepath);
					} catch (IOException e) {
						//failed to delete a bundle that has lower version than the one we want to export.
						//do not install the bundle, as the next try will probably overwrite it
						continue outer;
					}
					it.remove();
				}
			}
			Path lastbundle = entry.getValue().lastEntry().getValue();
			BundleInformation bundleinfo;
			try (InputStream lastbundlein = Files.newInputStream(lastbundle);
					JarInputStream jaris = new JarInputStream(lastbundlein)) {
				bundleinfo = new BundleInformation(jaris);
			} catch (IOException | InvalidNestBundleException e) {
				pendingBundleInfoStates.put(entrybundleid, new PendingBundleInfoState(lastbundle, e));
				continue;
			}
			BundleIdentifier infobundleid = bundleinfo.getBundleIdentifier();
			if (!infobundleid.equals(entrybundleid)) {
				pendingBundleInfoStates.put(entrybundleid,
						new PendingBundleInfoState(lastbundle,
								new InvalidNestBundleException(
										"Bundle identifier mismatch between manifest and exported bundle id: "
												+ infobundleid + " - " + entrybundleid)));
				continue;
			}
			pendingBundleInfoStates.put(entrybundleid,
					new PendingBundleInfoState(lastbundle, getTaskStringNames(bundleinfo)));
			Path installpath = getInstalledBundleJarPath(entrybundleid);
			try {
				Files.createDirectories(installpath.getParent());
			} catch (IOException e) {
				// can't create a parent directory.
				// failed to install the bundle
				// print the exception, but we can't handle it otherwise
				e.printStackTrace();
				continue;
			}
			BasicFileAttributes attrs;
			try {
				Files.move(lastbundle, installpath, StandardCopyOption.REPLACE_EXISTING);
				pendingBundleInfoStates.remove(entrybundleid);
				attrs = Files.readAttributes(installpath, BasicFileAttributes.class);
			} catch (IOException e) {
				// failed to move or read attributes
				// if the file move threw an exception, installing failed due to some concurrent operation
				// if read attributes failed, then the moved file was deleted concurrently
				//    either way, the installation is considered to be failed as other agents intervened
				// print the exception for some information, but doesn't need other handling
				e.printStackTrace();
				continue;
			}
			//overwrite
			bundleInfoStates.put(entrybundleid,
					new BundleInfoState(new FileEntry(attrs), getTaskStringNames(bundleinfo)));
			++installedcount;
		}
		return installedcount > 0;
	}

	private NavigableMap<BundleIdentifier, Entry<Integer, Path>> reduceInterestedPendingBundles(
			NavigableMap<BundleIdentifier, NavigableMap<Integer, Path>> pendingbundles) {
		if (pendingbundles.isEmpty()) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<BundleIdentifier, Entry<Integer, Path>> result = new TreeMap<>();
		for (Entry<BundleIdentifier, NavigableMap<Integer, Path>> entry : pendingbundles.entrySet()) {
			BundleIdentifier entrybundleid = entry.getKey();
			PendingBundleInfoState currentinfostate = pendingBundleInfoStates.get(entrybundleid);
			Entry<Integer, Path> lastentry = entry.getValue().lastEntry();
			if (currentinfostate != null) {
				Path lastbundle = lastentry.getValue();
				if (currentinfostate.bundlePath.equals(lastbundle)) {
					//no new version was added
					continue;
				}
			}
			result.put(entrybundleid, lastentry);
		}
		return result;
	}

	private void discoverPendingBundles(NavigableMap<BundleIdentifier, Entry<Integer, Path>> reducedpendingbundles) {
		if (reducedpendingbundles.isEmpty()) {
			return;
		}
		storageStateIdentity = UUID.randomUUID();
		for (Entry<BundleIdentifier, Entry<Integer, Path>> entry : reducedpendingbundles.entrySet()) {
			BundleIdentifier entrybundleid = entry.getKey();
			PendingBundleInfoState currentinfostate = pendingBundleInfoStates.get(entrybundleid);
			Entry<Integer, Path> pendingbundleentry = entry.getValue();
			Path lastbundle = pendingbundleentry.getValue();
			if (currentinfostate != null) {
				if (currentinfostate.bundlePath.equals(lastbundle)) {
					//no new version was added
					continue;
				}
			}

			BundleInformation bundleinfo;
			try (InputStream lastbundlein = Files.newInputStream(lastbundle);
					JarInputStream jaris = new JarInputStream(lastbundlein)) {
				bundleinfo = new BundleInformation(jaris);
			} catch (IOException | InvalidNestBundleException e) {
				pendingBundleInfoStates.put(entrybundleid, new PendingBundleInfoState(lastbundle, e));
				continue;
			}
			BundleIdentifier infobundleid = bundleinfo.getBundleIdentifier();
			if (!infobundleid.equals(entrybundleid)) {
				pendingBundleInfoStates.put(entrybundleid,
						new PendingBundleInfoState(lastbundle,
								new InvalidNestBundleException(
										"Bundle identifier mismatch between manifest and exported bundle id: "
												+ infobundleid + " - " + entrybundleid)));
				continue;
			}
			pendingBundleInfoStates.put(entrybundleid,
					new PendingBundleInfoState(lastbundle, getTaskStringNames(bundleinfo)));
		}

	}

	private NavigableMap<BundleIdentifier, NavigableMap<Integer, Path>> getPendingBundles() {
		NavigableMap<String, ? extends FileEntry> pendingdirentries;
		try {
			pendingdirentries = LocalFileProvider.getInstance().getDirectoryEntries(pendingDirectory);
		} catch (IOException e1) {
			//failed to enumerate the directory, return empty map
			return Collections.emptyNavigableMap();
		}
		NavigableMap<BundleIdentifier, NavigableMap<Integer, Path>> pendingbundles = new TreeMap<>();
		for (Entry<String, ? extends FileEntry> entry : pendingdirentries.entrySet()) {
			if (!entry.getValue().isRegularFile()) {
				continue;
			}
			String fname = entry.getKey();
			Matcher matcher = PATTERN_BUNDLE_PENDING_VERSION_FILE_NAME.matcher(fname);
			if (!matcher.matches()) {
				continue;
			}
			int version = Integer.parseUnsignedInt(matcher.group(2), 16);
			BundleIdentifier bundleid;
			try {
				bundleid = BundleIdentifier.valueOf(matcher.group(1));
			} catch (IllegalArgumentException ignored) {
				//if the file is not an actual bundle, some excess file, ignore
				continue;
			}
			Path prev = pendingbundles.computeIfAbsent(bundleid, Functionals.treeMapComputer()).put(version,
					pendingDirectory.resolve(fname));
			if (prev != null) {
				//some conflict. this can not happen, as the paths should be unique
				//however, sometimes it could, when multiple semantically same bundles are present
				//e.g.
				//    bundle.name-q1-q2-v1.b1.jar
				//    bundle.name-q2-q1-v1.b1.jar
				//    bundle.name-q1-q1-q2-v1.b1.jar
				//    BUNDLE.name-q1-q2-v1.b1.jar
				//all of the above parse to the same bundle identifier
				//ignore it, use the first one in alphabetical order (since the directory entries are ordered
				//(note that when bundles are properly installed, this doesn't happen, only if manually added by the user)
				continue;
			}
		}
		return pendingbundles;
	}

	private Path getInstalledBundleJarPath(BundleIdentifier bundleid) {
		return BundleUtils.getVersionedBundleJarPath(bundlesDirectory, bundleid);
	}

	private static void checkOnlySameBundleNamesForTask(NavigableSet<BundleIdentifier> bundles, TaskName tn) {
		if (bundles.isEmpty()) {
			return;
		}
		Iterator<BundleIdentifier> it = bundles.iterator();
		String expectedname = it.next().getName();
		while (it.hasNext()) {
			String nextname = it.next().getName();
			if (!expectedname.equals(nextname)) {
				throw new TaskNotFoundException("Multiple different bundle names contain the task: " + tn + " in "
						+ expectedname + " and " + nextname, tn);
			}
		}
	}

	private static TreeSet<String> getTaskStringNames(BundleInformation bundleinfo) {
		TreeSet<String> tnames = new TreeSet<>();
		for (TaskName tn : bundleinfo.getTaskClassNames().navigableKeySet()) {
			tnames.add(tn.getName());
		}
		return tnames;
	}

	private static Object getSharedAccessKey(NestRepositoryImpl repository, Map<String, String> userparams) {
		String rootparam = userparams.get(LocalBundleStorageView.PARAMETER_ROOT);
		SakerPath accesskeypath;
		if (rootparam == null) {
			accesskeypath = null;
		} else {
			Path rootpath = Paths.get(rootparam);
			if (!rootpath.isAbsolute()) {
				rootpath = repository.getRepositoryStorageDirectory().resolve(rootpath);
			}
			accesskeypath = SakerPath.valueOf(rootpath);
		}

		// use a type that can be serialized and deserialized independently from repository classes
		return TaskIdentifier.builder(LocalBundleStorage.class.getName()).field("scope", "remote-accessor")
				.field("path", accesskeypath).build();
	}

	private static void updateHashWithStorageKey(MessageDigest digest, LocalStorageKey storagekey) {
		digest.update((ConfiguredRepositoryStorage.STORAGE_TYPE_LOCAL + ":(" + storagekey.fileProviderKey.getUUID()
				+ ":" + storagekey.storageDirectory + ")").getBytes(StandardCharsets.UTF_8));
	}

	public static class BundleHashKey implements Externalizable, Comparable<BundleHashKey> {
		private static final long serialVersionUID = 1L;

		protected BundleIdentifier bundleId;
		protected String hash;

		/**
		 * For {@link Externalizable}.
		 */
		public BundleHashKey() {
		}

		public BundleHashKey(BundleIdentifier bundleId, String hash) {
			this.bundleId = bundleId;
			this.hash = hash;
		}

		public BundleHashKey(NestRepositoryBundle bundle) {
			this(bundle.getBundleIdentifier(), StringUtils.toHexString(bundle.getHash()));
		}

		@Override
		public int compareTo(BundleHashKey o) {
			int cmp = bundleId.compareTo(o.bundleId);
			if (cmp != 0) {
				return cmp;
			}
			cmp = this.hash.compareTo(o.hash);
			return cmp;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(bundleId);
			out.writeObject(hash);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			bundleId = SerialUtils.readExternalObject(in);
			hash = SerialUtils.readExternalObject(in);
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((bundleId == null) ? 0 : bundleId.hashCode());
			result = prime * result + ((hash == null) ? 0 : hash.hashCode());
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
			BundleHashKey other = (BundleHashKey) obj;
			if (bundleId == null) {
				if (other.bundleId != null)
					return false;
			} else if (!bundleId.equals(other.bundleId))
				return false;
			if (hash == null) {
				if (other.hash != null)
					return false;
			} else if (!hash.equals(other.hash))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "BundleHashKey[" + bundleId + " : " + hash + "]";
		}

	}

	public interface LocalStorageSharedAccessor {
		public InstallResult install(StreamWritable bundlecontents)
				throws NullPointerException, IOException, UnsupportedOperationException, InvalidNestBundleException;

		@RMICacheResult
		public LocalStorageKey getLocalStorageKey();

		@RMISerialize
		public UUID getStorageState();

		@RMIWrap(RMITreeSetSerializeElementWrapper.class)
		public Set<? extends BundleIdentifier> lookupBundleVersions(BundleIdentifier bundleid);

		@RMIWrap(BundleIdentifierVersionMapRMIWrapper.class)
		public Map<String, ? extends NavigableSet<? extends BundleIdentifier>> lookupBundleIdentifiers(
				String bundlename);

		public BundleHashKey lookupTaskBundle(TaskName taskname)
				throws NullPointerException, TaskNotFoundException, IOException;

		public BundleHashKey getBundleHash(BundleIdentifier bundle)
				throws NullPointerException, BundleLoadingFailedException;

		public void writeBundleContentsTo(BundleHashKey key, ByteSink output)
				throws IOException, BundleLoadingFailedException;
	}

	public static class BundleIdentifierVersionMapRMIWrapper implements RMIWrapper {
		private Map<String, ? extends Set<? extends BundleIdentifier>> map;

		public BundleIdentifierVersionMapRMIWrapper() {
		}

		public BundleIdentifierVersionMapRMIWrapper(Map<String, ? extends Set<? extends BundleIdentifier>> map) {
			this.map = map;
		}

		@Override
		public void writeWrapped(RMIObjectOutput out) throws IOException {
			SerialUtils.writeExternalMap(out, map, SerialUtils::writeExternalObject,
					SerialUtils::writeExternalCollection);
		}

		@Override
		public void readWrapped(RMIObjectInput in) throws IOException, ClassNotFoundException {
			map = SerialUtils.readExternalMap(new TreeMap<>(COMPARATOR_REVERSE_VERSION_NUMBER), in,
					SerialUtils::readExternalObject, SerialUtils::readExternalImmutableNavigableSet);
		}

		@Override
		public Object resolveWrapped() {
			return map;
		}

		@Override
		public Object getWrappedObject() {
			throw new UnsupportedOperationException();
		}

	}

	@RMIWriter(RemoteRMIObjectWriteHandler.class)
	private static class LocalStorageSharedAccessorImpl implements LocalStorageSharedAccessor {
		private final LocalBundleStorageViewImpl storageView;

		public LocalStorageSharedAccessorImpl(LocalBundleStorageViewImpl storageimpl) {
			this.storageView = storageimpl;
		}

		@Override
		public LocalStorageKey getLocalStorageKey() {
			return storageView.getStorage().storageKey;
		}

		@Override
		public InstallResult install(StreamWritable bundlecontents)
				throws NullPointerException, IOException, UnsupportedOperationException, InvalidNestBundleException {
			return storageView.install(bundlecontents);
		}

		@Override
		public UUID getStorageState() {
			return storageView.getStorage().storageStateIdentity;
		}

		@Override
		public Set<? extends BundleIdentifier> lookupBundleVersions(BundleIdentifier bundleid) {
			return storageView.lookupBundleVersions(bundleid);
		}

		@Override
		public Map<String, ? extends NavigableSet<? extends BundleIdentifier>> lookupBundleIdentifiers(
				String bundlename) {
			return storageView.lookupBundleIdentifiers(bundlename);
		}

		@Override
		public BundleHashKey lookupTaskBundle(TaskName taskname)
				throws NullPointerException, TaskNotFoundException, IOException {
			return new BundleHashKey(storageView.lookupTaskBundle(taskname));
		}

		@Override
		public BundleHashKey getBundleHash(BundleIdentifier bundle)
				throws NullPointerException, BundleLoadingFailedException {
			return new BundleHashKey(storageView.getBundle(bundle));
		}

		@Override
		public void writeBundleContentsTo(BundleHashKey key, ByteSink output)
				throws IOException, BundleLoadingFailedException {
			JarNestRepositoryBundle bundle = (JarNestRepositoryBundle) storageView.getBundle(key.bundleId);
			if (!StringUtils.toHexString(bundle.getHash()).equals(key.hash)) {
				throw new BundleLoadingFailedException("Bundle not found: " + key.bundleId + " with hash: " + key.hash);
			}
			LocalFileProvider.getInstance().writeTo(bundle.getJarPath(), output);
		}
	}

	private static class SimpleInstallResult implements InstallResult {
		private BundleIdentifier bundleIdentifier;
		private byte[] hash;

		public SimpleInstallResult(BundleIdentifier bundleIdentifier, byte[] hash) {
			this.bundleIdentifier = bundleIdentifier;
			this.hash = hash;
		}

		@Override
		public BundleIdentifier getBundleIdentifier() {
			return bundleIdentifier;
		}

		@Override
		public byte[] getBundleHash() {
			return hash;
		}
	}

	private static class BundleInfoState {
		private FileEntry jarAttrs;
		private NavigableSet<String> taskNames;

		/**
		 * For externalization.
		 */
		public BundleInfoState() {
		}

		public BundleInfoState(FileEntry jarAttrs, NavigableSet<String> taskNames) {
			this.jarAttrs = jarAttrs;
			this.taskNames = taskNames;
		}

		public void set(FileEntry jarAttrs, NavigableSet<String> taskNames) {
			this.jarAttrs = jarAttrs;
			this.taskNames = taskNames;
		}

		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(jarAttrs);
			SerialUtils.writeExternalCollection(out, taskNames);
		}

		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			jarAttrs = (FileEntry) in.readObject();
			taskNames = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[taskNames=" + taskNames + "]";
		}
	}

	private static class PendingBundleInfoState {
		protected Path bundlePath;
		protected transient NavigableSet<String> taskNames;
		protected transient Throwable openFailException;

		public PendingBundleInfoState(Path bundlePath, NavigableSet<String> taskNames) {
			this.bundlePath = bundlePath;
			this.taskNames = taskNames;
		}

		public PendingBundleInfoState(Path bundlePath, Throwable openFailException) {
			this.bundlePath = bundlePath;
			this.openFailException = openFailException;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + bundlePath + " : " + taskNames + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((bundlePath == null) ? 0 : bundlePath.hashCode());
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
			PendingBundleInfoState other = (PendingBundleInfoState) obj;
			if (bundlePath == null) {
				if (other.bundlePath != null)
					return false;
			} else if (!bundlePath.equals(other.bundlePath))
				return false;
			return true;
		}

	}

	private static final class LocalBundleStorageViewKeyImpl implements StorageViewKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private AbstractStorageKey storageKey;

		/**
		 * For {@link Externalizable}.
		 */
		public LocalBundleStorageViewKeyImpl() {
		}

		public LocalBundleStorageViewKeyImpl(AbstractStorageKey storageKey) {
			this.storageKey = storageKey;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(storageKey);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			storageKey = (AbstractStorageKey) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((storageKey == null) ? 0 : storageKey.hashCode());
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
			LocalBundleStorageViewKeyImpl other = (LocalBundleStorageViewKeyImpl) obj;
			if (storageKey == null) {
				if (other.storageKey != null)
					return false;
			} else if (!storageKey.equals(other.storageKey))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "LocalBundleStorageViewKeyImpl[storageKey=" + storageKey + "]";
		}

	}

	private abstract class BaseLocalBundleStorageViewImpl extends AbstractBundleStorageView
			implements LocalBundleStorageView {
		protected final LocalBundleStorageViewKeyImpl storageViewKey = new LocalBundleStorageViewKeyImpl(
				LocalBundleStorage.this.getStorageKey());

		@Override
		public StorageViewKey getStorageViewKey() {
			return storageViewKey;
		}

		@Override
		public void appendConfigurationUserParameters(Map<String, String> userparameters, String repositoryid,
				String storagename) {
		}

		@Override
		public LocalBundleStorage getStorage() {
			return LocalBundleStorage.this;
		}

		@Override
		public Map<? extends ExternalArchiveKey, ? extends ExternalArchive> loadExternalArchives(
				ExternalDependencyInformation depinfo)
				throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException {
			return repository.loadExternalArchives(depinfo, this);
		}
	}

	private final class RemoteMirrorLocalBundleStorageViewImpl extends BaseLocalBundleStorageViewImpl {
		private final StorageViewEnvironment viewEnvironment;
		private final Object sharedAccessKey;

		private LocalStorageSharedAccessor accessor;
		private UUID state;

		private final Path remoteDirectory = storageDirectory.resolve("remote");

		public RemoteMirrorLocalBundleStorageViewImpl(StorageViewEnvironment viewenvironment, Object sharedaccesskey) {
			this.viewEnvironment = viewenvironment;
			this.sharedAccessKey = sharedaccesskey;
			this.accessor = (LocalStorageSharedAccessor) viewenvironment.getSharedObject(sharedaccesskey);
			if (this.accessor == null) {
				throw new BundleStorageInitializationException(
						"Failed to retrieve remote accessor for local bundle storage. (" + sharedaccesskey + ")");
			}
			this.state = accessor.getStorageState();
			try {
				Files.createDirectories(remoteDirectory);
			} catch (IOException e) {
				throw new BundleStorageInitializationException("Failed to initialize storage for remote bundles.", e);
			}
		}

		@Override
		public void updateStorageViewHash(MessageDigest digest) {
			updateHashWithStorageKey(digest, accessor.getLocalStorageKey());
		}

		@Override
		public Set<? extends BundleIdentifier> lookupBundleVersions(BundleIdentifier bundleid)
				throws NullPointerException {
			// XXX cache
			return accessor.lookupBundleVersions(bundleid);
		}

		@Override
		public Map<String, ? extends NavigableSet<? extends BundleIdentifier>> lookupBundleIdentifiers(
				String bundlename) throws NullPointerException, IllegalArgumentException {
			// XXX cache
			return accessor.lookupBundleIdentifiers(bundlename);
		}

		@Override
		public InstallResult install(StreamWritable bundlecontents)
				throws NullPointerException, IOException, UnsupportedOperationException, InvalidNestBundleException {
			return accessor.install(bundlecontents);
		}

		@Override
		public Object detectChanges(ExecutionPathConfiguration pathconfig) {
			this.accessor = (LocalStorageSharedAccessor) viewEnvironment.getSharedObject(sharedAccessKey);
			UUID nstate = accessor.getStorageState();
			if (!Objects.equals(this.state, nstate)) {
				return nstate;
			}
			return null;
		}

		@Override
		public void handleChanges(ExecutionPathConfiguration pathconfig, Object detectedchanges) {
			this.state = (UUID) detectedchanges;
			//XXX clear cached things
		}

		@Override
		public NestRepositoryBundle lookupTaskBundle(TaskName taskname)
				throws NullPointerException, TaskNotFoundException, IOException {
			// XXX cache
			BundleHashKey bundle = accessor.lookupTaskBundle(taskname);
			try {
				return getBundle(bundle);
			} catch (BundleLoadingFailedException e) {
				throw new TaskNotFoundException(e, taskname);
			}
		}

		@Override
		public AbstractNestRepositoryBundle getBundle(BundleIdentifier bundleid)
				throws NullPointerException, BundleLoadingFailedException {
			// XXX cache
			return getBundle(accessor.getBundleHash(bundleid));
		}

		@Override
		public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
			//ignoreable for cluster repo
			return Collections.emptyNavigableSet();
		}

		@Override
		public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
			//ignoreable for cluster repo
			return Collections.emptyNavigableSet();
		}

		private AbstractNestRepositoryBundle getBundle(BundleHashKey key) throws BundleLoadingFailedException {
			AbstractNestRepositoryBundle got = remoteLoadedBundles.get(key);
			if (got != null) {
				return got;
			}
			synchronized (remoteBundleLoadLocks.computeIfAbsent(key, Functionals.objectComputer())) {
				got = remoteLoadedBundles.get(key);
				if (got != null) {
					return got;
				}
				Path bundledir = remoteDirectory.resolve(key.bundleId.toString());
				Path bundlepath = bundledir.resolve(key.hash + ".jar");
				try {
					Files.createDirectories(bundledir);
					if (!Files.isRegularFile(bundlepath)) {
						Path temppath = bundlepath.resolveSibling(UUID.randomUUID().toString());
						try {
							try (ByteSink outsink = LocalFileProvider.getInstance().openOutput(temppath,
									StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
								accessor.writeBundleContentsTo(key, outsink);
							}
							try {
								Files.move(temppath, bundlepath);
							} catch (IOException e) {
								//failed to move, others might've concurrently moved there
								//delete the temp file and proceed with loading
								//if the file still is not accessible at the path, an exception is thrown down the line.
								try {
									Files.deleteIfExists(temppath);
								} catch (Throwable e2) {
									e2.addSuppressed(e);
									throw e2;
								}
							}
						} catch (Throwable e) {
							//only need to delete if something failed, otherwise it was moved
							try {
								Files.deleteIfExists(temppath);
							} catch (Throwable e2) {
								e.addSuppressed(e2);
							}
							throw e;
						}
					}
					JarNestRepositoryBundleImpl result = JarNestRepositoryBundleImpl.create(LocalBundleStorage.this,
							bundlepath);
					try {
						if (!key.bundleId.equals(result.getBundleIdentifier())) {
							throw new InvalidNestBundleException("Bundle identifier mismatch for: "
									+ result.getBundleIdentifier() + " with expected: " + key.bundleId);
						}
						NestRepositoryBundle prevloaded = remoteLoadedBundles.putIfAbsent(key, result);
						if (prevloaded != null) {
							//shouldn't ever happen, as we're locking
							throw new AssertionError("Concurrency error when loading bundles.");
						}
						allLoadedBundles.add(result);
						return result;
					} catch (Throwable e) {
						IOUtils.addExc(e, IOUtils.closeExc(result));
						throw e;
					}
				} catch (IOException | InvalidNestBundleException e) {
					throw new BundleLoadingFailedException(
							"Failed to load remote bundle " + key.bundleId.toString() + " with hash: " + key.hash, e);
				}
			}
		}

	}

	private final class LocalBundleStorageViewImpl extends BaseLocalBundleStorageViewImpl {
		private NavigableMap<BundleIdentifier, BundleInfoState> bundleInfoStates = ImmutableUtils
				.makeImmutableNavigableMap(LocalBundleStorage.this.bundleInfoStates);
		private NavigableMap<BundleIdentifier, PendingBundleInfoState> pendingBundleInfoStates = ImmutableUtils
				.makeImmutableNavigableMap(LocalBundleStorage.this.pendingBundleInfoStates);

		public LocalBundleStorageViewImpl() {
		}

		@Override
		public void updateStorageViewHash(MessageDigest digest) {
			LocalStorageKey storagekey = storageKey;
			updateHashWithStorageKey(digest, storagekey);
		}

		@Override
		public NestRepositoryBundle lookupTaskBundle(TaskName taskname)
				throws NullPointerException, TaskNotFoundException {
			NavigableSet<BundleIdentifier> bundles = getBundlesForTaskName(taskname);
			if (ObjectUtils.isNullOrEmpty(bundles)) {
				throw new TaskNotFoundException("Bundle not found for task.", taskname);
			}
			checkOnlySameBundleNamesForTask(bundles, taskname);
			BundleIdentifier bundleid = BundleUtils.selectAppropriateBundleIdentifierForTask(taskname, bundles);
			if (bundleid == null) {
				throw new TaskNotFoundException("Bundle not found for task.", taskname);
			}
			try {
				NestRepositoryBundle bundle = getBundle(bundleid);
				return bundle;
			} catch (BundleLoadingFailedException e) {
				throw new TaskNotFoundException("Failed to instantiate task in bundle: " + bundleid, e, taskname);
			}
		}

		@Override
		public Set<BundleIdentifier> lookupBundleVersions(BundleIdentifier bundleid) throws NullPointerException {
			Objects.requireNonNull(bundleid, "bundle identifier");
			NavigableMap<String, BundleIdentifier> lookupres = new TreeMap<>(COMPARATOR_REVERSE_VERSION_NUMBER);
			for (BundleIdentifier b : bundleInfoStates.keySet()) {
				if (b.getName().equals(bundleid.getName())
						&& b.getBundleQualifiers().equals(bundleid.getBundleQualifiers())) {
					String vnum = b.getVersionNumber();
					if (vnum != null) {
						lookupres.put(vnum, b);
					}
				}
			}
			for (BundleIdentifier b : pendingBundleInfoStates.keySet()) {
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
		public Map<String, ? extends NavigableSet<? extends BundleIdentifier>> lookupBundleIdentifiers(
				String bundlename) throws NullPointerException, IllegalArgumentException {
			Objects.requireNonNull(bundlename, "bundle name");
			if (!BundleIdentifier.isValidBundleName(bundlename)) {
				throw new IllegalArgumentException("Invalid bundle name: " + bundlename);
			}
			Map<String, NavigableSet<BundleIdentifier>> result = new TreeMap<>(COMPARATOR_REVERSE_VERSION_NUMBER);
			for (BundleIdentifier b : bundleInfoStates.keySet()) {
				if (b.getName().equals(bundlename)) {
					String vnum = b.getVersionNumber();
					if (vnum != null) {
						result.computeIfAbsent(vnum, Functionals.treeSetComputer()).add(b);
					}
				}
			}
			for (BundleIdentifier b : pendingBundleInfoStates.keySet()) {
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
		public AbstractNestRepositoryBundle getBundle(BundleIdentifier bundleid)
				throws NullPointerException, BundleLoadingFailedException {
			Objects.requireNonNull(bundleid, "bundleid");
			AbstractNestRepositoryBundle got = loadedBundles.get(bundleid);
			if (got != null) {
				return got;
			}
			synchronized (bundleLoadLocks.computeIfAbsent(bundleid, Functionals.objectComputer())) {
				got = loadedBundles.get(bundleid);
				if (got != null) {
					return got;
				}
				Path bundlejar;
				PendingBundleInfoState pendinginfostate = pendingBundleInfoStates.get(bundleid);
				if (pendinginfostate != null) {
					if (pendinginfostate.openFailException != null) {
						throw new BundleLoadingFailedException(bundleid.toString(), pendinginfostate.openFailException);
					}
					bundlejar = pendinginfostate.bundlePath;
				} else {
					bundlejar = getInstalledBundleJarPath(bundleid);
				}
				try {
					JarNestRepositoryBundleImpl result = JarNestRepositoryBundleImpl.create(LocalBundleStorage.this,
							bundlejar);
					try {
						if (!bundleid.equals(result.getBundleIdentifier())) {
							throw new InvalidNestBundleException("Bundle identifier mismatch for: "
									+ result.getBundleIdentifier() + " with expected: " + bundleid);
						}
						NestRepositoryBundle prevloaded = loadedBundles.putIfAbsent(bundleid, result);
						if (prevloaded != null) {
							//shouldn't ever happen, as we're locking
							throw new AssertionError("Concurrency error when loading bundles.");
						}
						allLoadedBundles.add(result);
						return result;
					} catch (Throwable e) {
						IOUtils.addExc(e, IOUtils.closeExc(result));
						throw e;
					}
				} catch (IOException | InvalidNestBundleException e) {
					throw new BundleLoadingFailedException(bundleid.toString(), e);
				}
			}
		}

		@Override
		public Object detectChanges(ExecutionPathConfiguration pathconfig) {
			synchronized (detectChangeLock) {
				if (!this.bundleInfoStates.equals(LocalBundleStorage.this.bundleInfoStates)) {
					return new Object();
				}
				if (!this.pendingBundleInfoStates.equals(LocalBundleStorage.this.pendingBundleInfoStates)) {
					return new Object();
				}
				//XXX don't collect all pending bundles, only check if there is at least one
				NavigableMap<BundleIdentifier, NavigableMap<Integer, Path>> pendingbundles = getPendingBundles();
				NavigableMap<BundleIdentifier, Entry<Integer, Path>> reducedpendingbundles = reduceInterestedPendingBundles(
						pendingbundles);
				if (ObjectUtils.isNullOrEmpty(reducedpendingbundles)) {
					//no added pending bundles
					return null;
				}
				return new Object();
			}
		}

		@Override
		public void handleChanges(ExecutionPathConfiguration pathconfig, Object detectedchanges) {
			//we don't handle the pending bundles here, as the pending bundles 
			//  may be used in cached objects in the build environment or other build environments
			synchronized (detectChangeLock) {
				//rediscover the bundles to apply, as they might've been changed between the detectchanges and handlechanges calls
				NavigableMap<BundleIdentifier, NavigableMap<Integer, Path>> pendingbundles = getPendingBundles();
				NavigableMap<BundleIdentifier, Entry<Integer, Path>> reducedpendingbundles = reduceInterestedPendingBundles(
						pendingbundles);

				Map<BundleIdentifier, PendingBundleInfoState> modifiedbundleids = new TreeMap<>();
				LocalBundleStorage.this.discoverPendingBundles(reducedpendingbundles);
				ObjectUtils.iterateSortedMapEntries(LocalBundleStorage.this.pendingBundleInfoStates,
						this.pendingBundleInfoStates, (bid, main, viewpending) -> {
							if (!Objects.equals(main, viewpending)) {
								modifiedbundleids.put(bid, viewpending);
							}
						});
				//remove all loaded bundles from the cache
				for (Entry<BundleIdentifier, PendingBundleInfoState> entry : modifiedbundleids.entrySet()) {
					BundleIdentifier modbundleid = entry.getKey();
					synchronized (bundleLoadLocks.computeIfAbsent(modbundleid, Functionals.objectComputer())) {
						loadedBundles.remove(modbundleid);
					}
				}
				//copy the state from the owner
				this.pendingBundleInfoStates = ImmutableUtils
						.makeImmutableNavigableMap(LocalBundleStorage.this.pendingBundleInfoStates);
			}
		}

		@Override
		public InstallResult install(StreamWritable bundlecontents) throws IOException {
			Objects.requireNonNull(bundlecontents, "bundle contents");

			Files.createDirectories(tempDirectory);
			Path tempjar = tempDirectory.resolve(UUID.randomUUID() + ".jar");
			BundleInformation bundleinfo;
			MessageDigest hashdigest;
			try {
				hashdigest = MessageDigest.getInstance(JarNestRepositoryBundleImpl.BUNDLE_HASH_ALGORITHM);
			} catch (NoSuchAlgorithmException e) {
				throw new AssertionError(
						"Bundle hash algorithm not found: " + JarNestRepositoryBundleImpl.BUNDLE_HASH_ALGORITHM, e);
			}
			try (SeekableByteChannel tempjarchannel = Files.newByteChannel(tempjar, StandardOpenOption.CREATE_NEW,
					StandardOpenOption.READ, StandardOpenOption.WRITE)) {
				OutputStream multiplexout = new MultiplexOutputStream(Channels.newOutputStream(tempjarchannel),
						StreamUtils.toOutputStream(hashdigest));
				bundlecontents.writeTo(multiplexout);
				tempjarchannel.position(0);
				try (InputStream is = Channels.newInputStream(tempjarchannel);
						JarInputStream jaris = new JarInputStream(StreamUtils.closeProtectedInputStream(is))) {
					bundleinfo = new BundleInformation(jaris);
				}
			} catch (InvalidNestBundleException | IOException e) {
				Files.deleteIfExists(tempjar);
				throw e;
			}

			BundleIdentifier bundleid = bundleinfo.getBundleIdentifier();
			String bundleidstr = bundleid.toString();

			//only move if the jar was successfully exported
			Files.createDirectories(pendingDirectory);
			//use a loop to avoid concurrent exporting processes to the same bundle. should probably never happen, but might occasionally
			//    better safe than sorry
			while (true) {
				int maxv = 0;
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(pendingDirectory)) {
					Iterator<Path> it = ds.iterator();
					while (it.hasNext()) {
						String fn = it.next().getFileName().toString();
						Matcher matcher = PATTERN_BUNDLE_PENDING_VERSION_FILE_NAME.matcher(fn);
						if (!matcher.matches()) {
							continue;
						}
						try {
							if (!BundleIdentifier.valueOf(matcher.group(1)).equals(bundleid)) {
								continue;
							}
						} catch (IllegalArgumentException e) {
							continue;
						}
						int v = Integer.parseUnsignedInt(matcher.group(2), 16);
						if (v > maxv) {
							maxv = v;
						}
					}
				} catch (DirectoryIteratorException e) {
					throw e.getCause();
				}
				Path targetpath = pendingDirectory.resolve(bundleidstr + ".b" + Integer.toHexString(maxv + 1) + ".jar");
				try {
					Files.move(tempjar, targetpath);
				} catch (IOException e) {
					//failed to move the temp jar to the target
					//it might be that some other already exported something there
					//    or the temp jar has been deleted
					//continue the loop if the jar still exists, and try to find the next max version again
					if (Files.isRegularFile(tempjar)) {
						continue;
					}
					throw e;
				}
				break;
			}
			return new SimpleInstallResult(bundleid, hashdigest.digest());
		}

		@Override
		public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
			//XXX create a more efficient lookup
			NavigableSet<TaskName> result = new TreeSet<>();
			NavigableSet<String> qualifierbuffer = new TreeSet<>();
			for (Entry<BundleIdentifier, BundleInfoState> entry : bundleInfoStates.entrySet()) {
				BundleIdentifier bundle = entry.getKey();
				qualifierbuffer.clear();
				qualifierbuffer.addAll(bundle.getBundleQualifiers());
				qualifierbuffer.addAll(bundle.getMetaQualifiers());
				for (String tn : entry.getValue().taskNames) {
					result.add(TaskName.valueOf(tn, qualifierbuffer));
				}
			}
			for (Entry<BundleIdentifier, PendingBundleInfoState> entry : pendingBundleInfoStates.entrySet()) {
				BundleIdentifier bundle = entry.getKey();
				qualifierbuffer.clear();
				qualifierbuffer.addAll(bundle.getBundleQualifiers());
				qualifierbuffer.addAll(bundle.getMetaQualifiers());
				for (String tn : entry.getValue().taskNames) {
					result.add(TaskName.valueOf(tn, qualifierbuffer));
				}
			}
			return result;
		}

		@Override
		public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
			TreeSet<BundleIdentifier> result = new TreeSet<>(bundleInfoStates.navigableKeySet());
			result.addAll(pendingBundleInfoStates.navigableKeySet());
			return result;
		}

		private NavigableSet<BundleIdentifier> getBundlesForTaskName(TaskName taskname) {
			//XXX create a more efficient lookup
			TreeSet<BundleIdentifier> result = new TreeSet<>();
			String tnamestr = taskname.getName();
			for (Entry<BundleIdentifier, BundleInfoState> entry : bundleInfoStates.entrySet()) {
				if (ObjectUtils.contains(entry.getValue().taskNames, tnamestr)) {
					result.add(entry.getKey());
				}
			}
			for (Entry<BundleIdentifier, PendingBundleInfoState> entry : pendingBundleInfoStates.entrySet()) {
				if (ObjectUtils.contains(entry.getValue().taskNames, tnamestr)) {
					result.add(entry.getKey());
				}
			}
			return result;
		}
	}
}
