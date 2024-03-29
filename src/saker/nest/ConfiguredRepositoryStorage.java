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
package saker.nest;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import saker.build.file.path.WildcardPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.io.FileUtils;
import saker.build.util.java.JavaTools;
import saker.nest.bundle.AbstractExternalArchive;
import saker.nest.bundle.AbstractNestRepositoryBundle;
import saker.nest.bundle.BundleDependency;
import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleDependencyList;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.BundleKey;
import saker.nest.bundle.BundleUtils;
import saker.nest.bundle.DependencyConstraintConfiguration;
import saker.nest.bundle.ExternalArchive;
import saker.nest.bundle.ExternalArchiveKey;
import saker.nest.bundle.ExternalDependency;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.ExternalDependencyList;
import saker.nest.bundle.NestBundleStorageConfiguration;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.NestRepositoryBundleClassLoader;
import saker.nest.bundle.NestRepositoryBundleClassLoader.DependentClassLoader;
import saker.nest.bundle.NestRepositoryExternalArchiveClassLoader;
import saker.nest.bundle.SimpleBundleKey;
import saker.nest.bundle.SimpleDependencyConstraintConfiguration;
import saker.nest.bundle.SimpleExternalArchiveKey;
import saker.nest.bundle.lookup.AbstractBundleLookup;
import saker.nest.bundle.lookup.BundleLookup;
import saker.nest.bundle.lookup.BundleVersionLookupResult;
import saker.nest.bundle.lookup.LookupKey;
import saker.nest.bundle.lookup.MultiBundleLookup;
import saker.nest.bundle.lookup.SimpleBundleLookupResult;
import saker.nest.bundle.lookup.SingleBundleLookup;
import saker.nest.bundle.lookup.TaskLookupInfo;
import saker.nest.bundle.storage.AbstractBundleStorage;
import saker.nest.bundle.storage.AbstractBundleStorageView;
import saker.nest.bundle.storage.AbstractServerBundleStorageView;
import saker.nest.bundle.storage.AbstractStorageKey;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.bundle.storage.LocalBundleStorage;
import saker.nest.bundle.storage.LocalBundleStorageView;
import saker.nest.bundle.storage.ParameterBundleStorage;
import saker.nest.bundle.storage.ParameterBundleStorageView;
import saker.nest.bundle.storage.ServerBundleStorage;
import saker.nest.bundle.storage.StorageSharedObjectAccessor;
import saker.nest.bundle.storage.StorageViewEnvironment;
import saker.nest.bundle.storage.StorageViewKey;
import saker.nest.dependency.DependencyDomainResolutionResult;
import saker.nest.dependency.DependencyResolutionLogger;
import saker.nest.dependency.DependencyUtils;
import saker.nest.exc.BundleDependencyUnsatisfiedException;
import saker.nest.exc.BundleLoadingFailedException;
import saker.nest.exc.ExternalArchiveLoadingFailedException;
import saker.nest.meta.Versions;
import saker.nest.thirdparty.org.json.JSONArray;
import saker.nest.thirdparty.org.json.JSONObject;
import saker.nest.utils.IdentityComparisonPair;
import saker.nest.utils.NonSpaceIterator;
import testing.saker.nest.TestFlag;

public class ConfiguredRepositoryStorage implements Closeable, NestBundleStorageConfiguration {

	@SuppressWarnings("unchecked")
	private static final List<?> DEFAULT_STORAGE_CONFIG = ImmutableUtils.asUnmodifiableArrayList(
			ImmutableUtils.makeImmutableMapEntry(STORAGE_TYPE_PARAMETER, STORAGE_TYPE_PARAMETER),
			ImmutableUtils.makeImmutableMapEntry(STORAGE_TYPE_LOCAL, STORAGE_TYPE_LOCAL),
			ImmutableUtils.makeImmutableMapEntry(STORAGE_TYPE_SERVER, STORAGE_TYPE_SERVER));
	private static final Map<String, String> DEFAULT_STORAGE_NAME_TYPES = new TreeMap<>();
	static {
		DEFAULT_STORAGE_NAME_TYPES.put(STORAGE_TYPE_PARAMETER, STORAGE_TYPE_PARAMETER);
		DEFAULT_STORAGE_NAME_TYPES.put(STORAGE_TYPE_LOCAL, STORAGE_TYPE_LOCAL);
		DEFAULT_STORAGE_NAME_TYPES.put(STORAGE_TYPE_SERVER, STORAGE_TYPE_SERVER);
	}
	private static final NavigableSet<String> STORAGE_CONFIGURATION_TYPES = ImmutableUtils.makeImmutableNavigableSet(
			new String[] { STORAGE_TYPE_PARAMETER, STORAGE_TYPE_LOCAL, STORAGE_TYPE_SERVER, });

