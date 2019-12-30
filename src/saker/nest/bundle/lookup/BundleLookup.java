package saker.nest.bundle.lookup;

import java.util.Map;

import saker.apiextract.api.PublicApi;
import saker.nest.NestRepositoryFactory;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.NestBundleStorageConfiguration;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.exc.BundleLoadingFailedException;

/**
 * Lookup interface to resolve bundles.
 * <p>
 * This interface provides access to various resolution methods that look up appropriate information based on bundle
 * related inputs. In general it is used to query information about bundles, and the bundles themselves with a given
 * bundle identifier or bundle name.
 * <p>
 * Each bundle lookup has a different scope that it resolves information in. Based on the storage configuration that was
 * used to initialize the repository, each bundle lookup may resolve different bundles. It may be that if a bundle is
 * visible from a given bundle lookup, it may not be visible from a different one.
 * <p>
 * Bundle lookups are often relative to a given resolved bundle. This is necessary, as accidental misresolution of
 * bundles need to be avoided. <br>
 * Given the following example:
 * <p>
 * Given the storage configuration <code>[s1, s2, s3]</code>, and where the following bundles are contained in them:
 * <ul>
 * <li>s1: <code>bundle.dep</code></li>
 * <li>s2: <code>bundle.simple</code></li>
 * <li>s3: <code>bundle.dep</code></li>
 * </ul>
 * We can see that the bundle with name <code>bundle.dep</code> is present multiple times in <code>s1</code> and
 * <code>s2</code>. In this scenario, assume that <code>bundle.simple</code> has a dependency on
 * <code>bundle.dep</code>.
 * <p>
 * When we first resolve <code>bundle.simple</code> using the
 * {@linkplain NestBundleStorageConfiguration#getBundleLookup() root lookup}, we will find it in s2. After that, we need
 * to resolve <code>bundle.dep</code>, as <code>bundle.simple</code> depends on it. If we used the root lookup again, we
 * would find it in s1. However, that would be erroneous, as based on the storage configuration, we would have to use
 * the <code>bundle.dep</code> in s3.
 * <p>
 * In this case, instead of using the root lookup, we use the relative lookup of <code>bundle.simple</code> that doesn't
 * contain a back reference to s1, therefore correctly resolving <code>bundle.dep</code> in s3.
 * <p>
 * The resolution methods in this class also return an appropriate relative lookup object to use for further
 * resolutions.
 * <p>
 * Bundle lookups also have a {@link LookupKey} associated with them, that uniquely identifies a given lookup object in
 * the configuration. These keys may be serialized and used to
 * {@linkplain NestBundleStorageConfiguration#getBundleLookupForKey(LookupKey) query the lookup object} for them. This
 * can be used to properly support incremental builds and other operations.
 * <p>
 * This interface is not to be implemented by clients.
 * 
 * @see NestBundleStorageConfiguration#getBundleLookup()
 */
@PublicApi
public interface BundleLookup {
	/**
	 * Gets the unique lookup key for this bundle lookup.
	 * 
	 * @return The lookup key.
	 * @see NestBundleStorageConfiguration#getBundleLookupForKey(LookupKey)
	 */
	public LookupKey getLookupKey();

