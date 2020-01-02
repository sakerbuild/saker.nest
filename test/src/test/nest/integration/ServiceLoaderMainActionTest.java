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
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ServiceLoaderMainActionTest extends ManualLoadedRepositoryTestCase {
	public static class DependentRunnable implements Callable<String> {
		@Override
		public String call() throws Exception {
			return "DependentRunnable";
		}
	}

	public static class SimpleService {
		public String f() {
			return "SimpleService";
		}
	}

	public static class SimpleMain {
		public static void main(String[] args) throws Exception {
			SimpleService service = ServiceLoader.load(SimpleService.class, SimpleMain.class.getClassLoader())
					.iterator().next();
			String fres = service.f();
			if (!"SimpleService".equals(fres)) {
				throw new AssertionError(fres);
			}

			String depclres = ServiceLoader.load(Callable.class, DependentRunnable.class.getClassLoader()).iterator()
					.next().call().toString();
			if (!"DependentRunnable".equals(depclres)) {
				throw new AssertionError(depclres);
			}
			if (ServiceLoader.load(Callable.class, SimpleMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError("Found service in main bundle.");
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class, SimpleService.class))//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(DependentRunnable.class))//
				.build();

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");

	}

}
