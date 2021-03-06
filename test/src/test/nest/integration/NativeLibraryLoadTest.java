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
public class NativeLibraryLoadTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid, same as in dll source
	private static final String PROPERTY_NAME = "ece381df-4e1c-4175-9ed5-e0fc3ce66adc";

	public static class SimpleMain {
		public static void main(String[] args) {
			//XXX this test should be invoked in a separate VM, as libraries may be left loaded from previous tests
			System.gc();
			System.loadLibrary("JNIPropertySetLibrary");
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		if (!System.mapLibraryName("THE_LIB").equals("THE_LIB.dll")) {
			//do not run the test on non Windows machines, as the library loading would fail
			//TODO instead of skipping the test, test it for *nix and macOS
			return;
		}
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.build();

		System.clearProperty(PROPERTY_NAME);

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "lib-loaded-" + System.getProperty("os.arch"));

		//try once more to check that a single library can be loaded multiple times in different classloaders
		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "lib-loaded-" + System.getProperty("os.arch"));

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.architecture=null",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "lib-loaded");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.architecture=null",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "lib-loaded");
	}
}
