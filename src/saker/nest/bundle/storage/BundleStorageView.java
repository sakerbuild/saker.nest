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

import java.util.Map;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.NestBundleStorageConfiguration;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.exc.BundleLoadingFailedException;

/**
 * Interface for providing configured access to a backing {@linkplain BundleStorage bundle storage}.
 * <p>
 * A bundle storage view is a configured view to a backing bundle storage. It can be used to retrieve the actual bundles
 * and informations related to bundles contained in a given storage.
 * <p>
 * Based on the configuration that was used to instantiate it, a bundle storage view may perform different operations
 * and return different results. E.g. if it uses a server storage, but is configured to be offline, then it won't
 * download bundles from the associated server.
 * <p>
 * Instances of bundle storage views can be retrieved from {@link NestBundleStorageConfiguration}.
 * <p>
 * Each bundle storage view has a {@link StorageViewKey} that uniquely identifies its backing storage and configured
 * behaviour. These keys may be serialized and used to retrieve the storage view again using
 * {@link NestBundleStorageConfiguration#getBundleStorageViewForKey(StorageViewKey)}. This can useful for implementing
 * incremental builds and other operations.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see ParameterBundleStorageView
 * @see LocalBundleStorageView
 * @see ServerBundleStorageView
 */
@PublicApi
public interface BundleStorageView {
	/**
	 * Gets the backing bundle storage object.
	 * <p>
	 * In general, clients don't need to use the backing storage directly.
	 * 
	 * @return The backing bundle storage.
	 */
	public BundleStorage getStorage();

	/**
	 * Gets the storage view key of this view.
	 * 
	 * @return The key.
	 */
	public StorageViewKey getStorageViewKey();

	/**
	 * Gets the bundle for the given bundle identifier.
	 * <p>
	 * The bundle storage view is asked to locate and load the bundle with the given identifier. It will execute the
	 * operation in an implementation dependent manner for the storage view. If the bundle was not found, or cannot be
	 * loaded, {@link BundleLoadingFailedException} will be thrown.
	 * <p>
	 * The storage view may require the bundle identifier to have a {@linkplain BundleIdentifier#getVersionQualifier()
	 * version qualifier}.
	 * 
	 * @param bundleid
	 *            The bundle identifier to get the bundle for.
	 * @return The loaded bundle.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws BundleLoadingFailedException
	 *             If the bundle couldn't be loaded by the storage view.
	 */
	public NestRepositoryBundle getBundle(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException;

	/**
	 * Gets the information about a given bundle in the storage view.
	 * <p>
	 * The bundle storage view will attempt to locate and load the information of the bundle for the given identifier.
	 * This may include loading the bundle itself, or only the information related meta-data. The operation is executed
	 * in an implementation dependent manner based on the storage view.
	 * <p>
	 * The storage view may require the bundle identifier to have a {@linkplain BundleIdentifier#getVersionQualifier()
	 * version qualifier}.
	 * 
	 * @param bundleid
	 *            The bundle identifier to get the information for.
	 * @return The bundle information for the given identifier.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws BundleLoadingFailedException
	 *             If loading the bundle information failed.
	 */
	public default BundleInformation getBundleInformation(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException {
		return getBundle(bundleid).getInformation();
	}

	/**
	 * Looks up the identifiers of bundles which are present in this bundle storage view and only differ (or equal) in
	 * version number to the argument.
	 * <p>
	 * This method will search for all bundles present in the storage view with the same bundle name and qualifiers
	 * (except the version qualifier). The found bundle identifiers are returned in a set that is ordered by descending
	 * version numbers.
	 * <p>
	 * If the argument has a {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}, it is ignored by
	 * this method.
	 * 
	 * @param bundleid
	 *            The bundle identifier to get the available versions for.
	 * @return The bundle identifiers that were found. The result is ordered by descending version numbers.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public Set<? extends BundleIdentifier> lookupBundleVersions(BundleIdentifier bundleid) throws NullPointerException;

	/**
	 * Looks up the identifiers of bundles that have the same {@linkplain BundleIdentifier#getName() bundle name} as the
	 * argument.
	 * <p>
	 * This method will query all bundles that are present with the given name in this bundle storage view. The found
	 * bundle identifiers are returned in a map that contains version numbers mapped to the bundle identifiers for that
	 * version. The returned map is ordered by descending order of version numbers.
	 * 
	 * @param bundlename
	 *            The bundle name to get the bundles of.
	 * @return The version number-bundle identifiers map that contains the found bundles. Iteration order is descending
	 *             by version number.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not a {@linkplain BundleIdentifier#isValidBundleName(String) valid bundle name}.
	 */
	public Map<String, ? extends Set<? extends BundleIdentifier>> lookupBundleIdentifiers(String bundlename)
			throws NullPointerException, IllegalArgumentException;
}
