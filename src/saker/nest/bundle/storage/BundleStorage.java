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

import saker.apiextract.api.PublicApi;

/**
 * Interface providing access to a bundle storage.
 * <p>
 * The {@link BundleStorage} interface generally manages the structure and synchronization of a bundle storage location.
 * <p>
 * Currently this interface serves no purpose for client usage, however, it may be extended in the future to provide
 * access to various bundle storage facilities.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleStorageView
 */
@PublicApi
public interface BundleStorage {
	/**
	 * Gets the storage key for this bundle storage.
	 * 
	 * @return The storage key.
	 */
	public StorageKey getStorageKey();
}
