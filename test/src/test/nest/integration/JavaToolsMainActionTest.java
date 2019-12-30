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
public class JavaToolsMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "a2bda6ce-d969-444b-8f9b-317e4096d9ee";

	public static class SimpleMain {
		public static void main(String[] args) throws ClassNotFoundException {
			Class.forName("com.sun.source.tree.Tree", false, SimpleMain.class.getClassLoader());
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

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1", "first-arg");

		assertEquals(System.getProperty(PROPERTY_NAME), "first-arg");
		System.clearProperty(PROPERTY_NAME);
	}

}
