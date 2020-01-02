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
package test.nest.integration.externalinfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import test.nest.integration.execution.ServerStorageTaskTest.SimpleTask;
import test.nest.util.BasicServerNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.NestMetric;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ServerGetTasksExternalInfoTest extends ExternalScriptInformationTestCase {
	private Path bundleOutDir = getBuildDirectory().resolve("bundleout");
	private NestMetric nm = new NestMetricImplementation();

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		TestFlag.set(nm);
		super.runTest(parameters);
	}

	@Override
	protected void runOnInfoProvider(ExternalScriptInformationProvider infoprovider) {
		//trigger test pre-download
		infoprovider.getTasks(null);
		
		assertEquals(infoprovider.getTasks(null).keySet(),
				setOf(TaskName.valueOf("simple.task"), TaskName.valueOf("six.task")));
		assertEquals(infoprovider.getTasks("si").keySet(),
				setOf(TaskName.valueOf("simple.task"), TaskName.valueOf("six.task")));
		assertEquals(infoprovider.getTasks("simple").keySet(), setOf(TaskName.valueOf("simple.task")));
		assertEquals(infoprovider.getTasks("six").keySet(), setOf(TaskName.valueOf("six.task")));
	}

	@Override
	protected Map<String, String> getUserConfigurationUserParameters() throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(getWorkingDirectory()).resolve("bundles"), bundleOutDir, bundleclasses);
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[:server]");
		userparams.put("nest.server.url", "https://testurl");
		return userparams;
	}

	private final class NestMetricImplementation extends BasicServerNestMetric {
		@Override
		public Integer getServerRequestResponseCode(String requesturl) throws IOException {
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/tasks/index".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			if ("https://testurl/bundles/index".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			return super.getServerRequestResponseCode(requesturl);
		}

		@Override
		public InputStream getServerRequestResponseStream(String requesturl) throws IOException {
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				return Files.newInputStream(bundleOutDir.resolve("simple.bundle-v1.jar"));
			}
			if ("https://testurl/tasks/index".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("taskindex/index.json"));
			}
			if ("https://testurl/bundles/index".equals(requesturl)) {
				return Files.newInputStream(getWorkingDirectory().resolve("bundlesindex/index.json"));
			}
			return super.getServerRequestResponseStream(requesturl);
		}
	}
}
