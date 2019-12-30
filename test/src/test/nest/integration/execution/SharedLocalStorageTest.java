package test.nest.integration.execution;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.classpath.ClassPathLoadManager;
import saker.build.runtime.environment.RepositoryManager;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.runtime.params.ExecutionRepositoryConfiguration.RepositoryConfig;
import saker.build.runtime.repository.SakerRepository;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class SharedLocalStorageTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "78340385-dfae-4981-8ba8-3751a974298a";
	private static final String EXPORTEDTASK_PROPERTY_NAME = "e86ee38e-4be9-4af5-b5de-810a97ebbe77";

	public static class ExportedTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
		private static final long serialVersionUID = 1L;

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

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				Class.forName("test.nest.integration.execution.SharedLocalStorageTest$AdditionClass", false,
						ExportedTask.class.getClassLoader());
				System.setProperty(EXPORTEDTASK_PROPERTY_NAME, "exported2");
			} catch (ClassNotFoundException e) {
				System.setProperty(EXPORTEDTASK_PROPERTY_NAME, "exported");
			}

			return "export";
		}
	}

	public static class AdditionClass {
	}

	public static class SimpleMain {
		public static void main(String[] args) {
			System.setProperty(PROPERTY_NAME, args[0]);
		}
	}

	private static final UUID[] PRIVATE_REPO_UUIDS = { UUID.fromString("a2d773fd-27cc-421d-9c5c-7f479e9d1284"),
			UUID.fromString("cdde1e1a-26c0-496c-ae34-baba5d7bc2bd") };
	private AtomicInteger invocationId = new AtomicInteger();

	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		return EnvironmentTestCaseConfiguration.builder(super.getTestConfigurations())
				.setEnvironmentStorageDirectory(null).build();
	}

	@Override
	protected void runTestImpl() throws Throwable {
		UUID privarerepouuid = PRIVATE_REPO_UUIDS[invocationId.getAndIncrement()];
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(LocalBundleInstallingTask.class, SimpleMain.class))//
				.build();

		parameters.setRepositoryConfiguration(
				NestExecutionTestUtils.createPrivateRepositoryConfiguration(testParameters, privarerepouuid));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.server.offline", "true");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		//clear the repository storage directory for a clean stae
		RepositoryConfig repoconfig = parameters.getRepositoryConfiguration().getRepositories().iterator().next();
		LocalFileProvider.getInstance().clearDirectoryRecursively(
				environment.getRepositoryManager().getRepositoryStorageDirectory(repoconfig.getClassPathLocation()));

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
				LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "exported.bundle-v1",
				ObjectUtils.newHashSet(ExportedTask.class));

		try (ClassPathLoadManager classpathmanager = new ClassPathLoadManager(environment.getStorageDirectoryPath());
				RepositoryManager repomanager = new RepositoryManager(classpathmanager,
						EnvironmentTestCase.getSakerJarPath());
				SakerRepository repo = repomanager.loadRepository(repoconfig.getClassPathLocation(),
						repoconfig.getRepositoryFactoryEnumerator())) {
			repo.executeAction("main", "-Unest.server.offline=true",
					NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
					"-bundle", "simple.bundle-v1", "first-arg");
			assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg");

			runScriptTask("export");

			runScriptTask("use");
			assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported");

			NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
					LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "exported.bundle-v2",
					ObjectUtils.newHashSet(ExportedTask.class, AdditionClass.class, SimpleMain.class));

			System.out.println("SharedLocalStorageTest.runTestImpl() SECOND");
			runScriptTask("export");

			runScriptTask("use");
			assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported2");

			repo.executeAction("main", "-Unest.server.offline=true",
					NestIntegrationTestUtils.createParameterBundlesUserParameter(bundleclasses.keySet(), bundleoutdir),
					"-bundle", "exported.bundle-v2", "first-arg-ex");
			assertEquals(System.clearProperty(PROPERTY_NAME), "first-arg-ex");
		}
	}

	private void addUserParam(String propertyname, String encoded) {
		ExecutionParametersImpl parameters = this.parameters;
		NestIntegrationTestUtils.addUserParam(parameters, propertyname, encoded);
	}
}
