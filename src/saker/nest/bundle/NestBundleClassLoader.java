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

import java.util.Map;

import saker.apiextract.api.PublicApi;
import saker.nest.NestRepository;
import saker.nest.bundle.lookup.BundleLookup;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.bundle.storage.StorageViewKey;

//only implementation is NestRepositoryBundleClassLoader
/**
 * Interface implemented by {@link ClassLoader ClassLoaders} that load classes from bundles.
 * <p>
 * This interface is implemented by all class loaders which load classes from bundles of the Nest repository. You can
 * retrieve an instance of it by using the following:
 * 
 * <pre>
 * NestBundleClassLoader nestcl = (NestBundleClassLoader) this.getClass().getClassLoader();
 * </pre>
 * 
 * That is assuming that the class corresponding to <code>this</code> in the scope was loaded by the Nest repository.
 * <p>
 * This interface servers as the main communication point between classes loaded by the Nest repository and the Nest
 * repository.
 * <p>
 * Using this interface clients can query various aspects of the current configuration, look up other bundles, and
 * access their contents.
 * <p>
 * This interface is not to be implemented by clients.
 */
@PublicApi
public interface NestBundleClassLoader {
	/**
	 * Gets the repository instance that this class loader is part of.
	 * 
	 * @return The repository instance.
	 */
	public default NestRepository getRepository() {
		return getBundleStorageConfiguration().getRepository();
	}

	/**
	 * Gets the storage configuration that this bundle is part of.
	 * 
	 * @return The storage configuration.
	 */
	public NestBundleStorageConfiguration getBundleStorageConfiguration();

	/**
	 * Gets the bundle that this class loader loads the classes from.
	 * 
	 * @return The bundle.
	 */
	public NestRepositoryBundle getBundle();

	/**
	 * Gets the class loaders for the class path dependencies of this class loader.
	 * <p>
	 * The resulting map contains <b>direct</b> dependencies of this bundle, with optional classpath dependencies
	 * included if possible.
	 * <p>
	 * The result also contains the {@linkplain BundleInformation#DEPENDENCY_META_PRIVATE private} dependencies as well,
	 * however, it doesn't tell you if a dependency was included through a private dependency or not.
	 * <p>
	 * Transitive dependencies are not included, only the direct ones.
	 * <p>
	 * The result of this method is the classpath dependencies that were resolved by the Nest repository when this class
	 * loader was constructed. The classes loaded by this class loader have the classes accessible through the returned
	 * class loaders (transitively).
	 * 
	 * @return The immutable map of class path dependencies.
	 */
	public Map<? extends BundleKey, ? extends NestBundleClassLoader> getClassPathDependencies();

	/**
	 * Gets the classloaders for the external class path dependencies of this class loader.
	 * <p>
	 * The resulting map contains the declared {@linkplain BundleInformation#getExternalDependencyInformation() external
	 * dependencies} and their corresponding class loaders.
	 * 
	 * @return An immutable map of external class path dependencies.
	 * @since saker.nest 0.8.5
	 */
	public Map<? extends ExternalArchiveKey, ? extends ExternalArchiveClassLoader> getExternalClassPathDependencies();

	/**
	 * Gets a hash created from this bundle and all class path dependencies included transitively.
	 * <p>
	 * The returned array is constructed by hashing the {@linkplain NestRepositoryBundle#getHash() hashes} of the
	 * transitively available bundles on the classpath in deterministic order.
	 * <p>
	 * The hash algorithm is implementation dependent.
	 * 
	 * @return An array of bytes that is the hash of the entire classpath available to this classloader. Modifications
	 *             do not propagate back, i.e. the array is cloned when returned.
	 * @see NestRepositoryBundle#getHash()
	 */
	public byte[] getBundleHashWithClassPathDependencies();

	/**
	 * Gets the bundle lookup object that can be used to resolve bundles relative to this bundle.
	 * 
	 * @return The bundle lookup.
	 */
	public BundleLookup getRelativeBundleLookup();

	/**
	 * Gets the bundle key this classloader is associated with.
	 * <p>
	 * The returned bundle key can be used to retrieve the {@linkplain BundleStorageView bundle storage view} that was
	 * used to construct this classloader.
	 * 
	 * @return The bundle key.
	 * @since saker.nest 0.8.5
	 * @see NestBundleStorageConfiguration#getBundleStorageViewForKey(StorageViewKey)
	 */
	public BundleKey getBundleKey();
}
