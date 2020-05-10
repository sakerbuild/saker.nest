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

import java.net.URI;
import java.net.URL;
import java.util.Map;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.util.java.JavaTools;
import saker.nest.NestRepository;
import saker.nest.bundle.lookup.BundleLookup;
import saker.nest.bundle.lookup.LookupKey;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.bundle.storage.LocalBundleStorageView;
import saker.nest.bundle.storage.ParameterBundleStorageView;
import saker.nest.bundle.storage.ServerBundleStorageView;
import saker.nest.bundle.storage.StorageViewKey;
import saker.nest.exc.BundleDependencyUnsatisfiedException;
import saker.nest.exc.BundleLoadingFailedException;
import saker.nest.exc.ExternalArchiveLoadingFailedException;

/**
 * The main interface that encloses configured bundle storages and provides access to them.
 * <p>
 * When the Nest repository is instantiated and configured, an instance of this interface will handle the access to the
 * configured storages. It is responsible for providing access to the {@linkplain #getStorages() storages}, the
 * {@link #getBundleLookup() root lookup}, {@linkplain #lookupTask(TaskName) build system tasks}, and the
 * {@linkplain #getBundleClassLoader(BundleLookup, BundleIdentifier) class loaders} for the bundles.
 * <p>
 * The configuration consists of named {@linkplain BundleStorageView bundle storage views}. The names are chosen
 * arbitrarily by the user, and may consist of any characters.
 * <p>
 * The configuration is initialized with {@linkplain #getDependencyConstraintConfiguration() dependency constraints}
 * that are used to appropriately handle dependencies.
 * <p>
 * The configuration also responsible for looking up and instantiating tasks for using them with the saker.build system.
 * <p>
 * The repository storage configuration can be customized defining appropriate values for the
 * {@linkplain ExecutionContext#getUserParameters() execution user parameters} when used with the build system. The
 * possible names of the parameters and their uses are defined with the <code>PARAMETER_*</code> constants.
 * <p>
 * Different storage views can be configured similarly, see the appropriate storage view interface for parameter and
 * usage information. ({@link ParameterBundleStorageView}, {@link LocalBundleStorageView},
 * {@link ServerBundleStorageView})
 * <p>
 * When not used with the saker.build system, consult the associated tool or environment for the possible opportunities
 * to pass these configuration parameters to the repository.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleClassLoader#getBundleStorageConfiguration()
 */
@PublicApi
public interface NestBundleStorageConfiguration {

