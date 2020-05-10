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

import java.nio.file.Path;

import saker.apiextract.api.PublicApi;

/**
 * {@link ExternalArchive} that is backed by a Java ARchive file.
 * <p>
 * The {@link #getJarPath()} can be used to retrieve the path to the JAR file.
 * 
 * @since saker.nest 0.8.5
 */
@PublicApi
public interface JarExternalArchive extends ExternalArchive {
	/**
	 * Gets the JAR file path that this bundle is backed by.
	 * 
	 * @return The absolute path to the JAR.
	 */
	public Path getJarPath();
}
