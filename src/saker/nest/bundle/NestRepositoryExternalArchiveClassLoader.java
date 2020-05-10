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

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.thirdparty.saker.util.classloader.MultiDataClassLoader;
import saker.build.thirdparty.saker.util.io.IOUtils;

public final class NestRepositoryExternalArchiveClassLoader extends MultiDataClassLoader
		implements ExternalArchiveClassLoader {
	static {
		registerAsParallelCapable();
	}

	private final NestRepositoryBundleClassLoader owner;
	private final AbstractExternalArchive archive;

	private final Set<NestRepositoryExternalArchiveClassLoader> externalClassLoaderDomain;

	private final ConcurrentSkipListMap<String, Class<?>> archiveLoadedClasses = new ConcurrentSkipListMap<>();

	public NestRepositoryExternalArchiveClassLoader(NestRepositoryBundleClassLoader owner, ClassLoader parent,
			AbstractExternalArchive archive, Set<NestRepositoryExternalArchiveClassLoader> externalClassLoaderDomain) {
		super(parent, new ExternalArchiveClassLoaderDataFinder(archive));
		this.owner = owner;
		this.archive = archive;
		this.externalClassLoaderDomain = externalClassLoaderDomain;
	}

	public NestRepositoryBundleClassLoader getOwner() {
		return owner;
	}

	@Override
	public AbstractExternalArchive getExternalArchive() {
		return archive;
	}

	/**
	 * Must be locked on {@link #getClassLoadingLock(String)}.
	 * <p>
	 * We can't use {@link #findLoadedClass(String)} as that may return classes that weren't defined by this
	 * classloader.
	 */
	private Class<?> getAlreadyLoadedClassByThisArchive(String name) {
		return archiveLoadedClasses.get(name);
	}

	/**
	 * Must be locked on {@link #getClassLoadingLock(String)}.
	 */
	private Class<?> loadDefineClassFromArchive(String name) throws ClassNotFoundException {
		Class<?> result = super.findClass(name);
		Class<?> prev = archiveLoadedClasses.putIfAbsent(name, result);
		if (prev != null) {
			throw new AssertionError("Loaded class multiple times: " + name);
		}
		return result;
	}

	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		throw new ClassNotFoundException("This should never be called. (" + name + ")");
	}

	public Class<?> loadClassFromArchive(String name) throws ClassNotFoundException {
		synchronized (getClassLoadingLock(name)) {
			Class<?> c = getAlreadyLoadedClassByThisArchive(name);
			if (c != null) {
				return c;
			}
			return loadDefineClassFromArchive(name);
		}
	}

	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
		ClassNotFoundException e = null;
		Class<?> c;
		synchronized (getClassLoadingLock(name)) {
			c = getAlreadyLoadedClassByThisArchive(name);
			if (c == null) {
				try {
					c = Class.forName(name, false, getParent());
				} catch (ClassNotFoundException e2) {
					e = IOUtils.addExc(e, e2);
					try {
						c = loadDefineClassFromArchive(name);
					} catch (ClassNotFoundException e3) {
						e = IOUtils.addExc(e, e3);
					}
				}
			}
		}
		if (c == null) {
			for (NestRepositoryExternalArchiveClassLoader extcl : externalClassLoaderDomain) {
				if (extcl == this) {
					//don't try ourselves once more
					continue;
				}
				try {
					c = extcl.loadClassFromArchive(name);
					if (c != null) {
						break;
					}
				} catch (ClassNotFoundException e2) {
					e = IOUtils.addExc(e, e2);
				}
			}
		}
		if (c != null) {
			if (resolve) {
				resolveClass(c);
			}
			return c;
		}
		throw e;
	}
}
