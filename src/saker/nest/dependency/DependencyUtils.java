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
package saker.nest.dependency;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.nest.bundle.BundleDependency;
import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleDependencyList;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleIdentifierHolder;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.BundleKey;
import saker.nest.bundle.DependencyConstraintConfiguration;
import saker.nest.bundle.lookup.BundleLookup;
import saker.nest.exc.InvalidNestBundleException;
import saker.nest.version.VersionRange;

/**
 * Utility class providing functionality for working with dependencies.
 * <p>
 * The class implements the basic dependency resolution algorithm used by the repository.
 */
public class DependencyUtils {

	//doc: the returned map from the lookup function should not be modified
	//     the bundle context should implement equality and hashcode
	/**
	 * Executes the dependency resolution with the given arguments.
	 * <p>
	 * The dependency resolution algorithm defines no methodology about findig the bundles and their dependency
	 * information. These functionalities must be implemented by the caller and plugged in to the dependency resolution
	 * algorithm via the argument function interface instances.
	 * <p>
	 * The dependency resolution algorithm is deterministic, meaning that if the inputs are the same and have the same
	 * iteration orders, it will produce the same output.
	 * <p>
	 * The algorithm basically works on {@link BundleIdentifier BundleIdentifiers}, however, in order to support various
	 * scenarios such as a bundle identifier occurring multiple times, the algorithm works with the type parameters of
	 * <code>BK</code> (bundle key) and <code>BC</code> (bundle context). <code>BK</code> type serves as the key to the
	 * bundle, that uniquely locates both the bundle identifier and its location in some arbitrary space. The
	 * <code>BC</code> type serves as a transient information about the found bundle that can be used to store
	 * information for further operations. The bundle context is later passed to the dependency lookup function.
	 * <p>
	 * In general, types such as {@link BundleKey} or similar should be used as a substitute for the <code>BK</code>
	 * type parameter, and {@link BundleLookup} or other information for <code>BC</code>. Other arbitrary client
	 * provided types may be used as well.
	 * <p>
	 * Both the <code>BK</code> and <code>BC</code> types should be comparable using {@linkplain Object#equals(Object)
	 * equality}.
	 * <p>
	 * The dependency resolution algorithm first tries to resolve all non-optional (required) dependencies. If the
	 * resolution of these succeed, then these dependencies will be pinned, and it will attempt to resolve the
	 * {@linkplain BundleDependency#isOptional() optional} dependencies as well. However, the resolution of the optional
	 * dependencies may fail, in which case it is silently ignored by the method implementation.
	 * <p>
	 * The dependency resolution algorithm is exhaustive, meaning that it will not fail if there is a possible
	 * resolution of dependencies. The algorithm supports circular dependencies.
	 * <p>
	 * The method accepts a {@linkplain DependencyResolutionLogger logger} which will be called at different times
	 * during the dependency resolution to notify about the current state. If the dependency resolution fails, the
	 * caller can use the information passed to the logger to determine the cause of the failure. This method doesn't
	 * directly report the cause, and doesn't analyze the failures by itself.
	 * 
	 * @param <BK>
	 *            The bundle key type. The type should be comparable using {@linkplain Object#equals(Object) equality}.
	 * @param <BC>
	 *            The bundle context type. The type should be comparable using {@linkplain Object#equals(Object)
	 *            equality}.
	 * @param basebundle
	 *            The root bundle to resolve the dependencies of. Must not be <code>null</code>. <br>
	 *            If one cannot provide a root bundle identifier, as the resolution happens in a way that there's none,
	 *            one can use the {@link #randomBundleIdentifier()} to create a unique bundle identifier that this
	 *            function accepts. Make sure to remove logging information about the generated bundle identifier not to
	 *            pollute the user logs.
	 * @param basebundlecontext
	 *            The bundle context for the root bundle.
	 * @param basedependencyinfo
	 *            The dependency information of the root bundle that should be resolved.
	 * @param bundleslookupfunction
	 *            The function that looks up the bundles for a given bundle identifier. The bundle identifier argument
	 *            doesn't contain a {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}. If the result
	 *            iterable is backed by a {@link Map} entry set, then it is recommended that the {@link Map} returned
	 *            from this function has a deterministic iteration order. (See {@link LinkedHashMap} or
	 *            {@link TreeMap}.) <br>
	 *            The returned iterable from the function may be lazily populated.
	 * @param bundledependencieslookupfunction
	 *            The function that looks up the dependency information for a bundle. The arguments for this function is
	 *            the ones that were returned from the lookup function (or the root bundle key and context). The bundle
	 *            identifier argument contains a {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}.
	 * @param logger
	 *            The dependency resolution logger or <code>null</code> to not use one.
	 * @return The result of the dependency resolution or <code>null</code> if the resolution failed.
	 * @throws NullPointerException
	 *             If the base bundle, base dependency information, bundle lookup function, or bundle dependencies
	 *             lookup function arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the base bundle identifier doesn't have a {@linkplain BundleIdentifier#getVersionQualifier()
	 *             version qualifier}.
	 */
	public static <BK extends BundleIdentifierHolder, BC> DependencyResolutionResult<BK, BC> satisfyDependencyRequirements(
			BK basebundle, BC basebundlecontext, BundleDependencyInformation basedependencyinfo,
			BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
			BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
			DependencyResolutionLogger<? super BC> logger) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(basebundle, "base bundle");
		Objects.requireNonNull(basedependencyinfo, "base dependency information");
		Objects.requireNonNull(bundleslookupfunction, "bundles lookup function");
		Objects.requireNonNull(bundledependencieslookupfunction, "bundle dependencies lookup function");
		if (basebundle.getBundleIdentifier().getVersionQualifier() == null) {
			throw new IllegalArgumentException("Base bundle identifier has no version: " + basebundle);
		}

