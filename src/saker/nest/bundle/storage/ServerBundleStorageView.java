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
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.nest.bundle.NestBundleStorageConfiguration;
import saker.nest.exc.OfflineStorageIOException;

/**
 * {@link BundleStorageView} subinterface that represents a bundle storage that retrieves and loads bundles from a
 * network server.
 * <p>
 * The storage view provides access to bundles accessible over the network from a server. The storage retrieves the
 * bundles using the HTTPS protocol. This storage can provide access to the Nest repository at
 * <code>{@value #REPOSITORY_DEFAULT_SERVER_URL}</code>.
 * <p>
 * The bundles in a server storage are immutable. The contents of a bundle with a given identifier cannot change over
 * time. The storage implementation caches the downloaded bundles locally to avoid unnecessary downloads.
 * <p>
 * The storage looks up bundles and build tasks using index files which contain a list of bundles that are available
 * from the server. These index files are also cached locally, and refreshed in certain intervals if necessary.
 * <p>
 * The storage supports using secondary URLs that serve as a mirror to the primary server, in order to distribute and
 * reduce load on the primary server.
 * <p>
 * Unless disabled, the storage performs integrity verification of the downloaded bundles. This is also done when the
 * bundles are loaded from the local cache. This ensures that only bundles which were actually downloaded from the given
 * server are used.
 * <p>
 * The server storage can work in an offline mode, in which case if an operation requires network access,
 * {@link OfflineStorageIOException} will be thrown. The storage can still work with locally cached bundles and
 * information.
 * <p>
 * Uploading bundles to a server can be done by using the functions in the {@link ServerStorageUtils} utility class.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleStorageConfiguration#getServerStorages()
 */
@PublicApi
public interface ServerBundleStorageView extends BundleStorageView {
	/**
	 * The default name of a server bundle storage if omitted by the user in the configuration.
	 */
	public static final String DEFAULT_STORAGE_NAME = "server";

