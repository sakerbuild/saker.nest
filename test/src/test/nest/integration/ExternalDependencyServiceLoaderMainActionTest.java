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
import java.util.ServiceLoader;
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
public class ExternalDependencyServiceLoaderMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "424619bc-beca-46ec-98ba-a8273c5152fe";

	public static class SimpleMain {
		public static void main(String[] args) {
			FirstMain.main(args);
			SecondMain.main(args);

			if (!ServiceLoader.load(ServiceFirst.class, FirstMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}
			if (!ServiceLoader.load(ServiceSecond.class, SecondMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}

			if (ServiceLoader.load(ServiceFirst.class, SimpleMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}
			if (ServiceLoader.load(ServiceSecond.class, SimpleMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}

			System.setProperty(PROPERTY_NAME, args[0]);
		}
	}

	public static class FirstMain {
		public static void main(String[] args) {
			if (!ServiceLoader.load(ServiceFirst.class, FirstMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}
			if (!ServiceLoader.load(ServiceSecond.class, SecondMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}
		}
	}

	public static class SecondMain {
		public static void main(String[] args) {
			if (!ServiceLoader.load(ServiceFirst.class, FirstMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}
			if (!ServiceLoader.load(ServiceSecond.class, SecondMain.class.getClassLoader()).iterator().hasNext()) {
				throw new AssertionError();
			}
		}
	}

	public interface ServiceFirst {
	}

	public interface ServiceSecond {
	}

	public static class FirstImpl implements ServiceFirst {
	}

	public static class SecondImpl implements ServiceSecond {
	}

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private ExternalArchiveResolvingNestMetric nm = new ExternalArchiveResolvingNestMetric();
	{
		try {
			{
				Map<SakerPath, ByteArrayRegion> entries = new TreeMap<>();
				NestIntegrationTestUtils.addClassesAsEntries(entries, ServiceFirst.class, FirstImpl.class,
						FirstMain.class);
				NestIntegrationTestUtils.addServices(entries, ServiceFirst.class, FirstImpl.class);
				nm.put("https://example.com/external_first.jar",
						NestIntegrationTestUtils.createJarWithEntries(entries));
			}
			{
				Map<SakerPath, ByteArrayRegion> entries = new TreeMap<>();
				NestIntegrationTestUtils.addClassesAsEntries(entries, ServiceSecond.class, SecondImpl.class,
						SecondMain.class);
				NestIntegrationTestUtils.addServices(entries, ServiceSecond.class, SecondImpl.class);
				nm.put("https://example.com/external_second.jar",
						NestIntegrationTestUtils.createJarWithEntries(entries));
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