		Map<Entry<? extends BundleIdentifier, ? extends BC>, Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupcache = new HashMap<>();
		Map<BK, Supplier<BundleDependencyInformation>> dependencieslookupcache = new HashMap<>();
		Map<BK, Supplier<BundleDependencyInformation>> dependencieswithoutoptionalslookupcache = new HashMap<>();
		Map<BK, BundleDependencyInformation> optionalhavingbundles = new HashMap<>();

		Entry<? extends BK, ? extends BC> basebundleentry = ImmutableUtils.makeImmutableMapEntry(basebundle,
				basebundlecontext);
		Entry<? extends BundleIdentifier, ? extends BC> basewithoutversionbundleentry = ImmutableUtils
				.makeImmutableMapEntry(basebundle.getBundleIdentifier().withoutMetaQualifiers(), basebundlecontext);
		if (basedependencyinfo.hasOptional()) {
			optionalhavingbundles.put(basebundle, basedependencyinfo);
		}

		bundleslookupcache.put(
				ImmutableUtils.makeImmutableMapEntry(basebundle.getBundleIdentifier(), basebundlecontext),
				Collections.singletonList(ImmutableUtils.makeImmutableMapEntry(basebundle, null)));

		BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> cachingbundleslookupfunction = (
				bi, bc) -> {
			Entry<BundleIdentifier, BC> lookupentry = ImmutableUtils.makeImmutableMapEntry(bi, bc);
			return bundleslookupcache.computeIfAbsent(lookupentry, kentry -> {
				Iterable<? extends Entry<? extends BK, ? extends BC>> lookedup = bundleslookupfunction
						.apply(kentry.getKey(), kentry.getValue());
				if (lookedup == null) {
					return Collections.emptyList();
				}
				return lookedup;
			});
		};

		BundleDependencyInformation basedepinfowithoutoptionals = basedependencyinfo.withoutOptionals();
		dependencieslookupcache.put(basebundle, Functionals.valSupplier(basedependencyinfo));
		dependencieswithoutoptionalslookupcache.put(basebundle, Functionals.valSupplier(basedepinfowithoutoptionals));

		BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> cachingbundledependencieslookupfunction = (
				bi, bc) -> {
			return dependencieslookupcache.computeIfAbsent(bi, kentry -> {
				return LazySupplier.of(() -> {
					BundleDependencyInformation depinfos = bundledependencieslookupfunction.apply(bi, bc);
					if (depinfos == null) {
						return null;
					}
					if (depinfos.hasOptional()) {
						optionalhavingbundles.put(kentry, depinfos);
						return depinfos;
					}
					return depinfos;
				});
			}).get();
		};
		BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> cachingbundledependencieswithoutoptionalslookupfunction = (
				bi, bc) -> {
			return dependencieswithoutoptionalslookupcache.computeIfAbsent(bi, kentry -> {
				return LazySupplier.of(() -> {
					BundleDependencyInformation depinfos = cachingbundledependencieslookupfunction.apply(bi, bc);
					if (depinfos != null) {
						return depinfos.withoutOptionals();
					}
					return null;
				});
			}).get();
		};