	/**
	 * Specifies the server URL to be used by the storage view to access bundles.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 *
	 * In some case the {@linkplain #PARAMETER_SECONDARY_URL secondary URL} can take precedence when issuing requests
	 * for load balancing purposes.
	 * <p>
	 * The specified URL shouldn't have a slash separator at the end.
	 * 
	 * @see #getServerHost()
	 */
	public static final String PARAMETER_URL = "url";
	/**
	 * Specifies the secondary URL that can be used for load balancing when making network requests.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The secondary URL is used first to retrieve the bundle and task indexes from the server. As they might be often
	 * queried by clients, the secondary URL help taking the load off the primary server and lets it deal with other
	 * requests.
	 * <p>
	 * If the request to the secondary URL fails, the request will be tried again from the primary server.
	 * <p>
	 * Bundle downloads always use the primary URL.
	 * <p>
	 * The specified URL shouldn't have a slash separator at the end.
	 * <p>
	 * Secondary URL can be disabled by setting the empty or <code>"null"</code> value to it.
	 */
	public static final String PARAMETER_SECONDARY_URL = "url.secondary";
	/**
	 * Specifies if the server storage should operate in offline mode.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * If the storage is set to operate in offline mode, any operations that require making network requests will result
	 * in {@link OfflineStorageIOException}. The storage is still safe to operate on locally cached bundles and indexes.
	 * 
	 * @see #isOffline()
	 */
	public static final String PARAMETER_OFFLINE = "offline";
	/**
	 * Specifies if an attempt should be made to avoid using network caches when making requests.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * Various agents in the web can cache the results of requests from a given server. In some cases when using cached
	 * information from the server can cause build failures, as the build may expect the up-to-date information to
	 * properly finish.
	 * <p>
	 * These cases can happen if a package is just published, and the developer wants to use the latest version.
	 * However, the content delivery network will not yet serve the latest index files to the developer, as the cached
	 * copies hasn't expired yet. In these cases, the developer would need to wait out the cache expiration in order to
	 * be able to build their projects.
	 * <p>
	 * Specifying <code>true</code> to the value of this parameter will make the underlying storage issue requests to
	 * the servers that attempt to avoid retrieving the cached copies, and use the up-to-date indexes instead.
	 * <p>
	 * This parameter is mostly useful when running build on a CI, as in other environments the developer could manually
	 * force the index update if necessary.
	 * <p>
	 * In general, developers shouldn't use this parameter.
	 * <p>
	 * The default value of the parameter is based on the environment variables of the process. If there's an
	 * environment variable with <code>CI=true</code>, or <code>TF_BUILD=true</code> (values case insensitive), then the
	 * value of this parameter is <code>true</code>. Otherwise <code>false</code>.
	 * <p>
	 * (The environment variables are determined by known CI providers. While <code>CI</code> is supported by many,
	 * <code>TF_BUILD</code> is for <a href=
	 * "https://docs.microsoft.com/en-us/azure/devops/pipelines/build/variables?view=azure-devops&tabs=yaml">Azure
	 * Pipelines</a>.)
	 */
	public static final String PARAMETER_REQUESTS_UNCACHE = "requests.uncache";
	/**
	 * Specifies if the bundle signature verification should be disabled by the storage.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * If the value of this parameter equals to <code>"false"</code> in a case-insensitive manner, the storage will
	 * <b>not</b> verify the downloaded bundle integrity, and will load them without verifying that they've been
	 * downloaded from the expected server.
	 * <p>
	 * In general, developers shouldn't disable the signature verification, as errors in the verification process either
	 * signal an internal implementation error, security breach, or other serious issue with the supporting environment.
	 * In case you feel the need to disable it, it is recommended to throughly examine the cause of error and verify
	 * that it is acceptable to impose the security risk caused by the lack of verification.
	 */
	public static final String PARAMETER_SIGNATURE_VERIFICATION = "signature.verify";
	/**
	 * Parameter setting the minimum accepted server provided signature version.
	 * <p>
	 * The parameter must be prefixed by the repository identifier and storage name in the following format:
	 * 
	 * <pre>
	 * &lt;{@link RepositoryBuildEnvironment#getIdentifier() repo-id}&gt;.&lt;{@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION storage-name}&gt;.&lt;param&gt;
	 * </pre>
	 * 
	 * The expected value of this parameter is an integer that specifies the minimum version.
	 * <p>
	 * By default, the verification mechanism only accepts the latest signature version for a downloaded bundle.
	 * Lowering the minimum accepted version may result in increased security risk as it may allow the loading of
	 * bundles that were signed with a compromised private key.
	 * <p>
	 * Newer releases of the Nest repository runtime will always use the signature version that is the most recent at
	 * the time of release. However, there may be cases when the signature version is increased by the server and older
	 * releases of the repository needs to be used by the developer. In these cases it is recommended to use the
	 * parameter to <i>increase</i> the minimum accepted signature version to the one that is provided by the server.
	 * This will prevent loading cached bundles that was signed with signature version more recent than the repository
	 * runtime release, but older than the version provided by the server.
	 * <p>
	 * In general, if the value of this parameter needs to be set below the version that is currently advertised by the
	 * server, that can signal serious issues in relation of security or internal implementation. Make sure to examine
	 * throughly the possible security consequences of such action.
	 */
	public static final String PARAMETER_MIN_SIGNATURE_VERSION = "signature.version.min";

	/**
	 * The default server URL that is used when no {@linkplain #PARAMETER_URL server URL parameter} is specified.
	 */
	public static final String REPOSITORY_DEFAULT_SERVER_URL = "https://api.nest.saker.build";

	/**
	 * Gets the URL to the server that this storage issues requests to.
	 * <p>
	 * This method returns the server URL even if it is in {@linkplain #isOffline() offline mode}.
	 * 
	 * @return The server host.
	 * @see #PARAMETER_URL
	 */
	public String getServerHost();

	/**
	 * Checks if this server storage view was configured to operate in offline mode.
	 * 
	 * @return <code>true</code> if the storage is offline.
	 * @see #PARAMETER_OFFLINE
	 */
	public boolean isOffline();
}
