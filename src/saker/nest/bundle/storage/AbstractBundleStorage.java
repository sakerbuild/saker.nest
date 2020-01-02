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

import java.io.Closeable;
import java.nio.file.Path;
import java.util.Map;

import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.nest.bundle.NestRepositoryBundle;

public abstract class AbstractBundleStorage implements BundleStorage, Closeable {
	public abstract AbstractBundleStorageView newStorageView(Map<String, String> userparameters,
			ExecutionPathConfiguration pathconfig);

	public abstract Path getBundleLibStoragePath(NestRepositoryBundle bundle)
			throws NullPointerException, IllegalArgumentException;

	public abstract Path getBundleStoragePath(NestRepositoryBundle bundle)
			throws NullPointerException, IllegalArgumentException;

	@Override
	public abstract AbstractStorageKey getStorageKey();

	public abstract String getType();

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getStorageKey() + "]";
	}
}
