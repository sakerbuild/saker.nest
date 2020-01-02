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
import saker.nest.bundle.NestBundleStorageConfiguration;

/**
 * An unique key identifier for {@linkplain BundleLookup bundle lookups}.
 * <p>
 * Instances of this interface can be compared by equality and serialized.
 * <p>
 * They can be retrieved using {@link BundleLookup#getLookupKey()}.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleStorageConfiguration#getBundleLookupForKey(LookupKey)
 */
@PublicApi
public interface LookupKey {
	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);
}
