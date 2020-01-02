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

import java.util.Objects;

import saker.nest.bundle.storage.StorageViewKey;

/**
 * Interface for identifying a bundle in a configured bundle storage view.
 * <p>
 * The interface is based on a given {@link StorageViewKey} and {@link BundleIdentifier} that specifies a bundle.
 * {@link BundleKey} object are not directly used by the repository classes, but are to be used by clients to specify a
 * bundle location in a unique format.
 * <p>
 * Instances of this interface can be compared by equality and serialized. If two bundle keys equal then the associated
 * bundle is to be loaded from the same location. It doesn't ensure that the contents of the bundles are the same.
 * <p>
 * Bundle keys can be created using the {@link #create(StorageViewKey, BundleIdentifier)} function.
 * <p>
 * This interface is not to be implemented by clients.
 */
public interface BundleKey extends BundleIdentifierHolder {
	/**
	 * Gets the bundle identifier of the bundle key.
	 * 
	 * @return The bundle identifier. (Never <code>null</code>.)
	 */
	@Override
	public BundleIdentifier getBundleIdentifier();

	/**
	 * Gets the storage view key from where the bundle should be retrieved from.
	 * 
	 * @return The storage view key or <code>null</code> if the
	 *             {@linkplain NestBundleStorageConfiguration#getBundleLookup() root bundle lookup} should be used to
	 *             resolve the identifier.
	 */
	public StorageViewKey getStorageViewKey();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	/**
	 * Creates a new {@link BundleKey} with the given arguments.
	 * 
	 * @param storageviewkey
	 *            The storage view key or <code>null</code> if the bundle key should be interpreted in a way that the
	 *            bundle identifier is to be resolved against the
	 *            {@linkplain NestBundleStorageConfiguration#getBundleLookup() root bundle lookup}.
	 * @param bundleid
	 *            The bundle identifier.
	 * @return The created bundle key.
	 * @throws NullPointerException
	 *             If the bundle identifier is <code>null</code>.
	 */
	public static BundleKey create(StorageViewKey storageviewkey, BundleIdentifier bundleid)
			throws NullPointerException {
		Objects.requireNonNull(bundleid, "bundle identifier");
		return new SimpleBundleKey(bundleid, storageviewkey);
	}
}
