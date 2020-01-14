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
 * Testing the scenario when the same bundle is loaded multiple times with different classpath.
 * 
 * <pre>
 *     private/-->X1
 *           /    V
 *      /-->B1--->D2
 *     /       private
 * A1-/
 *    \        private
 *     \-->C1---->X1
 *           \    V
 *     private\-->D1
 * </pre>
 */
@SakerTest
@SuppressWarnings("unused")

public class DifferentDomainPrivateDependencyTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "4880e9fe-a5db-4e71-8dc5-2bff54f00877";

	public static class AMain {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, args[0]);

			new A1();

			try {
				new X1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}

			B1 b1 = new B1();
			C1 c1 = new C1();
			Object b1x = b1.getx();
			Object c1x = c1.getx();
			if (b1x.getClass() == c1x.getClass()) {
				throw new AssertionError("X have same class");
			}
			b1.test();
			c1.test();
		}
	}

	public static class A1 {
		public A1() {
		}
	}

	public static class B1 {
		public B1() {
		}

		public Object getx() {
			return new X1();
		}

		public void test() {
			new D2();
			new X1().test(2);
		}
	}

	public static class C1 {
		public C1() {
		}

		public Object getx() {
			return new X1();
		}

		public void test() {
			new D1();
			new X1().test(1);
		}
	}

	public static class X1 {
		public X1() {
			// only the construction of one of them may succeed
		}

		public void test(int ver) {
			ClassLoader cl = this.getClass().getClassLoader();
			System.out.println("Classloader: " + System.identityHashCode(cl) + " -> " + cl);
			switch (ver) {
				case 1: {
					new D1();
					try {
						new D2();
						throw new AssertionError();
					} catch (LinkageError e) {
						//good
					}
					break;
				}
				case 2: {
					new D2();
					try {
						new D1();
						throw new AssertionError();
					} catch (LinkageError e) {
						//good
					}
					break;
				}
				default: {
					throw new AssertionError(ver);
				}
			}
		}
	}

	public static class D1 {
		public D1() {
			try {
				new D2();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
			try {
				new X1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
		}
	}

	public static class D2 {
		public D2() {
			try {
				new D1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
			try {
				new X1();
				throw new AssertionError();
			} catch (LinkageError e) {
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("a-v1", ObjectUtils.newHashSet(AMain.class, A1.class))//
				.put("b-v1", ObjectUtils.newHashSet(B1.class))//
				.put("c-v1", ObjectUtils.newHashSet(C1.class))//
				.put("d-v1", ObjectUtils.newHashSet(D1.class))//
				.put("d-v2", ObjectUtils.newHashSet(D2.class))//
				.put("x-v1", ObjectUtils.newHashSet(X1.class))//
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
