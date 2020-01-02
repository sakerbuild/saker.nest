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
import saker.build.file.path.WildcardPath;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.nest.bundle.NestBundleStorageConfiguration;

/**
 * {@link BundleStorageView} subinterface that provides access to bundles specified by the configuration parameters.
 * <p>
 * The parameter bundle storage requires the user to specify one or more bundles to provide access to them through the
 * storage view. The specified bundles will be copied to an implementation dependent internal storage location from
 * where they are loaded into the storage view.
 * <p>
 * After a parameter storage view has been instantiated, the bundles specified by the parameters may be freely modified
 * without affecting the loaded bundles.
 * <p>
 * See {@link #PARAMETER_NEST_REPOSITORY_BUNDLES} for the way of specifying the bundles.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleStorageConfiguration#getParameterStorages()
 */
@PublicApi
public interface ParameterBundleStorageView extends BundleStorageView {
	/**
	 * The default name of a parameter bundle storage if omitted by the user in the configuration.
	 */
	public static final String DEFAULT_STORAGE_NAME = "params";

	/**
	 * Parameter specifying the bundles that should be provided by the storage view.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The parameter value is a semicolon (<code>;</code>) separated list of bundle paths or {@linkplain WildcardPath
	 * wildcards}. The bundle paths may be resolved either against the local file system, or against the
	 * {@linkplain RepositoryBuildEnvironment#getPathConfiguration() path configuration} of the build.
	 * <p>
	 * If a path is prefixed by double forward slashes (<code>//</code>), then the remaining part of the path will be
	 * interpreted against the local file system. Local file system paths must be absolute.
	 * <p>
	 * Extra semicolons in the parameter value are ignored.
	 * <p>
	 * E.g.
	 * 
	 * <pre>
	 * lib/*.jar;///usr/lib/my-jar.jar;wd:/lib/build-lib.jar
	 * </pre>
	 * 
	 * The above configuration will result in using the following bundles:
	 * <ul>
	 * <li><code>lib/*.jar</code>: All the bundles in the <code>lib</code> subdirectory of the working directory that has
	 * the <code>.jar</code> extension.</li>
	 * <li><code>///usr/lib/my-jar.jar</code>: Will use the <code>/usr/lib/my-jar.jar</code> from the local file
	 * system.</li>
	 * <li><code>wd:/lib/build-lib.jar</code>: Locates the bundle with the build execution path of
	 * <code>wd:/lib/build-lib.jar</code>. (Based on the path configuration.)</li>
	 * </ul>
	 * If the storage is not used in conjunction with a build execution then the path configuration depends on the
	 * environment that sets up the repository.
	 * <p>
	 * <i>Note</i>: Using the <code>//</code> prefix to specify local paths may not work when using build clusters.
	 */
	public static final String PARAMETER_NEST_REPOSITORY_BUNDLES = "bundles";
}
