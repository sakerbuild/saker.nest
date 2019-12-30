package saker.nest.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.NavigableSet;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.nest.bundle.storage.BundleStorage;

// only implementation is JarRepositoryBundleImpl
/**
 * Interface providing access to the contents of a Nest repository bundle.
 * <p>
 * A bundle is considered to be a simple immutable container of data by the perspective of the Nest repository. This
 * interface provides functions that can be used to access the contents of it.
 * <p>
 * The contents may be interpreted in any way suitable for the caller.
 * <p>
 * This interface is not related to any current storage configurations, it only serves as a handle to the contents of a
 * bundle. Bundles may be shared internally by different storage configurations.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleClassLoader#getBundle()
 * @see JarNestRepositoryBundle
 */
@PublicApi
public interface NestRepositoryBundle {
	/**
	 * Gets the bundle information associated with this bundle.
	 * 
	 * @return The bundle information. (Never <code>null</code>.)
	 */
	public BundleInformation getInformation();

	/**
	 * Gets the identifier of the bundle.
	 * <p>
	 * The returned identifier can have arbitrary contents specified by the {@link BundleIdentifier} class. It may or
	 * may not contain any version qualifiers.
	 * 
	 * @return The identifier. Always non-<code>null</code>.
	 */
	public default BundleIdentifier getBundleIdentifier() {
		return getInformation().getBundleIdentifier();
	}

	/**
	 * Gets the bundle storage that this bundle is contained in.
	 * 
	 * @return The bundle storage.
	 */
	public BundleStorage getStorage();

	/**
	 * Gets a hash of the contents of the bundle.
	 * <p>
	 * The hash is produced by hashing the raw byte contents of the bundle itself. If the bundle was compressed, the
	 * contents are not uncompressed for hashing. In case of JAR bundles, the bytes of the JAR is used to produce the
	 * hash.
	 * <p>
	 * Hashes of dependencies are not included.
	 * <p>
	 * The hash algorithm is implementation dependent.
	 * 
	 * @return The hash. Modifications don't propagate, the array is cloned.
	 */
	public byte[] getHash();

	/**
	 * Gets the path to the bundle-private storage directory.
	 * <p>
	 * Each bundle is provided with a private storage that they can use to store arbitrary data. It can be mainly used
	 * for caching purposes. The storage directory can be cleared at any moment, and clients shouldn't store crucial
	 * data in it.
	 * <p>
	 * One example use-case for it is to extract bundle entries to this directory which is later then passed to external
	 * processes as an input.
	 * 
	 * @return The absolute path to the storage directory.
	 */
	public Path getBundleStoragePath();

	/**
	 * Gets the names of entries in this bundle.
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
	 * Checks if a given entry with the specified name is present in the bundle.
	 * <p>
	 * If the argument is <code>null</code>, this method returns <code>false</code>.
	 * 
	 * @param name
	 *            The name.
	 * @return <code>true</code> if the entry is present.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>. (Optional exception, implementations may return
	 *             <code>null</code> instead.)
	 */
	public default boolean hasEntry(String name) throws NullPointerException {
		return name != null && getEntryNames().contains(name);
	}

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