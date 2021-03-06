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
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

/**
 * <pre>
 * A1-->B1<--\  
 *      |     \ 
 *      V      \
 *      C1----P>D1
 * </pre>
 * 
 * While B1 is in the private domain of C1 via D1, it still can use the classloader of B1, as it resolves to have the same
 * domain through A1 and through D1.
 * <p>
 * If A1 decides to modify the version range for B, then D1 would have a private dependency of B1 on its own. This is
 * tested in the other Overlap tests.
 */
@SakerTest
@SuppressWarnings("unused")
public class Overlap2PrivateDependencyTest extends ManualLoadedRepositoryTestCase {
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
			System.out.println("Overlap2PrivateDependencyTest.AMain.main() B1 " + System.identityHashCode(B1.class));

			if (new C1().getC1ViaD1().getClass() != C1.class) {
				throw new AssertionError("different class C1");
			}
			if (new C1().getB1ViaD1().getClass() != B1.class) {
				throw new AssertionError("not same class B1");
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
		}
	}

	public static class C1 {
		public C1() {
			new D1();
			System.out.println("Overlap2PrivateDependencyTest.C1.C1() B1 " + System.identityHashCode(B1.class));
		}

		public Object getC1ViaD1() {
			return new D1().getC1();
		}

		public Object getB1ViaD1() {
			return new D1().getB1();
		}
	}

	public static class D1 {
		public D1() {
			System.out.println("Overlap2PrivateDependencyTest.D1.D1() B1 " + System.identityHashCode(B1.class));
		}

		public Object getC1() {
			return new C1();
		}

		public Object getB1() {
			return new B1();
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("a-v1", ObjectUtils.newHashSet(AMain.class, A1.class))//
				.put("b-v1", ObjectUtils.newHashSet(B1.class))//
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
