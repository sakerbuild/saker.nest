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

import java.io.IOException;
import java.util.function.Supplier;

import saker.build.thirdparty.saker.util.classloader.ClassLoaderDataFinder;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSource;

public class NestBundleClassLoaderDataFinder implements ClassLoaderDataFinder {
	private NestRepositoryBundle bundle;

	public NestBundleClassLoaderDataFinder(NestRepositoryBundle bundle) {
		this.bundle = bundle;
	}

	@Override
	public Supplier<? extends ByteSource> getResource(String name) {
		if (!bundle.hasEntry(name)) {
			return null;
		}
		return () -> {
			try {
				return ByteSource.valueOf(bundle.openEntry(name));
			} catch (IOException e) {
				return null;
			}
		};
	}

	@Override
	public ByteArrayRegion getResourceBytes(String name) {
		try {
			return bundle.getEntryBytes(name);
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public ByteSource getResourceAsStream(String name) {
		try {
			return ByteSource.valueOf(bundle.openEntry(name));
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public void close() throws IOException {
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + bundle + "]";
	}

}
