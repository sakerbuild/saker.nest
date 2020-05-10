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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.nest.bundle.ExternalArchive;
import saker.nest.bundle.ExternalArchiveKey;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.NestBundleClassLoader;
import saker.nest.bundle.NestBundleStorageConfiguration;
import test.nest.util.ExternalArchiveResolvingNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ExternalDependencyAttachmentLoadMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "3f47c670-d379-4e95-b8bb-46ffc64f60b9";

	public static class SimpleMain {
		public static void main(String[] args) throws Throwable {
			URI externaluri = URI.create("https://example.com/external.jar");
			URI externallibsuri = URI.create("https://example.com/external_libs.jar");
			URI externalsourcesuri = URI.create("https://example.com/external_sources.jar");
			URI externalsourcesfirsturi = URI.create("https://example.com/external_sources_first.jar");
			URI externalsourcesseconduri = URI.create("https://example.com/external_sources_second.jar");

			{
				String deps = "https://example.com/external.jar\n" //
						+ "    classpath\n"//
						+ "    source-attachment: https://example.com/external_sources.jar\n";
				assertExpectedArchives(deps, ExternalArchiveKey.create(externaluri),
						ExternalArchiveKey.create(externalsourcesuri));
			}
			{
				String deps = "https://example.com/external_libs.jar\n" //
						+ "    classpath\n"//
						+ "        entries: lib/*.jar\n"//
						+ "    source-attachment: https://example.com/external_sources_first.jar\n"//
						+ "        target: lib/first.jar\n"//
						+ "    source-attachment: https://example.com/external_sources_second.jar\n"//
						+ "        target: lib/second.jar\n"//
				;
				assertExpectedArchives(deps, ExternalArchiveKey.create(externallibsuri, "lib/first.jar"),
						ExternalArchiveKey.create(externalsourcesfirsturi));
			}
			{
				String deps = "https://example.com/external_libs.jar\n" //
						+ "    classpath\n"//
						+ "        entries: lib/*.jar\n"//
						+ "    source-attachment: https://example.com/external_sources_first.jar\n"//
						+ "        target: lib/*.jar\n"//
						+ "    source-attachment: https://example.com/external_sources_second.jar\n"//
						+ "        target: lib/*.jar\n"//
				;
				assertExpectedArchives(deps, ExternalArchiveKey.create(externallibsuri, "lib/first.jar"),
						ExternalArchiveKey.create(externalsourcesfirsturi),
						ExternalArchiveKey.create(externalsourcesseconduri));
			}
			{
				String deps = "https://example.com/external_libs.jar\n" //
						+ "    classpath\n"//
						+ "        entries: lib/*.jar\n"//
						+ "    source-attachment: https://example.com/external_sources_first.jar\n"//
				;
				assertExpectedArchives(deps, ExternalArchiveKey.create(externallibsuri, "lib/first.jar"));
			}
			{
				String deps = "https://example.com/external_libs.jar\n" //
						+ "    classpath\n"//
						+ "        entries: /;lib/*.jar\n"//
						+ "    source-attachment: https://example.com/external_sources_first.jar\n"//
				;
				assertExpectedArchives(deps, ExternalArchiveKey.create(externallibsuri),
						ExternalArchiveKey.create(externallibsuri, "lib/first.jar"),
						ExternalArchiveKey.create(externalsourcesfirsturi));
			}
			{
				String deps = "https://example.com/external_libs.jar\n" //
						+ "    classpath\n"//
						+ "    source-attachment: https://example.com/external_sources_first.jar\n"//
						+ "        entries: /\n"//
				;
				assertExpectedArchives(deps, ExternalArchiveKey.create(externallibsuri),
						ExternalArchiveKey.create(externalsourcesfirsturi));
			}
			{
				String deps = "https://example.com/external_libs.jar\n" //
						+ "    classpath\n"//
						+ "    source-attachment: https://example.com/external_sources_first.jar\n"//
						+ "        target: lib/*.jar\n"//
				;
				assertExpectedArchives(deps, ExternalArchiveKey.create(externallibsuri));
			}
			System.setProperty(PROPERTY_NAME, args[0]);
		}

		private static void assertExpectedArchives(String depsrc, ExternalArchiveKey... archivekeys) throws Exception {
			System.out.println(depsrc);

			NestBundleClassLoader cl = (NestBundleClassLoader) SimpleMain.class.getClassLoader();
			NestBundleStorageConfiguration storageconfig = cl.getBundleStorageConfiguration();

			ExternalDependencyInformation depinfo = ExternalDependencyInformation
					.readFrom(new UnsyncByteArrayInputStream(depsrc.getBytes(StandardCharsets.UTF_8)));
			System.out.println(depinfo);

			Map<? extends ExternalArchiveKey, ? extends ExternalArchive> loaded = storageconfig
					.loadExternalArchives(depinfo);
			if (!loaded.keySet().equals(new HashSet<>(Arrays.asList(archivekeys)))) {
				throw new AssertionError(loaded.keySet());
			}
		}
	}

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private ExternalArchiveResolvingNestMetric nm = new ExternalArchiveResolvingNestMetric();
	{
		try {
			nm.put("https://example.com/external.jar", NestIntegrationTestUtils.createJarBytesWithClasses(setOf()));
			nm.put("https://example.com/external_sources.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf()));
			nm.put("https://example.com/external_sources_first.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf()));
			nm.put("https://example.com/external_sources_second.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf()));

			{
				Map<SakerPath, ByteArrayRegion> entries = new TreeMap<>();
				entries.put(SakerPath.valueOf("lib/first.jar"),
						NestIntegrationTestUtils.createJarBytesWithClasses(setOf()));
				nm.put("https://example.com/external_libs.jar", NestIntegrationTestUtils.createJarWithEntries(entries));
			}
		} catch (Exception e) {
			fail(e);
		}
	}

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		TestFlag.set(nm);
		LocalFileProvider.getInstance().clearDirectoryRecursively(getStorageDirectory());
		super.runTest(parameters);
	}

	@Override
	protected Path getStorageDirectory() {
		return super.getStorageDirectory().resolve(this.getClass().getName());
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.build();

		System.clearProperty(PROPERTY_NAME);

		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classSubDirPath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workingDir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1", "first-arg");
		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");
	}

}
