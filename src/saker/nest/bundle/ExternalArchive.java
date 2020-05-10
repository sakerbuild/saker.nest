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
import java.net.URI;
import java.util.NavigableSet;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;

// only implementation is JarExternalArchiveImpl
/**
 * An archive that was loaded from an external source from the perspective of the saker.nest repository.
 * <p>
 * External archives are not part of the saker.nest repository, but they are loaded from their associated {@link URI
 * URIs}. Bundles may declare external dependencies in which case they can be loaded and consumed as part of the
 * repository operations.
 * <p>
 * The contents of the archive may be interpreted in any way suitable for the caller.
 * <p>
 * This interface is not to be implemented by clients.
 *
 * @since saker.nest 0.8.5
 * @see ExternalDependencyInformation
 * @see BundleInformation#getExternalDependencyInformation()
 * @see NestBundleClassLoader#getExternalClassPathDependencies()
 * @see JarExternalArchive
 */
@PublicApi
public interface ExternalArchive {
	/**
	 * Gets the archive key of this instance.
	 * <p>
	 * The archive key contains the origins of the archive.
	 * 
	 * @return The archive key.
	 */
	public ExternalArchiveKey getArchiveKey();

	/**
	 * Gets a hash of the contents of the archive.
	 * <p>
	 * The hash is produced by hashing the raw byte contents of the archive itself. If the archive was compressed, the
	 * contents are not uncompressed for hashing.
	 * <p>
	 * The hash algorithm is implementation dependent. It is the same as used by {@link NestRepositoryBundle#getHash()}.
	 * 
	 * @return The hash. Modifications don't propagate, the array is cloned.
	 */
	public byte[] getHash();

	/**
	 * Gets the names of entries in this archive.
	 * <p>
	 * Directory entries are not included.
	 * <p>
	 * The {@link #hasEntry(String)} method will return <code>true</code> for any entry name contained in the returned
	 * set.
	 * 
	 * @return An immutable set of entry names.
	 */
	public NavigableSet<String> getEntryNames();

	/**
	 * Opens a byte input stream to the contents of the entry with the given name.
	 * <p>
	 * The returned stream must be closed by the caller.
	 * 
	 * @param name
	 *            The name of the entry.
	 * @return The input stream to the contents of the entry.
	 * @throws NullPointerException
	 *             If the argument name is <code>null</code>.
	 * @throws IOException
	 *             If the entry was not found, or an I/O error occurrs.
	 */
	public InputStream openEntry(String name) throws NullPointerException, IOException;

	/**
	 * Checks if a given entry with the specified name is present in the archive.
	 * <p>
	 * If the argument is <code>null</code>, this method returns <code>false</code>.
	 * 
	 * @param name
	 *            The name.
	 * @return <code>true</code> if the entry is present.
	 */
	public default boolean hasEntry(String name) {
		return name != null && getEntryNames().contains(name);
	}

	/**
	 * Gets the full byte array contents of the given entry.
	 * 
	 * @param name
	 *            The name of the entry.
	 * @return The byte contents.
	 * @throws NullPointerException
	 *             If the argument name is <code>null</code>.
	 * @throws IOException
	 *             If the entry was not found, or an I/O error occurrs.
	 */
	public default ByteArrayRegion getEntryBytes(String name) throws NullPointerException, IOException {
		try (InputStream is = openEntry(name)) {
			return StreamUtils.readStreamFully(is);
		}
	}
}
