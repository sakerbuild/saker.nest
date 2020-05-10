package saker.nest.bundle;

import java.net.URI;

import saker.apiextract.api.PublicApi;

/**
 * Identifies a specific content of an external archive.
 * <p>
 * The interface contains information that identifies an external archive and optionally an entry in it. The interface
 * is used to distinguish differentiate the loading origins of an {@link ExternalArchive}.
 * <p>
 * The interface defines its properties to be immutable.
 * <p>
 * This interface is not to be implemented by clients.
 * <p>
 * Use {@link #create(URI, String) create} to create a new instance.
 */
@PublicApi
public interface ExternalArchiveKey {

	/**
	 * Gets the {@link URI} origin of the external archive.
	 * <p>
	 * The {@link URI} is used to load the archive from. If {@link #getEntryName()} is <code>null</code>, then this
	 * {@link ExternalArchiveKey} represents the main archive that was loaded.
	 * 
	 * @return The {@link URI}. Non-<code>null</code>.
	 */
	public URI getUri();

	/**
	 * Gets the name of the entry that was extracted from the main archive.
	 * <p>
	 * This property specifies the full path in the main archive from where the entry was extracted from and loaded as
	 * an {@link ExternalArchive}.
	 * <p>
	 * If this property is <code>null</code>, then the {@link ExternalArchiveKey} represents the main archive without
	 * any entry extraction.
	 * 
	 * @return The full entry name or <code>null</code>.
	 */
	public String getEntryName();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	/**
	 * Creates a new instance.
	 * 
	 * @param uri
	 *            The {@link URI} of the main archive.
	 * @param entryname
	 *            The entry name or <code>null</code> if none.
	 * @return The created {@link ExternalArchiveKey}.
	 * @throws NullPointerException
	 *             If the uri is <code>null</code>.
	 */
	public static ExternalArchiveKey create(URI uri, String entryname) throws NullPointerException {
		return new SimpleExternalArchiveKey(uri, entryname);
	}

	/**
	 * Creates a new instance for the main archive.
	 * <p>
	 * This is the same as:
	 * 
	 * <pre>
	 * create(uri, null)
	 * </pre>
	 * 
	 * @param uri
	 *            The {@link URI} of the main archive.
	 * @return The created {@link ExternalArchiveKey}.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static ExternalArchiveKey create(URI uri) throws NullPointerException {
		return new SimpleExternalArchiveKey(uri);
	}
}
