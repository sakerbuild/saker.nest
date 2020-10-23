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
import java.util.concurrent.ConcurrentSkipListSet;

import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import test.nest.util.BasicServerNestMetric;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.nest.TestFlag;

@SakerTest
public class ServerIndexUpdateCommandActionTest extends ManualLoadedRepositoryTestCase {
	//index structure taken from IndexHopServerStorageTaskTest

	private String classSubDirPath = getClass().getName().replace('.', '/');
	private Path workingDir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classSubDirPath);
	private NestMetricImplementation nm = new NestMetricImplementation();

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		TestFlag.set(nm);
		LocalFileProvider.getInstance().clearDirectoryRecursively(getStorageDirectory());
		super.runTest(parameters);
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		repo.executeAction("server", "index", "update", "-Unest.repository.storage.configuration=[:server]",
				"-Unest.server.url=https://testurl");
		assertEquals(nm.queriedPaths.size(), 7);
	}

	@Override
	protected Path getStorageDirectory() {
		return super.getStorageDirectory().resolve(this.getClass().getName());
	}

	private final class NestMetricImplementation extends BasicServerNestMetric {

		protected ConcurrentSkipListSet<Path> queriedPaths = new ConcurrentSkipListSet<>();

		@Override
		public Integer getServerRequestResponseCode(String method, String requesturl) throws IOException {
			System.out.println("IndexHopServerStorageTaskTest.NestMetricImplementation.getServerRequestResponseCode() "
					+ requesturl);
			if (requesturl.startsWith("https://testurl/bundle/download/simple.bundle-v1?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/bundle/download/s-v1?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/tasks/index?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/bundles/index?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s.lookup?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s/i?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s/i/split.1?")) {
				return HttpURLConnection.HTTP_OK;
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s/i/split.2?")) {
				return HttpURLConnection.HTTP_OK;
			}
			System.out.println("Unhandled response code: " + requesturl);
			return super.getServerRequestResponseCode(method, requesturl);
		}

		@Override
		public InputStream getServerRequestResponseStream(String method, String requesturl) throws IOException {
			System.out
					.println("IndexHopServerStorageTaskTest.NestMetricImplementation.getServerRequestResponseStream() "
							+ requesturl);
			if (requesturl.startsWith("https://testurl/tasks/index?")) {
				Path path = workingDir.resolve("taskindex/index.json");
				queriedPaths.add(path);
				return Files.newInputStream(path);
			}
			if (requesturl.startsWith("https://testurl/bundles/index?")) {
				Path path = workingDir.resolve("bundlesindex/index.json");
				queriedPaths.add(path);
				return Files.newInputStream(path);
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s?")) {
				Path path = workingDir.resolve("bundlesindex/s/index.json");
				queriedPaths.add(path);
				return Files.newInputStream(path);
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s.lookup?")) {
				Path path = workingDir.resolve("bundlesindex/s.lookup");
				queriedPaths.add(path);
				return Files.newInputStream(path);
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s/i?")) {
				Path path = workingDir.resolve("bundlesindex/s/i/index.json");
				queriedPaths.add(path);
				return Files.newInputStream(path);
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s/i/split.1?")) {
				Path path = workingDir.resolve("bundlesindex/s/i/split.1.json");
				queriedPaths.add(path);
				return Files.newInputStream(path);
			}
			if (requesturl.startsWith("https://testurl/bundles/index/s/i/split.2?")) {
				Path path = workingDir.resolve("bundlesindex/s/i/split.2.json");
				queriedPaths.add(path);
				return Files.newInputStream(path);
			}
			System.out.println("Unhandled stream: " + requesturl);
			return super.getServerRequestResponseStream(method, requesturl);
		}
	}
}
