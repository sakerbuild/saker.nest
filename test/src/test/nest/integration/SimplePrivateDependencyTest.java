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
import saker.nest.utils.NestUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
@SuppressWarnings("unused")
public class SimplePrivateDependencyTest extends ManualLoadedRepositoryTestCase {
	public static class SimpleMain {
		public static void main(String[] args) {
			new DepClass();
			new Third1();
			try {
				//should not be found
				new Third2();
				throw new AssertionError(Third2.class.getClassLoader());
			} catch (LinkageError e) {
			}
			if (!NestUtils.getClassBundleIdentifier(Third.class).getVersionNumber().equals("1")) {
				throw new AssertionError();
			}
		}
	}

	public static class DepClass {
		public DepClass() {
			try {
				//should not be found
				new Third1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
			new Third2();
			if (!NestUtils.getClassBundleIdentifier(Third.class).getVersionNumber().equals("2")) {
				throw new AssertionError();
			}
		}
	}

	public static class Third {
		public Third() {
		}
	}

	public static class Third1 {
		public Third1() {
		}
	}

	public static class Third2 {
		public Third2() {
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(DepClass.class))//
				.put("third.bundle-v1", ObjectUtils.newHashSet(Third.class, Third1.class))//
				.put("third.bundle-v2", ObjectUtils.newHashSet(Third.class, Third2.class))//
				.build();

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1", "first-arg");
	}

}
