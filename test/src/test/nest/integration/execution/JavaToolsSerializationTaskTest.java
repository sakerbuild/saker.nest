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
package test.nest.integration.execution;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class JavaToolsSerializationTaskTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "76fe4bbc-f0c8-43dc-b1e2-15b99fe1b189";

	public static class SimpleTask implements TaskFactory<Object>, ParameterizableTask<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		public SimpleTask() {
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			System.setProperty(PROPERTY_NAME, "hello");
			return Enum.valueOf(
					(Class) Class.forName("com.sun.source.tree.Tree$Kind", false, SimpleTask.class.getClassLoader()),
					"CLASS");
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		System.clearProperty(PROPERTY_NAME);

		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.put("opens.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.build();

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.server.offline", "true");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		runScriptTask("simple");
		assertEquals(System.clearProperty(PROPERTY_NAME), "hello");

		runScriptTask("simple");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);

		runScriptTask("opens");
		assertEquals(System.clearProperty(PROPERTY_NAME), "hello");

		runScriptTask("opens");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
	}

}
