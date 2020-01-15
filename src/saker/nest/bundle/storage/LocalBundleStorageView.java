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

import java.io.IOException;

import saker.apiextract.api.PublicApi;
import saker.build.file.StreamWritable;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.NestBundleStorageConfiguration;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.exc.InvalidNestBundleException;

/**
 * {@link BundleStorageView} subinterface that represents a bundle storage backed by the local file system.
 * <p>
 * The bundles in this storage is stored on the local file system in a directory. This directory is under an
 * implementation dependent directory based on the {@linkplain RepositoryEnvironment#getRepositoryStorageDirectory()
 * repository storage directory} by default.
 * <p>
 * The bundles in the storage are accessible to all processes on the local machine. The storage supports concurrent
 * reading and modification of the storage by different processes.
 * <p>
 * Bundles can be added to the storage using the {@link #install(StreamWritable)} function.
 * <p>
 * Using the local bundle storage with build clusters may not work.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleStorageConfiguration#getLocalStorages()
 */
@PublicApi
public interface LocalBundleStorageView extends BundleStorageView {
	/**
	 * The default name of a local bundle storage if omitted by the user in the configuration.
	 */
	public static final String DEFAULT_STORAGE_NAME = "local";

	/**
	 * Parameter specifying the root storage directory for the local bundle storage.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The parameter value can be an absolute path on the local file system that specifies the root directory that the
	 * local storage should use to store and retrieve bundles.
	 * <p>
	 * It can also be a relative path in wich case it will be resolved against the
	 * {@linkplain RepositoryEnvironment#getRepositoryStorageDirectory() repository storage directory}.
	 * <p>
	 * The default value is in an implementation dependent subdirectory of the
	 * {@linkplain RepositoryEnvironment#getRepositoryStorageDirectory() repository storage directory}.
	 */
	public static final String PARAMETER_ROOT = "root";

	/**
	 * Installs the bundle specified by the argument contents to this bundle storage.
	 * <p>
	 * This method will examine and validate the bundle contents, and will install it to the local storage. If there are
	 * already bundles with the same identifier, they will be overwritten.
	 * <p>
	 * When this method is called during a build execution, the contents of the installed bundle will not be visible in
	 * the same build execution that was used to install it. This is to avoid ambiguity, and encourage deterministic
	 * builds. Therefore any tasks that are present in the bundle cannot be used in the build that installed the bundle.
	 * <p>
	 * The method implementation may not install the bundle to its target location right away. To ensure concurrent
	 * access to the storage, the implementation may place the bundle to a pending location that is in a different
	 * location than the usual one. These pending bundles will be installed to their final location when the storage
	 * implementation sees appropriate. Normally users don't need to specially handle this scenario in any way.
	 * <p>
	 * After this method returns, the bundle contents represented by the argument may be freely modified without having
	 * an effect on the installed bundle.
	 * 
	 * @param bundlecontents
	 *            The bundle contents.
	 * @return The result of the bundle installation.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws UnsupportedOperationException
	 *             If bundle installation is not supported to this storage.
	 * @throws InvalidNestBundleException
	 *             If the bundle content validation failed.
	 */
	public InstallResult install(StreamWritable bundlecontents)
			throws NullPointerException, IOException, UnsupportedOperationException, InvalidNestBundleException;

	/**
	 * Holds information about a bundle installation operation.
	 * <p>
	 * This interface is not to be implemented by clients.
	 */
	public interface InstallResult {
		/**
		 * Gets the identifier of the bundle that was installed.
		 * 
		 * @return The bundle identifier.
		 */
		public BundleIdentifier getBundleIdentifier();

		/**
		 * Gets the hash of the bundle contents.
		 * <p>
		 * The hash is computed the same way as it is by {@link NestRepositoryBundle#getHash()}.
		 * 
		 * @return The byte array of the hash. Modifications made to the array may or may not propagate.
		 */
		public byte[] getBundleHash();

	}
}