	/**
	 * Looks up a bundle with a given identifier.
	 * <p>
	 * The operation will attempt to load the {@link NestRepositoryBundle} for the given bundle identifier. If the
	 * bundle was not found, or cannot be loaded, {@link BundleLoadingFailedException} is thrown.
	 * <p>
	 * Some storages may require that the argument bundle identifier has a
	 * {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}.
	 * 
	 * @param bundleid
	 *            The bundle identifier to look up.
	 * @return The bundle lookup result. Never <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws BundleLoadingFailedException
	 *             If the bundle lookup failed.
	 * @see BundleStorageView#getBundle(BundleIdentifier)
	 */
	public BundleLookupResult lookupBundle(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException;

	/**
	 * Looks up a bundle information for the given identifier.
	 * <p>
	 * The operation will attempt to get the {@link BundleInformation} for the given bundle identifier. If the bundle
	 * was not found, or the information cannot be loaded, {@link BundleLoadingFailedException} is thrown.
	 * <p>
	 * Some storages may require that the argument bundle identifier has a
	 * {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}.
	 * 
	 * @param bundleid
	 *            The bundle identifier to look up the information for.
	 * @return The bundle information lookup result. Never <code>null</code>.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws BundleLoadingFailedException
	 *             If the bundle information lookup failed.
	 * @see BundleStorageView#getBundleInformation(BundleIdentifier)
	 */
	public BundleInformationLookupResult lookupBundleInformation(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException;

	/**
	 * Looks up the identifiers of bundles which only differ (or equal) in version number to the argument.
	 * <p>
	 * This method will search for bundles which have the same bundle name and qualifiers (except the version qualifier)
	 * as the argument. The found bundle identifiers are returned in the lookup result.
	 * <p>
	 * Any {@linkplain BundleIdentifier#getVersionQualifier() version qualifier} in the agument bundle identifier is
	 * ignored by this method.
	 * 
	 * @param bundleid
	 *            The bundle identifier to look up the versions for.
	 * @return The result of the lookup or <code>null</code> if no bundle identifiers were found.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @see BundleStorageView#lookupBundleVersions(BundleIdentifier)
	 */
	public BundleVersionLookupResult lookupBundleVersions(BundleIdentifier bundleid) throws NullPointerException;

	/**
	 * Look up bundle identifiers of bundles that have the same {@linkplain BundleIdentifier#getName() bundle name} as
	 * the argument.
	 * <p>
	 * The method will search for all bundles that have the same bundle name as the argument. The results are returned
	 * in the lookup result object.
	 * 
	 * @param bundlename
	 *            The bundle name to get the bundles of.
	 * @return The result of the lookup or <code>null</code> if no bundles found with the given name.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not a valid bundle name.
	 * @see BundleStorageView#lookupBundleIdentifiers(String)
	 */
	public BundleIdentifierLookupResult lookupBundleIdentifiers(String bundlename)
			throws NullPointerException, IllegalArgumentException;

	/**
	 * Creates a repository user parameter configuration that provides access to the bundles accessible from this bundle
	 * lookup.
	 * <p>
	 * This method can be used to create the user parameters that can be used to configure the repository the way that
	 * the same bundles are accessible as from this bundle lookup object.
	 * <p>
	 * This method is generally useful when external processes need to be spawned that use the classes the same way as
	 * the storage is currently configured from the view of this lookup.
	 * <p>
	 * One use-case for this is when Java compilation needs to be executed using a different JDK. In this case the
	 * compiler build task will spawn a new process that uses the saker.build runtime, loads the Nest repository, and
	 * the required compiler bundles to execute the compilation. However, in order for the new process to be able to
	 * locate the compiler bundles, the storage of the Nest repository needs to be configured appropriately. This method
	 * will create the configuration user parameters that can be directly passed to the spawned Nest repository to
	 * interpret.
	 * <p>
	 * Different bundle lookups may produce different results.
	 * <p>
	 * The returned parameters only contains
	 * {@link NestBundleStorageConfiguration#PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION} from the bundle storage
	 * configuration parameters. Constraints, task version pins, etc... are not included and need to be added manually.
	 * All appropriate storage view parameters will be included.
	 * <p>
	 * The created parameter map can only be used on the local file system.
	 * 
	 * @param repositoryid
	 *            The repository identifier to prefix the user parameters with. Specify <code>null</code> to use the
	 *            default. ({@value NestRepositoryFactory#IDENTIFIER})
	 * @return The created configuration.
	 * @throws UnsupportedOperationException
	 *             If the current configuration cannot be converted to parameters that can be used on the local file
	 *             system.
	 */
	public Map<String, String> getLocalConfigurationUserParameters(String repositoryid)
			throws UnsupportedOperationException;
}
