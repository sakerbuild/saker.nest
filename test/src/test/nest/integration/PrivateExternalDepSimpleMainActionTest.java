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
import test.nest.util.ExternalArchiveResolvingNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class PrivateExternalDepSimpleMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "00ec7209-9a67-4879-a269-78d150fdaeae";

	public static class SimpleMain {
		public static void main(String[] args) {
			try {
				System.out.println(new ExternalClass());
				throw new AssertionError();
			} catch (LinkageError e) {
				//expected
			}
			DepClass.main(args);
		}
	}

	public static class ExternalClass {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, args[0]);
		}
	}

	public static class DepClass {
		public static void main(String[] args) {
			ExternalClass.main(args);
		}
	}

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private ExternalArchiveResolvingNestMetric nm = new ExternalArchiveResolvingNestMetric();
	{
		try {
			nm.put("https://example.com/external.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalClass.class)));
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
				.put("dep.bundle-v1", ObjectUtils.newHashSet(DepClass.class))//
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
