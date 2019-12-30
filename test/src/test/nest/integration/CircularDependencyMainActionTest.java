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
public class CircularDependencyMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "b2ed83b1-b7e5-4888-9253-5135d391d7a5";

	public static class SimpleMain {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, args[0]);

			new Dependent();
			new Third();

			try {
				Class.forName("not.found.class", false, SimpleMain.class.getClassLoader());
				throw new AssertionError("Class was found.");
			} catch (ClassNotFoundException e) {
			}
		}
	}

	public static class Dependent extends SimpleMain {
	}

	public static class Third extends Dependent {
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(Dependent.class))//
				.put("third.bundle-v1", ObjectUtils.newHashSet(Third.class))//
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

		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");
	}

}
