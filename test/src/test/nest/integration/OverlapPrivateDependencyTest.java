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

/**
 * <pre>
 * A1-->B1  /--B2
 *      |  /   /\
 *      V \/    |
 *      C1----P>D1
 * </pre>
 */
@SakerTest
@SuppressWarnings("unused")
public class OverlapPrivateDependencyTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "2bb8abf0-4d1d-4543-9243-48611aecfed8";

	public static class AMain {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, args[0]);

			new B1();
			new C1();

			try {
				new D1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
			try {
				new B2();
				throw new AssertionError();
			} catch (LinkageError e) {
			}

			Object c1viad1 = new C1().getC1ViaD1();
			if (c1viad1.getClass() != C1.class) {
				throw new AssertionError("different class");
			}
		}
	}

	public static class A1 {
		public A1() {
			new B1();
		}
	}

	public static class B1 {
		public B1() {
			try {
				new A1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
			new C1();
			try {
				new D1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
			try {
				new B2();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
		}
	}

	public static class B2 {
		public B2() {
			//just to test availability, dont construct as that causes overflow
			System.out.println("OverlapPrivateDependencyTest.B2.B2() " + C1.class);
			try {
				new D1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
			try {
				new B1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
		}
	}

	public static class C1 {
		public C1() {
			new D1();
			new B2();
			try {
				new B1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
		}

		public Object getC1ViaD1() {
			return new D1().getC1();
		}
	}

	public static class D1 {
		public D1() {
			new B2();
		}

		public Object getC1() {
			return new C1();
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("a-v1", ObjectUtils.newHashSet(AMain.class, A1.class))//
				.put("b-v1", ObjectUtils.newHashSet(B1.class))//
				.put("b-v2", ObjectUtils.newHashSet(B2.class))//
				.put("c-v1", ObjectUtils.newHashSet(C1.class))//
				.put("d-v1", ObjectUtils.newHashSet(D1.class))//
				.build();

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "a-v1", "first-arg");

		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");
	}

}
