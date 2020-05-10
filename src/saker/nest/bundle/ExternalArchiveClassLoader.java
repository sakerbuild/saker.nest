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

import saker.apiextract.api.PublicApi;

//only implementation is NestRepositoryExternalArchiveClassLoader
/**
 * Interface implemented by {@link ClassLoader ClassLoaders} that load classes from external archives.
 * <p>
 * The interface is implemented by all class laoders which load classes from extternal archives. External archives are
 * downloaded by the repository runtime based on the {@linkplain BundleInformation#getExternalDependencyInformation()
 * external dependencies} of a bundle.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @since saker.nest 0.8.5
 */
@PublicApi
public interface ExternalArchiveClassLoader {
	/**
	 * Gets the external archive this classloader loads the classes from.
	 * 
	 * @return The external archive.
	 */
	public ExternalArchive getExternalArchive();

}
