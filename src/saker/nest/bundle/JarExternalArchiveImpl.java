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
import java.util.Enumeration;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import saker.build.file.provider.LocalFileProvider;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.JarFileUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.nest.exc.IllegalArchiveEntryNameException;

public class JarExternalArchiveImpl extends AbstractExternalArchive implements JarExternalArchive {
	private final SimpleExternalArchiveKey archiveKey;
	private final SeekableByteChannel channel;
	private final JarFile jar;
	private final NavigableSet<String> entryNames;

	private final LazySupplier<byte[]> jarHash = LazySupplier.of(this::computeJarHash);

	private JarExternalArchiveImpl(SimpleExternalArchiveKey archiveKey, SeekableByteChannel channel, JarFile jar) {
		this.archiveKey = archiveKey;
		this.channel = channel;
		this.jar = jar;

		//compute the entry names eagerly to discover any invalid names
		this.entryNames = getEntryNamesValidate(jar);
	}

	public static JarExternalArchiveImpl create(SimpleExternalArchiveKey archiveKey, Path jarpath) throws IOException {
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
	public SimpleExternalArchiveKey getArchiveKey() {
		return archiveKey;
	}

	public SeekableByteChannel getChannel() {
		return channel;
	}

	@Override
	public NavigableSet<String> getEntryNames() {
		return entryNames;
	}

	@Override
	public boolean hasEntry(String name) {
		if (name == null) {
			return false;
		}
		return entryNames.contains(name);
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

	@Override
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

	private static NavigableSet<String> getEntryNamesValidate(JarFile jar) {
		NavigableSet<String> entrynames = new TreeSet<>(String::compareToIgnoreCase);
		Enumeration<JarEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			JarEntry jarentry = entries.nextElement();
			if (jarentry.isDirectory()) {
				continue;
			}
			String entryname = jarentry.getName();
			BundleUtils.checkArchiveEntryName(entryname);
			if (!entrynames.add(entryname)) {
				throw new IllegalArchiveEntryNameException(entryname);
			}
		}
		return entrynames;
	}
}
