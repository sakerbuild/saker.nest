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
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import test.nest.util.BasicServerNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.NestMetric;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class IndexHopServerStorageTaskTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "1d16723b-85bc-49c4-89d3-ae2af035c1e7";

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

	private Path bundleOutDir = getBuildDirectory().resolve("bundleout");
	private NestMetric nm = new NestMetricImplementation();

	@Override
	public void executeRunning() throws Exception {
		TestFlag.set(nm);
		super.executeRunning();
	}

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setEnvironmentStorageDirectory(null).build();
	}

	@Override
	protected void runTestImpl() throws Throwable {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.put("s-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.build();
		System.clearProperty(PROPERTY_NAME);

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[:server]");
		userparams.put("nest.server.url", "https://testurl");
		parameters.setUserParameters(userparams);

		//clear the repository storage directory for a clean state
		LocalFileProvider.getInstance()
				.clearDirectoryRecursively(environment.getRepositoryManager().getRepositoryStorageDirectory(parameters
						.getRepositoryConfiguration().getRepositories().iterator().next().getClassPathLocation()));

		Path workdir = getWorkingDirectory();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleOutDir, bundleclasses);

		runScriptTask("build");
		assertEquals(System.clearProperty(PROPERTY_NAME), "hello");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);

		runScriptTask("sbuild");
		assertEquals(System.clearProperty(PROPERTY_NAME), "hello");
	}

	private final class NestMetricImplementation extends BasicServerNestMetric {

		@Override
		public Integer getServerRequestResponseCode(String requesturl) throws IOException {
			System.out.println("IndexHopServerStorageTaskTest.NestMetricImplementation.getServerRequestResponseCode() "
					+ requesturl);
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundle/download/s-v1".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/tasks/index".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index/s.lookup".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index/s".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index/s/i".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index/s/i/split.1".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index/s/i/split.2".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			return super.getServerRequestResponseCode(requesturl);
		}

		@Override
		public InputStream getServerRequestResponseStream(String requesturl) throws IOException {
			System.out
					.println("IndexHopServerStorageTaskTest.NestMetricImplementation.getServerRequestResponseStream() "
							+ requesturl);
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				return Files.newInputStream(bundleOutDir.resolve("simple.bundle-v1.jar"));
			}
			if ("https://testurl/bundle/download/s-v1".equals(requesturl)) {
				return Files.newInputStream(bundleOutDir.resolve("s-v1.jar"));
			}
			if ("https://testurl/tasks/index".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("taskindex/index.json"));
			}
			if ("https://testurl/bundles/index".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("bundlesindex/index.json"));
			}
			if ("https://testurl/bundles/index/s".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("bundlesindex/s/index.json"));
			}
			if ("https://testurl/bundles/index/s.lookup".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("bundlesindex/s.lookup"));
			}
			if ("https://testurl/bundles/index/s/i".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("bundlesindex/s/i/index.json"));
			}
			if ("https://testurl/bundles/index/s/i/split.1".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("bundlesindex/s/i/split.1.json"));
			}
			if ("https://testurl/bundles/index/s/i/split.2".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("bundlesindex/s/i/split.2.json"));
			}
			return super.getServerRequestResponseStream(requesturl);
		}
	}
}