	/**
	 * Parameter for configuring the bundle storages and their relations.
	 * <p>
	 * The parameter must be prefixed by the repository identifier in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The value of this parameter contains the configured storage names and their types. They can be specified in a
	 * <code>&lt;name&gt;:&lt;type&gt;</code> format where the name must only contains alphabetic (<code>a-z</code>,
	 * <code>A-Z</code>), numberic (<code>0-9</code>), or underscore (<code>_</code>) characters. The type must be one
	 * of the <code>STORAGE_TYPE_*</code> constants declared in this interface.
	 * <p>
	 * Multiple storage declarations can be specified in a list that is enclosed in brackets (<code>[</code>,
	 * <code>]</code>) and separated by commas (<code>,</code>). The list declarations can be nested. These subsequent
	 * storage declarations define the lookup behaviour assigned to each configuration.
	 * <p>
	 * The declared storages can be configured with parameters that begin with the repository identifier part that is
	 * the same as this parameter, and continues with the name of the storage and ends with the parameter name in a dot
	 * separated format. Parameters for bundle storages must have the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * <p>
	 * Examples:
	 * 
	 * <pre>
	 * "server: server"
	 * </pre>
	 * 
	 * The only storage configuration that is used is a {@linkplain #STORAGE_TYPE_SERVER server} storage. The
	 * configuration will only use bundles that is available from that server.
	 * <p>
	 * If the storage name is the same as the storage type, the storage name can be omitted. Specifying simply
	 * <code>":server"</code> is the same as the above declaration.
	 * 
	 * <pre>
	 * "[:params, :local, :server]"
	 * </pre>
	 * 
	 * The above is the default storage configuration if the parameter is not specified. It contains 3 storages:
	 * <ul>
	 * <li><code>params</code>: A {@linkplain ParameterBundleStorageView parameter storage}. Bundles from
	 * <code>local</code> and <code>server</code> is visible from it.</li>
	 * <li><code>local</code>: A {@linkplain LocalBundleStorageView local storage}. Bundles from <code>server</code> is
	 * visible to it, but bundles from <code>params</code> are not.</li>
	 * <li><code>server</code>: A {@linkplain ServerBundleStorageView server storage}. It can only use bundles that it
	 * contains.</li>
	 * </ul>
	 * The definiton place of storages affect what bundles they can use. This specifically important when dependencies
	 * are resolved in the repository. If a bundle from <code>params</code> depend on a bundle from <code>local</code>,
	 * the dependency can be successfully resolved. However, if a bundle in <code>local</code> tries to depend on a
	 * bundle from <code>params</code>, the dependency resolution will fail.
	 * 
	 * <pre>
	 * "[p1:params, p2:params]"
	 * </pre>
	 * 
	 * Multiple storages with the same type can be declared. In this case there are 2
	 * {@linkplain ParameterBundleStorageView parameter storages} in the specified order. The parameters
	 * <code>&lt;repo-id&gt;.p1.bundles</code> and <code>&lt;repo-id&gt;.p2.bundles</code> can be used to differently
	 * configure the two storages.
	 * 
	 * <pre>
	 * "[[p3:params, :local], p4:params]"
	 * </pre>
	 * 
	 * In the above example we use nested scopes to specify different lookup properties. In this case the bundles from
	 * <code>local</code> will be visible to <code>p3</code>. The bundles from <code>p4</code> will not be visible to
	 * either <code>p3</code> or <code>local</code>, as they've been declared in a different scope.
	 * <p>
	 * If we would want to make the bundles in <code>p4</code> visible to <code>p3</code>, but still keep them from
	 * <code>local</code>, we can use the following configuration:
	 * 
	 * <pre>
	 * "[p3:params, [:local], p4:params]"
	 * </pre>
	 * 
	 * In this case the bundles from <code>local</code> couldn't depend on <code>p4</code>, but the bundles in
	 * <code>p3</code> could use the bundles from <code>p4</code>.
	 * <p>
	 * The bundle storages can be repeated in order to make them accessible through multiple lookup scopes. However,
	 * when doing so, all declarations must have the same tail resolution. (That is, all repeating configurations must
	 * have the same lookup scope after them.) See the following:
	 * 
	 * <pre>
	 * "[[p5:params, :local], [p6:params, :local]]"
	 * </pre>
	 * 
	 * In this case both <code>p5</code> and <code>p6</code> have access to the bundles from <code>local</code>, while
	 * neither <code>p5</code> can depend on bundles from <code>p6</code>, and the <code>local</code> storage doesn't
	 * see bundles in <code>p6</code> as well.
	 * <p>
	 * Note that modifying the above configuration the following way will cause an initialization error:
	 * 
	 * <pre>
	 * "[[p5:params, :local], [p6:params, :local, :server]]"
	 * </pre>
	 * 
	 * This is due to the <code>local</code> storage has different tail resolution defined in the locations it appears.
	 * To have the <code>server</code> storage visible for <code>local</code> (and the parameter storages as well),
	 * modify it like the following:
	 * 
	 * <pre>
	 * "[[p5:params, :local, :server], [p6:params, :local, :server]]"
	 * </pre>
	 * 
	 * Putting the <code>server</code> storage in the outer scope (as in
	 * <code>"[[p5:params, :local], [p6:params, :local], :server]"</code>) will not work as that will be in a different
	 * scope, and visibility from outer scopes don't work.
	 * <p>
	 * The configuration value format allows the type for repeating declarations to be omitted. The following
	 * configurations are the same:
	 * 
	 * <pre>
	 * "[[p5:params, l:local], [p6:params, l:local]]"
	 * "[[p5:params, l:local], [p6:params, l:]]"
	 * "[[p5:params, l:], [p6:params, l:local]]"
	 * </pre>
	 */
	public static final String PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION = "repository.storage.configuration";
	/**
	 * Parameter for overriding the default Java Runtime version dependency constraint.
	 * <p>
	 * The parameter must be prefixed by the repository identifier in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The value of the parameter must be an integer that is greater or equals to 1. It will be used to constrain
	 * dependencies and bundle loading instead of the default value. Set it to <code>"null"</code> or empty string to
	 * disable the JRE major version constraint.
	 * <p>
	 * The default value is the current {@linkplain JavaTools#getCurrentJavaMajorVersion() Java Runtime major version}.
	 * <p>
	 * 
	 * @see DependencyConstraintConfiguration#getJreMajorVersion()
	 * @see BundleInformation#DEPENDENCY_META_JRE_VERSION
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS
	 */
	public static final String PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_JRE_MAJOR = "repository.constraint.force.jre.major";
	/**
	 * Parameter for overriding the native architecture dependency constraint.
	 * <p>
	 * The parameter must be prefixed by the repository identifier in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The value of the parameter may be any arbitrary string that corresponds to a valid native architecture. (e.g.
	 * <code>"x86"</code>, <code>"amd64"</code>) It is used to constrain dependencies and bundle loading instead of the
	 * default value. Set it to <code>"null"</code> or empty string to disable the native architecture constraint.
	 * <p>
	 * The default value is the current value of the {@link System#getProperties() "os.arch"} system property.
	 * 
	 * @see DependencyConstraintConfiguration#getNativeArchitecture()
	 * @see BundleInformation#DEPENDENCY_META_NATIVE_ARCHITECTURE
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_ARCHITECTURES
	 */
	public static final String PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_NATIVE_ARCHITECTURE = "repository.constraint.force.architecture";
	/**
	 * Parameter for overriding the Nest repository version dependency constraint.
	 * <p>
	 * The parameter must be prefixed by the repository identifier in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The value of the parameter must be a valid {@linkplain BundleIdentifier#isValidVersionNumber(String) version
	 * number}. It will be used to constrain dependencies and bundle loading instead of the default value. Set it to
	 * <code>"null"</code> or empty string to disable the repository version version constraint.
	 * <p>
	 * The default value is the current {@linkplain saker.nest.meta.Versions#VERSION_STRING_FULL full version} of the
	 * Nest repository.
	 * 
	 * @see DependencyConstraintConfiguration#getRepositoryVersion()
	 * @see BundleInformation#DEPENDENCY_META_REPOSITORY_VERSION
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS
	 */
	public static final String PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_REPOSITORY_VERSION = "repository.constraint.force.repo.version";
	/**
	 * Parameter for overriding the saker.build system version dependency constraint.
	 * <p>
	 * The parameter must be prefixed by the repository identifier in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The value of the parameter must be a valid {@linkplain BundleIdentifier#isValidVersionNumber(String) version
	 * number}. It will be used to constrain dependencies and bundle loading instead of the default value. Set it to
	 * <code>"null"</code> or empty string to disable the build system version version constraint.
	 * <p>
	 * The default value is the current {@linkplain saker.build.meta.Versions#VERSION_STRING_FULL full version} of the
	 * saker.build system.
	 * 
	 * @see DependencyConstraintConfiguration#getBuildSystemVersion()
	 * @see BundleInformation#DEPENDENCY_META_BUILD_SYSTEM_VERSION
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS
	 */
	public static final String PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_BUILD_SYSTEM_VERSION = "repository.constraint.force.buildsystem.version";
	/**
	 * Parameter for pinning a specific task version.
	 * <p>
	 * The parameter must be prefixed by the repository identifier in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The value of the parameter is a semicolon (<code>;</code>) separated list that specifies the task names and their
	 * pinned version numbers. When a task lookup request is served by the repository, it will only try to load the task
	 * that has the same version number as the pinned one. Note, that this only happens if the task lookup request has
	 * no version number specified already.
	 * <p>
	 * Value example:
	 * 
	 * <pre>
	 * my.task:1.0;my.task-q1:1.1;other.task:2.0
	 * </pre>
	 * 
	 * Extraneous semicolons and whitespace is omitted. The given pin configuration will cause the following build
	 * script to work in the following way:
	 * 
	 * <pre>
	 * my.task(...)      # will use my.task-v1.0
	 * my.task-v3.0(...) # will use my.task-v3.0, no override
	 * my.task-q1(...)   # will use my.task-q1-v1.1
	 * </pre>
	 * 
	 * If the repository fails to load the task with the pinned version, the lookup will fail, and no other versions
	 * will be searched for.
	 */
	public static final String PARAMETER_NEST_REPOSITORY_PIN_TASK_VERSION = "repository.pin.task.version";

