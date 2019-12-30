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
public class ServerStorageMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String SERVER_PROPERTY_NAME = "aae16971-e05a-46eb-a0c0-cee3795b3c02";
	private static final String LOCAL_PROPERTY_NAME = "03caf361-2442-458d-866c-f9e3b09bbb28";
	private static final String PARAMS_PROPERTY_NAME = "6af0f6ac-a14d-41e4-bf7a-fbada8876d79";

	public static class SimpleMain {
		public static void main(String[] args) {
			System.setProperty(SERVER_PROPERTY_NAME, args[0]);
		}
	}

	public static class LocalMain {
		public static void main(String[] args) {
			SimpleMain.main(args);
			System.setProperty(LOCAL_PROPERTY_NAME, args[0]);
		}
	}

	public static class ParamsMain {
		public static void main(String[] args) {
			LocalMain.main(args);
			System.setProperty(PARAMS_PROPERTY_NAME, args[0]);
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
				.put("local.bundle-v1", ObjectUtils.newHashSet(LocalMain.class))//
				.put("params.bundle-v1", ObjectUtils.newHashSet(ParamsMain.class))//
				.build();
		System.clearProperty(SERVER_PROPERTY_NAME);
		System.clearProperty(LOCAL_PROPERTY_NAME);
		System.clearProperty(PARAMS_PROPERTY_NAME);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workingDir).resolve("bundles"), bundleOutDir, bundleclasses);

		repo.executeAction("main", "-Unest.repository.storage.configuration=[:server]",
				"-Unest.server.url=https://testurl", "-bundle", "simple.bundle-v1", "first-server-arg");
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), "first-server-arg");

		assertException("saker.nest.exc.BundleLoadingFailedException",
				() -> repo.executeAction("main", "-Unest.repository.storage.configuration=[:local,:server]",
						"-Unest.server.url=https://testurl", "-bundle", "local.bundle-v1", "first-local-arg"));

		repo.executeAction("local", "install", "-Unest.repository.storage.configuration=[:local]",
				bundleOutDir.resolve("local.bundle-v1.jar").toString());

		System.out.println("ServerStorageMainActionTest.runTestOnRepo()");

		repo.executeAction("main", "-Unest.repository.storage.configuration=[:local,:server]",
				"-Unest.server.url=https://testurl", "-bundle", "local.bundle-v1", "first-local-arg");
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), "first-local-arg");
		assertEquals(System.clearProperty(LOCAL_PROPERTY_NAME), "first-local-arg");

		repo.executeAction("main", "-Unest.repository.storage.configuration=[:params,:local,:server]",
				"-Unest.server.url=https://testurl",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(setOf("params.bundle-v1"), bundleOutDir),
				"-bundle", "params.bundle-v1", "first-params-arg");
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), "first-params-arg");
		assertEquals(System.clearProperty(LOCAL_PROPERTY_NAME), "first-params-arg");
		assertEquals(System.clearProperty(PARAMS_PROPERTY_NAME), "first-params-arg");

		System.out.println("ServerStorageMainActionTest.runTestOnRepo() " + nm);
	}

	private final class NestMetricImplementation extends BasicServerNestMetric {
		@Override
		public Integer getServerRequestResponseCode(String requesturl) throws IOException {
			if ("https://testurl/bundle/download/simple.bundle-v1".equals(requesturl)) {
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
			if ("https://testurl/bundles/index".equals(requesturl)) {
				return Files.newInputStream(workingDir.resolve("bundlesindex/index.json"));
			}
			return super.getServerRequestResponseStream(requesturl);
		}

	}

}
