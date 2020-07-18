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
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.params.ExecutionRepositoryConfiguration.RepositoryConfig;
import saker.build.runtime.repository.SakerRepository;
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
public class LocalStorageClusterTaskTest extends NestClusterBuildTestCase {
	public static class Added {
	}

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
				try {
					new Added();
					return "added-" + value;
				} catch (LinkageError e) {
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
		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));

		//clean state
		Path localroot = getBuildDirectory().resolve("local-root");
		LocalFileProvider.getInstance().clearDirectoryRecursively(localroot);

		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", ":local");
		parameters.setUserParameters(userparams);

		runTheTest();
		if (project != null) {
			project.waitExecutionFinalization();
			project.clean();
		}
		files.clearDirectoryRecursively(PATH_BUILD_DIRECTORY);

		//test with a different storage root
		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", ":local");
		userparams.put("nest.local.root", localroot.toString());
		parameters.setUserParameters(userparams);
		runTheTest();
	}

	private void runTheTest() throws IOException, Exception, Throwable, AssertionError {
		System.out.println("LocalStorageClusterTaskTest.runTheTest()");
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(RemoteableStringTaskFactory.class,
						RemoteableStringTaskFactory.TaskImpl.class, TestClusterNameExecutionEnvironmentSelector.class))//
				.put("simple.bundle-v2",
						ObjectUtils.newHashSet(RemoteableStringTaskFactory.class,
								RemoteableStringTaskFactory.TaskImpl.class,
								TestClusterNameExecutionEnvironmentSelector.class, Added.class))//
				.build();
		Path workdir = getWorkingDirectory();
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		//clear the repository storage directory for a clean state
		LocalFileProvider.getInstance()
				.clearDirectoryRecursively(environment.getRepositoryManager().getRepositoryStorageDirectory(parameters
						.getRepositoryConfiguration().getRepositories().iterator().next().getClassPathLocation()));

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		installBundle(bundleoutdir.resolve("simple.bundle-v1.jar"));

		CombinedTargetTaskResult res;
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), "in");

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());

		installBundle(bundleoutdir.resolve("simple.bundle-v2.jar"));

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("result"), "added-in");
	}

	private void installBundle(Path installbundlepath) throws Exception, IOException {
		RepositoryConfig repoconfig = parameters.getRepositoryConfiguration().getRepositories().iterator().next();
		try (SakerRepository repo = environment.getRepositoryManager().loadRepository(repoconfig.getClassPathLocation(),
				repoconfig.getRepositoryFactoryEnumerator())) {
			List<String> cmds = new ArrayList<>();
			cmds.add("local");
			cmds.add("install");
			for (Entry<String, String> entry : parameters.getUserParameters().entrySet()) {
				cmds.add("-U" + entry.getKey() + "=" + entry.getValue());
			}
			cmds.add(installbundlepath.toString());
			repo.executeAction(cmds.toArray(ObjectUtils.EMPTY_STRING_ARRAY));
		}
	}

}
