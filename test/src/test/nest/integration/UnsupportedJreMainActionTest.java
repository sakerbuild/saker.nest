package test.nest.integration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class UnsupportedJreMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "c6e779ce-899e-4352-a6bf-a09fd16bc225";

	public static class SimpleMain {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, args[0]);
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", Collections.singleton(SimpleMain.class))//
				.build();

		System.clearProperty(PROPERTY_NAME);

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=8",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1", "first-arg");
		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=9",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1", "first-arg");
		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");

		try {
			repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=10",
					NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
					"-bundle", "simple.bundle-v1", "first-arg");
			fail("shouldve thrown an exception");
		} catch (Exception e) {
		}
	}

}
