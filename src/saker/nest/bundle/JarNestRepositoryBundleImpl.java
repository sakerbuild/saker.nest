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
import java.io.InputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.JarFileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.nest.bundle.storage.AbstractBundleStorage;

public class JarNestRepositoryBundleImpl extends AbstractNestRepositoryBundle implements JarNestRepositoryBundle {
	public static final String BUNDLE_HASH_ALGORITHM = "MD5";

	private static final Set<OpenOption> OPEN_OPTIONS_READ_WITHOUT_SHARING;
	static {
		Set<OpenOption> withoutsharing;
		try {
			Class<?> exooclass = Class.forName("com.sun.nio.file.ExtendedOpenOption");
			@SuppressWarnings({ "unchecked", "rawtypes" })
			OpenOption nosharewrite = (OpenOption) Enum.valueOf((Class) exooclass, "NOSHARE_WRITE");
			@SuppressWarnings({ "unchecked", "rawtypes" })
			OpenOption nosharedelete = (OpenOption) Enum.valueOf((Class) exooclass, "NOSHARE_DELETE");
			withoutsharing = ImmutableUtils
					.makeImmutableHashSet(new OpenOption[] { StandardOpenOption.READ, nosharewrite, nosharedelete });
		} catch (ClassNotFoundException | IllegalArgumentException | ClassCastException e) {
			//the enumerations somewhy couldn't be found
			withoutsharing = null;
		}
		OPEN_OPTIONS_READ_WITHOUT_SHARING = withoutsharing;
	}

	private final SeekableByteChannel channel;
	private final JarFile jar;
	private final BundleInformation information;
	private final AbstractBundleStorage storage;

	private final LazySupplier<NavigableSet<String>> entryNames;
	private final LazySupplier<byte[]> jarHash = LazySupplier.of(this::computeJarHash);

	private static SeekableByteChannel openExclusiveChannelForJar(Path bundlejar) throws IOException {
		if (OPEN_OPTIONS_READ_WITHOUT_SHARING != null) {
			try {
				return Files.newByteChannel(bundlejar, OPEN_OPTIONS_READ_WITHOUT_SHARING);
			} catch (UnsupportedOperationException e) {
				//not supported on mac or linux (ubuntu)
				//  we fall back to simply opening the file as it makes no sense to fail by default
				//XXX support exclusive read access to the file on mac and linux
				//    if there's no OS support for exclusive access, then we could read the JAR contents into
				//    memory and work with that to protect external malicious modifications. However, this
				//    may require significant memory usage for large bundles.
			}
		}
		return Files.newByteChannel(bundlejar, StandardOpenOption.READ);
	}

	public static JarNestRepositoryBundleImpl create(AbstractBundleStorage storage, Path bundlejar) throws IOException {
		SeekableByteChannel channel = openExclusiveChannelForJar(bundlejar);
		try {
			JarFile jarfile = JarFileUtils.createMultiReleaseJarFile(bundlejar);
			try {
				return new JarNestRepositoryBundleImpl(storage, jarfile, channel);
			} catch (Throwable e) {
				IOUtils.addExc(e, IOUtils.closeExc(jarfile));
				throw e;
			}
		} catch (Throwable e) {
			IOUtils.addExc(e, IOUtils.closeExc(channel));
			throw e;
		}
	}

	private JarNestRepositoryBundleImpl(AbstractBundleStorage storage, JarFile jar, SeekableByteChannel channel,
			BundleInformation bundleinfo) {
		this.channel = channel;
		this.storage = storage;
		this.jar = jar;
		this.information = bundleinfo;
		this.entryNames = LazySupplier.of(() -> {
			NavigableSet<String> result = new TreeSet<>();
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry jarentry = entries.nextElement();
				if (!jarentry.isDirectory()) {
					result.add(jarentry.getName());
				}
			}
			return ImmutableUtils.makeImmutableNavigableSet(result);
		});
	}

	private JarNestRepositoryBundleImpl(AbstractBundleStorage storage, JarFile jar, SeekableByteChannel channel)
			throws IOException {
		this(storage, jar, channel, new BundleInformation(jar));
	}

	/**
	 * Gets the channel for the underlying JAR.
	 * <p>
	 * Callers should synchronize on <code>this</code> when accessing the channel.
	 * 
	 * @return The channel for the file or <code>null</code> if the implementation cannot ensure that the JAR is not
	 *             modified after being opened.
	 */
	public SeekableByteChannel getChannel() {
		return channel;
	}

	@Override
	public NavigableSet<String> getEntryNames() {
		return entryNames.get();
	}

	@Override
	public boolean hasEntry(String name) {
		if (name == null) {
			return false;
		}
		NavigableSet<String> entries = entryNames.getIfComputed();
		if (entries != null) {
			return entries.contains(name);
		}
		return jar.getEntry(name) != null;
	}

	@Override
	public InputStream openEntry(String name) throws IOException {
		Objects.requireNonNull(name, "name");
		ZipEntry je = jar.getEntry(name);
		if (je == null) {
			throw new NoSuchFileException(name, null, "Bundle entry not found in: " + jar.getName());
		}
		return jar.getInputStream(je);
	}

	@Override
	public ByteArrayRegion getEntryBytes(String name) throws IOException {
		Objects.requireNonNull(name, "name");
		ZipEntry je = jar.getEntry(name);
		if (je == null) {
			throw new NoSuchFileException(name, null, "Bundle entry not found in: " + jar.getName());
		}
		long entrysize = je.getSize();
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream(entrysize < 0 ? 4096 : (int) entrysize);
				InputStream is = jar.getInputStream(je)) {
			baos.readFrom(is);
			return baos.toByteArrayRegion();
		}
	}

	@Override
	public BundleInformation getInformation() {
		return information;
	}

	@Override
	public BundleIdentifier getBundleIdentifier() {
		return information.getBundleIdentifier();
	}

	@Override
	public Path getJarPath() {
		return Paths.get(jar.getName());
	}

	@Override
	public void close() throws IOException {
		IOUtils.close(jar, channel);
	}

	@Override
	public AbstractBundleStorage getStorage() {
		return storage;
	}

	@Override
	public Path getBundleStoragePath() {
		return getStorage().getBundleStoragePath(this);
	}

	@Override
	public byte[] getHash() {
		return getSharedHash().clone();
	}

	@Override
	public byte[] getSharedHash() {
		return jarHash.get();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getBundleIdentifier() + " : " + jar.getName() + "]";
	}

	private byte[] computeJarHash() {
		Path jarpath = getJarPath();
		try {
			//use the file provider instead of FileUtils.hashFiles(jarpath), so we don't accidentally hash a directory
			return generateBundleHash(jarpath);
		} catch (IOException e) {
			System.err.println("Failed to hash bundle: " + jarpath + " : " + e);
			//generate a random hash to detect any differences next time
			byte[] hash = new byte[Long.BYTES * 2];
			UUID uuid = UUID.randomUUID();
			SerialUtils.writeLongToBuffer(uuid.getMostSignificantBits(), hash, 0);
			SerialUtils.writeLongToBuffer(uuid.getLeastSignificantBits(), hash, Long.BYTES);
			return hash;
		}
	}

	private static byte[] generateBundleHash(Path jarpath) throws IOException {
		try {
			return LocalFileProvider.getInstance().hash(jarpath, BUNDLE_HASH_ALGORITHM).getHash();
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError("Hash algorithm not found: " + BUNDLE_HASH_ALGORITHM, e);
		}
	}

}
