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
import java.util.Collections;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.util.java.JavaTools;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class JavaToolsMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "a2bda6ce-d969-444b-8f9b-317e4096d9ee";

	public static class SimpleMain {
		public static void main(String[] args) throws Exception {
			//test saker.build classpath as well
			System.out.println(SakerPath.valueOf("/home"));
			Class.forName("com.sun.source.tree.Tree", false, SimpleMain.class.getClassLoader());
			if (JavaTools.getCurrentJavaMajorVersion() >= 9) {
				try {
					//this class is not exported, this shouldn't work. an exception is expected
					Class.forName("com.sun.tools.javac.platform.PlatformUtils", false,
							SimpleMain.class.getClassLoader()).getMethod("lookupPlatformDescription", String.class)
							.invoke(null, "8");
					throw new AssertionError();
				} catch (IllegalAccessException e) {
					//expected
				} catch (Exception e) {
					throw e;
				}
			}
			System.setProperty(PROPERTY_NAME, args[0]);
		}
	}

	public static class OpensMain {
		public static void main(String[] args) throws Exception {
			//test saker.build classpath as well
			System.out.println(SakerPath.valueOf("/home"));
			Class.forName("com.sun.source.tree.Tree", false, OpensMain.class.getClassLoader());
			if (JavaTools.getCurrentJavaMajorVersion() >= 9) {
				//this should work as the jdk.compiler is exported
				Object platformdescriptionobject = Class
						.forName("com.sun.tools.javac.platform.PlatformUtils", false, OpensMain.class.getClassLoader())
						.getMethod("lookupPlatformDescription", String.class).invoke(null, "8");
				System.out.println(platformdescriptionobject);
			}
			System.setProperty(PROPERTY_NAME, args[0]);
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", Collections.singleton(SimpleMain.class))//
				.put("opens.bundle-v1", Collections.singleton(OpensMain.class))//
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

		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "opens.bundle-v1", "first-arg");
		assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");
	}

}
