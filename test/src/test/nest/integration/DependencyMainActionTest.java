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
public class DependencyMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "b2ed83b1-b7e5-4888-9253-5135d391d7a5";

	public static class SimpleMain {
		public static void main(String[] args) {
			new Dependent();
			new Third();

			System.setProperty(PROPERTY_NAME, args[0]);
		}
	}

	public static class Dependent {
		public Dependent() {
			try {
				Class.forName("test.nest.integration.DependencyMainActionTest$SimpleMain", false,
						Dependent.class.getClassLoader());
				throw new AssertionError("This class shouldn't be found from the dependent bundle.");
			} catch (ClassNotFoundException e) {
			}
			new Third();
		}
	}

	public static class Third {
		public Third() {
			try {
				Class.forName("test.nest.integration.DependencyMainActionTest$Dependent", false,
						Third.class.getClassLoader());
				throw new AssertionError("This class shouldn't be found from the dependent bundle.");
			} catch (ClassNotFoundException e) {
			}
			try {
				Class.forName("test.nest.integration.DependencyMainActionTest$SimpleMain", false,
						Third.class.getClassLoader());
				throw new AssertionError("This class shouldn't be found from the dependent bundle.");
			} catch (ClassNotFoundException e) {
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		System.clearProperty(PROPERTY_NAME);

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(Dependent.class))//
				.put("third.bundle-v1", ObjectUtils.newHashSet(Third.class))//
				.build();

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1", "first-arg");

		assertEquals(System.getProperty(PROPERTY_NAME), "first-arg");
		System.clearProperty(PROPERTY_NAME);
	}

}