		BundleResolutionState<BK, BC> bundleresstate = new BundleResolutionState<>(basebundleentry,
				basedepinfowithoutoptionals, logger);

		Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> result = new HashMap<>();

		result.put(basewithoutversionbundleentry, basebundleentry);
		satisfyBundleVersion(cachingbundleslookupfunction, cachingbundledependencieswithoutoptionalslookupfunction,
				result, logger, bundleresstate);
		if (bundleresstate.isBackTracking()) {
			return null;
		}

		{
			Set<Entry<? extends BK, ? extends BC>> checkedoptionals = new HashSet<>();
			LinkedList<Entry<? extends BK, ? extends BC>> bundlestack = new LinkedList<>();
			bundlestack.add(basebundleentry);
			while (!bundlestack.isEmpty()) {
				Entry<? extends BK, ? extends BC> bundle = bundlestack.removeFirst();
				if (!checkedoptionals.add(bundle)) {
					continue;
				}
				BundleDependencyInformation optionalinfo = optionalhavingbundles.get(bundle.getKey());
				if (optionalinfo != null) {
					for (Entry<BundleIdentifier, ? extends BundleDependencyList> entry : optionalinfo.getDependencies()
							.entrySet()) {
						BundleDependencyList deplist = entry.getValue();
						if (deplist.hasOptional()) {
							BundleDependencyInformation optionaladded = BundleDependencyInformation
									.create(Collections.singletonMap(entry.getKey(), deplist));
							BundleResolutionState<BK, BC> nbundleresstate = new BundleResolutionState<>(bundle,
									optionaladded, logger);
							satisfyBundleVersion(cachingbundleslookupfunction,
									cachingbundledependencieswithoutoptionalslookupfunction, result, logger,
									nbundleresstate);
							//doesnt matter if the satisfying of the optional bundle have succeeded
						}
					}
				}
				BundleDependencyInformation deps = dependencieslookupcache.get(bundle.getKey()).get();
				for (BundleIdentifier depbundle : deps.getDependencies().keySet()) {
					Entry<? extends BK, ? extends BC> resbundle = result
							.get(ImmutableUtils.makeImmutableMapEntry(depbundle, bundle.getValue()));
					if (resbundle != null) {
						bundlestack.add(resbundle);
					}
				}
			}
		}

