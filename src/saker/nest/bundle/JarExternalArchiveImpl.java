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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.NavigableSet;
import java.util.UUID;
import java.util.jar.JarFile;

import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.JarFileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

public class JarExternalArchiveImpl extends AbstractExternalArchive implements JarExternalArchive {
	private final ExternalArchiveKey archiveKey;
	private final SeekableByteChannel channel;
	private final JarFile jar;
	private final LazySupplier<NavigableSet<String>> entryNames;

	private final LazySupplier<byte[]> jarHash = LazySupplier.of(this::computeJarHash);

	private JarExternalArchiveImpl(ExternalArchiveKey archiveKey, SeekableByteChannel channel, JarFile jar) {
		this.archiveKey = archiveKey;
		this.channel = channel;
		this.jar = jar;
		this.entryNames = LazySupplier.of(() -> BundleUtils.getJarEntryNames(jar));
	}

	public static JarExternalArchiveImpl create(ExternalArchiveKey archiveKey, Path jarpath) throws IOException {
		SeekableByteChannel channel = BundleUtils.openExclusiveChannelForJar(jarpath);
		try {
			JarFile jarfile = JarFileUtils.createMultiReleaseJarFile(jarpath);
			try {
				return new JarExternalArchiveImpl(archiveKey, channel, jarfile);
			} catch (Throwable e) {
				IOUtils.addExc(e, IOUtils.closeExc(jarfile));
				throw e;
			}
		} catch (Throwable e) {
			IOUtils.addExc(e, IOUtils.closeExc(channel));
			throw e;
		}
	}

	@Override
	public ExternalArchiveKey getArchiveKey() {
		return archiveKey;
	}

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
		return BundleUtils.openJarEntry(jar, name);
	}

	@Override
	public ByteArrayRegion getEntryBytes(String name) throws IOException {
		return BundleUtils.getJarEntryBytes(jar, name);
	}

	@Override
	public Path getJarPath() {
		return Paths.get(jar.getName());
	}

	public byte[] getHash() {
		return getSharedHash().clone();
	}

	@Override
	public byte[] getSharedHash() {
		return jarHash.get();
	}

	@Override
	public void close() throws IOException {
		IOUtils.close(jar, channel);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + jar.getName() + "]";
	}

	private byte[] computeJarHash() {
		Path jarpath = getJarPath();
		try {
			return generateBundleHash(jarpath);
		} catch (IOException e) {
			System.err.println("Failed to hash archive: " + jarpath + " : " + e);
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
			return LocalFileProvider.getInstance().hash(jarpath, JarNestRepositoryBundleImpl.BUNDLE_HASH_ALGORITHM)
					.getHash();
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError("Hash algorithm not found: " + JarNestRepositoryBundleImpl.BUNDLE_HASH_ALGORITHM,
					e);
		}
	}
}
