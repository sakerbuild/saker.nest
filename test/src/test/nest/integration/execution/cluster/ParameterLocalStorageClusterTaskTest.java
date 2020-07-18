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
package test.nest.integration.execution.cluster;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.TaskContext;
import saker.build.task.TaskExecutionEnvironmentSelector;
import saker.build.task.TaskFactory;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import test.nest.integration.execution.NestExecutionTestUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestClusterNameExecutionEnvironmentSelector;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ParameterLocalStorageClusterTaskTest extends NestClusterBuildTestCase {
	public static class RemoteableStringTaskFactory implements TaskFactory<String>, Externalizable {
		public static final class TaskImpl implements ParameterizableTask<String> {
			@SakerInput({ "" })
			public String value;

			@Override
			public String run(TaskContext taskcontext) throws Exception {
				String clname = taskcontext.getExecutionContext().getEnvironment().getUserParameters()
						.get(EnvironmentTestCase.TEST_CLUSTER_NAME_ENV_PARAM);
				if (!DEFAULT_CLUSTER_NAME.equals(clname)) {
					throw new AssertionError(clname);
				}
				return value;
			}
		}

		private static final long serialVersionUID = 1L;

		public RemoteableStringTaskFactory() {
		}

		@Override
		public NavigableSet<String> getCapabilities() {
			return ObjectUtils.newTreeSet(CAPABILITY_REMOTE_DISPATCHABLE);
		}

		@Override
		public TaskExecutionEnvironmentSelector getExecutionEnvironmentSelector() {
			return new TestClusterNameExecutionEnvironmentSelector(DEFAULT_CLUSTER_NAME);
		}

		@Override
		public ParameterizableTask<? extends String> createTask(ExecutionContext executioncontext) {
			return new TaskImpl();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		Path workdir = getWorkingDirectory();
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(RemoteableStringTaskFactory.class,
						RemoteableStringTaskFactory.TaskImpl.class, TestClusterNameExecutionEnvironmentSelector.class))//
				.build();

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", ":params");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		CombinedTargetTaskResult res;
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), "in");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