		return new ResolutionResult<>(result, basewithoutversionbundleentry, basebundleentry, dependencieslookupcache);
	}

	private static class RandomHolder {
		static final SecureRandom RANDOMER = new SecureRandom();
	}

	/**
	 * Generates a random bundle identifier.
	 * <p>
	 * The generated bundle identifier will have a random name and version assigned to it.
	 * <p>
	 * It is primarily to be used with {@link DependencyUtils#satisfyDependencyRequirements
	 * DependencyUtils.satisfyDependencyRequirements} to ensure that it has a root bundle identifier for resolution.
	 * <p>
	 * The generated bundle identifier has 128 bit of randomness (similar to {@link UUID}).
	 * 
	 * @return The generated random bundle identifier.
	 */
	public static BundleIdentifier randomBundleIdentifier() {
		byte[] rand = new byte[16];
		RandomHolder.RANDOMER.nextBytes(rand);
		StringBuilder sb = new StringBuilder();
		StringUtils.toHexString(rand, 0, 15, sb);
		sb.append("-v");
		sb.append(Byte.toUnsignedInt(rand[15]));
		return BundleIdentifier.valueOf(sb.toString());
	}

	private static boolean isVersionRangeMetaDataExcludesDependency(BundleDependency dep, String metaname,
			String currentversionconstraint) throws NullPointerException {
		if (currentversionconstraint == null) {
			//if no constraint is specified, don't apply exclusion
			return false;
		}
		Objects.requireNonNull(dep, "dependency");
		String jrerange = dep.getMetaData().get(metaname);
		if (jrerange == null) {
			return false;
		}
		VersionRange range;
		try {
			range = VersionRange.valueOf(jrerange);
		} catch (IllegalArgumentException e) {
			throw new InvalidNestBundleException("Failed to parse " + metaname, e);
		}
		if (!range.includes(currentversionconstraint)) {
			return true;
		}
		return false;
	}

	private static final Pattern PATTERN_COMMA_WHITESPACE_SPLIT = Pattern.compile("[, \\t]+");

	private static boolean isNativeArchitectureDependencyMetaDataExcludes(BundleDependency dependency,
			String architecture) throws NullPointerException {
		if (architecture == null) {
			return false;
		}
		Objects.requireNonNull(dependency, "dependency");
		String metadata = dependency.getMetaData().get(BundleInformation.DEPENDENCY_META_NATIVE_ARCHITECTURE);
		if (metadata == null) {
			return false;
		}
		for (String arch : PATTERN_COMMA_WHITESPACE_SPLIT.split(metadata)) {
			if (architecture.equals(arch)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if the given constraint configuration excludes the specified bundle dependency.
	 * <p>
	 * The method will check the {@linkplain BundleDependency#getMetaData() meta-data} of the dependency and examine it
	 * against the specified constraint configuration. The constraint meta-data names declared in
	 * {@link BundleInformation} will be used to determine exclusion.
	 * <p>
	 * If the argument constraint configuration object is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param constraints
	 *            The constraint configuration.
	 * @param dependency
	 *            The dependency to examine.
	 * @return <code>true</code> if the dependency should be excluded.
	 * @throws NullPointerException
	 *             If the dependency is <code>null</code>.
	 * @see BundleInformation#DEPENDENCY_META_JRE_VERSION
	 * @see BundleInformation#DEPENDENCY_META_NATIVE_ARCHITECTURE
	 * @see BundleInformation#DEPENDENCY_META_REPOSITORY_VERSION
	 * @see BundleInformation#DEPENDENCY_META_BUILD_SYSTEM_VERSION
	 */
	public static boolean isDependencyConstraintExcludes(DependencyConstraintConfiguration constraints,
			BundleDependency dependency) throws NullPointerException {
		if (constraints == null) {
			return false;
		}
		Objects.requireNonNull(dependency, "dependency");
		if (DependencyUtils.isVersionRangeMetaDataExcludesDependency(dependency,
				BundleInformation.DEPENDENCY_META_JRE_VERSION,
				Objects.toString(constraints.getJreMajorVersion(), null))) {
			return true;
		}
		if (DependencyUtils.isVersionRangeMetaDataExcludesDependency(dependency,
				BundleInformation.DEPENDENCY_META_BUILD_SYSTEM_VERSION, constraints.getBuildSystemVersion())) {
			return true;
		}
		if (DependencyUtils.isVersionRangeMetaDataExcludesDependency(dependency,
				BundleInformation.DEPENDENCY_META_REPOSITORY_VERSION, constraints.getRepositoryVersion())) {
			return true;
		}
		if (DependencyUtils.isNativeArchitectureDependencyMetaDataExcludes(dependency,
				constraints.getNativeArchitecture())) {
			return true;
		}
		return false;
	}

	/**
	 * Checks if the given constraint configuration excludes the specified bundle information for usage on the
	 * classpath.
	 * <p>
	 * The method will examine the allowed classpath constraints in the bundle information, and check if the specified
	 * constraints are allowed with it.
	 * <p>
	 * If the argument constraint configuration object is <code>null</code>, <code>false</code> is returned.
	 * 
	 * @param constraints
	 *            The constraint configuration.
	 * @param bundleinfo
	 *            The bundle information.
	 * @return <code>true</code> if the bundle should be excluded.
	 * @throws NullPointerException
	 *             If the bundle information is <code>null</code>.
	 * @see BundleInformation#getSupportedClassPathJreVersionRange()
	 * @see BundleInformation#getSupportedClassPathArchitectures()
	 * @see BundleInformation#getSupportedClassPathRepositoryVersionRange()
	 * @see BundleInformation#getSupportedClassPathBuildSystemVersionRange()
	 */
	public static boolean isDependencyConstraintClassPathExcludes(DependencyConstraintConfiguration constraints,
			BundleInformation bundleinfo) throws NullPointerException {
		if (constraints == null) {
			return false;
		}
		Objects.requireNonNull(bundleinfo, "bundle info");
		Integer jremajor = constraints.getJreMajorVersion();
		if (!supportsClassPathJreVersion(bundleinfo, jremajor)) {
			return true;
		}
		String repoversion = constraints.getRepositoryVersion();
		if (!supportsClassPathRepositoryVersion(bundleinfo, repoversion)) {
			return true;
		}
		String buildsystemversion = constraints.getBuildSystemVersion();
		if (!supportsClassPathBuildSystemVersion(bundleinfo, buildsystemversion)) {
			return true;
		}
		String nativearch = constraints.getNativeArchitecture();
		if (!supportsClassPathArchitecture(bundleinfo, nativearch)) {
			return true;
		}
		return false;
	}

	/**
	 * Checks if the argument bundle supports the specified Java Runtime major version.
	 * <p>
	 * If the version is <code>null</code>, the method will return <code>true</code>.
	 * 
	 * @param bundleinfo
	 *            The bundle information.
	 * @param version
	 *            The Java major version.
	 * @return <code>true</code> if the bundle supports the JRE major version.
	 * @throws NullPointerException
	 *             If the bundle information is <code>null</code>.
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS
	 * @see BundleInformation#DEPENDENCY_META_JRE_VERSION
	 */
	public static boolean supportsClassPathJreVersion(BundleInformation bundleinfo, Integer version)
			throws NullPointerException {
		if (version == null) {
			return true;
		}
		return supportsClassPathJreVersion(bundleinfo, version.intValue());
	}

	/**
	 * Checks if the argument bundle supports the specified Java Runtime major version.
	 * <p>
	 * Same as {@link #supportsClassPathJreVersion(BundleInformation, Integer)}, but takes a primitive <code>int</code>
	 * version as the argument.
	 * 
	 * @param bundleinfo
	 *            The bundle information.
	 * @param version
	 *            The Java major version.
	 * @return <code>true</code> if the bundle supports the JRE major version.
	 * @throws NullPointerException
	 *             If the bundle information is <code>null</code>.
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS
	 * @see BundleInformation#DEPENDENCY_META_JRE_VERSION
	 */
	public static boolean supportsClassPathJreVersion(BundleInformation bundleinfo, int version)
			throws NullPointerException {
		Objects.requireNonNull(bundleinfo, "bundle information");
		VersionRange range = bundleinfo.getSupportedClassPathJreVersionRange();
		return range == null || range.includes(Integer.toString(version));
	}

	/**
	 * Checks if the argument bundle supports the specified Nest repository version.
	 * <p>
	 * If the version is <code>null</code>, the method will return <code>true</code>.
	 * 
	 * @param bundleinfo
	 *            The bundle information.
	 * @param version
	 *            The Nest repository version.
	 * @return <code>true</code> if the bundle supports the Nest repository version.
	 * @throws NullPointerException
	 *             If the bundle information is <code>null</code>.
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS
	 * @see BundleInformation#DEPENDENCY_META_REPOSITORY_VERSION
	 */
	public static boolean supportsClassPathRepositoryVersion(BundleInformation bundleinfo, String version)
			throws NullPointerException {
		if (version == null) {
			return true;
		}
		Objects.requireNonNull(bundleinfo, "bundle information");
		VersionRange range = bundleinfo.getSupportedClassPathRepositoryVersionRange();
		return range == null || range.includes(version);
	}

	/**
	 * Checks if the argument bundle supports the specified saker.build system version.
	 * <p>
	 * If the version is <code>null</code>, the method will return <code>true</code>.
	 * 
	 * @param bundleinfo
	 *            The bundle information.
	 * @param version
	 *            The saker.build system version.
	 * @return <code>true</code> if the bundle supports the saker.build system version.
	 * @throws NullPointerException
	 *             If the bundle information is <code>null</code>.
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS
	 * @see BundleInformation#DEPENDENCY_META_BUILD_SYSTEM_VERSION
	 */
	public static boolean supportsClassPathBuildSystemVersion(BundleInformation bundleinfo, String version)
			throws NullPointerException {
		if (version == null) {
			return true;
		}
		Objects.requireNonNull(bundleinfo, "bundle information");
		VersionRange range = bundleinfo.getSupportedClassPathBuildSystemVersionRange();
		return range == null || range.includes(version);
	}

	/**
	 * Checks if the argument bundle supports the specified native architecture.
	 * <p>
	 * If the architecture is <code>null</code>, the method will return <code>true</code>.
	 * 
	 * @param bundleinfo
	 *            The bundle information.
	 * @param arch
	 *            The native architecture.
	 * @return <code>true</code> if the bundle supports the specified native architecture.
	 * @throws NullPointerException
	 *             If the bundle information is <code>null</code>.
	 * @see BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS
	 * @see BundleInformation#DEPENDENCY_META_BUILD_SYSTEM_VERSION
	 */
	public static boolean supportsClassPathArchitecture(BundleInformation bundleinfo, String arch)
			throws NullPointerException {
		if (arch == null) {
			return true;
		}
		Objects.requireNonNull(bundleinfo, "bundle information");
		Set<String> architectures = bundleinfo.getSupportedClassPathArchitectures();
		return architectures == null || architectures.contains(arch);
	}

	private static <BK extends BundleIdentifierHolder, BC> boolean satisfy(DependencyResolutionState<BK, BC> state,
			BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
			BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
			Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> pinnedbundles,
			DependencyResolutionLogger<? super BC> logger) {
		if (logger != null) {
			logger.enter(state.bundleId.getKey(), state.bundleId.getValue());
		}
		while (state.hasNextBundle()) {
			BundleResolutionState<BK, BC> bundleresolutionstate = state.nextBundle();
			{
				Object prev = pinnedbundles.putIfAbsent(state.bundleId, bundleresolutionstate.bundleEntry);
				if (prev != null) {
					throw new AssertionError("Already present: " + state.bundleId + " - " + prev + " against "
							+ bundleresolutionstate.bundleEntry);
				}
			}
			satisfyBundleVersion(bundleslookupfunction, bundledependencieslookupfunction, pinnedbundles, logger,
					bundleresolutionstate);
			//satisfied the bundle, if we're no longer backtracking
			if (!bundleresolutionstate.isBackTracking()) {
				if (logger != null) {
					logger.exit(state.bundleId.getKey(), state.bundleId.getValue(),
							bundleresolutionstate.getBundleKey().getBundleIdentifier(),
							bundleresolutionstate.getBundleContext());
				}
				return true;
			}
			pinnedbundles.remove(state.bundleId);
		}
		if (logger != null) {
			logger.exit(state.bundleId.getKey(), state.bundleId.getValue(), null, null);
		}
		return false;
	}

	private static <BK extends BundleIdentifierHolder, BC> void satisfyBundleVersion(
			BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
			BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
			Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> pinnedbundles,
			DependencyResolutionLogger<? super BC> logger, BundleResolutionState<BK, BC> bundleresolutionstate) {
		if (logger != null) {
			logger.enterVersion(bundleresolutionstate.getBundleKey().getBundleIdentifier(),
					bundleresolutionstate.getBundleContext());
		}
		bundle_resolver:
		while (bundleresolutionstate.hasNext()) {
			DependencyResolutionState<BK, BC> deprs = bundleresolutionstate.next(bundleslookupfunction,
					bundledependencieslookupfunction);
			if (deprs.satisfiedBundleResults != null) {
				pinnedbundles.keySet().removeAll(deprs.satisfiedBundleResults);
				deprs.satisfiedBundleResults = null;
			}
			Entry<? extends BK, ? extends BC> presentbundle = pinnedbundles.get(deprs.bundleId);
			if (presentbundle != null) {
				String version = presentbundle.getKey().getBundleIdentifier().getVersionNumber();
				BundleDependencyList deplist = bundleresolutionstate.dependencyInfo
						.getDependencyList(deprs.bundleId.getKey());
				for (BundleDependency bdep : deplist.getDependencies()) {
					VersionRange range = bdep.getRange();
					if (!range.includes(version)) {
						if (logger != null) {
							logger.dependencyVersionRangeMismatchForPinnedBundle(bdep,
									presentbundle.getKey().getBundleIdentifier(), presentbundle.getValue());
						}
						bundleresolutionstate.startBackTrack();
						continue bundle_resolver;
					}
				}
				if (logger != null) {
					logger.dependencyFoundPinned(deprs.bundleId.getKey(), deprs.bundleId.getValue(),
							presentbundle.getKey().getBundleIdentifier(), presentbundle.getValue());
				}
				continue;
			}
			Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> subpinned = new HashMap<>(
					pinnedbundles);
			boolean satisfied = satisfy(deprs, bundleslookupfunction, bundledependencieslookupfunction, subpinned,
					logger);
			if (!satisfied) {
				bundleresolutionstate.startBackTrack();
			} else {
				subpinned.keySet().removeAll(pinnedbundles.keySet());
				pinnedbundles.putAll(subpinned);
				deprs.satisfiedBundleResults = subpinned.keySet();
				bundleresolutionstate.clearBackTrack();
			}
		}
		if (logger != null) {
			logger.exitVersion(bundleresolutionstate.getBundleKey().getBundleIdentifier(),
					bundleresolutionstate.getBundleContext());
		}
	}

	private DependencyUtils() {
		throw new UnsupportedOperationException();
	}

	private static final class BundleResolutionState<BK extends BundleIdentifierHolder, BC> {
		//versioned bundle id
		protected final BundleDependencyInformation dependencyInfo;

		protected final ListIterator<? extends Entry<BundleIdentifier, ? extends BundleDependencyList>> depsIt;
		protected final LinkedList<DependencyResolutionState<BK, BC>> resolutionStateBacktrack = new LinkedList<>();
		protected boolean backTracking = false;
		protected final DependencyResolutionLogger<? super BC> logger;
		protected final Entry<? extends BK, ? extends BC> bundleEntry;

		public BundleResolutionState(Entry<? extends BK, ? extends BC> bundleentry,
				BundleDependencyInformation dependencyInfo, DependencyResolutionLogger<? super BC> logger) {
			this.bundleEntry = bundleentry;
			this.dependencyInfo = dependencyInfo;
			this.logger = logger;
			this.depsIt = new ArrayList<>(dependencyInfo.getDependencies().entrySet()).listIterator();
		}

		public boolean hasNext() {
			if (backTracking) {
				return !resolutionStateBacktrack.isEmpty();
			}
			return depsIt.hasNext();
		}

		public DependencyResolutionState<BK, BC> next(
				BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
				BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction) {
			if (backTracking) {
				depsIt.previous();
				return resolutionStateBacktrack.removeLast();
			}
			Entry<BundleIdentifier, ? extends BundleDependencyList> depentry = depsIt.next();
			BundleIdentifier withouversionbi = depentry.getKey();
			DependencyResolutionState<BK, BC> result = new DependencyResolutionState<>(
					ImmutableUtils.makeImmutableMapEntry(withouversionbi, getBundleContext()),
					bundleslookupfunction.apply(withouversionbi, getBundleContext()), depentry.getValue(),
					bundledependencieslookupfunction, logger);
			resolutionStateBacktrack.add(result);
			return result;
		}

		public BC getBundleContext() {
			return bundleEntry.getValue();
		}

		public BK getBundleKey() {
			return bundleEntry.getKey();
		}

		public void startBackTrack() {
			if (!backTracking) {
				backTracking = true;
				depsIt.previous();
				resolutionStateBacktrack.removeLast();
			}
		}

		public void clearBackTrack() {
			if (backTracking) {
				backTracking = false;
				depsIt.next();
			}
		}

		public boolean isBackTracking() {
			return backTracking;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + dependencyInfo + "]";
		}
	}

	private static final class DependencyResolutionState<BK extends BundleIdentifierHolder, BC> {
		//versionless bundle id
		protected final Entry<? extends BundleIdentifier, ? extends BC> bundleId;

		//iterates over the looked up bundles
		protected Iterator<? extends Entry<? extends BK, ? extends BC>> bundleIt;
		protected BundleResolutionState<BK, BC> next;

		protected final BundleDependencyList dependencyList;

		protected Set<Entry<? extends BundleIdentifier, ? extends BC>> satisfiedBundleResults;

		protected final DependencyResolutionLogger<? super BC> logger;
		protected final Iterable<? extends Entry<? extends BK, ? extends BC>> lookedupBundles;
		protected final BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundleDependenciesLookupFunction;

		public DependencyResolutionState(Entry<? extends BundleIdentifier, ? extends BC> bundleId,
				Iterable<? extends Entry<? extends BK, ? extends BC>> lookedupbundles, BundleDependencyList deplist,
				BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
				DependencyResolutionLogger<? super BC> logger) {
			this.bundleId = bundleId;
			this.lookedupBundles = lookedupbundles;
			this.dependencyList = deplist;
			this.bundleDependenciesLookupFunction = bundledependencieslookupfunction;
			this.logger = logger;
		}

		private void moveToNext() {
			outer:
			while (this.bundleIt.hasNext()) {
				Entry<? extends BK, ? extends BC> n = this.bundleIt.next();
				String vnumber = n.getKey().getBundleIdentifier().getVersionNumber();
				if (dependencyList != null) {
					for (BundleDependency bdep : dependencyList.getDependencies()) {
						VersionRange range = bdep.getRange();
						if (!range.includes(vnumber)) {
							if (logger != null) {
								logger.dependencyVersionRangeMismatch(bdep, n.getKey().getBundleIdentifier(),
										n.getValue());
							}
							continue outer;
						}
					}
				}
				BundleDependencyInformation deps = bundleDependenciesLookupFunction.apply(n.getKey(), n.getValue());
				if (deps == null) {
					continue;
				}
				next = new BundleResolutionState<>(n, deps, logger);
				return;
			}
			//not found
			next = null;
		}

		public boolean hasNextBundle() {
			if (this.bundleIt == null) {
				this.bundleIt = this.lookedupBundles.iterator();
				moveToNext();
			}
			return next != null;
		}

		public BundleResolutionState<BK, BC> nextBundle() {
			BundleResolutionState<BK, BC> res = next;
			moveToNext();
			return res;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + bundleId + "]";
		}
	}

	private static final class ResolutionResult<BK extends BundleIdentifierHolder, BC>
			implements DependencyResolutionResult<BK, BC> {
		private Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> result;
		private Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> declarationOrderedResult;
		private Map<Entry<? extends BK, ? extends BC>, Map<BK, BC>> dependencyDomainResult;
		private LazySupplier<Void> lazyComputer;

		public ResolutionResult(
				Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> result,
				Entry<? extends BundleIdentifier, ? extends BC> basewithoutversion,
				Entry<? extends BK, ? extends BC> basebundleentry,
				Map<BK, Supplier<BundleDependencyInformation>> dependencieslookupcache) {
			this.result = ImmutableUtils.unmodifiableMap(result);
			lazyComputer = LazySupplier.of(() -> {
				computeLazily(result, basewithoutversion, basebundleentry, dependencieslookupcache);
				return null;
			});
		}

		private void computeLazily(
				Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> result,
				Entry<? extends BundleIdentifier, ? extends BC> basewithoutversion,
				Entry<? extends BK, ? extends BC> basebundleentry,
				Map<BK, Supplier<BundleDependencyInformation>> dependencieslookupcache) {
			Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> declarationordered = new LinkedHashMap<>();
			Map<Entry<? extends BK, ? extends BC>, Map<BK, BC>> dependencydomainresult = new LinkedHashMap<>();
			LinkedList<Entry<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>>> entrystack = new LinkedList<>();
			entrystack.add(ImmutableUtils.makeImmutableMapEntry(basewithoutversion, basebundleentry));
			while (!entrystack.isEmpty()) {
				Entry<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> entry = entrystack
						.removeFirst();
				Entry<? extends BK, ? extends BC> presentbundle = entry.getValue();
				Object resprev = declarationordered.putIfAbsent(entry.getKey(), presentbundle);
				if (resprev != null) {
					continue;
				}
				LinkedHashMap<BK, BC> domain = new LinkedHashMap<>();

				BundleDependencyInformation deps = dependencieslookupcache.get(presentbundle.getKey()).get();
				for (BundleIdentifier depbundle : deps.getDependencies().keySet()) {
					Entry<BundleIdentifier, ? extends BC> depbundleentry = ImmutableUtils
							.makeImmutableMapEntry(depbundle, presentbundle.getValue());
					Entry<? extends BK, ? extends BC> depactualbundle = result.get(depbundleentry);
					if (depactualbundle == null) {
						//that bundle was not satisfied. probably because optional
						continue;
					}
					domain.put(depactualbundle.getKey(), depactualbundle.getValue());
					entrystack.add(ImmutableUtils.makeImmutableMapEntry(depbundleentry, depactualbundle));
				}
				dependencydomainresult.put(presentbundle, ImmutableUtils.unmodifiableMap(domain));
			}
			this.declarationOrderedResult = ImmutableUtils.unmodifiableMap(declarationordered);
			this.dependencyDomainResult = ImmutableUtils.unmodifiableMap(dependencydomainresult);
		}

		@Override
		public Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> getResultInAnyOrder() {
			return result;
		}

		@Override
		public Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> getResultInDeclarationOrder() {
			lazyComputer.get();
			return declarationOrderedResult;
		}

		@Override
		public Map<Entry<? extends BK, ? extends BC>, ? extends Map<? extends BK, ? extends BC>> getDependencyDomainResult() {
			lazyComputer.get();
			return dependencyDomainResult;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(getClass().getSimpleName());
			sb.append("[");
			for (Iterator<Entry<? extends BK, ? extends BC>> it = result.values().iterator(); it.hasNext();) {
				Entry<? extends BK, ? extends BC> entry = it.next();
				sb.append(entry.getKey().getBundleIdentifier());
				if (it.hasNext()) {
					sb.append(", ");
				}
			}
			sb.append("]");
			return sb.toString();
		}

	}
}