	/**
	 * Storage type corresponding to the {@linkplain ParameterBundleStorageView parameter storage}.
	 * 
	 * @see #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION
	 */
	public static final String STORAGE_TYPE_PARAMETER = ParameterBundleStorageView.DEFAULT_STORAGE_NAME;
	/**
	 * Storage type corresponding to the {@linkplain LocalBundleStorageView local storage}.
	 * 
	 * @see #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION
	 */
	public static final String STORAGE_TYPE_LOCAL = LocalBundleStorageView.DEFAULT_STORAGE_NAME;
	/**
	 * Storage type corresponding to the {@linkplain ServerBundleStorageView server storage}.
	 * 
	 * @see #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION
	 */
	public static final String STORAGE_TYPE_SERVER = ServerBundleStorageView.DEFAULT_STORAGE_NAME;

	/**
	 * Gets the Nest repository instance that this storage configuration uses.
	 * 
	 * @return The repository instance.
	 */
	public NestRepository getRepository();

	/**
	 * Gets the root bundle lookup for this storage configuration.
	 * 
	 * @return The root bundle lookup.
	 */
	public BundleLookup getBundleLookup();

	/**
	 * Gets the bundle lookup in the storage configuration for the given lookup key.
	 * 
	 * @param key
	 *            The lookup key.
	 * @return The associated bundle lookup or <code>null</code> if not found.
	 */
	public BundleLookup getBundleLookupForKey(LookupKey key);

