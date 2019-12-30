package test.nest.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
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
import testing.saker.nest.TestFlag;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class InvalidSignatureServerStorageMainActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String SERVER_PROPERTY_NAME = "66b43304-645d-4943-bf76-174cfc2dd84a";

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
	private NestMetricImplementation nm = new NestMetricImplementation();

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

		//test that signing with other private key verification fails
		assertException("saker.nest.exc.BundleLoadingFailedException",
				() -> repo.executeAction("main", "-Unest.repository.storage.configuration=[:server]",
						"-Unest.server.url=https://testurl", "-bundle", "simple.bundle-v1", "first-server-arg"));
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), null);

		//test that it succeeds if the verification is turned off
		repo.executeAction("main", "-Unest.repository.storage.configuration=[:server]",
				"-Unest.server.url=https://testurl", "-Unest.server.signature.verify=false", "-bundle",
				"simple.bundle-v1", "first-server-arg");
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), "first-server-arg");

		//test greater than available version fails, as the server doesn't provide key
		assertException("saker.nest.exc.BundleLoadingFailedException",
				() -> repo.executeAction("main", "-Unest.repository.storage.configuration=[:server]",
						"-Unest.server.url=https://testurl", "-Unest.server.signature.version.min=2", "-bundle",
						"simple.bundle-v1", "first-server-arg"));
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), null);

		//update the server to provide v2 key, and valid signature, test again, succeed
		nm.updateForValidVersion2();
		repo.executeAction("main", "-Unest.repository.storage.configuration=[:server]",
				"-Unest.server.url=https://testurl", "-Unest.server.signature.version.min=2", "-bundle",
				"simple.bundle-v1", "first-server-arg");
		assertEquals(System.clearProperty(SERVER_PROPERTY_NAME), "first-server-arg");
	}

	private final class NestMetricImplementation extends BasicServerNestMetric {
		private PrivateKey signingPrivateKey;

		public NestMetricImplementation() {
			signingPrivateKey = NestIntegrationTestUtils.generateRSAKeyPair().getPrivate();
		}

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

		@Override
		protected PrivateKey getBundleSigningPrivateKey() {
			return signingPrivateKey;
		}

		public void updateForValidVersion2() {
			System.out.println(
					"InvalidSignatureServerStorageMainActionTest.NestMetricImplementation.updateForValidVersion2()");
			bundleSigningVersion = 2;
			bundleSigningKeyPair = NestIntegrationTestUtils.generateRSAKeyPair();
			signingPrivateKey = bundleSigningKeyPair.getPrivate();
		}

		@Override
		public boolean allowCachedVerificationState(String bundleid) {
			//don't cache the verification states as we modify the server provided information without reloading the repository
			return false;
		}
	}

}
