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
public class ExternalDependencyConstraintMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "9f06784e-9cd5-42fa-a2f0-f3b3c7c7833d";

	public static class SimpleMain {
		public static void main(String[] args) {
			try {
				ExternalClass.main(args);
			} catch (LinkageError e) {
				System.setProperty(PROPERTY_NAME, "none");
			}
		}
	}

	public static class ExternalClass {
		public static void main(String[] args) {
			try {
				System.out.println(new V8Class());
				System.setProperty(PROPERTY_NAME, "v8");
				return;
			} catch (LinkageError e) {
			}
			try {
				System.out.println(new V9Class());
				System.setProperty(PROPERTY_NAME, "v9");
				return;
			} catch (LinkageError e) {
			}
			try {
				System.out.println(new X86Class());
				System.setProperty(PROPERTY_NAME, "x86");
				return;
			} catch (LinkageError e) {
			}
			try {
				System.out.println(new AMD64Class());
				System.setProperty(PROPERTY_NAME, "amd64");
				return;
			} catch (LinkageError e) {
			}
			throw new AssertionError("None found.");
		}
	}

	public static class V8Class {
	}

	public static class V9Class {
	}

	public static class X86Class {
	}

	public static class AMD64Class {
	}

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private ExternalArchiveResolvingNestMetric nm = new ExternalArchiveResolvingNestMetric();
	{
		try {
			nm.put("https://example.com/external_v8.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalClass.class, V8Class.class)));
			nm.put("https://example.com/external_v9.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalClass.class, V9Class.class)));
			nm.put("https://example.com/external_x86.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalClass.class, X86Class.class)));
			nm.put("https://example.com/external_amd64.jar",
					NestIntegrationTestUtils.createJarBytesWithClasses(setOf(ExternalClass.class, AMD64Class.class)));
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

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=8",
				"-Unest.repository.constraint.force.architecture=NO_ARCH",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "v8");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=9",
				"-Unest.repository.constraint.force.architecture=NO_ARCH",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "v9");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=10",
				"-Unest.repository.constraint.force.architecture=NO_ARCH",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "none");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=10",
				"-Unest.repository.constraint.force.architecture=x86",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "x86");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=10",
				"-Unest.repository.constraint.force.architecture=amd64",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "amd64");
	}

}
