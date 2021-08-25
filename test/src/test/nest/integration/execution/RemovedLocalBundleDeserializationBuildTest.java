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
import java.util.UUID;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

/**
 * Test for a proper exception when a bundle is removed from the local bundle storage.
 * <p>
 * Pre-fix an {@link AssertionError} was thrown.
 */
@SakerTest
public class RemovedLocalBundleDeserializationBuildTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "405dbc4d-9659-41ca-863a-8a0f1336c568";

	public static class SimpleTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		public SimpleTask() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			System.setProperty(PROPERTY_NAME, "hello");
			return "hello";
		}

		@Override
		public Task<? extends String> createTask(ExecutionContext executioncontext) {
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
				.put("installer.bundle-v1", ObjectUtils.newHashSet(LocalBundleInstallingTask.class))//
				.build();

		parameters.setRepositoryConfiguration(NestExecutionTestUtils
				.createPrivateRepositoryConfiguration(testParameters, UUID.fromString(PROPERTY_NAME)));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[:params, :local]");
		userparams.put("nest.server.offline", "true");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		//clear the repository storage directory for a clean state
		Path repostoragedir = environment.getRepositoryManager().getRepositoryStorageDirectory(
				parameters.getRepositoryConfiguration().getRepositories().iterator().next().getClassPathLocation());
		LocalFileProvider.getInstance().clearDirectoryRecursively(repostoragedir);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
				LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "simple.bundle-v1",
				ObjectUtils.newHashSet(SimpleTask.class));

		runScriptTask("export");

		runScriptTask("build");
		assertEquals(System.clearProperty(PROPERTY_NAME), "hello");

		if (project != null) {
			//clear the execution cache to trigger repository reloading
			project.waitExecutionFinalization();
			project.getExecutionCache().clear();
		}

		Path localstoragepath = repostoragedir.resolve("local");
		System.out.println("Clear local storage: " + localstoragepath);
		LocalFileProvider.getInstance().clearDirectoryRecursively(localstoragepath);

		assertTaskException(TaskNotFoundException.class, () -> runScriptTask("build"));
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
	}

}
