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
package saker.nest.bundle.lookup;

import java.util.Map;
import java.util.Set;

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.storage.BundleStorageView;

/**
 * Represents the result of a bundle identifier lookup request.
 * <p>
 * The interface provides access to the {@linkplain #getBundles() found bundles}, the {@linkplain #getStorageView()
 * storage view} that contains them , and the {@linkplain #getRelativeLookup() relative lookup} that can be used to look
 * up further bundles in relation to them.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleLookup#lookupBundleIdentifiers(String)
 */
@PublicApi
public interface BundleIdentifierLookupResult {
	/**
	 * Gets the bundles found as the result of the lookup.
	 * <p>
	 * The found bundle identifiers are returned in a map that contains version numbers mapped to the bundle identifiers
	 * for that version. The returned map is ordered by descending order of version numbers.
	 * 
	 * @return The found bundles.
	 */
	public Map<String, ? extends Set<? extends BundleIdentifier>> getBundles();

	/**
	 * Gets the relative lookup in relation to the found bundles.
	 * <p>
	 * The lookup can be used to look up other bundles and information that is accessible in relation to the bundles.
	 * 
	 * @return The relative bundle lookup.
	 */
	public BundleLookup getRelativeLookup();

	/**
	 * Gets the storage view that contains the found bundles.
	 * 
	 * @return The storage view.
	 */
	public BundleStorageView getStorageView();
}