	/**
	 * Gets the {@link BundleStorageView} that is associated with the given storage view key in this storage
	 * configuration.
	 * <p>
	 * If no storage view is configured for the given storage key, <code>null</code> is returned.
	 * 
	 * @param key
	 *            The storage view key.
	 * @return The found {@link BundleStorageView} or <code>null</code> if the key is <code>null</code> or not found.
	 */
	public BundleStorageView getBundleStorageViewForKey(StorageViewKey key);

	/**
	 * Gets the local storages defined in this storage configuration.
	 * <p>
	 * The storages are mapped to their defined names by the user.
	 * 
	 * @return The local storages.
	 * @see #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION
	 */
	public Map<String, ? extends LocalBundleStorageView> getLocalStorages();

	/**
	 * Gets the parameter storages defined in this storage configuration.
	 * <p>
	 * The storages are mapped to their defined names by the user.
	 * 
	 * @return The parameter storages.
	 * @see #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION
	 */
	public Map<String, ? extends ParameterBundleStorageView> getParameterStorages();

	/**
	 * Gets the server storages defined in this storage configuration.
	 * <p>
	 * The storages are mapped to their defined names by the user.
	 * 
	 * @return The server storages.
	 * @see #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION
	 */
	public Map<String, ? extends ServerBundleStorageView> getServerStorages();

	/**
	 * Gets the bundle storage views in the configuration.
	 * <p>
	 * The bundle storages are mapped to their names. The names are user specified names to reference them in the
	 * configuration only containing characters specified in {@link #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION}.
	 * 
	 * @return All the bundle storage views present in the configuration.
	 * @see #PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION
	 */
	public Map<String, ? extends BundleStorageView> getStorages();

