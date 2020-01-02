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
 * Key identifier interface for identifying a given bundle storage.
 * <p>
 * Storage keys are used to differentiate various {@link BundleStorage} objects. If two storage keys equal, then they
 * provide the storage to the same location. However, storage key equality doesn't mean that they provide access to the
 * same bundles.
 * <p>
 * Instances of this interface can be compared by equality and serialized.
 * <p>
 * They can be retrieved using {@link BundleStorage#getStorageKey()}.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see BundleStorage
 * @see BundleStorageView
 * @see StorageViewKey
 */
@PublicApi
public interface StorageKey {
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
