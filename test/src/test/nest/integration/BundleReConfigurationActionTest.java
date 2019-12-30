package test.nest.integration;

import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.repository.SakerRepository;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.bundle.NestBundleClassLoader;
import saker.nest.bundle.storage.ServerBundleStorageView;
import testing.saker.SakerTest;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class BundleReConfigurationActionTest extends ManualLoadedRepositoryTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "ac52e7fd-6efb-4319-8f7d-a9ea040fa2e1";
	private static final String PROPERTY_NAME_NEXT = "ac52e7fd-6efb-4319-8f7d-a9ea040fa2e2";

	public static class SimpleMain {
		public static void main(String[] args) throws UnsupportedOperationException, NullPointerException {
			System.setProperty(PROPERTY_NAME, args[0]);
			Map<String, String> reconfigparams = ((NestBundleClassLoader) SimpleMain.class.getClassLoader())
					.getRelativeBundleLookup().getLocalConfigurationUserParameters("nest");
			for (Entry<String, String> entry : reconfigparams.entrySet()) {
				System.setProperty(PROPERTY_NAME + entry.getKey(), entry.getValue());
			}
		}
	}

	@Override
	protected void runTestOnRepo(SakerRepository repo) throws Exception {
		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("rcsimple.bundle-v1", ObjectUtils.newHashSet(SimpleMain.class))//
				.build();

		System.clearProperty(PROPERTY_NAME);

		String classsubdirpath = getClass().getName().replace('.', '/');
		Path workdir = EnvironmentTestCase.getTestingBaseWorkingDirectory().resolve(classsubdirpath);
		Path bundleoutdir = EnvironmentTestCase.getTestingBaseBuildDirectory().resolve(classsubdirpath);
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		String bundleoutdirstr = bundleoutdir.toString();
		repo.executeAction("main", "-Unest.server.offline=true",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "rcsimple.bundle-v1", bundleoutdirstr);
		assertEquals(System.clearProperty(PROPERTY_NAME), bundleoutdirstr);
		assertMap(new TreeMap<>(System.getProperties()).subMap(PROPERTY_NAME, false, PROPERTY_NAME_NEXT, false))
				.contains(PROPERTY_NAME + "nest.params.bundles",
						"//" + SakerPath.valueOf(bundleoutdir.resolve("rcsimple.bundle-v1.jar")).toString())
				.contains(PROPERTY_NAME + "nest.server.offline", "true")
				.contains(PROPERTY_NAME + "nest.repository.storage.configuration", "[:params,:local,:server]")
				.contains(PROPERTY_NAME + "nest.server.signature.version.min", "1")
				.contains(PROPERTY_NAME + "nest.server.url", ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL)
				.noRemaining();

		repo.executeAction("main", "-Unest.server.offline=true",
				"-Unest.repository.storage.configuration=[:local,:params]",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "rcsimple.bundle-v1", bundleoutdirstr);
		assertEquals(System.clearProperty(PROPERTY_NAME), bundleoutdirstr);
		assertMap(new TreeMap<>(System.getProperties()).subMap(PROPERTY_NAME, false, PROPERTY_NAME_NEXT, false))
				.contains(PROPERTY_NAME + "nest.params.bundles",
						"//" + SakerPath.valueOf(bundleoutdir.resolve("rcsimple.bundle-v1.jar")).toString())
				.contains(PROPERTY_NAME + "nest.server.offline", "true")
				.contains(PROPERTY_NAME + "nest.repository.storage.configuration", "[:params]")
				.contains(PROPERTY_NAME + "nest.server.signature.version.min", "1")
				.contains(PROPERTY_NAME + "nest.server.url", ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL)
				.noRemaining();

		repo.executeAction("main", "-Unest.server.offline=true",
				"-Unest.repository.storage.configuration=[:local,[:params,:server]]",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "rcsimple.bundle-v1", bundleoutdirstr);
		assertEquals(System.clearProperty(PROPERTY_NAME), bundleoutdirstr);
		assertMap(new TreeMap<>(System.getProperties()).subMap(PROPERTY_NAME, false, PROPERTY_NAME_NEXT, false))
				.contains(PROPERTY_NAME + "nest.params.bundles",
						"//" + SakerPath.valueOf(bundleoutdir.resolve("rcsimple.bundle-v1.jar")).toString())
				.contains(PROPERTY_NAME + "nest.server.offline", "true")
				.contains(PROPERTY_NAME + "nest.repository.storage.configuration", "[:params,:server]")
				.contains(PROPERTY_NAME + "nest.server.signature.version.min", "1")
				.contains(PROPERTY_NAME + "nest.server.url", ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL)
				.noRemaining();

		repo.executeAction("main", "-Unest.server.offline=true",
				"-Unest.repository.storage.configuration=[:params,[:local,:server]]",
				NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
				"-bundle", "rcsimple.bundle-v1", bundleoutdirstr);
		assertEquals(System.clearProperty(PROPERTY_NAME), bundleoutdirstr);
		assertMap(new TreeMap<>(System.getProperties()).subMap(PROPERTY_NAME, false, PROPERTY_NAME_NEXT, false))
				.contains(PROPERTY_NAME + "nest.params.bundles",
						"//" + SakerPath.valueOf(bundleoutdir.resolve("rcsimple.bundle-v1.jar")).toString())
				.contains(PROPERTY_NAME + "nest.server.offline", "true")
				.contains(PROPERTY_NAME + "nest.repository.storage.configuration", "[:params,[:local,:server]]")
				.contains(PROPERTY_NAME + "nest.server.signature.version.min", "1")
				.contains(PROPERTY_NAME + "nest.server.url", ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL)
				.noRemaining();
	}

}