	/**
	 * Gets the {@link ClassLoader} for a bundle specified by the given identifier and looked up using the specified
	 * bundle lookup.
	 * <p>
	 * The method will use the argument bundle lookup to find the bundle with the given identifier, and will create the
	 * {@link ClassLoader} instance for it. The class loader will use the transitive dependencies declared in the bundle
	 * with the <code>classpath</code> kind.
	 * <p>
	 * This method doesn't create a new {@link ClassLoader} but reuse them if they've been already loaded.
	 * 
	 * @param lookup
	 *            The bundle lookup to find the bundle identifier or <code>null</code> to use the
	 *            {@linkplain #getBundleLookup() root lookup}.
	 * @param bundleid
	 *            The bundle identifier to create the {@link ClassLoader} for.
	 * @return The class loader for the bundle. The result implements {@link NestBundleClassLoader}.
	 * @throws NullPointerException
	 *             If the bundle identifier is <code>null</code>.
	 * @throws BundleLoadingFailedException
	 *             If the bundle wasn't found or failed to be loaded.
	 * @throws BundleDependencyUnsatisfiedException
	 *             If the dependencies of the bundle failed to be satisfied.
	 */
	public ClassLoader getBundleClassLoader(BundleLookup lookup, BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException, BundleDependencyUnsatisfiedException;

	/**
	 * Gets the {@link ClassLoader} for a bundle specified by the given bundle key.
	 * <p>
	 * The method will attempt to find the bundle in the associated storage, and resolve the dependencies in order to
	 * get the class loader.
	 * <p>
	 * The method may fail for the same conditions as {@link #getBundleClassLoader(BundleLookup, BundleIdentifier)}, and
	 * additionally if the associated bundle storage is not present in this configuration.
	 * 
	 * @param bundlekey
	 *            The bundle key to get the class loader of.
	 * @return The class loader for the bundle. The result implements {@link NestBundleClassLoader}.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws BundleLoadingFailedException
	 *             If the bundle wasn't found or failed to be loaded.
	 * @throws BundleDependencyUnsatisfiedException
	 *             If the dependencies of the bundle failed to be satisfied.
	 * @see #getBundleClassLoader(BundleLookup, BundleIdentifier)
	 */
	public ClassLoader getBundleClassLoader(BundleKey bundlekey)
			throws NullPointerException, BundleLoadingFailedException, BundleDependencyUnsatisfiedException;

	/**
	 * Gets the dependency constraint configuration that the storage configuration uses.
	 * <p>
	 * This constraint configuration is used when classpath dependencies are resolved during {@link ClassLoader}
	 * construction for bundles. Clients may but not required to use this when resolving dependencies themselves.
	 * 
	 * @return The current constraint configuration.
	 * @see #PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_JRE_MAJOR
	 * @see #PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_NATIVE_ARCHITECTURE
	 * @see #PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_BUILD_SYSTEM_VERSION
	 * @see #PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_REPOSITORY_VERSION
	 */
	public DependencyConstraintConfiguration getDependencyConstraintConfiguration();

	/**
	 * @see BuildRepository#lookupTask(TaskName)
	 */
	public TaskFactory<?> lookupTask(TaskName taskname) throws TaskNotFoundException;

	/**
	 * Loads the external archives from the argument dependency information.
	 * <p>
	 * The method will analyze the argument dependency information and load all dependency archives from it. Attachments
	 * (source and documentation) are only loaded if they are present and their targeted archives are also loaded.
	 * <p>
	 * The dependency kinds are <b>not</b> taken into account.
	 * <p>
	 * If you want to prevent loading specific archives, construct a new {@link ExternalDependencyInformation} that
	 * contains only the relevant information.
	 * <p>
	 * The {@link URI URIs} are loaded by converting them to an {@link URL} without any bundle storage specific
	 * behaviour. The resources are not loaded from any mirror servers whatsoever.
	 * <p>
	 * If you expect the resources to be loaded from a mirror (e.g. using the saker.nest repository server), then use
	 * {@link BundleStorageView#loadExternalArchives(ExternalDependencyInformation)} as that function takes bundle
	 * storage specific configuration into account.
	 * 
	 * @param depinfo
	 *            The dependency information.
	 * @return The loaded external archives. Mapped to their {@linkplain ExternalArchiveKey external archive keys}.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument dependency information contains invalid data. (E.g. multiple different hashes are
	 *             defined for a given {@link URI}.)
	 * @throws ExternalArchiveLoadingFailedException
	 *             If the loading failed.
	 * @since saker.nest 0.8.5
	 */
	public Map<? extends ExternalArchiveKey, ? extends ExternalArchive> loadExternalArchives(
			ExternalDependencyInformation depinfo)
			throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException;
}
