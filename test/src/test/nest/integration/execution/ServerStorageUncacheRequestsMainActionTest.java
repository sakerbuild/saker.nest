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
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import test.nest.util.BasicServerNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.NestMetric;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ServerStorageUncacheRequestsMainActionTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "4c903cef-8328-4e79-a618-d634e2683588";

	public static class SimpleTask implements TaskFactory<Object>, Task<Object>, Externalizable {
		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public SimpleTask() {
		}

		@Override
		public Object run(TaskContext taskcontext) throws Exception {
			System.setProperty(PROPERTY_NAME, "CALLED");
			return null;
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return this;
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

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path bundleOutDir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classSubDirPath);
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private NestMetric nm = new NestMetricImplementation();

	@Override
	public void executeRunning() throws Exception {
		TestFlag.set(nm);
		super.executeRunning();
	}

	@Override
	protected void runTestImpl() throws Throwable {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.build();
		System.clearProperty(PROPERTY_NAME);

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[:server]");
		userparams.put("nest.server.url", "https://testurl");
		userparams.put("nest.server.requests.uncache", "true");
		parameters.setUserParameters(userparams);

		//clear the repository storage directory for a clean state
		LocalFileProvider.getInstance()
				.clearDirectoryRecursively(environment.getRepositoryManager().getRepositoryStorageDirectory(parameters
						.getRepositoryConfiguration().getRepositories().iterator().next().getClassPathLocation()));

		Path workdir = getWorkingDirectory();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleOutDir, bundleclasses);

		runScriptTask("build");
		assertEquals(System.clearProperty(PROPERTY_NAME), "CALLED");
	}

	private final class NestMetricImplementation extends BasicServerNestMetric {
		@Override
		public Integer getServerRequestResponseCode(String method,String requesturl) throws IOException {
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index".equals(requesturl)) {
				//not found, we should get a get parameter
				return super.getServerRequestResponseCode(method, requesturl);
			}
			if (requesturl.startsWith("https://testurl/bundles/index?uncache-")) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/tasks/index".equals(requesturl)) {
				//not found, we should get a get parameter
				return super.getServerRequestResponseCode(method, requesturl);
			}
			if (requesturl.startsWith("https://testurl/tasks/index?uncache-")) {
				return HttpURLConnection.HTTP_OK;
			}
			return super.getServerRequestResponseCode(method, requesturl);
		}

		@Override
		public InputStream getServerRequestResponseStream(String method, String requesturl) throws IOException {
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				return Files.newInputStream(bundleOutDir.resolve("simple.bundle-v1.jar"));
			}
			if ("https://testurl/bundles/index".equals(requesturl)) {
				//not found, we should get a get parameter
				return super.getServerRequestResponseStream(method, requesturl);
			}
			if (requesturl.startsWith("https://testurl/bundles/index?uncache-")) {
				return Files.newInputStream(workingDir.resolve("bundlesindex/index.json"));
			}
			if ("https://testurl/tasks/index".equals(requesturl)) {
				//not found, we should get a get parameter
				return super.getServerRequestResponseStream(method, requesturl);
			}
			if (requesturl.startsWith("https://testurl/tasks/index?uncache-")) {
				return Files.newInputStream(workingDir.resolve("taskindex/index.json"));
			}
			return super.getServerRequestResponseStream(method, requesturl);

		}

	}
}
