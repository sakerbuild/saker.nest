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
				.put("third.bundle-v1", ObjectUtils.newHashSet(Third1.class))//
				.put("third.bundle-v2", ObjectUtils.newHashSet(Third2.class))//
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
