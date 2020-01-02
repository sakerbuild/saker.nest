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
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class CommonDependencyLoadedOnceTaskTest extends CollectingMetricEnvironmentTestCase {
	//bundles which load native libraries shouldn't declare their own dependencies, as that could cause them to be loaded multiple times
	//with the same library location

	public static class FirstTask
			implements TaskFactory<DependentVersion1>, ParameterizableTask<DependentVersion1>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public DependentVersion1 run(TaskContext taskcontext) throws Exception {
			return new DependentVersion1();
		}

		@Override
		public ParameterizableTask<? extends DependentVersion1> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	public static class SecondTask
			implements TaskFactory<DependentVersion1>, ParameterizableTask<DependentVersion1>, Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public DependentVersion1 run(TaskContext taskcontext) throws Exception {
			return new DependentVersion1();
		}

		@Override
		public ParameterizableTask<? extends DependentVersion1> createTask(ExecutionContext executioncontext) {
			return this;
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}

		@Override
		public int hashCode() {
			return getClass().hashCode();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	public static class DependentVersion1 implements Externalizable {
		private static final long serialVersionUID = 1L;

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("first.bundle-v1", ObjectUtils.newHashSet(FirstTask.class))//
				.put("second.bundle-v1", ObjectUtils.newHashSet(SecondTask.class))//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(DependentVersion1.class))//
				.build();

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.server.offline", "true");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		CombinedTargetTaskResult res = runScriptTask("build");
		assertSameClass(res.getTargetTaskResult("first"), res.getTargetTaskResult("second"));

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
