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
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import test.nest.util.BasicServerNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.NestMetric;
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ServerRedirectMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String SERVER_PROPERTY_NAME = "439f9ae2-0b71-43dd-b98b-986e05f07cee";

	public static class SimpleMain {
		public static void main(String[] args) {
			System.setProperty(SERVER_PROPERTY_NAME, args[0]);
		}
	}

	@Override
	protected Path getStorageDirectory() {
		return super.getStorageDirectory().resolve(this.getClass().getName());
	}

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path bundleOutDir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classSubDirPath);
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private NestMetric nm = new NestMetricImplementation();

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		TestFlag.set(nm);
		LocalFileProvider.getInstance().clearDirectoryRecursively(getStorageDirectory());
		super.runTest(parameters);
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.build();
		System.clearProperty(SERVER_PROPERTY_NAME);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workingDir).resolve("bundles"), bundleOutDir, bundleclasses);

		repo.executeAction("main", "-Unest.repository.storage.configuration=[:server]",
				"-Unest.server.url=https://testurl", "-bundle", "simple.bundle-v1", "first-server-arg");
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), "first-server-arg");
	}

	private final class NestMetricImplementation extends BasicServerNestMetric {
		@Override
		public Integer getServerRequestResponseCode(String method, String requesturl) throws IOException {
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				return HttpURLConnection.HTTP_MOVED_TEMP;
			}
			if ("https://redirected/simple.bundle-v1".equals(requesturl)) {
				return HttpURLConnection.HTTP_OK;
			}
			return super.getServerRequestResponseCode(method, requesturl);
		}

		@Override
		public Map<String, String> getServerRequestResponseHeaders(String method, String requesturl) {
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
				TreeMap<String, String> res = ObjectUtils
						.newTreeMap(super.getServerRequestResponseHeaders(method, requesturl));
				res.put("Location", "https://redirected/simple.bundle-v1");
				return res;
			}
			return super.getServerRequestResponseHeaders(method, requesturl);
		}

		@Override
		public InputStream getServerRequestResponseStream(String method, String requesturl) throws IOException {
			if ("https://redirected/simple.bundle-v1".equals(requesturl)) {
				return getBundleInputStream(method, "simple.bundle-v1");
			}
			return super.getServerRequestResponseStream(method, requesturl);
		}

		@Override
		protected InputStream getBundleInputStream(String method, String bundleid) throws IOException {
			if (bundleid.equals("simple.bundle-v1")) {
				return Files.newInputStream(bundleOutDir.resolve("simple.bundle-v1.jar"));
			}
			return super.getBundleInputStream(method, bundleid);
		}

	}

}
