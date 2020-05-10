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

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import test.nest.util.ExternalArchiveResolvingNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ExternalDependencyEntryLoadMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "64211fec-1402-497b-b882-a614b41008c4";

	public static class SimpleMain {
		public static void main(String[] args) {
			try {
				System.out.println(new ExternalArchiveClass());
				throw new AssertionError();
			} catch (LinkageError e) {
				//expected
			}
			System.setProperty(PROPERTY_NAME, ExternalFirst.hello() + ExternalSecond.hello());
		}
	}

	public static class ExternalFirst {
		public static String hello() {
			System.out.println(new ExternalSecond());
			return "first";
		}
	}

	public static class ExternalSecond {
		public static String hello() {
			System.out.println(new ExternalFirst());
			return "second";
		}
	}

	public static class LibsMain {
		public static void main(String[] args) {
			ExternalArchiveClass.main(args);
		}
	}

	public static class ExternalArchiveClass {
		public static void main(String[] args) {
			System.out.println(new ExternalFirst());
			System.out.println(new ExternalSecond());
			System.setProperty(PROPERTY_NAME, "ext-" + ExternalFirst.hello() + ExternalSecond.hello());
		}
	}

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private ExternalArchiveResolvingNestMetric nm = new ExternalArchiveResolvingNestMetric();
	{
		try {
			{
				Map<SakerPath, ByteArrayRegion> entries = new TreeMap<>();
				entries.put(SakerPath.valueOf("lib/first.jar"),
						NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalFirst.class)));
				entries.put(SakerPath.valueOf("lib/second.jar"),
						NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalSecond.class)));
				//add it and test that it is NOT on the classpath
				NestIntegrationTestUtils.addClassesAsEntries(entries, ExternalArchiveClass.class);
				nm.put("https://example.com/external.jar", NestIntegrationTestUtils.createJarWithEntries(entries));
			}
			{
				Map<SakerPath, ByteArrayRegion> entries = new TreeMap<>();
				entries.put(SakerPath.valueOf("lib/first.jar"),
						NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalFirst.class)));
				entries.put(SakerPath.valueOf("lib/second.jar"),
						NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalSecond.class)));
				NestIntegrationTestUtils.addClassesAsEntries(entries, ExternalArchiveClass.class);
				nm.put("https://example.com/libs.jar", NestIntegrationTestUtils.createJarWithEntries(entries));
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
				.put("libs.bundle-v1", ObjectUtils.newHashSet(LibsMain.class))//
				.build();

		System.clearProperty(PROPERTY_NAME);

		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classSubDirPath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workingDir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "firstsecond");
		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "libs.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "ext-firstsecond");
	}
}
