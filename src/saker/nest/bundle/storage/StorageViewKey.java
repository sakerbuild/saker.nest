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
 * An unique key identifier for {@linkplain BundleStorageView bundle storage views}.
 * <p>
 * Instances of this interface can be compared by equality and serialized.
 * <p>
 * Storage view keys uniquely identify the backing storage and the configuration used to modify the view behaviour. They
 * can be compared for equality, however, if two storage view keys equal, then they still may provide access to
 * different bundles. The equality of storage view keys only ensure that the given views were configured semantically
 * the same way.
 * <p>
 * A bundle addition to a backing storage may still result in equality of storage view keys.
 * <p>
 * Instances can be retrieved using {@link BundleStorageView#getStorageViewKey()}.
 * <p>
 * This interface is not to be implemented by clients.
 */
@PublicApi
public interface StorageViewKey {
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
