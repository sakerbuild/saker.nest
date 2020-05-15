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
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class RecurringBundleIdentifierTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "85ce5aa8-f402-491c-8723-9c4b22259e8c";

	public static class SimpleMain {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, args[0]);
			CParams1 cp1 = new CParams1();
			cp1.testcp2();
			cp1.testcp3();

			CParams2 cp2 = new CParams2();
			cp2.testcp1();
			cp2.testcp3();

			CParams3 cp3 = new CParams3();
			cp3.testcp1();
			cp3.testcp2();
		}
	}

	public static class SimpleMain3 {
		public static void main(String[] args) {
			throw new AssertionError();
		}
	}

	public static class CParams1 {
		public void testcp2() {
			new CParams2();
		}

		public void testcp3() {
			new CParams3();
		}
	}

	public static class CParams2 {
		public void testcp1() {
			try {
				Class.forName("test.nest.integration.RecurringBundleIdentifierTest$CParams1", false,
						this.getClass().getClassLoader());
				throw new AssertionError("this class shouldn't be found");
			} catch (Exception e) {
			}

		}

		public void testcp3() {
			new CParams3();
		}
	}

	public static class CParams3 {
		public void testcp1() {
			try {
				Class.forName("test.nest.integration.RecurringBundleIdentifierTest$CParams1", false,
						this.getClass().getClassLoader());
				throw new AssertionError("this class shouldn't be found");
			} catch (Exception e) {
			}

		}

		public void testcp2() {
			try {
				Class.forName("test.nest.integration.RecurringBundleIdentifierTest$CParams2", false,
						this.getClass().getClassLoader());
				throw new AssertionError("this class shouldn't be found");
			} catch (Exception e) {
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses;

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);

		bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class, CParams1.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles1"), bundleoutdir.resolve("bout1"), bundleclasses);

		bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(CParams2.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles2"), bundleoutdir.resolve("bout2"), bundleclasses);

		bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain3.class, CParams3.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles3"), bundleoutdir.resolve("bout3"), bundleclasses);

		repo.executeAction("main",
				"-Unest.repository.storage.configuration=[params1:params, params2:params, params3:params]",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(Collections.singleton("simple.bundle-v1"),
						bundleoutdir.resolve("bout1"), "params1"),
				NestIntegrationTestUtils.createParameterBundlesUserParameter(Collections.singleton("dep.bundle-v1"),
						bundleoutdir.resolve("bout2"), "params2"),
				NestIntegrationTestUtils.createParameterBundlesUserParameter(Collections.singleton("simple.bundle-v1"),
						bundleoutdir.resolve("bout3"), "params3"),
				"-bundle", "simple.bundle-v1", "first-arg");

		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");
	}
}
