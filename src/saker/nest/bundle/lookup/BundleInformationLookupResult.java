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

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.storage.BundleStorageView;

/**
 * Represents the result of a bundle information lookup request.
 * <p>
 * The interface provides access to the {@linkplain #getBundleInformation() bundle information} result of the lookup,
 * the {@linkplain #getStorageView() storage view} that contains it, and the {@linkplain #getRelativeLookup() relative
 * lookup} that can be used to look up further bundles in relation to this.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleLookup#lookupBundleInformation(BundleIdentifier)
 */
@PublicApi
public interface BundleInformationLookupResult {
	/**
	 * Gets the bundle information that is the result of the lookup operation.
	 * 
	 * @return The bundle information.
	 */
	public BundleInformation getBundleInformation();

	/**
	 * Gets the relative lookup in relation to the found bundle information.
	 * <p>
	 * The lookup can be used to look up other bundles and information that is accessible in relation to the loaded
	 * bundle.
	 * 
	 * @return The relative bundle lookup.
	 */
	public BundleLookup getRelativeLookup();

	/**
	 * Gets the storage view that contains the bundle associated with the information.
	 * 
	 * @return The storage view.
	 */
	public BundleStorageView getStorageView();
}
