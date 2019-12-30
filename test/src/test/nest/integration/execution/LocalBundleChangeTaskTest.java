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
import saker.build.runtime.execution.ExecutionContext;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class LocalBundleChangeTaskTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String EXPORTEDTASK_PROPERTY_NAME = "8c8a13d2-e3d5-40dc-97fa-2b89ba5ec433";

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
				Class.forName("test.nest.integration.execution.LocalBundleChangeTaskTest$AdditionClass", false,
						ExportedTask.class.getClassLoader());
				try {
					Class.forName("test.nest.integration.execution.LocalBundleChangeTaskTest$AdditionClassV3", false,
							ExportedTask.class.getClassLoader());
					try {
						Class.forName("test.nest.integration.execution.LocalBundleChangeTaskTest$AdditionClassV4",
								false, ExportedTask.class.getClassLoader());
						System.setProperty(EXPORTEDTASK_PROPERTY_NAME, "exported4");
					} catch (ClassNotFoundException e) {
						System.setProperty(EXPORTEDTASK_PROPERTY_NAME, "exported3");
					}
				} catch (ClassNotFoundException e) {
					System.setProperty(EXPORTEDTASK_PROPERTY_NAME, "exported2");
				}
			} catch (ClassNotFoundException e) {
				System.setProperty(EXPORTEDTASK_PROPERTY_NAME, "exported");
			}

			return "export";
		}
	}

	public static class AdditionClass {
	}

	public static class AdditionClassV3 {
	}

	public static class AdditionClassV4 {
	}

	private static final UUID[] PRIVATE_REPO_UUIDS = { UUID.fromString("87027827-7128-4ff0-90f3-ae2d14f4c046"),
			UUID.fromString("6af0e915-aef2-4967-bd80-772511a5d91a") };
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
				.put("simple.bundle-v1", ObjectUtils.newHashSet(LocalBundleInstallingTask.class))//
				.build();

		parameters.setRepositoryConfiguration(
				NestExecutionTestUtils.createPrivateRepositoryConfiguration(testParameters, privarerepouuid));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.server.offline", "true");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		//clear the repository storage directory for a clean state
		LocalFileProvider.getInstance()
				.clearDirectoryRecursively(environment.getRepositoryManager().getRepositoryStorageDirectory(parameters
						.getRepositoryConfiguration().getRepositories().iterator().next().getClassPathLocation()));

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
				LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "exported.bundle-v1",
				ObjectUtils.newHashSet(ExportedTask.class));

		runScriptTask("export");

		runScriptTask("use");
		assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported");

		NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
				LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "exported.bundle-v2",
				ObjectUtils.newHashSet(ExportedTask.class, AdditionClass.class));

		runScriptTask("export");

		runScriptTask("use");
		assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported2");

		//update the local bundle with a different implementation
		NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
				LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "exported.bundle-v2",
				ObjectUtils.newHashSet(ExportedTask.class, AdditionClass.class, AdditionClassV3.class));

		runScriptTask("export");

		runScriptTask("use");
		assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported3");

		//run a no-op to have any transient bundle changes propagate in the repository
		runScriptTask("use");
		assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), null);

		//update once more
		NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
				LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "exported.bundle-v2",
				ObjectUtils.newHashSet(ExportedTask.class, AdditionClass.class, AdditionClassV3.class,
						AdditionClassV4.class));

		runScriptTask("export");

		runScriptTask("use");
		assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported4");
	}

	private void addUserParam(String propertyname, String encoded) {
		ExecutionParametersImpl parameters = this.parameters;
		NestIntegrationTestUtils.addUserParam(parameters, propertyname, encoded);
	}

}
