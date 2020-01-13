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
package test.nest.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.NestBundleClassLoader;
import saker.nest.bundle.NestBundleStorageConfiguration;
import saker.nest.bundle.lookup.BundleLookup;
import saker.nest.bundle.lookup.BundleVersionLookupResult;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class KnownBundlesDependencyResolutionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "3107ae69-d3c0-4e2b-9a84-c061d279666e";

	public static class SimpleMain {
		private static final Pattern PATTERN_JDK = Pattern.compile("jdk[0-9]+");

		public static void main(String[] args) throws Throwable {
			System.setProperty(PROPERTY_NAME, args[0]);

			NestBundleClassLoader thiscl = (NestBundleClassLoader) SimpleMain.class.getClassLoader();
			NestBundleStorageConfiguration storageconfig = thiscl.getBundleStorageConfiguration();

			BundleLookup lookup = storageconfig.getBundleLookup();

			//taken from the repository index
			//the version qualifiers will be stripped by the method
			Set<BundleIdentifier> packnames = bundleNamesWithoutVersion("nest.repository.support-v0.8.0",
					"nest.repository.support-api-v0.8.0", "nest.repository.support-impl-v0.8.0",
					"nest.repository.support-sources-v0.8.0", "saker.apiextract-api-v0.8.0",
					"saker.apiextract-processor-v0.8.0", "saker.apiextract-sources-v0.8.0", "saker.build-v0.8.0",
					"saker.build-api-v0.8.0", "saker.build-ide-v0.8.0", "saker.build-runner-test-v0.8.0",
					"saker.build-sources-v0.8.0", "saker.build-test-v0.8.0", "saker.build-test-utils-v0.8.0",
					"saker.compiler.utils-v0.8.0", "saker.compiler.utils-api-v0.8.0",
					"saker.compiler.utils-impl-v0.8.0", "saker.compiler.utils-sources-v0.8.0", "saker.jar-v0.8.0",
					"saker.jar-sources-v0.8.0", "saker.java.compiler-v0.8.0", "saker.java.compiler-api-v0.8.0",
					"saker.java.compiler-impl-v0.8.0", "saker.java.compiler-impl-jdk12-v0.8.0",
					"saker.java.compiler-impl-jdk12-sources-v0.8.0", "saker.java.compiler-impl-jdk12-util-v0.8.0",
					"saker.java.compiler-impl-jdk13-v0.8.0", "saker.java.compiler-impl-jdk13-sources-v0.8.0",
					"saker.java.compiler-impl-jdk13-util-v0.8.0", "saker.java.compiler-impl-jdk8-v0.8.0",
					"saker.java.compiler-impl-jdk8-sources-v0.8.0", "saker.java.compiler-impl-jdk8-util-v0.8.0",
					"saker.java.compiler-impl-jdk9-v0.8.0", "saker.java.compiler-impl-jdk9-sources-v0.8.0",
					"saker.java.compiler-impl-jdk9-util-v0.8.0", "saker.java.compiler-sources-v0.8.0",
					"saker.java.testing-v0.8.0", "saker.java.testing-agent-jdk8-v0.8.0",
					"saker.java.testing-agent-jdk9-v0.8.0", "saker.java.testing-agent_sources-jdk8-v0.8.0",
					"saker.java.testing-agent_sources-jdk9-v0.8.0", "saker.java.testing-api-v0.8.0",
					"saker.java.testing-bootstrapagent-jdk8-v0.8.0", "saker.java.testing-bootstrapagent-jdk9-v0.8.0",
					"saker.java.testing-impl-v0.8.0", "saker.java.testing-sources-v0.8.0",
					"saker.maven.classpath-v0.8.0", "saker.maven.classpath-sources-v0.8.0",
					"saker.maven.support-v0.8.0", "saker.maven.support-v0.8.1", "saker.maven.support-v0.8.2",
					"saker.maven.support-api-v0.8.0", "saker.maven.support-api-v0.8.1",
					"saker.maven.support-api-v0.8.2", "saker.maven.support-impl-v0.8.0",
					"saker.maven.support-impl-v0.8.1", "saker.maven.support-impl-v0.8.2",
					"saker.maven.support-lib-v0.8.0", "saker.maven.support-lib-v0.8.2",
					"saker.maven.support-sources-v0.8.0", "saker.maven.support-sources-v0.8.1",
					"saker.maven.support-sources-v0.8.2", "saker.msvc-v0.8.0", "saker.msvc-api-v0.8.0",
					"saker.msvc-impl-v0.8.0", "saker.msvc-proc-v0.8.0", "saker.msvc-sources-v0.8.0",
					"saker.nest-v0.8.0", "saker.nest-api-v0.8.0", "saker.nest-saker.build-test-v0.8.0",
					"saker.nest-sources-v0.8.0", "saker.nest-test-v0.8.0", "saker.nest-test-utils-v0.8.0",
					"saker.rmi-v0.8.0", "saker.rmi-api-v0.7.9", "saker.rmi-api-v0.8.0", "saker.rmi-sources-v0.7.9",
					"saker.rmi-sources-v0.8.0", "saker.sdk.support-v0.8.0", "saker.sdk.support-api-v0.8.0",
					"saker.sdk.support-impl-v0.8.0", "saker.sdk.support-sources-v0.8.0", "saker.standard-v0.8.0",
					"saker.standard-api-v0.8.0", "saker.standard-impl-v0.8.0", "saker.standard-sources-v0.8.0",
					"saker.util-v0.8.0", "saker.util-sources-v0.8.0", "saker.zip-v0.8.0", "saker.zip-api-v0.8.0",
					"saker.zip-impl-v0.8.0", "saker.zip-sources-v0.8.0", "sipka.cmdline-api-v0.8.0",
					"sipka.cmdline-processor-v0.8.0", "sipka.cmdline-runtime-v0.8.0", "sipka.cmdline-sources-v0.8.0",
					"sipka.syntax.parser-v0.8.0", "sipka.syntax.parser-sources-v0.8.0");

			Map<BundleIdentifier, Set<NestBundleClassLoader>> bundleclassloaders = new TreeMap<>();

			try {
				outer:
				for (BundleIdentifier bundlename : packnames) {
					//filter out JDK specific bundles, don't test them
					for (String q : bundlename.getBundleQualifiers()) {
						if (PATTERN_JDK.matcher(q).matches()) {
							continue outer;
						}
					}
					BundleVersionLookupResult versions = lookup.lookupBundleVersions(bundlename);
					if (versions == null) {
						throw new AssertionError("No version found of: " + bundlename);
					}
					for (BundleIdentifier bid : versions.getBundles()) {
						System.out.println("Test " + bid);
						ClassLoader loadedcl = storageconfig.getBundleClassLoader(lookup, bid);
						NestBundleClassLoader bcl = (NestBundleClassLoader) loadedcl;

						bundleclassloaders.computeIfAbsent(bid, Functionals.linkedHashSetComputer()).add(bcl);

						//try loading the classes
						for (String entry : bcl.getBundle().getEntryNames()) {
							if (!entry.endsWith(".class") || entry.startsWith("META-INF/")) {
								continue;
							}
							//ignore some bundles that are special
							if ((bid.getName().equals("saker.build") && entry.startsWith("internal/"))
									|| (bid.getName().equals("saker.nest") && entry.startsWith("internal/"))
									|| bundlename.equals(BundleIdentifier.valueOf("saker.nest-test"))
									|| bundlename.equals(BundleIdentifier.valueOf("saker.nest-test-utils"))
									|| bundlename.equals(BundleIdentifier.valueOf("saker.maven.support-lib"))
									|| bundlename.equals(BundleIdentifier.valueOf("saker.build-test-runner"))
									|| bundlename.equals(BundleIdentifier.valueOf("saker.build-test-utils"))) {
								continue;
							}
							Class<?> c = Class.forName(entry.replace('/', '.').substring(0, entry.length() - 6), true,
									loadedcl);
							//examine the reflection elements as they may trigger other loading things
							exhaustMethods(c.getMethods());
							exhaustMethods(c.getDeclaredMethods());
							exhaustFields(c.getFields());
							exhaustFields(c.getDeclaredFields());
							c.getEnumConstants();
							c.getAnnotations();
						}
					}
				}
			} finally {
				System.out.println("Classloader dump:");
				for (Entry<BundleIdentifier, Set<NestBundleClassLoader>> entry : bundleclassloaders.entrySet()) {
					System.out.println(entry.getKey());
					for (NestBundleClassLoader cl : entry.getValue()) {
						System.out.println("    " + cl);
					}
				}
			}

		}

		private static void exhaustFields(Field[] fields) {
			for (Field f : fields) {
				f.getType();
				f.getGenericType();
				f.getAnnotations();
			}
		}

		private static void exhaustMethods(Method[] methods) {
			for (Method m : methods) {
				m.getReturnType();
				m.getParameterTypes();
				m.getParameters();
				m.getParameterAnnotations();
				m.getAnnotations();
				m.getExceptionTypes();
				m.getDefaultValue();
			}
		}

		private static Set<BundleIdentifier> bundleNamesWithoutVersion(String... ids) {
			Set<BundleIdentifier> result = new TreeSet<>();
			for (String id : ids) {
				BundleIdentifier bundleid = BundleIdentifier.valueOf(id);
				result.add(bundleid.withoutMetaQualifiers());
			}
			return result;
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.build();

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		//non-offline
		repo.executeAction("main",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1", "first-arg");

		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");
	}

}