	private static final NavigableSet<String> RESERVED_STORAGE_NAMES = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { "all", "repository" });

	private static final NavigableSet<String> CLASSPATH_DEPENDENCY_KIND_SINGLETON = ImmutableUtils
			.singletonNavigableSet(BundleInformation.DEPENDENCY_KIND_CLASSPATH);

	private static final Pattern PATTERN_SEMICOLON_SPACES_SPLIT = Pattern.compile("[; \\t]+");

	private final NestRepositoryImpl repository;
	private final AbstractBundleLookup lookupConfiguration;
	private final Map<String, AbstractBundleLookup> storageKeyIdentifierBundleLookups = new TreeMap<>();
	private final Map<LookupKey, BundleLookup> lookupKeyBundleLookups = new HashMap<>();
	private final Map<BundleLookup, String> bundleLookupStorageIdentifiers = new IdentityHashMap<>();

	private final transient Map<String, AbstractBundleStorageView> stringIdentifierStorageViews = new TreeMap<>();
	private final transient Map<AbstractBundleStorageView, String> storageViewStringIdentifiers = new HashMap<>();
	private final transient Map<StorageViewKey, AbstractBundleStorageView> storageViewKeyStorageViews = new HashMap<>();
	private final transient Map<StorageViewKey, String> storageViewKeyStringIdentifiers = new HashMap<>();

	private final Map<TaskName, TaskName> pinnedTaskVersion = new TreeMap<>();

	private volatile boolean closed = false;
	private final Object classLoaderLock = new Object();
	private final ConcurrentHashMap<NestRepositoryBundle, NestRepositoryBundleClassLoader> classLoaders = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ClassLoaderDomain, NestRepositoryBundleClassLoader> domainClassLoaders = new ConcurrentHashMap<>();

	private transient final ConcurrentSkipListMap<TaskName, Supplier<? extends TaskFactory<?>>> taskClasses = new ConcurrentSkipListMap<>();

	private final DependencyConstraintConfiguration constraintConfiguration;

	private final Object detectChangeLock = new Object();
	private DetectedChanges expectedDetectedChanges;

	public static ConfiguredRepositoryStorage forBuildRepository(NestRepositoryImpl nestRepository,
			RepositoryBuildEnvironment environment) {
		SakerFileProvider localfp;
		boolean remotecluster;
		if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_015) {
			localfp = environment.getLocalFileProvider();
			remotecluster = environment.isRemoteCluster();
		} else {
			localfp = LocalFileProvider.getInstance();
			remotecluster = false;
		}
		return new ConfiguredRepositoryStorage(nestRepository, environment.getIdentifier(),
				environment.getPathConfiguration(), environment.getUserParameters(),
				new RepositoryBuildEnvironmentStorageSharedObjectAccessor(environment), remotecluster, localfp);
	}

	public static ConfiguredRepositoryStorage forRepositoryAction(NestRepositoryImpl repository, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> parameters) {
		return new ConfiguredRepositoryStorage(repository, repoid, pathconfig, parameters,
				StorageSharedObjectAccessor.NULL_ACCESSOR, false, LocalFileProvider.getInstance());
	}

	private ConfiguredRepositoryStorage(NestRepositoryImpl repository, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> parameters,
			StorageSharedObjectAccessor sharedobjecthandler, boolean remotecluster,
			SakerFileProvider localfileprovider) {
		this.repository = repository;

		final Integer classPathJreMajorVersion;
		final String classPathRepositoryVersion;
		final String classPathBuildSystemVersion;
		final String nativeLibraryOsArchitecture;
		{
			String forcedmajorparamname = repoid + "." + PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_JRE_MAJOR;
			String forcedmajorstr = parameters.get(forcedmajorparamname);
			if (forcedmajorstr != null) {
				Integer parsed;
				if ("null".equals(forcedmajorstr) || forcedmajorstr.isEmpty()) {
					parsed = null;
				} else {
					try {
						parsed = Integer.parseInt(forcedmajorstr);
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException("Failed to parse argument for: " + forcedmajorparamname
								+ " : " + forcedmajorstr + " (" + repoid + ")", e);
					}
					//any value under 8 doesn't make sense, but currently we leave that decision to the user
					//any value that is 0 or negative is invalid
					//any value that is greater than the latest release doesn't make sense either
					if (parsed < 1) {
						throw new IllegalArgumentException(
								"Invalid forced classpath JRE version: " + parsed + " (" + repoid + ")");
					}
				}
				classPathJreMajorVersion = parsed;
			} else {
				classPathJreMajorVersion = JavaTools.getCurrentJavaMajorVersion();
			}
		}
		{
			String forcedrepoversionparamname = repoid + "."
					+ PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_REPOSITORY_VERSION;
			String forcedrepoversionstr = parameters.get(forcedrepoversionparamname);
			if (forcedrepoversionstr != null) {
				if ("null".equals(forcedrepoversionstr) || forcedrepoversionstr.isEmpty()) {
					classPathRepositoryVersion = null;
				} else {
					if (!BundleIdentifier.isValidVersionNumber(forcedrepoversionstr)) {
						throw new IllegalArgumentException(
								"Invalid forced repository version: " + forcedrepoversionstr + " (" + repoid + ")");
					}
					classPathRepositoryVersion = forcedrepoversionstr;
				}
			} else {
				classPathRepositoryVersion = Versions.VERSION_STRING_FULL;
			}
		}
		{
			String forcedbuildsystemversionparamname = repoid + "."
					+ PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_BUILD_SYSTEM_VERSION;
			String forcedbuildsystemversionstr = parameters.get(forcedbuildsystemversionparamname);
			if (forcedbuildsystemversionstr != null) {
				if ("null".equals(forcedbuildsystemversionstr) || forcedbuildsystemversionstr.isEmpty()) {
					classPathBuildSystemVersion = null;
				} else {
					if (!BundleIdentifier.isValidVersionNumber(forcedbuildsystemversionstr)) {
						throw new IllegalArgumentException("Invalid forced build system version: "
								+ forcedbuildsystemversionstr + " (" + repoid + ")");
					}
					classPathBuildSystemVersion = forcedbuildsystemversionstr;
				}
			} else {
				classPathBuildSystemVersion = saker.build.meta.Versions.VERSION_STRING_FULL;
			}
		}
		{
			String forcedlibosarchparamname = repoid + "."
					+ PARAMETER_NEST_REPOSITORY_CONSTRAINT_FORCE_NATIVE_ARCHITECTURE;
			String forcedlibosarch = parameters.get(forcedlibosarchparamname);
			if (forcedlibosarch != null) {
				if ("null".equals(forcedlibosarch) || forcedlibosarch.isEmpty()) {
					nativeLibraryOsArchitecture = null;
				} else {
					nativeLibraryOsArchitecture = forcedlibosarch;
				}
			} else {
				nativeLibraryOsArchitecture = System.getProperty("os.arch");
			}
		}

		this.constraintConfiguration = new SimpleDependencyConstraintConfiguration(classPathJreMajorVersion,
				classPathRepositoryVersion, classPathBuildSystemVersion, nativeLibraryOsArchitecture);

		String storageconfigparamname = repoid + "." + PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION;
		String storageconfigparam = parameters.get(storageconfigparamname);
		List<?> storageconfig;
		Map<String, String> storagenametypes;
		if (storageconfigparam != null) {
			storageconfig = parseStorageConfigurationUserParameter(storageconfigparam);
			storagenametypes = getStorageConfigurationNameTypes(storageconfig);
		} else {
			storageconfig = DEFAULT_STORAGE_CONFIG;
			storagenametypes = DEFAULT_STORAGE_NAME_TYPES;
		}
		Map<String, StorageInitializationInfo> namedstorageinitializers = new TreeMap<>();
		for (Entry<String, String> entry : storagenametypes.entrySet()) {
			String storagename = entry.getKey();
			if (isReservedStorageName(storagename)) {
				throw new IllegalArgumentException("The storage name is reserved: " + storagename);
			}
			String startwithcheck = repoid + "." + storagename + ".";
			NavigableMap<String, String> storageuserparams = new TreeMap<>();
			for (Entry<String, String> paramentry : parameters.entrySet()) {
				if (paramentry.getKey().startsWith(startwithcheck)) {
					storageuserparams.put(paramentry.getKey().substring(startwithcheck.length()),
							paramentry.getValue());
				}
			}
			StorageInitializationInfo initinfo;
			switch (entry.getValue()) {
				case STORAGE_TYPE_LOCAL: {
					initinfo = new StorageInitializationInfo(
							LocalBundleStorage.LocalStorageKey.create(repository, storageuserparams),
							storageuserparams);
					break;
				}
				case STORAGE_TYPE_PARAMETER: {
					initinfo = new StorageInitializationInfo(
							ParameterBundleStorage.ParameterStorageKey.create(repository, storageuserparams),
							storageuserparams);
					break;
				}
				case STORAGE_TYPE_SERVER: {
					initinfo = new StorageInitializationInfo(
							ServerBundleStorage.ServerStorageKey.create(repository, storageuserparams),
							storageuserparams);
					break;
				}
				default: {
					throw new AssertionError("Unknown storage type: " + entry.getValue());
				}
			}
			namedstorageinitializers.put(storagename, initinfo);
		}
		for (Entry<String, StorageInitializationInfo> entry : namedstorageinitializers.entrySet()) {
			String storageviewname = entry.getKey();
			StorageInitializationInfo initinfo = entry.getValue();

			AbstractBundleStorage storage = repository.loadStorage(initinfo.storageKey);
			AbstractBundleStorageView storageview = storage.newStorageView(new StorageViewEnvironmentImpl(pathconfig,
					sharedobjecthandler, remotecluster, initinfo.userParameters, storageviewname, localfileprovider));

			String storageviewstringid = createStorageViewStringIdentifier(storageview);

			initinfo.storageView = storageview;
			initinfo.storageViewStringIdentifier = storageviewstringid;

			stringIdentifierStorageViews.put(storageviewstringid, storageview);
			storageViewStringIdentifiers.put(storageview, storageviewstringid);
			storageViewKeyStorageViews.put(storageview.getStorageViewKey(), storageview);
			storageViewKeyStringIdentifiers.put(storageview.getStorageViewKey(), storageviewstringid);
		}

		this.lookupConfiguration = createBundleLookupForConfiguration(storageconfig, namedstorageinitializers);
		for (Entry<String, AbstractBundleLookup> entry : storageKeyIdentifierBundleLookups.entrySet()) {
			AbstractBundleLookup lookup = entry.getValue();
			bundleLookupStorageIdentifiers.put(lookup, entry.getKey());
			lookupKeyBundleLookups.put(lookup.getLookupKey(), lookup);
		}
		lookupKeyBundleLookups.put(this.lookupConfiguration.getLookupKey(), this.lookupConfiguration);

		String pintaskversionsparamname = repoid + "." + PARAMETER_NEST_REPOSITORY_PIN_TASK_VERSION;
		String pintaskversionsparam = parameters.get(pintaskversionsparamname);
		if (!ObjectUtils.isNullOrEmpty(pintaskversionsparam)) {
			for (String taskpin : PATTERN_SEMICOLON_SPACES_SPLIT.split(pintaskversionsparam)) {
				if (ObjectUtils.isNullOrEmpty(taskpin)) {
					continue;
				}
				int colonidx = taskpin.indexOf(':');
				if (colonidx < 0) {
					throw new IllegalArgumentException("Task version pin parameter: " + taskpin
							+ " has invalid format. Expected format: <task-name>:<version-num>");
				}
				String vernum = taskpin.substring(colonidx + 1).trim();
				if (!BundleIdentifier.isValidVersionNumber(vernum)) {
					throw new IllegalArgumentException("Task version pin parameter: " + taskpin
							+ " has invalid version number. Expected format: <task-name>:<version-num>");
				}
				TaskName tn;
				try {
					tn = TaskName.valueOf(taskpin.substring(0, colonidx).trim());
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("Task version pin parameter: " + taskpin
							+ " has invalid task name. Expected format: <task-name>:<version-num>", e);
				}
				if (BundleIdentifier.hasVersionQualifier(tn.getTaskQualifiers())) {
					throw new IllegalArgumentException("Task version pin parameter: " + taskpin
							+ " must not contain version qualifiers for the task name. Expected format: <task-name>:<version-num>");
				}
				TreeSet<String> nqualifiers = new TreeSet<>(tn.getTaskQualifiers());
				nqualifiers.add(BundleIdentifier.makeVersionQualifier(vernum));
				TaskName pinnedtn = TaskName.valueOf(tn.getName(), nqualifiers);
				TaskName prev = pinnedTaskVersion.putIfAbsent(tn, pinnedtn);
				if (prev != null && !prev.equals(pinnedtn)) {
					throw new IllegalArgumentException("Multiple pinned task versions: " + prev + " and " + pinnedtn);
				}
			}
		}
	}

	private static String createStorageViewStringIdentifier(AbstractBundleStorageView storageview)
			throws AssertionError {
		MessageDigest infodigest;
		try {
			infodigest = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError("JVM doesn't support MD5 hash.", e);
		}
		storageview.updateStorageViewHash(infodigest);
		return StringUtils.toHexString(infodigest.digest());
	}

	private static boolean isReservedStorageName(String storagename) {
		return RESERVED_STORAGE_NAMES.contains(storagename);
	}

	public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
		return lookupConfiguration.getPresentTaskNamesForInformationProvider();
	}

	public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
		return lookupConfiguration.getPresentBundlesForInformationProvider();
	}

	@Override
	public DependencyConstraintConfiguration getDependencyConstraintConfiguration() {
		return constraintConfiguration;
	}

	@Override
	public NestRepositoryImpl getRepository() {
		return repository;
	}

	public AbstractBundleLookup getLookupConfiguration() {
		return lookupConfiguration;
	}

	public String getClassLoaderReconstructionIdentifier(NestRepositoryBundleClassLoader nestbundlecl) {
		ClassLoaderDomain cldomain = ClassLoaderDomain.fromClassLoader(nestbundlecl);
		return domainToReconstructionString(cldomain);
	}

	public NestRepositoryBundleClassLoader getBundleClassLoaderForReconstructionIdentifier(String reconstructionid) {
		ClassLoaderDomain domain = domainFromReconstructionString(reconstructionid);
		if (domain == null) {
			return null;
		}
		synchronized (classLoaderLock) {
			return createDomainClassLoaderLockedImpl(domain, (d, e) -> {
				//XXX log exception?
				if (TestFlag.ENABLED) {
					e.printStackTrace();
				}
				return;
			});
		}
	}

	private String bundleKeyToReconstructionIdentifier(BundleKey bk) {
		return bk.getBundleIdentifier() + "|" + storageViewKeyStringIdentifiers.get(bk.getStorageViewKey());
	}

	private BundleKey reconstructionIdentifierToBundleKey(String reconstid) {
		if (reconstid == null) {
			return null;
		}
		int idx = reconstid.indexOf('|');
		if (idx < 0) {
			return null;
		}
		try {
			AbstractBundleStorageView storageview = stringIdentifierStorageViews.get(reconstid.substring(idx + 1));
			if (storageview == null) {
				return null;
			}
			BundleIdentifier bundleid = BundleIdentifier.valueOf(reconstid.substring(0, idx));
			return BundleKey.create(storageview.getStorageViewKey(), bundleid);
		} catch (IllegalArgumentException e) {
		}
		return null;
	}

	private String domainToReconstructionString(ClassLoaderDomain domain) {
		JSONObject obj = new JSONObject();
		obj.put("@bk", bundleKeyToReconstructionIdentifier(domain.bundle));
		domainToReconstructionJSONImpl(domain, obj, new HashMap<>());
		return obj.toString();
	}

	private void domainToReconstructionJSONImpl(ClassLoaderDomain domain, JSONObject obj,
			Map<ClassLoaderDomain, Integer> backreferences) {
		Integer prevbackref = backreferences.get(domain);
		if (prevbackref != null) {
			obj.put("@r", prevbackref.intValue());
			return;
		}
		int backrefnum = backreferences.size();
		backreferences.put(domain, backrefnum);
		obj.put("@i", backrefnum);
		if (!domain.dependencies.isEmpty()) {
			JSONArray depsarray = new JSONArray();
			for (Entry<BundleKey, ClassLoaderDomain.DomainDependency> entry : domain.dependencies.entrySet()) {
				JSONObject depobj = new JSONObject();
				if (entry.getValue().privateScope) {
					depobj.put("@p", 1);
				}
				String bkreconstructid = bundleKeyToReconstructionIdentifier(entry.getKey());
				depobj.put("@bk", bkreconstructid);
				depsarray.put(depobj);
				domainToReconstructionJSONImpl(entry.getValue().domain, depobj, backreferences);
			}
			obj.put("@d", depsarray);
		}
	}

	private ClassLoaderDomain domainFromReconstructionString(String str) {
		JSONObject json;
		try {
			json = new JSONObject(str);
			String rootbkreconstructionid = json.getString("@bk");
			BundleKey rootbk = reconstructionIdentifierToBundleKey(rootbkreconstructionid);
			if (rootbk == null) {
				return null;
			}
			return domainFromReconstructionJSON(json, rootbk, new TreeMap<>());
		} catch (Exception e) {
			System.err.println(e);
		}
		return null;
	}

	private ClassLoaderDomain domainFromReconstructionJSON(JSONObject obj, BundleKey bundlekey,
			Map<Integer, ClassLoaderDomain> refs) {
		int backref = obj.optInt("@r", -1);
		if (backref >= 0) {
			return refs.get(backref);
		}
		int objid = obj.getInt("@i");
		LinkedHashMap<BundleKey, ClassLoaderDomain.DomainDependency> deps = new LinkedHashMap<>();
		ClassLoaderDomain result = new ClassLoaderDomain(bundlekey, deps);
		refs.put(objid, result);

		JSONArray depsarray = obj.optJSONArray("@d");
		if (depsarray != null) {
			Iterator<Object> dit = depsarray.iterator();
			while (dit.hasNext()) {
				JSONObject depobj = (JSONObject) dit.next();
				BundleKey depbundlekey = reconstructionIdentifierToBundleKey(depobj.optString("@bk", null));
				if (depbundlekey == null) {
					return null;
				}
				ClassLoaderDomain depcldomain = domainFromReconstructionJSON(depobj, depbundlekey, refs);
				if (depcldomain == null) {
					return null;
				}
				boolean privatescope = depobj.optInt("@p", -1) == 1;
				deps.put(depbundlekey, new ClassLoaderDomain.DomainDependency(depcldomain, privatescope));
			}
		}
		return result;
	}

	public NestRepositoryBundleClassLoader getBundleClassLoader(BundleIdentifier bundleid)
			throws BundleLoadingFailedException {
		return getBundleClassLoader(lookupConfiguration.lookupBundle(bundleid));
	}

	private NestRepositoryBundleClassLoader getBundleClassLoader(SimpleBundleLookupResult bundlelookup) {
		return getBundleClassLoader((AbstractNestRepositoryBundle) bundlelookup.getBundle(),
				bundlelookup.getRelativeLookup(), bundlelookup.getStorageView());
	}

	public Class<?> getTaskClass(TaskName tn) throws TaskNotFoundException {
		TaskLookupInfo tasklookupinfo = lookupConfiguration.lookupTaskBundle(tn);
		String taskclassname = tasklookupinfo.getTaskClassName();
		try {
			NestRepositoryBundleClassLoader bundlecl = getBundleClassLoader(tasklookupinfo);
			return bundlecl.loadClassFromBundle(taskclassname);
		} catch (ClassNotFoundException | BundleDependencyUnsatisfiedException e) {
			throw new TaskNotFoundException("Task class not found " + taskclassname + " in bundle "
					+ tasklookupinfo.getBundle().getBundleIdentifier(), e, tn);
		}
	}

	public Class<?> getTaskClassForInformationProvider(TaskName tn) {
		TaskLookupInfo tasklookupinfo = lookupConfiguration.lookupTaskBundleForInformationProvider(tn);
		if (tasklookupinfo == null) {
			return null;
		}
		String taskclassname = tasklookupinfo.getTaskClassName();
		try {
			NestRepositoryBundleClassLoader bundlecl = getBundleClassLoader(tasklookupinfo);
			return bundlecl.loadClassFromBundle(taskclassname);
		} catch (ClassNotFoundException | BundleDependencyUnsatisfiedException e) {
			throw new TaskNotFoundException("Task class not found " + taskclassname + " in bundle "
					+ tasklookupinfo.getBundle().getBundleIdentifier(), e, tn);
		}
	}

	@Override
	public BundleLookup getBundleLookup() {
		return lookupConfiguration;
	}

	@Override
	public BundleLookup getBundleLookupForKey(LookupKey key) {
		if (key == null) {
			return null;
		}
		return lookupKeyBundleLookups.get(key);
	}

	@Override
	public BundleStorageView getBundleStorageViewForKey(StorageViewKey key) {
		if (key == null) {
			return null;
		}
		return storageViewKeyStorageViews.get(key);
	}

	@Override
	public Map<String, ? extends LocalBundleStorageView> getLocalStorages() {
		return lookupConfiguration.getLocalStorages();
	}

	@Override
	public Map<String, ? extends ParameterBundleStorageView> getParameterStorages() {
		return lookupConfiguration.getParameterStorages();
	}

	@Override
	public Map<String, ? extends AbstractServerBundleStorageView> getServerStorages() {
		return lookupConfiguration.getServerStorages();
	}

	@Override
	public Map<String, ? extends BundleStorageView> getStorages() {
		return lookupConfiguration.getStorages();
	}

	@Override
	public NestRepositoryBundleClassLoader getBundleClassLoader(BundleLookup lookup, BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException {
		if (lookup == null) {
			lookup = this.lookupConfiguration;
		}
		Objects.requireNonNull(bundleid, "bundle identifier");
		return getBundleClassLoader(((AbstractBundleLookup) lookup).lookupBundle(bundleid));
	}

	@Override
	public NestRepositoryBundleClassLoader getBundleClassLoader(BundleKey bundlekey)
			throws NullPointerException, BundleLoadingFailedException, BundleDependencyUnsatisfiedException {
		Objects.requireNonNull(bundlekey, "bundle key");
		StorageViewKey storageviewkey = bundlekey.getStorageViewKey();
		BundleLookup lookup = this.lookupConfiguration.findStorageViewBundleLookup(storageviewkey);
		if (lookup == null) {
			throw new BundleLoadingFailedException("Failed to determine bundle lookup for storage view key: "
					+ storageviewkey + " with bundle identifier: " + bundlekey.getBundleIdentifier());
		}
		return getBundleClassLoader(lookup, bundlekey.getBundleIdentifier());
	}

	@Override
	public void close() throws IOException {
		// XXX release storages if it was allocated only for this configuration
		closed = true;
		synchronized (classLoaderLock) {
			classLoaders.clear();
			domainClassLoaders.clear();
		}
		taskClasses.clear();
	}

	public Object detectChanges(ExecutionPathConfiguration pathconfig) {
		synchronized (detectChangeLock) {
			if (this.expectedDetectedChanges != null) {
				throw new IllegalStateException("Multiple detect changes call without handling it.");
			}
			Map<AbstractBundleStorageView, Object> detectedchanges = new LinkedHashMap<>();
			for (AbstractBundleStorageView storage : stringIdentifierStorageViews.values()) {
				Object detected = storage.detectChanges(pathconfig);
				if (detected != null) {
					detectedchanges.put(storage, detected);
				}
			}
			DetectedChanges result = detectedchanges.isEmpty() ? null : new DetectedChanges(detectedchanges);
			expectedDetectedChanges = result;
			return result;
		}
	}

	public void handleChanges(ExecutionPathConfiguration pathconfig, Object detectedchangesobj) {
		synchronized (detectChangeLock) {
			if (this.expectedDetectedChanges != detectedchangesobj) {
				throw new IllegalArgumentException("Detected changes object is not expected.");
			}
			this.expectedDetectedChanges = null;

			DetectedChanges detectedchanges = (DetectedChanges) detectedchangesobj;
			synchronized (classLoaderLock) {
				//XXX do not clear all, but only modifieds
				taskClasses.clear();
				classLoaders.clear();
				domainClassLoaders.clear();

				for (Entry<AbstractBundleStorageView, Object> entry : detectedchanges.detectedChanges.entrySet()) {
					entry.getKey().handleChanges(pathconfig, entry.getValue());
				}
				//reset the storage view key map as they can be modified in case of changes
				Map<String, AbstractBundleStorageView> nstorageviewstringidstorages = new TreeMap<>();
				Map<StorageViewKey, AbstractBundleStorageView> nstoragekeybundlestorageviews = new HashMap<>();
				Map<AbstractBundleStorageView, String> nstorageviewstringidentifiers = new HashMap<>();
				Map<StorageViewKey, String> nstoragekeystringidentfiers = new HashMap<>();
				for (AbstractBundleStorageView storageview : storageViewKeyStorageViews.values()) {
					String nstorageviewstringid = createStorageViewStringIdentifier(storageview);
					StorageViewKey storageviewkey = storageview.getStorageViewKey();

					nstoragekeybundlestorageviews.put(storageviewkey, storageview);
					nstorageviewstringidstorages.put(nstorageviewstringid, storageview);
					nstorageviewstringidentifiers.put(storageview, nstorageviewstringid);
					nstoragekeystringidentfiers.put(storageviewkey, nstorageviewstringid);
				}
				storageViewKeyStorageViews.clear();
				storageViewKeyStorageViews.putAll(nstoragekeybundlestorageviews);
				storageViewKeyStringIdentifiers.clear();
				storageViewKeyStringIdentifiers.putAll(nstoragekeystringidentfiers);
				stringIdentifierStorageViews.clear();
				stringIdentifierStorageViews.putAll(nstorageviewstringidstorages);
				storageViewStringIdentifiers.clear();
				storageViewStringIdentifiers.putAll(nstorageviewstringidentifiers);

			}
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public TaskFactory<?> lookupTask(TaskName taskname) throws TaskNotFoundException {
		if (closed) {
			throw new IllegalStateException("closed.");
		}
		if (!BundleIdentifier.hasVersionQualifier(taskname.getTaskQualifiers())) {
			taskname = pinnedTaskVersion.getOrDefault(taskname, taskname);
		}
		TaskFactory<?> result = taskClasses.computeIfAbsent(taskname, tn -> {
			Class<?> taskclass;
			try {
				taskclass = this.getTaskClass(tn);
			} catch (TaskNotFoundException e) {
				return () -> {
					throw new TaskNotFoundException(e, tn);
				};
			}
			try {
				Method providermethod = taskclass.getMethod("provider");
				if (Modifier.isStatic(providermethod.getModifiers())
						&& TaskFactory.class.isAssignableFrom(providermethod.getReturnType())) {
					return () -> {
						try {
							return (TaskFactory<?>) ReflectUtils.invokeMethod(null, providermethod);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
								| SecurityException | ClassCastException e) {
							throw new TaskNotFoundException("Failed to instantiate task.", e, tn);
						}
					};
				}
			} catch (NoSuchMethodException e) {
			}
			if (!TaskFactory.class.isAssignableFrom(taskclass)) {
				return () -> {
					throw new TaskNotFoundException(
							"Class associated with task is not assignable to TaskFactory. Valid \"provider\" static method was not found.",
							tn);
				};
			}
			Constructor<? extends TaskFactory<?>> constructor;
			try {
				constructor = (Constructor<? extends TaskFactory<?>>) taskclass.getConstructor();
			} catch (NoSuchMethodException | SecurityException e) {
				return () -> {
					throw new TaskNotFoundException("Task class no-arg constructor not found: " + taskclass.getName(),
							e, tn);
				};
			}
			return () -> {
				try {
					return ReflectUtils.invokeConstructor(constructor);
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | SecurityException | ClassCastException e2) {
					throw new TaskNotFoundException("Failed to instantiate task.", e2, tn);
				}
			};
		}).get();
		if (closed) {
			//remove if it was added meanwhile between closing
			taskClasses.remove(taskname);
			throw new IllegalStateException("closed.");
		}
		return result;
	}

	public static String getSubDirectoryNameForServerStorage(String serverhost) {
		return StringUtils.toHexString(FileUtils.hashString(serverhost));
	}

	public static List<?> parseStorageConfigurationUserParameter(String s) {
		NonSpaceIterator it = new NonSpaceIterator(s);
		if (!it.hasNext()) {
			return Collections.emptyList();
		}
		Object result = readStorageConfigurations(it);
		if (it.hasNext()) {
			throw new IllegalArgumentException("Extra characters in storage configuration at index: " + it.getIndex()
					+ " in " + it.getCharSequence());
		}
		if (result instanceof List) {
			return (List<?>) result;
		}
		return ImmutableUtils.singletonList(result);
	}

	private static Object readStorageConfigurations(NonSpaceIterator it) {
		char fc = it.peek();
		if (fc == '[') {
			it.move();
			//a list
			List<Object> result = new ArrayList<>();
			while (it.hasNext()) {
				char nc = it.peek();
				if (nc == ']') {
					break;
				}
				Object subsconfig = readStorageConfigurations(it);
				if (subsconfig instanceof List) {
					if (!((List<?>) subsconfig).isEmpty()) {
						result.add(subsconfig);
					}
				} else {
					result.add(subsconfig);
				}
				if (it.hasNext() && it.peek() == ',') {
					it.move();
					if (!it.hasNext() || it.peek() == ']') {
						throw new IllegalArgumentException("Missing storage configuration at index: " + it.getIndex()
								+ " in " + it.getCharSequence());
					}
					continue;
				}
			}
			if (!it.hasNext() || it.peek() != ']') {
				throw new IllegalArgumentException("Missing storage configuration closing character ']' at index: "
						+ it.getIndex() + " in " + it.getCharSequence());
			}
			it.move();
			return result;
		}
		if (isValidStorageConfigurationNameCharacter(fc)) {
			StringBuilder sb = new StringBuilder();
			sb.append(fc);
			it.move();
			while (it.hasNext()) {
				char c = it.peek();
				if (c == ':') {
					//got the name
					it.move();
					break;
				}
				if (!isValidStorageConfigurationNameCharacter(c)) {
					throw new IllegalArgumentException("Invalid storage configuration name character at index: "
							+ it.getIndex() + " in " + it.getCharSequence());
				}
				sb.append(c);
				it.move();
			}
			String name = sb.toString();
			sb.setLength(0);
			while (it.hasNext()) {
				char c = it.peek();
				if (c >= 'A' && c <= 'Z') {
					//to lower case
					c = (char) (c - 'A' + 'a');
				}
				if (c == ',' || c == ']') {
					break;
				}
				if (c < 'a' || c > 'z') {
					throw new IllegalArgumentException("Invalid storage type character at index: " + it.getIndex()
							+ " in " + it.getCharSequence());
				}
				it.move();
				sb.append(c);
			}
			String type = sb.toString();
			if (type.isEmpty()) {
				type = null;
			} else {
				if (!STORAGE_CONFIGURATION_TYPES.contains(type)) {
					throw new IllegalArgumentException("Invalid storage configuration type: " + type + " at index: "
							+ it.getIndex() + " in " + it.getCharSequence());
				}
			}
			return ImmutableUtils.makeImmutableMapEntry(name, type);
		}
		if (fc == ':') {
			//starting directly with ':'
			StringBuilder sb = new StringBuilder();
			it.move();
			while (it.hasNext()) {
				char c = it.peek();
				if (c >= 'A' && c <= 'Z') {
					//to lower case
					c = (char) (c - 'A' + 'a');
				}
				if (c == ',' || c == ']') {
					break;
				}
				if ((c < 'a' || c > 'z') && c != '_') {
					throw new IllegalArgumentException("Invalid storage type character at index: " + it.getIndex()
							+ " in " + it.getCharSequence());
				}
				it.move();
				sb.append(c);
			}
			String type = sb.toString();
			if (!STORAGE_CONFIGURATION_TYPES.contains(type)) {
				throw new IllegalArgumentException("Invalid storage configuration type: " + type + " at index: "
						+ it.getIndex() + " in " + it.getCharSequence());
			}
			return ImmutableUtils.makeImmutableMapEntry(type, type);
		}
		throw new IllegalArgumentException("Invalid storage configuration name character at index: " + it.getIndex()
				+ " in " + it.getCharSequence());
	}

	private static boolean isValidStorageConfigurationNameCharacter(char fc) {
		return (fc >= 'a' && fc <= 'z') || (fc >= 'A' && fc <= 'Z') || (fc >= '0' && fc <= '9') || fc == '_';
	}

	private static void getStorageConfigurationNameTypesImpl(List<?> storageconfig, Map<String, String> result) {
		for (Object c : storageconfig) {
			if (c instanceof List<?>) {
				getStorageConfigurationNameTypesImpl((List<?>) c, result);
				continue;
			}
			@SuppressWarnings("unchecked")
			Map.Entry<String, String> entry = (Entry<String, String>) c;
			String confname = entry.getKey();
			String conftype = entry.getValue();
			if (conftype == null) {
				//just put to be present
				result.putIfAbsent(confname, null);
			} else {
				String prev = result.put(confname, conftype);
				if (prev != null && !prev.equals(conftype)) {
					throw new IllegalArgumentException("Different types defined for storage configuration named: "
							+ confname + " with " + conftype + " and " + prev);
				}
			}
		}
	}

	private static Map<String, String> getStorageConfigurationNameTypes(List<?> storageconfig) {
		Map<String, String> result = new TreeMap<>();
		getStorageConfigurationNameTypesImpl(storageconfig, result);
		for (Entry<String, String> entry : result.entrySet()) {
			String storagename = entry.getKey();
			if (RESERVED_STORAGE_NAMES.contains(storagename)) {
				throw new IllegalArgumentException(
						"Invalid storage configuration name: " + storagename + " (reserved)");
			}
			if (entry.getValue() == null) {
				throw new IllegalArgumentException("Storage type is undefined for name: " + entry.getKey());
			}
		}
		return result;
	}

	private AbstractBundleLookup createBundleLookupForConfiguration(List<?> storageconfig,
			Map<String, StorageInitializationInfo> namedstorageinitializers) {
		Map<String, Collection<MultiBundleLookup>> conflicts = new TreeMap<>();
		AbstractBundleLookup result = createBundleLookupForConfigurationImpl(storageconfig, namedstorageinitializers,
				conflicts);
		for (Entry<String, Collection<MultiBundleLookup>> entry : conflicts.entrySet()) {
			Iterator<MultiBundleLookup> it = entry.getValue().iterator();
			MultiBundleLookup first = it.next();
			while (it.hasNext()) {
				MultiBundleLookup l = it.next();
				if (!first.equals(l)) {
					throw new IllegalArgumentException(
							"Different tail resolution configuration for recurring storage configuration: "
									+ entry.getKey());
				}
			}
		}
		return result;
	}

	private AbstractBundleLookup createBundleLookupForConfigurationImpl(List<?> storageconfig,
			Map<String, StorageInitializationInfo> namedstorageinitializers,
			Map<String, Collection<MultiBundleLookup>> conflictinglookups) {
		AbstractBundleLookup[] lookups = new AbstractBundleLookup[storageconfig.size()];
		int i = 0;
		for (Object c : storageconfig) {
			if (c instanceof List<?>) {
				AbstractBundleLookup sub = createBundleLookupForConfigurationImpl((List<?>) c, namedstorageinitializers,
						conflictinglookups);
				lookups[i++] = sub;
				continue;
			}
			@SuppressWarnings("unchecked")
			Map.Entry<String, String> entry = (Entry<String, String>) c;
			String confname = entry.getKey();
			MultiBundleLookup enclosinglookup = new MultiBundleLookup(
					ImmutableUtils.unmodifiableArrayList(lookups, i, lookups.length));
			StorageInitializationInfo namedstorage = namedstorageinitializers.get(confname);
			SingleBundleLookup sub = new SingleBundleLookup(confname, namedstorage.storageView, enclosinglookup);

			conflictinglookups.computeIfAbsent(confname, Functionals.arrayListComputer()).add(enclosinglookup);

			storageKeyIdentifierBundleLookups.put(namedstorage.storageViewStringIdentifier, enclosinglookup);
			lookups[i++] = sub;
		}
		return new MultiBundleLookup(ImmutableUtils.unmodifiableArrayList(lookups));
	}

	private NestRepositoryBundleClassLoader getBundleClassLoader(TaskLookupInfo tasklookup) {
		return getBundleClassLoader((AbstractNestRepositoryBundle) tasklookup.getBundle(),
				tasklookup.getLookupConfiguration(), tasklookup.getStorageView());
	}

	private final class StorageViewEnvironmentImpl implements StorageViewEnvironment {
		private final ExecutionPathConfiguration pathconfig;
		private final StorageSharedObjectAccessor sharedobjecthandler;
		private final boolean remotecluster;
		private final NavigableMap<String, String> userParameters;
		private final String storageviewname;
		private final SakerFileProvider localFileProvider;

		public StorageViewEnvironmentImpl(ExecutionPathConfiguration pathconfig,
				StorageSharedObjectAccessor sharedobjecthandler, boolean remotecluster,
				NavigableMap<String, String> userParameters, String storageviewname,
				SakerFileProvider localFileProvider) {
			this.pathconfig = pathconfig;
			this.sharedobjecthandler = sharedobjecthandler;
			this.remotecluster = remotecluster;
			this.userParameters = userParameters;
			this.storageviewname = storageviewname;
			this.localFileProvider = localFileProvider;
		}

		@Override
		public void setSharedObject(Object key, Object value) {
			if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_015) {
				sharedobjecthandler.setSharedObject(key, value);
			}
		}

		@Override
		public Object getSharedObject(Object key) {
			if (saker.build.meta.Versions.VERSION_FULL_COMPOUND >= 8_015) {
				return sharedobjecthandler.getSharedObject(key);
			}
			return null;
		}

		@Override
		public boolean isRemoteCluster() {
			return remotecluster;
		}

		@Override
		public NavigableMap<String, String> getUserParameters() {
			return userParameters;
		}

		@Override
		public String getStorageViewName() {
			return storageviewname;
		}

		@Override
		public ExecutionPathConfiguration getPathConfiguration() {
			return pathconfig;
		}

		@Override
		public SakerFileProvider getLocalFileProvider() {
			return localFileProvider;
		}
	}

	private static final class RepositoryBuildEnvironmentStorageSharedObjectAccessor
			implements StorageSharedObjectAccessor {
		private final RepositoryBuildEnvironment environment;

		private RepositoryBuildEnvironmentStorageSharedObjectAccessor(RepositoryBuildEnvironment environment) {
			this.environment = environment;
		}

		@Override
		public void setSharedObject(Object key, Object value) {
			environment.setSharedObject(key, value);
		}

		@Override
		public Object getSharedObject(Object key) {
			return environment.getSharedObject(key);
		}
	}

	private static class ClassLoaderDependencyResolutionBundleContext {
		private final BundleStorageView storageView;
		private final BundleLookup relativeLookup;

		public ClassLoaderDependencyResolutionBundleContext(BundleVersionLookupResult lookupresult) {
			this.storageView = lookupresult.getStorageView();
			this.relativeLookup = lookupresult.getRelativeLookup();
		}

		public ClassLoaderDependencyResolutionBundleContext(BundleStorageView storageView,
				BundleLookup relativeLookup) {
			this.storageView = storageView;
			this.relativeLookup = relativeLookup;
		}

		public BundleStorageView getStorageView() {
			return storageView;
		}

		public BundleLookup getRelativeLookup() {
			return relativeLookup;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((relativeLookup == null) ? 0 : relativeLookup.hashCode());
			result = prime * result + ((storageView == null) ? 0 : storageView.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ClassLoaderDependencyResolutionBundleContext other = (ClassLoaderDependencyResolutionBundleContext) obj;
			if (relativeLookup == null) {
				if (other.relativeLookup != null)
					return false;
			} else if (!relativeLookup.equals(other.relativeLookup))
				return false;
			if (storageView == null) {
				if (other.storageView != null)
					return false;
			} else if (!storageView.equals(other.storageView))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "ClassLoaderDependencyResolutionBundleContext["
					+ (storageView != null ? "storageView=" + storageView + ", " : "")
					+ (relativeLookup != null ? "relativeLookup=" + relativeLookup : "") + "]";
		}
	}

	private NestRepositoryBundleClassLoader getBundleClassLoader(AbstractNestRepositoryBundle bundle,
			AbstractBundleLookup bundlelookupconfig, BundleStorageView bundlestorage) {
		{
			NestRepositoryBundleClassLoader presentcl = classLoaders.get(bundle);
			if (presentcl != null) {
				return presentcl;
			}
		}
		BundleIdentifier bundleid = bundle.getBundleIdentifier();
		BundleKey bundlekey = new SimpleBundleKey(bundleid, bundlestorage.getStorageViewKey());
		BundleInformation bundleinfo = bundle.getInformation();
		DependencyConstraintConfiguration constraints = getDependencyConstraintConfiguration();
		if (!DependencyUtils.supportsClassPathJreVersion(bundleinfo, constraints.getJreMajorVersion())) {
			throw new BundleDependencyUnsatisfiedException(
					"Bundle doesn't support JRE version: " + constraints.getJreMajorVersion() + " for range: "
							+ bundleinfo.getSupportedClassPathJreVersionRange());
		}
		if (!DependencyUtils.supportsClassPathRepositoryVersion(bundleinfo, constraints.getRepositoryVersion())) {
			throw new BundleDependencyUnsatisfiedException(
					"Bundle doesn't support repository version: " + constraints.getRepositoryVersion() + " for range: "
							+ bundleinfo.getSupportedClassPathRepositoryVersionRange());
		}
		if (!DependencyUtils.supportsClassPathBuildSystemVersion(bundleinfo, constraints.getBuildSystemVersion())) {
			throw new BundleDependencyUnsatisfiedException(
					"Bundle doesn't support build system version: " + constraints.getBuildSystemVersion()
							+ " for range: " + bundleinfo.getSupportedClassPathBuildSystemVersionRange());
		}
		if (!DependencyUtils.supportsClassPathArchitecture(bundleinfo, constraints.getNativeArchitecture())) {
			throw new BundleDependencyUnsatisfiedException("Bundle doesn't support architecture: "
					+ constraints.getNativeArchitecture() + " for architectures: "
					+ StringUtils.toStringJoin(", ", bundleinfo.getSupportedClassPathArchitectures()));
		}
		BundleDependencyInformation basefiltereddepinfo = filterDependencyInformationForClassPath(
				bundleinfo.getDependencyInformation(), CLASSPATH_DEPENDENCY_KIND_SINGLETON);
		DependencyResolutionLogger<ClassLoaderDependencyResolutionBundleContext> logger = null;
		List<Throwable> unsatisfiedsuppressions = new ArrayList<>();
		BiFunction<? super BundleIdentifier, ? super ClassLoaderDependencyResolutionBundleContext, ? extends Iterable<? extends Entry<? extends BundleKey, ? extends ClassLoaderDependencyResolutionBundleContext>>> bundleslookupfunction = (
				bi, bc) -> {
			BundleVersionLookupResult lookedupversions = bc.getRelativeLookup().lookupBundleVersions(bi);
			if (lookedupversions == null) {
				return null;
			}
			return ObjectUtils.singleValueMap(toBundleKeySet(lookedupversions),
					new ClassLoaderDependencyResolutionBundleContext(lookedupversions)).entrySet();
		};
		BiFunction<? super BundleKey, ? super ClassLoaderDependencyResolutionBundleContext, ? extends BundleDependencyInformation> bundledependencieslookupfunction = (
				bi, bc) -> {
			try {
				BundleInformation lookupbundleinfo = bc.getStorageView().getBundleInformation(bi.getBundleIdentifier());
				if (DependencyUtils.isDependencyConstraintClassPathExcludes(constraints, lookupbundleinfo)) {
					//XXX log somewhere?
					return null;
				}
				BundleDependencyInformation lookupbundledepinfo = lookupbundleinfo.getDependencyInformation();
				return filterDependencyInformationForClassPath(lookupbundledepinfo,
						CLASSPATH_DEPENDENCY_KIND_SINGLETON);
			} catch (BundleLoadingFailedException e) {
				unsatisfiedsuppressions.add(e);
			}
			return null;
		};

		DependencyDomainResolutionResult<BundleKey, ClassLoaderDependencyResolutionBundleContext> domainsatisfied = DependencyUtils
				.satisfyDependencyDomain(bundlekey,
						new ClassLoaderDependencyResolutionBundleContext(bundlestorage, bundlelookupconfig),
						basefiltereddepinfo, bundleslookupfunction, bundledependencieslookupfunction, logger);
		if (domainsatisfied == null) {
			//XXX handle dependency satisfaction failure better
			BundleDependencyUnsatisfiedException unsatisfiedexc = new BundleDependencyUnsatisfiedException(
					"Failed to satisfy dependencies for: " + bundleid);
			unsatisfiedsuppressions.forEach(unsatisfiedexc::addSuppressed);
			throw unsatisfiedexc;
		}
		ClassLoaderDomain rootbundledomain = createClassLoaderDomain(bundlekey, domainsatisfied);

		{
			NestRepositoryBundleClassLoader presentdomaincl = domainClassLoaders.get(rootbundledomain);
			if (presentdomaincl != null) {
				classLoaders.putIfAbsent(bundle, presentdomaincl);
				return presentdomaincl;
			}
			NestRepositoryBundleClassLoader presentcl = classLoaders.get(bundle);
			if (presentcl != null) {
				return presentcl;
			}
		}

		synchronized (classLoaderLock) {
			if (closed) {
				throw new IllegalStateException("closed");
			}
			{
				NestRepositoryBundleClassLoader presentcl = classLoaders.get(bundle);
				if (presentcl != null) {
					return presentcl;
				}
			}

			NestRepositoryBundleClassLoader result = createDomainClassLoaderLockedImpl(rootbundledomain,
					(domain, e) -> {
						throw new AssertionError("Failed to retrieve previously resolved bundle. ("
								+ domain.bundle.getBundleIdentifier() + ")", e);
					});

			classLoaders.putIfAbsent(bundle, result);
			return result;
		}
	}

	/**
	 * Locked on {@link #classLoaderLock}.
	 */
	private NestRepositoryBundleClassLoader createDomainClassLoaderLockedImpl(ClassLoaderDomain rootbundledomain,
			BiConsumer<? super ClassLoaderDomain, ? super BundleLoadingFailedException> bundleloadingfailurehandler) {
		{
			NestRepositoryBundleClassLoader presentrootdomaincl = domainClassLoaders.get(rootbundledomain);
			if (presentrootdomaincl != null) {
				return presentrootdomaincl;
			}
		}

		Map<ClassLoaderDomain, NestRepositoryBundleClassLoader> constructeddomaincls = new HashMap<>();
		Map<ClassLoaderDomain, Map<BundleKey, DependentClassLoader<NestRepositoryBundleClassLoader>>> constructedcldependencies = new HashMap<>();
		for (ClassLoaderDomain domain : rootbundledomain.getAllDomains()) {
			NestRepositoryBundleClassLoader presentdomaincl = domainClassLoaders.get(domain);
			if (presentdomaincl != null) {
				continue;
			}
			if (constructeddomaincls.containsKey(domain)) {
				//already constructed
				continue;
			}
			StorageViewKey storageviewkey = domain.bundle.getStorageViewKey();
			AbstractBundleStorageView domainbundlestorage = storageViewKeyStorageViews.get(storageviewkey);
			AbstractNestRepositoryBundle domainbundle;
			try {
				domainbundle = domainbundlestorage.getBundle(domain.bundle.getBundleIdentifier());
			} catch (BundleLoadingFailedException e) {
				bundleloadingfailurehandler.accept(domain, e);
				return null;
			}
			Map<BundleKey, DependentClassLoader<NestRepositoryBundleClassLoader>> dependencyclassloaders = new LinkedHashMap<>();

			BundleLookup relativebundlelookup = this.lookupConfiguration.findStorageViewBundleLookup(storageviewkey);

			BundleInformation bundleinfo = domainbundle.getInformation();
			ClassLoader parentcl = BundleUtils.createAppropriateParentClassLoader(bundleinfo);

			Map<SimpleExternalArchiveKey, DependentClassLoader<? extends NestRepositoryExternalArchiveClassLoader>> externaldependencyclassloaders = new LinkedHashMap<>();
			ExternalDependencyInformation extdependencies = bundleinfo.getExternalDependencyInformation();
			NestRepositoryBundleClassLoader constructedcl = new NestRepositoryBundleClassLoader(parentcl, this,
					domain.bundle, domainbundle, dependencyclassloaders, relativebundlelookup,
					externaldependencyclassloaders);

			if (!extdependencies.isEmpty()) {
				ExternalDependencyInformation filteredextdep = filterExternalDependencyForClasspath(extdependencies);
				Map<SimpleExternalArchiveKey, ? extends AbstractExternalArchive> loadedarchives;
				try {
					loadedarchives = repository.loadExternalArchives(filteredextdep, domainbundlestorage);
				} catch (NullPointerException | IllegalArgumentException | ExternalArchiveLoadingFailedException e) {
					throw new BundleDependencyUnsatisfiedException(
							"Failed to load external dependencies for: " + bundleinfo.getBundleIdentifier(), e);
				}
				if (!loadedarchives.isEmpty()) {
					Map<URI, ? extends ExternalDependencyList> cpextdependencies = filteredextdep.getDependencies();
					Set<NestRepositoryExternalArchiveClassLoader> extclassloaderdomain = new LinkedHashSet<>();
					for (Entry<? extends SimpleExternalArchiveKey, ? extends AbstractExternalArchive> entry : loadedarchives
							.entrySet()) {

						SimpleExternalArchiveKey archivekey = entry.getKey();
						URI archiveuri = archivekey.getUri();
						ExternalDependencyList deplist = cpextdependencies.get(archiveuri);

						boolean privatedep;
						String entryname = archivekey.getEntryName();
						if (entryname == null) {
							privatedep = isMainAllPrivateDependencies(deplist.getDependencies());
						} else {
							privatedep = isEntryAllPrivateDependencies(entryname, deplist.getDependencies());
						}

						NestRepositoryExternalArchiveClassLoader extcl = new NestRepositoryExternalArchiveClassLoader(
								constructedcl, parentcl, entry.getValue(), extclassloaderdomain);
						extclassloaderdomain.add(extcl);
						externaldependencyclassloaders.put(entry.getKey(),
								new DependentClassLoader<>(extcl, privatedep));
					}
				}

			}

			constructedcldependencies.put(domain, dependencyclassloaders);
			constructeddomaincls.put(domain, constructedcl);
		}

		for (Entry<ClassLoaderDomain, Map<BundleKey, DependentClassLoader<NestRepositoryBundleClassLoader>>> entry : constructedcldependencies
				.entrySet()) {
			Map<BundleKey, DependentClassLoader<NestRepositoryBundleClassLoader>> cldepmap = entry.getValue();
			for (Entry<? extends BundleKey, ClassLoaderDomain.DomainDependency> depentry : entry.getKey().dependencies
					.entrySet()) {
				if (cldepmap.containsKey(depentry.getKey())) {
					continue;
				}
				NestRepositoryBundleClassLoader domaincl = domainClassLoaders.get(depentry.getValue().domain);
				if (domaincl == null) {
					domaincl = constructeddomaincls.get(depentry.getValue().domain);
				}
				cldepmap.put(depentry.getKey(), new DependentClassLoader<>(domaincl, depentry.getValue().privateScope));
			}
		}

		NestRepositoryBundleClassLoader result = constructeddomaincls.get(rootbundledomain);
		if (result == null) {
			throw new AssertionError("Failed to retrieve classloader. ");
		}

		domainClassLoaders.putAll(constructeddomaincls);
		return result;
	}

	@Override
	public Map<? extends ExternalArchiveKey, ? extends ExternalArchive> loadExternalArchives(
			ExternalDependencyInformation depinfo)
			throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException {
		return repository.loadExternalArchives(depinfo, (uri, hashes) -> BundleUtils.openExternalDependencyURI(uri));
	}

	private ExternalDependencyInformation filterExternalDependencyForClasspath(
			ExternalDependencyInformation extdependencies) {
		Map<URI, ExternalDependencyList> cpextdependencies = new LinkedHashMap<>();

		for (Entry<URI, ? extends ExternalDependencyList> entry : extdependencies.getDependencies().entrySet()) {
			ExternalDependencyList deplist = entry.getValue();
			ExternalDependencyList.Builder deplistbuilder = ExternalDependencyList.builder();
			deplistbuilder.setSha256Hash(deplist.getSha256Hash());
			deplistbuilder.setSha1Hash(deplist.getSha1Hash());
			deplistbuilder.setMd5Hash(deplist.getMd5Hash());

			boolean hadres = false;
			for (ExternalDependency extdep : deplist.getDependencies()) {
				if (DependencyUtils.isDependencyConstraintExcludes(constraintConfiguration, extdep)) {
					continue;
				}
				Set<String> kinds = extdep.getKinds();
				if (!kinds.contains(BundleInformation.DEPENDENCY_KIND_CLASSPATH)) {
					continue;
				}
				deplistbuilder.addDepdendency(extdep);
				hadres = true;
			}
			if (!hadres) {
				continue;
			}
			//no need for attachments to load classloader
			cpextdependencies.put(entry.getKey(), deplistbuilder.build());
		}

		ExternalDependencyInformation filteredextdep = ExternalDependencyInformation.create(cpextdependencies);
		return filteredextdep;
	}

	private static boolean isEntryAllPrivateDependencies(String ename, Iterable<? extends ExternalDependency> deps) {
		for (ExternalDependency dep : deps) {
			Set<WildcardPath> entries = dep.getEntries();
			if (ObjectUtils.isNullOrEmpty(entries)) {
				continue;
			}
			if (!BundleUtils.isWildcardsInclude(ename, entries)) {
				continue;
			}
			if (!dep.isPrivate()) {
				return false;
			}
		}
		return true;
	}

	private static boolean isMainAllPrivateDependencies(Iterable<? extends ExternalDependency> deps) {
		for (ExternalDependency dep : deps) {
			if (!dep.isIncludesMainArchive()) {
				continue;
			}
			if (!dep.isPrivate()) {
				return false;
			}
		}
		return true;
	}

	private static Set<BundleKey> toBundleKeySet(BundleVersionLookupResult lookedupversions) {
		//keep order, version descending
		Set<BundleKey> result = new LinkedHashSet<>();
		StorageViewKey storagekey = lookedupversions.getStorageView().getStorageViewKey();
		for (BundleIdentifier bid : lookedupversions.getBundles()) {
			result.add(new SimpleBundleKey(bid, storagekey));
		}
		return result;
	}

	private BundleDependencyList filterDependencyInformationForClassPath(BundleDependencyList deplist,
			Set<String> lookupkinds) {
		DependencyConstraintConfiguration constraints = getDependencyConstraintConfiguration();
		return deplist.filter(dep -> {
			if (DependencyUtils.isDependencyConstraintExcludes(constraints, dep)) {
				return null;
			}
			Set<String> depkinds = dep.getKinds();
			if (ObjectUtils.containsAny(depkinds, lookupkinds)) {
				BundleDependency.Builder b = BundleDependency.builder(dep);
				b.clearKinds();
				for (String kind : lookupkinds) {
					if (depkinds.contains(kind)) {
						b.addKind(kind);
					}
				}
				return b.build();
			}
			return null;
		});
	}

	private BundleDependencyInformation filterDependencyInformationForClassPath(BundleDependencyInformation depinfo,
			Set<String> lookupkinds) {
		Map<BundleIdentifier, BundleDependencyList> resultdeps = new LinkedHashMap<>();
		for (Entry<BundleIdentifier, ? extends BundleDependencyList> entry : depinfo.getDependencies().entrySet()) {
			BundleDependencyList deplist = entry.getValue();
			if (!deplist.isEmpty()) {
				BundleDependencyList ndeplist = filterDependencyInformationForClassPath(deplist, lookupkinds);
				if (ndeplist != null) {
					resultdeps.put(entry.getKey(), ndeplist);
				}
			}
		}
		return BundleDependencyInformation.create(resultdeps);
	}

	private <BC> ClassLoaderDomain createClassLoaderDomain(BundleKey domainbundleid,
			DependencyDomainResolutionResult<BundleKey, BC> dependencies) {
		return createClassLoaderDomainImpl(domainbundleid, dependencies, new HashMap<>());
	}

	private <BC> ClassLoaderDomain createClassLoaderDomainImpl(BundleKey enclosingbundleid,
			DependencyDomainResolutionResult<BundleKey, BC> dependencies,
			Map<Entry<DependencyDomainResolutionResult<BundleKey, BC>, BundleKey>, ClassLoaderDomain> constructeddomains) {
		Entry<DependencyDomainResolutionResult<BundleKey, BC>, BundleKey> lookupentry = ImmutableUtils
				.makeImmutableMapEntry(dependencies, enclosingbundleid);
		ClassLoaderDomain presentdomain = constructeddomains.get(lookupentry);
		if (presentdomain != null) {
			return presentdomain;
		}
		LinkedHashMap<BundleKey, ClassLoaderDomain.DomainDependency> dependencydomains = new LinkedHashMap<>();
		ClassLoaderDomain result = new ClassLoaderDomain(enclosingbundleid, dependencydomains);
		constructeddomains.put(lookupentry, result);

		Map<Entry<? extends BundleKey, ? extends BC>, ? extends DependencyDomainResolutionResult<BundleKey, BC>> directdeps = dependencies
				.getDirectDependencies();
		if (!directdeps.isEmpty()) {
			BundleDependencyInformation depinfo;
			try {
				depinfo = storageViewKeyStorageViews.get(enclosingbundleid.getStorageViewKey())
						.getBundleInformation(enclosingbundleid.getBundleIdentifier()).getDependencyInformation();
			} catch (NullPointerException | BundleLoadingFailedException e) {
				throw new AssertionError(
						"Failed to retrieve previously resolved bundle: " + enclosingbundleid.getBundleIdentifier(), e);
			}
			for (Entry<Entry<? extends BundleKey, ? extends BC>, ? extends DependencyDomainResolutionResult<BundleKey, BC>> entry : directdeps
					.entrySet()) {
				BundleKey dependencybundlekey = entry.getKey().getKey();
				BundleDependencyList deplist = depinfo
						.getDependencyList(dependencybundlekey.getBundleIdentifier().withoutMetaQualifiers());
				if (deplist == null) {
					throw new AssertionError("Dependency not found. " + dependencybundlekey.getBundleIdentifier()
							+ " in " + enclosingbundleid);
				}

				ClassLoaderDomain depdomain = createClassLoaderDomainImpl(dependencybundlekey, entry.getValue(),
						constructeddomains);
				boolean privateScope;
				privateScope = isAllPrivateDependencies(deplist);
				dependencydomains.put(dependencybundlekey,
						new ClassLoaderDomain.DomainDependency(depdomain, privateScope));
			}
		}
		return result;
	}

	public static boolean isAllPrivateDependencies(BundleDependencyList deplist) {
		for (BundleDependency dep : deplist.getDependencies()) {
			if (!dep.isPrivate()) {
				return false;
			}
		}
		return true;
	}

	private static class StorageInitializationInfo {
		final AbstractStorageKey storageKey;
		final NavigableMap<String, String> userParameters;
		AbstractBundleStorageView storageView;
		String storageViewStringIdentifier;

		public StorageInitializationInfo(AbstractStorageKey storageKey, NavigableMap<String, String> userParameters) {
			this.storageKey = storageKey;
			this.userParameters = userParameters;
		}
	}

	private static class DetectedChanges {
		Map<AbstractBundleStorageView, Object> detectedChanges;

		public DetectedChanges(Map<AbstractBundleStorageView, Object> detectedChanges) {
			this.detectedChanges = detectedChanges;
		}
	}

	private static class ClassLoaderDomain {
		public static class DomainDependency {
			protected final ClassLoaderDomain domain;
			protected final boolean privateScope;

			public DomainDependency(ClassLoaderDomain domain, boolean privateScope) {
				this.domain = domain;
				this.privateScope = privateScope;
			}
		}

		protected final BundleKey bundle;
		/**
		 * Maps bundle keys to the nature of privateness of the dependency.
		 */
		protected final LinkedHashMap<BundleKey, DomainDependency> dependencies;

		public ClassLoaderDomain(BundleKey bundle, LinkedHashMap<BundleKey, DomainDependency> dependencies) {
			this.bundle = bundle;
			this.dependencies = dependencies;
		}

		public static ClassLoaderDomain fromClassLoader(NestRepositoryBundleClassLoader cl) {
			return fromClassLoaderImpl(cl, new HashMap<>());
		}

		private static ClassLoaderDomain fromClassLoaderImpl(NestRepositoryBundleClassLoader cl,
				Map<NestRepositoryBundleClassLoader, ClassLoaderDomain> cldomains) {
			ClassLoaderDomain present = cldomains.get(cl);
			if (present != null) {
				return present;
			}
			LinkedHashMap<BundleKey, DomainDependency> deps = new LinkedHashMap<>();
			ClassLoaderDomain result = new ClassLoaderDomain(cl.getBundleKey(), deps);
			cldomains.put(cl, result);

			for (Entry<BundleKey, DependentClassLoader<? extends NestRepositoryBundleClassLoader>> entry : cl
					.getDependencyClassLoaders().entrySet()) {
				DependentClassLoader<? extends NestRepositoryBundleClassLoader> depclref = entry.getValue();
				ClassLoaderDomain depdomain = fromClassLoaderImpl(depclref.classLoader, cldomains);
				deps.put(entry.getKey(), new DomainDependency(depdomain, depclref.privateScope));
			}

			return result;
		}

		public Set<ClassLoaderDomain> getAllDomains() {
			Set<ClassLoaderDomain> result = new HashSet<>();
			collectAllDomains(result);
			return result;
		}

		private void collectAllDomains(Set<ClassLoaderDomain> result) {
			if (!result.add(this)) {
				return;
			}
			for (DomainDependency d : this.dependencies.values()) {
				d.domain.collectAllDomains(result);
			}
		}

		private boolean equals(ClassLoaderDomain domain, Set<IdentityComparisonPair<ClassLoaderDomain>> compared) {
			if (!compared.add(new IdentityComparisonPair<>(this, domain))) {
				return true;
			}
			if (this == domain) {
				return true;
			}
			//we need to check
			if (this.dependencies.size() != domain.dependencies.size()) {
				return false;
			}
			Iterator<? extends Entry<? extends BundleKey, DomainDependency>> thisit = this.dependencies.entrySet()
					.iterator();
			Iterator<? extends Entry<? extends BundleKey, DomainDependency>> dit = domain.dependencies.entrySet()
					.iterator();
			while (thisit.hasNext()) {
				if (!dit.hasNext()) {
					return false;
				}
				Entry<? extends BundleKey, DomainDependency> thisentry = thisit.next();
				Entry<? extends BundleKey, DomainDependency> dentry = dit.next();
				if (!thisentry.getKey().equals(dentry.getKey())) {
					return false;
				}
				DomainDependency thisdomain = thisentry.getValue();
				DomainDependency ddomain = dentry.getValue();
				if (thisdomain.privateScope != ddomain.privateScope) {
					return false;
				}
				if (!thisdomain.domain.equals(ddomain.domain, compared)) {
					return false;
				}
			}
			if (dit.hasNext()) {
				return false;
			}
			return true;
		}

		private void toString(StringBuilder sb, Set<ClassLoaderDomain> added) {
			if (!added.add(this)) {
				sb.append("<previous ");
				sb.append(this.bundle.getBundleIdentifier());
				sb.append("@");
				sb.append(Integer.toHexString(System.identityHashCode(this)));
				sb.append(">");
				return;
			}
			sb.append(bundle.getBundleIdentifier());
			sb.append("@");
			sb.append(Integer.toHexString(System.identityHashCode(this)));
			sb.append("{");
			for (Iterator<? extends Entry<? extends BundleKey, DomainDependency>> it = dependencies.entrySet()
					.iterator(); it.hasNext();) {
				Entry<? extends BundleKey, DomainDependency> entry = it.next();
				if (entry.getValue().privateScope) {
					sb.append("<private>: ");
				}
				entry.getValue().domain.toString(sb, added);
				if (it.hasNext()) {
					sb.append(", ");
				}
			}
			sb.append("}");
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + bundle.hashCode();
			//no dependencies in hashcode
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ClassLoaderDomain other = (ClassLoaderDomain) obj;
			if (!bundle.equals(other.bundle))
				return false;
			if (!this.equals(other, new HashSet<>())) {
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			toString(sb, ObjectUtils.newIdentityHashSet());
			return sb.toString();
		}

	}

}
