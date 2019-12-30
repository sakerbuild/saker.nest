package test.nest.integration;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.bundle.NestBundleClassLoader;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class JreVersionDependencyMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "09219767-04f1-4f8c-907e-1cff317a5e10";

	public static class SimpleMain {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, new Dependent().bundleName());
		}
	}

	public static class Dependent {
		public String bundleName() {
			return ((NestBundleClassLoader) Dependent.class.getClassLoader()).getBundle().getBundleIdentifier()
					.toString();
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.put("dep.bundle-jre8-v1", ObjectUtils.newHashSet(Dependent.class))//
				.put("dep.bundle-jre910-v1", ObjectUtils.newHashSet(Dependent.class))//
				.put("dep.bundle-jre11over-v1", ObjectUtils.newHashSet(Dependent.class))//
				.put("dep.bundle-jre11over-v2", ObjectUtils.newHashSet(Dependent.class))//
				.build();

		System.clearProperty(PROPERTY_NAME);

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=8",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");

		assertEquals(System.clearProperty(PROPERTY_NAME), "dep.bundle-jre8-v1");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=9",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "dep.bundle-jre910-v1");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=10",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "dep.bundle-jre910-v1");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=11",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "dep.bundle-jre11over-v1");

		repo.executeAction("main", "-Unest.server.offline=true", "-Unest.repository.constraint.force.jre.major=12",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "simple.bundle-v1");
		assertEquals(System.clearProperty(PROPERTY_NAME), "dep.bundle-jre11over-v2");
	}

}
