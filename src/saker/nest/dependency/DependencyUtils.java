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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
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
import saker.nest.ConfiguredRepositoryStorage;
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
import saker.nest.meta.Versions;
import saker.nest.version.VersionRange;
import testing.saker.nest.TestFlag;

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
	 * @deprecated Use {@link DependencyUtils#satisfyDependencyDomain(BundleIdentifierHolder, Object, BundleDependencyInformation, BiFunction, BiFunction, DependencyResolutionLogger)
	 *                 DependencyUtils.satisfyDependencyDomain} instead.
	 */
	@Deprecated
	public static <BK extends BundleIdentifierHolder, BC> DependencyResolutionResult<BK, BC> satisfyDependencyRequirements(
			BK basebundle, BC basebundlecontext, BundleDependencyInformation basedependencyinfo,
			BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
			BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
			DependencyResolutionLogger<? super BC> logger) throws NullPointerException, IllegalArgumentException {
		if (((querySpecialDependendyFlags(basedependencyinfo) & SPECIAL_PRIVATE) == SPECIAL_PRIVATE)) {
			throw new UnsupportedOperationException(
					"Private dependencies are not supported in this version: " + Versions.VERSION_STRING_FULL);
		}
		DependencyDomainResolutionResult<BK, BC> satisfied = satisfyDependencyDomain(basebundle, basebundlecontext,
				basedependencyinfo, bundleslookupfunction, (t, u) -> {
					BundleDependencyInformation r = bundledependencieslookupfunction.apply(t, u);
					if (r != null && ((querySpecialDependendyFlags(r) & SPECIAL_PRIVATE) == SPECIAL_PRIVATE)) {
						throw new UnsupportedOperationException(
								"Private dependencies are not supported in this version: "
										+ Versions.VERSION_STRING_FULL);
					}
					return r;
				}, logger);
		if (satisfied == null) {
			return null;
		}
		return new ResolutionResult<>(basebundle, basebundlecontext, satisfied);
	}

	//TODO @since saker.nest 0.8.1
	public static <BK extends BundleIdentifierHolder, BC> DependencyDomainResolutionResult<BK, BC> satisfyDependencyDomain(
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
		Map<BK, Supplier<BundleDependencyInformation>> dependencieswithoutspecialslookupcache = new HashMap<>();
		Map<BK, BundleDependencyInformation> optionalhavingbundles = new HashMap<>();

		Entry<? extends BK, ? extends BC> basebundleentry = ImmutableUtils.makeImmutableMapEntry(basebundle,
				basebundlecontext);
		int basespeciality = querySpecialDependendyFlags(basedependencyinfo);
		if (((basespeciality & SPECIAL_OPTIONAL) == SPECIAL_OPTIONAL)) {
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

		BundleDependencyInformation basedepinfowithoutspecials = withoutOptionalDependencies(basedependencyinfo);
		dependencieslookupcache.put(basebundle, Functionals.valSupplier(basedependencyinfo));
		dependencieswithoutspecialslookupcache.put(basebundle, Functionals.valSupplier(basedepinfowithoutspecials));

		BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> cachingbundledependencieslookupfunction = (
				bi, bc) -> {
			return dependencieslookupcache.computeIfAbsent(bi, kentry -> {
				return LazySupplier.of(() -> {
					BundleDependencyInformation depinfos = bundledependencieslookupfunction.apply(bi, bc);
					if (depinfos == null) {
						return null;
					}

					int depspeciality = querySpecialDependendyFlags(depinfos);
					if (((depspeciality & SPECIAL_OPTIONAL) == SPECIAL_OPTIONAL)) {
						optionalhavingbundles.put(kentry, depinfos);
					}

					return depinfos;
				});
			}).get();
		};
		BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> cachingbundledependencieswithoutspecialslookupfunction = (
				bi, bc) -> {
			return dependencieswithoutspecialslookupcache.computeIfAbsent(bi, kentry -> {
				return LazySupplier.of(() -> {
					BundleDependencyInformation depinfos = cachingbundledependencieslookupfunction.apply(bi, bc);
					if (depinfos != null) {
						return withoutOptionalDependencies(depinfos);
					}
					return null;
				});
			}).get();
		};

		Map<PrivateScopeDependencyRoot<? extends BK, ? extends BC>, Optional<DomainResult<BK, BC>>> privatescopedomains = new HashMap<>();
		DomainResult<BK, BC> basedomain = DomainResult.newDomain(basebundleentry);
		{
			BundleResolutionState<BK, BC> bundleresstate = new BundleResolutionState<>(basedepinfowithoutspecials,
					logger, basebundleentry);
			satisfyBundleVersion(cachingbundleslookupfunction, cachingbundledependencieswithoutspecialslookupfunction,
					basedomain, logger, bundleresstate, privatescopedomains);
			if (bundleresstate.isBackTracking()) {
				return null;
			}
		}

		Set<DomainResult<BK, BC>> checkedoptionals = new HashSet<>();
		while (true) {
			boolean hadchange = false;
			ArrayDeque<DomainResult<BK, BC>> bundlestack = new ArrayDeque<>(basedomain.getTotalDomain());
			while (!bundlestack.isEmpty()) {
				DomainResult<BK, BC> bundledomain = bundlestack.removeFirst();
				if (!checkedoptionals.add(bundledomain)) {
					continue;
				}

				BundleDependencyInformation optionalinfo = optionalhavingbundles.get(bundledomain.bundleEntry.getKey());
				if (optionalinfo == null) {
					continue;
				}

				optional_dependencies_loop:
				for (Entry<BundleIdentifier, ? extends BundleDependencyList> entry : optionalinfo.getDependencies()
						.entrySet()) {
					BundleDependencyList deplist = entry.getValue();
					if (!deplist.hasOptional()) {
						continue;
					}
					deplist = onlyOptionals(deplist);
					if (ConfiguredRepositoryStorage.isAllPrivateDependencies(deplist)) {
						//the optional dependency refers to a private dependency
						Iterable<? extends Entry<? extends BK, ? extends BC>> lookedupbundles = cachingbundleslookupfunction
								.apply(entry.getKey(), bundledomain.bundleEntry.getValue());
						for (Entry<? extends BK, ? extends BC> depbundle : lookedupbundles) {
							PrivateScopeDependencyRoot<BK, BC> privatescope = new PrivateScopeDependencyRoot<>(
									bundledomain.bundleEntry, deplist, depbundle);
							Optional<DomainResult<BK, BC>> privscopeopt = privatescopedomains.get(privatescope);
							if (privscopeopt != null) {
								//it was already tried to be resolved 
								DomainResult<BK, BC> resolveddomain = privscopeopt.orElse(null);
								if (resolveddomain != null) {
									//the private scope is succesfully resolved.
									bundledomain.directDependencies.put(depbundle, resolveddomain);
									continue optional_dependencies_loop;
								}
								//the resolution failed already by others, don't try again
								continue optional_dependencies_loop;
							}
							//try resolving the private dependency

							DomainResult<BK, BC> resolvedomain = DomainResult.newDomain(bundledomain.bundleEntry);
							BundleDependencyInformation optionaladded = BundleDependencyInformation
									.create(Collections.singletonMap(entry.getKey(), deplist));
							BundleResolutionState<BK, BC> nbundleresstate = new BundleResolutionState<>(optionaladded,
									logger, bundledomain.bundleEntry);
							satisfyBundleVersion(cachingbundleslookupfunction,
									cachingbundledependencieswithoutspecialslookupfunction, resolvedomain, logger,
									nbundleresstate, privatescopedomains);
							if (!nbundleresstate.isBackTracking()) {
								//successfully resolved it
								hadchange = true;
								bundledomain.directDependencies.put(depbundle, resolvedomain);
								privatescopedomains.put(privatescope, Optional.of(resolvedomain));
							} else {
								//failed to resolve 
								privatescopedomains.put(privatescope, Optional.empty());
							}
						}
						continue optional_dependencies_loop;
					}
					BundleDependencyInformation optionaladded = BundleDependencyInformation
							.create(Collections.singletonMap(entry.getKey(), deplist));
					BundleResolutionState<BK, BC> nbundleresstate = new BundleResolutionState<>(optionaladded, logger,
							bundledomain.bundleEntry);
					satisfyBundleVersion(cachingbundleslookupfunction,
							cachingbundledependencieswithoutspecialslookupfunction, bundledomain, logger,
							nbundleresstate, privatescopedomains);
					if (!nbundleresstate.isBackTracking()) {
						hadchange = true;
					}
					//doesnt matter if the satisfying of the optional bundle have succeeded
				}
			}
			if (!hadchange) {
				break;
			}
		}

		return new DependencyDomainResolutionResultImpl<>(basedomain, dependencieslookupcache);
	}

	private static class DependencyDomainResolutionResultImpl<BK extends BundleIdentifierHolder, BC>
			implements DependencyDomainResolutionResult<BK, BC> {

		protected Map<Entry<? extends BK, ? extends BC>, ? extends DependencyDomainResolutionResult<BK, BC>> directDependencies;

		public DependencyDomainResolutionResultImpl() {
		}

		public DependencyDomainResolutionResultImpl(DomainResult<BK, BC> basedomain,
				Map<BK, Supplier<BundleDependencyInformation>> dependencieslookupcache) {
			this(basedomain, dependencieslookupcache, new HashMap<>());
		}

		public DependencyDomainResolutionResultImpl(DomainResult<BK, BC> basedomain,
				Map<BK, Supplier<BundleDependencyInformation>> dependencieslookupcache,
				Map<Entry<? extends BK, ? extends BC>, DependencyDomainResolutionResultImpl<BK, BC>> created) {
			Map<Entry<? extends BK, ? extends BC>, DependencyDomainResolutionResult<BK, BC>> directdependencies = createDirectDependencies(
					basedomain, dependencieslookupcache, created);
			this.directDependencies = ImmutableUtils.unmodifiableMap(directdependencies);
		}

		private static <BK extends BundleIdentifierHolder, BC> Map<Entry<? extends BK, ? extends BC>, DependencyDomainResolutionResult<BK, BC>> createDirectDependencies(
				DomainResult<BK, BC> basedomain, Map<BK, Supplier<BundleDependencyInformation>> dependencieslookupcache,
				Map<Entry<? extends BK, ? extends BC>, DependencyDomainResolutionResultImpl<BK, BC>> created) {
			Map<Entry<? extends BK, ? extends BC>, DependencyDomainResolutionResult<BK, BC>> directdependencies = new LinkedHashMap<>();
			BundleDependencyInformation depinfo = dependencieslookupcache.get(basedomain.bundleEntry.getKey()).get();

			for (BundleIdentifier dep : depinfo.getDependencies().keySet()) {
				Entry<Entry<? extends BK, ? extends BC>, DomainResult<BK, BC>> depentry = getDependencyEntryWithBundleId(
						basedomain.directDependencies, dep);
				if (depentry == null) {
					//possible when optional was not resolved
					continue;
				}

				DependencyDomainResolutionResultImpl<BK, BC> directdomainres = created.get(depentry.getKey());
				if (directdomainres == null) {
					directdomainres = new DependencyDomainResolutionResultImpl<>();
					created.put(depentry.getKey(), directdomainres);

					directdomainres.directDependencies = createDirectDependencies(depentry.getValue(),
							dependencieslookupcache, created);
				}
				directdependencies.put(depentry.getKey(), directdomainres);
			}

			return directdependencies;
		}

		@Override
		public Map<Entry<? extends BK, ? extends BC>, ? extends DependencyDomainResolutionResult<BK, BC>> getDirectDependencies() {
			return directDependencies;
		}

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

	private static final int SPECIAL_OPTIONAL = 1 << 1;
	private static final int SPECIAL_PRIVATE = 1 << 2;
	private static final int SPECIAL_ALL = SPECIAL_OPTIONAL | SPECIAL_PRIVATE;

	private static int querySpecialDependendyFlags(BundleDependencyInformation deps) {
		int result = 0;
		for (BundleDependencyList dlist : deps.getDependencies().values()) {
			for (BundleDependency d : dlist.getDependencies()) {
				if (((result & SPECIAL_OPTIONAL) != SPECIAL_OPTIONAL)) {
					if (d.isOptional()) {
						result |= SPECIAL_OPTIONAL;
						if (result == SPECIAL_ALL) {
							return SPECIAL_ALL;
						}
					}
				}
				if (((result & SPECIAL_PRIVATE) != SPECIAL_PRIVATE)) {
					if (d.isPrivate()) {
						result |= SPECIAL_PRIVATE;
						if (result == SPECIAL_ALL) {
							return SPECIAL_ALL;
						}
					}
				}
			}
		}
		return result;
	}

	private static BundleDependencyList onlyOptionals(BundleDependencyList deplist) {
		return deplist.filter(d -> !d.isOptional() ? null : d);
	}

	private static BundleDependencyInformation withoutOptionalDependencies(BundleDependencyInformation deps) {
		return deps.filter((bi, deplist) -> deplist.filter(d -> d.isOptional() ? null : d));
	}

	private static <BK extends BundleIdentifierHolder, BC> boolean satisfy(DependencyResolutionState<BK, BC> state,
			BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
			BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
			DomainResult<BK, BC> domain, DependencyResolutionLogger<? super BC> logger,
			Map<PrivateScopeDependencyRoot<? extends BK, ? extends BC>, Optional<DomainResult<BK, BC>>> privatescopedomains) {
		if (logger != null) {
			logger.enter(state.versionlessBundleId.getKey(), state.versionlessBundleId.getValue());
		}
		BundleResolutionState<BK, BC> bundleresolutionstate;

		while ((bundleresolutionstate = state.nextBundle()) != null) {
			DomainResult<BK, BC> usedomain = DomainResult.subResolveDomain(domain, bundleresolutionstate.bundleEntry);
			domain.pin(state.versionlessBundleId, usedomain);

			satisfyBundleVersion(bundleslookupfunction, bundledependencieslookupfunction, usedomain, logger,
					bundleresolutionstate, privatescopedomains);
			//satisfied the bundle, if we're no longer backtracking
			if (!bundleresolutionstate.isBackTracking()) {
				domain.directDependencies.put(bundleresolutionstate.bundleEntry, usedomain);
				if (logger != null) {
					logger.exit(state.versionlessBundleId.getKey(), state.versionlessBundleId.getValue(),
							bundleresolutionstate.getBundleKey().getBundleIdentifier(),
							bundleresolutionstate.getBundleContext());
				}
				//store the state so if we need to backtract, we can continue from here
				state.storeState(bundleresolutionstate);
				return true;
			}
			domain.unpin(state.versionlessBundleId, usedomain);
		}
		if (logger != null) {
			logger.exit(state.versionlessBundleId.getKey(), state.versionlessBundleId.getValue(), null, null);
		}
		return false;
	}

	private static <BK extends BundleIdentifierHolder, BC> void satisfyBundleVersion(
			BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
			BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
			DomainResult<BK, BC> domain, DependencyResolutionLogger<? super BC> logger,
			BundleResolutionState<BK, BC> bundleresolutionstate,
			Map<PrivateScopeDependencyRoot<? extends BK, ? extends BC>, Optional<DomainResult<BK, BC>>> privatescopedomains) {
		if (logger != null) {
			logger.enterVersion(bundleresolutionstate.getBundleKey().getBundleIdentifier(),
					bundleresolutionstate.getBundleContext());
		}
		DependencyResolutionState<BK, BC> deprs;
		bundle_resolver:
		while ((deprs = bundleresolutionstate.next(bundleslookupfunction, bundledependencieslookupfunction)) != null) {
			if (deprs.satisfiedBundleResults != null) {
				domain.pinContext.keySet().removeAll(deprs.satisfiedBundleResults);
				deprs.satisfiedBundleResults = null;
			}
			if (deprs.satisfiedDomain != null) {
				domain.directDependencies.keySet().removeAll(deprs.satisfiedDomain.keySet());
				deprs.satisfiedDomain = null;
			}
			if (ConfiguredRepositoryStorage.isAllPrivateDependencies(deprs.dependencyList)) {
				//handle private dependencies specially
				for (Entry<? extends BK, ? extends BC> bundleentry : deprs.lookedupBundles) {
					if (!isAllDependencyIncludesVersion(deprs.dependencyList,
							bundleentry.getKey().getBundleIdentifier().getVersionNumber())) {
						continue;
					}
					PrivateScopeDependencyRoot<BK, BC> privscope = new PrivateScopeDependencyRoot<>(
							bundleresolutionstate.bundleEntry, deprs.dependencyList, bundleentry);
					Optional<DomainResult<BK, BC>> presentprivscopeopt = privatescopedomains.get(privscope);
					if (presentprivscopeopt != null) {
						//the private scope was already resoved by others
						DomainResult<BK, BC> resolveddomain = presentprivscopeopt.orElse(null);
						if (resolveddomain == null) {
							//the resolution of this private scope failed.
							//try the next bundle
							continue;
						}
						//the private scope was resolved successfully by others, or is being under resolution right now.
						domain.directDependencies.put(bundleentry, resolveddomain);
						DomainResult<BK, BC> prev = domain.directDependencies.putIfAbsent(bundleentry, resolveddomain);
						if (prev == null) {
							deprs.satisfiedDomain = Collections.singletonMap(bundleentry, resolveddomain);
						}
						continue bundle_resolver;
					}
					DomainResult<BK, BC> privatedomain = DomainResult.newDomain(bundleentry);
					presentprivscopeopt = Optional.of(privatedomain);
					privatescopedomains.put(privscope, presentprivscopeopt);

					privatedomain.pinContext.put(versionlessBundleEntry(domain.bundleEntry), domain);
					BundleDependencyInformation deps = bundledependencieslookupfunction.apply(bundleentry.getKey(),
							bundleentry.getValue());
					BundleResolutionState<BK, BC> privatebundleresstate = new BundleResolutionState<>(
							withoutOptionalDependencies(deps), logger, bundleentry);
					satisfyBundleVersion(bundleslookupfunction, bundledependencieslookupfunction, privatedomain, logger,
							privatebundleresstate, privatescopedomains);
					if (!privatebundleresstate.isBackTracking()) {
						//successfully satisfied this private dependency
						DomainResult<BK, BC> prev = domain.directDependencies.putIfAbsent(bundleentry, privatedomain);
						if (prev == null) {
							deprs.satisfiedDomain = Collections.singletonMap(bundleentry, privatedomain);
						}
						continue bundle_resolver;
					}
					//replace with empty so others will notice and won't try again
					privatescopedomains.put(privscope, Optional.empty());
				}
				//failed to resolve the private dependency.
				bundleresolutionstate.startBackTrack();
				continue bundle_resolver;
			}
			DomainResult<BK, BC> presentbundledomain = domain.getPinned(deprs.versionlessBundleId);
			if (presentbundledomain != null) {
				Entry<? extends BK, ? extends BC> presentbundle = presentbundledomain.bundleEntry;
				String version = presentbundle.getKey().getBundleIdentifier().getVersionNumber();
				BundleDependencyList deplist = bundleresolutionstate.dependencyInfo
						.getDependencyList(deprs.versionlessBundleId.getKey());
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
				domain.directDependencies.put(presentbundle, presentbundledomain);
				if (logger != null) {
					logger.dependencyFoundPinned(deprs.versionlessBundleId.getKey(),
							deprs.versionlessBundleId.getValue(), presentbundle.getKey().getBundleIdentifier(),
							presentbundle.getValue());
				}
				continue bundle_resolver;
			}
			DomainResult<BK, BC> subdomain = DomainResult.subSatisfyDomain(domain);

			boolean satisfied = satisfy(deprs, bundleslookupfunction, bundledependencieslookupfunction, subdomain,
					logger, privatescopedomains);
			if (!satisfied) {
				bundleresolutionstate.startBackTrack();
			} else {
				subdomain.directDependencies.keySet().removeAll(domain.directDependencies.keySet());
				domain.directDependencies.putAll(subdomain.directDependencies);
				deprs.satisfiedBundleResults = subdomain.repinParent().keySet();
				deprs.satisfiedDomain = subdomain.directDependencies;
				bundleresolutionstate.clearBackTrack();
			}
		}
		if (logger != null) {
			logger.exitVersion(bundleresolutionstate.getBundleKey().getBundleIdentifier(),
					bundleresolutionstate.getBundleContext());
		}
	}

	private static boolean isAllDependencyIncludesVersion(BundleDependencyList deplist, String versionnumber) {
		for (BundleDependency dep : deplist.getDependencies()) {
			if (!dep.getRange().includes(versionnumber)) {
				return false;
			}
		}
		return true;
	}

	private static <BK extends BundleIdentifierHolder, BC> Entry<BundleIdentifier, BC> versionlessBundleEntry(
			Entry<? extends BK, ? extends BC> entry) {
		return ImmutableUtils.makeImmutableMapEntry(entry.getKey().getBundleIdentifier().withoutMetaQualifiers(),
				entry.getValue());
	}

	private static final class PrivateScopeDependencyRoot<BK extends BundleIdentifierHolder, BC> {
		protected final Entry<? extends BK, ? extends BC> referingBundle;
		protected final BundleDependencyList dependencyList;
		protected final Entry<? extends BK, ? extends BC> dependencyBundle;

		public PrivateScopeDependencyRoot(Entry<? extends BK, ? extends BC> referingBundle,
				BundleDependencyList dependencyList, Entry<? extends BK, ? extends BC> dependencyBundle) {
			this.referingBundle = referingBundle;
			this.dependencyList = dependencyList;
			this.dependencyBundle = dependencyBundle;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((dependencyBundle == null) ? 0 : dependencyBundle.hashCode());
			result = prime * result + ((dependencyList == null) ? 0 : dependencyList.hashCode());
			result = prime * result + ((referingBundle == null) ? 0 : referingBundle.hashCode());
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
			PrivateScopeDependencyRoot<?, ?> other = (PrivateScopeDependencyRoot<?, ?>) obj;
			if (dependencyBundle == null) {
				if (other.dependencyBundle != null)
					return false;
			} else if (!dependencyBundle.equals(other.dependencyBundle))
				return false;
			if (dependencyList == null) {
				if (other.dependencyList != null)
					return false;
			} else if (!dependencyList.equals(other.dependencyList))
				return false;
			if (referingBundle == null) {
				if (other.referingBundle != null)
					return false;
			} else if (!referingBundle.equals(other.referingBundle))
				return false;
			return true;
		}
	}

	private static <BK extends BundleIdentifierHolder, BC> Entry<Entry<? extends BK, ? extends BC>, DomainResult<BK, BC>> getDependencyEntryWithBundleId(
			Map<Entry<? extends BK, ? extends BC>, DomainResult<BK, BC>> directDependencies,
			BundleIdentifier bundleid) {
		for (Entry<Entry<? extends BK, ? extends BC>, DomainResult<BK, BC>> entry : directDependencies.entrySet()) {
			if (entry.getKey().getKey().getBundleIdentifier().withoutMetaQualifiers().equals(bundleid)) {
				return entry;
			}
		}
		return null;
	}

	private DependencyUtils() {
		throw new UnsupportedOperationException();
	}

	private static final class DomainResult<BK extends BundleIdentifierHolder, BC> {
		protected final Entry<? extends BK, ? extends BC> bundleEntry;
		protected final Map<Entry<? extends BK, ? extends BC>, DomainResult<BK, BC>> directDependencies;
		protected DomainResult<BK, BC> pinParent;
		protected Map<Entry<? extends BundleIdentifier, ? extends BC>, DomainResult<BK, BC>> pinContext;

		public static <BK extends BundleIdentifierHolder, BC> DomainResult<BK, BC> newDomain(
				Entry<? extends BK, ? extends BC> bundleEntry) {
			DomainResult<BK, BC> result = new DomainResult<>(bundleEntry);
			result.pinParent = null;
			result.pinContext = new HashMap<>();
			result.pinContext.put(versionlessBundleEntry(bundleEntry), result);
			return result;
		}

		public static <BK extends BundleIdentifierHolder, BC> DomainResult<BK, BC> subSatisfyDomain(
				DomainResult<BK, BC> copy) {
			DomainResult<BK, BC> result = new DomainResult<>(copy);
			result.pinParent = copy;
			result.pinContext = new HashMap<>();
			return result;
		}

		public static <BK extends BundleIdentifierHolder, BC> DomainResult<BK, BC> subResolveDomain(
				DomainResult<BK, BC> referencingdomain, Entry<? extends BK, ? extends BC> bundleEntry) {
			DomainResult<BK, BC> result = new DomainResult<>(bundleEntry);
			result.pinParent = referencingdomain.pinParent;
			result.pinContext = referencingdomain.pinContext;
			return result;
		}

		public DomainResult<BK, BC> getPinned(Entry<? extends BundleIdentifier, ? extends BC> versionlessBundleId) {
			DomainResult<BK, BC> got = pinContext.get(versionlessBundleId);
			if (got != null) {
				return got;
			}
			if (pinParent != null) {
				return pinParent.getPinned(versionlessBundleId);
			}
			return null;
		}

		public void unpin(Entry<? extends BundleIdentifier, ? extends BC> versionlessBundleId,
				DomainResult<BK, BC> domain) {
			boolean removed = this.pinContext.remove(versionlessBundleId, domain);
			if (!removed) {
				throw new AssertionError("Failed to remove: " + versionlessBundleId + " with " + domain);
			}
		}

		public void pin(Entry<? extends BundleIdentifier, ? extends BC> versionlessBundleId,
				DomainResult<BK, BC> domain) {
			DomainResult<BK, BC> prev = this.pinContext.put(versionlessBundleId, domain);
			if (prev != null) {
				throw new AssertionError(
						"Already present: " + versionlessBundleId + " - " + prev + " against " + domain);
			}
		}

		public Map<Entry<? extends BundleIdentifier, ? extends BC>, DomainResult<BK, BC>> repinParent() {
			if (pinParent == null) {
				return null;
			}
			Map<Entry<? extends BundleIdentifier, ? extends BC>, DomainResult<BK, BC>> thispin = this.pinContext;
			pinParent.pinContext.putAll(thispin);
			this.pinContext = pinParent.pinContext;
			this.pinParent = pinParent.pinParent;
			return thispin;
		}

		private DomainResult(Entry<? extends BK, ? extends BC> bundleEntry) {
			this.bundleEntry = bundleEntry;
			this.directDependencies = new HashMap<>();
		}

		private DomainResult(DomainResult<BK, BC> copy) {
			this.bundleEntry = copy.bundleEntry;
			this.directDependencies = new HashMap<>(copy.directDependencies);
		}

		public Set<DomainResult<BK, BC>> getTotalDomain() {
			LinkedHashSet<DomainResult<BK, BC>> result = new LinkedHashSet<>();
			collectTotalDomain(result);
			return result;
		}

		private void collectTotalDomain(Set<DomainResult<BK, BC>> result) {
			if (!result.add(this)) {
				return;
			}
			directDependencies.values().forEach(dr -> dr.collectTotalDomain(result));
		}

		@Override
		public String toString() {
			return "DomainResult[" + (bundleEntry != null ? "bundleEntry=" + bundleEntry : "") + "]";
		}

	}

	private static final class BundleResolutionState<BK extends BundleIdentifierHolder, BC> {
		//versioned bundle id
		protected final BundleDependencyInformation dependencyInfo;

		protected final ListIterator<? extends Entry<BundleIdentifier, ? extends BundleDependencyList>> depsIt;
		protected final ArrayDeque<DependencyResolutionState<BK, BC>> resolutionStateBacktrack = new ArrayDeque<>();
		protected boolean backTracking = false;
		protected final DependencyResolutionLogger<? super BC> logger;
		protected final Entry<? extends BK, ? extends BC> bundleEntry;

		public BundleResolutionState(BundleDependencyInformation dependencyInfo,
				DependencyResolutionLogger<? super BC> logger, Entry<? extends BK, ? extends BC> bundleEntry) {
			this.bundleEntry = bundleEntry;
			this.dependencyInfo = dependencyInfo;
			this.logger = logger;
			this.depsIt = ImmutableUtils.makeImmutableList(dependencyInfo.getDependencies().entrySet()).listIterator();
		}

		public DependencyResolutionState<BK, BC> next(
				BiFunction<? super BundleIdentifier, ? super BC, ? extends Iterable<? extends Entry<? extends BK, ? extends BC>>> bundleslookupfunction,
				BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction) {
			if (backTracking) {
				if (resolutionStateBacktrack.isEmpty()) {
					return null;
				}
				depsIt.previous();
				return resolutionStateBacktrack.removeLast();
			}
			while (depsIt.hasNext()) {
				Entry<BundleIdentifier, ? extends BundleDependencyList> depentry = depsIt.next();
				BundleDependencyList deplist = depentry.getValue();
				if (deplist.isEmpty()) {
					//sanity check
					continue;
				}
				BundleIdentifier withouversionbi = depentry.getKey();
				BC bundlecontext = getBundleContext();
				DependencyResolutionState<BK, BC> result = new DependencyResolutionState<>(
						ImmutableUtils.makeImmutableMapEntry(withouversionbi, bundlecontext),
						bundleslookupfunction.apply(withouversionbi, bundlecontext), deplist,
						bundledependencieslookupfunction, logger);
				resolutionStateBacktrack.add(result);
				return result;
			}
			return null;
		}

		public BC getBundleContext() {
			return bundleEntry.getValue();
		}

		public BK getBundleKey() {
			return bundleEntry.getKey();
		}

		public void startBackTrackWithoutPop() {
			backTracking = true;
		}

		public void startBackTrack() {
			if (!backTracking) {
				backTracking = true;
				if (!resolutionStateBacktrack.isEmpty()) {
					depsIt.previous();
					resolutionStateBacktrack.removeLast();
				}
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
		protected final Entry<? extends BundleIdentifier, ? extends BC> versionlessBundleId;

		//iterates over the looked up bundles
		protected ListIterator<? extends Entry<? extends BK, ? extends BC>> bundleIt;

		protected final BundleDependencyList dependencyList;

		protected Set<Entry<? extends BundleIdentifier, ? extends BC>> satisfiedBundleResults;
		protected Map<Entry<? extends BK, ? extends BC>, DomainResult<BK, BC>> satisfiedDomain;

		protected final DependencyResolutionLogger<? super BC> logger;
		protected final List<? extends Entry<? extends BK, ? extends BC>> lookedupBundles;
		protected final BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundleDependenciesLookupFunction;

		private BundleResolutionState<BK, BC> storedState;

		public DependencyResolutionState(Entry<? extends BundleIdentifier, ? extends BC> bundleId,
				Iterable<? extends Entry<? extends BK, ? extends BC>> lookedupbundles, BundleDependencyList deplist,
				BiFunction<? super BK, ? super BC, ? extends BundleDependencyInformation> bundledependencieslookupfunction,
				DependencyResolutionLogger<? super BC> logger) {
			this.versionlessBundleId = bundleId;
			this.lookedupBundles = ImmutableUtils.makeImmutableList(lookedupbundles);
			this.dependencyList = deplist;
			this.bundleDependenciesLookupFunction = bundledependencieslookupfunction;
			this.logger = logger;
		}

		public void storeState(BundleResolutionState<BK, BC> bundleresolutionstate) {
			if (TestFlag.ENABLED && this.storedState != null) {
				throw new AssertionError();
			}
			this.storedState = bundleresolutionstate;
		}

		private BundleResolutionState<BK, BC> moveToNext() {
			outer:
			while (this.bundleIt.hasNext()) {
				Entry<? extends BK, ? extends BC> n = this.bundleIt.next();
				String vnumber = n.getKey().getBundleIdentifier().getVersionNumber();
				Set<? extends BundleDependency> dependencies = dependencyList.getDependencies();
				for (BundleDependency bdep : dependencies) {
					VersionRange range = bdep.getRange();
					if (!range.includes(vnumber)) {
						if (logger != null) {
							logger.dependencyVersionRangeMismatch(bdep, n.getKey().getBundleIdentifier(), n.getValue());
						}
						continue outer;
					}
				}
				BundleDependencyInformation deps = bundleDependenciesLookupFunction.apply(n.getKey(), n.getValue());
				if (deps == null) {
					continue;
				}
				return new BundleResolutionState<>(deps, logger, n);
			}
			//not found
			return null;
		}

		public BundleResolutionState<BK, BC> nextBundle() {
			BundleResolutionState<BK, BC> storedstate = this.storedState;
			if (storedstate != null) {
				this.storedState = null;
				//flag as backtracking to continue where we left off
				storedstate.startBackTrackWithoutPop();
				return storedstate;
			}
			if (this.bundleIt == null) {
				this.bundleIt = this.lookedupBundles.listIterator();
			}
			return moveToNext();
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + versionlessBundleId + "]";
		}
	}

	@Deprecated
	private static final class ResolutionResult<BK extends BundleIdentifierHolder, BC>
			implements DependencyResolutionResult<BK, BC> {
		private Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> declarationOrderedResult;
		private Map<Entry<? extends BK, ? extends BC>, Map<BK, BC>> dependencyDomainResult;

		public ResolutionResult(BK basebundle, BC basebundlecontext, DependencyDomainResolutionResult<BK, BC> result) {
			Entry<BK, BC> basebundleentry = ImmutableUtils.makeImmutableMapEntry(basebundle, basebundlecontext);
			Map<Entry<? extends BK, ? extends BC>, Map<BK, BC>> dependencydomainresult = new LinkedHashMap<>();
			Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> declordered = new LinkedHashMap<>();

			declordered.put(versionlessBundleEntry(basebundleentry), basebundleentry);
			dependencydomainresult.put(basebundleentry, new LinkedHashMap<>());
			collectDomainResult(basebundleentry, result, dependencydomainresult, declordered, new HashSet<>());

			this.declarationOrderedResult = ImmutableUtils.unmodifiableMap(declordered);
			this.dependencyDomainResult = ImmutableUtils.unmodifiableMap(dependencydomainresult);
		}

		private static <BK extends BundleIdentifierHolder, BC> void collectDomainResult(
				Entry<? extends BK, ? extends BC> basebundleentry, DependencyDomainResolutionResult<BK, BC> result,
				Map<Entry<? extends BK, ? extends BC>, Map<BK, BC>> dependencydomainresult,
				Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> declordered,
				Set<Entry<? extends BK, ? extends BC>> processed) {
			if (!processed.add(basebundleentry)) {
				return;
			}
			Map<BK, BC> depmap = dependencydomainresult.get(basebundleentry);

			for (Entry<Entry<? extends BK, ? extends BC>, ? extends DependencyDomainResolutionResult<BK, BC>> directdepentry : result
					.getDirectDependencies().entrySet()) {
				Entry<? extends BK, ? extends BC> directdepbundleentry = directdepentry.getKey();
				depmap.put(directdepbundleentry.getKey(), directdepbundleentry.getValue());
				declordered.putIfAbsent(versionlessBundleEntry(directdepbundleentry), directdepbundleentry);
				dependencydomainresult.putIfAbsent(directdepbundleentry, new LinkedHashMap<>());
			}
			for (Entry<Entry<? extends BK, ? extends BC>, ? extends DependencyDomainResolutionResult<BK, BC>> directdepentry : result
					.getDirectDependencies().entrySet()) {
				collectDomainResult(directdepentry.getKey(), directdepentry.getValue(), dependencydomainresult,
						declordered, processed);
			}
		}

		@Override
		public Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> getResultInAnyOrder() {
			return getResultInDeclarationOrder();
		}

		@Override
		public Map<Entry<? extends BundleIdentifier, ? extends BC>, Entry<? extends BK, ? extends BC>> getResultInDeclarationOrder() {
			return declarationOrderedResult;
		}

		@Override
		public Map<Entry<? extends BK, ? extends BC>, ? extends Map<? extends BK, ? extends BC>> getDependencyDomainResult() {
			return dependencyDomainResult;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(getClass().getSimpleName());
			sb.append("[");
			for (Iterator<Entry<? extends BK, ? extends BC>> it = getResultInAnyOrder().values().iterator(); it
					.hasNext();) {
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
