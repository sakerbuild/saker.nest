package test.nest.integration.execution;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
import saker.nest.bundle.NestBundleClassLoader;
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class LocalInstallAddTaskTaskTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuids
	private static final String EXPORTEDTASK_PROPERTY_NAME = "b8f229d5-b6ca-4b67-98bc-4200ce1be69b";
	private static final String EXPORTEDTASK2_PROPERTY_NAME = "2eb05ae3-a29f-4f1b-a6e1-0f22c9616664";

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
			System.setProperty(EXPORTEDTASK_PROPERTY_NAME, "exported_"
					+ ((NestBundleClassLoader) this.getClass().getClassLoader()).getBundle().getBundleIdentifier());
			return "export";
		}
	}

	public static class ExportedTask2 implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
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
			System.setProperty(EXPORTEDTASK2_PROPERTY_NAME, "exported2_"
					+ ((NestBundleClassLoader) this.getClass().getClassLoader()).getBundle().getBundleIdentifier());
			return "export2";
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(LocalBundleInstallingTask.class))//
				.build();

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
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

		//the export 2 task should exist
		assertException(Exception.class, () -> runScriptTask("use2"));

		runScriptTask("use1");
		assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported_exported.bundle-v1");

		NestIntegrationTestUtils.exportBundleBase64ToUserParameter(parameters, workdir,
				LocalBundleInstallingTask.EXPORT_JAR_BASE64_USER_PARAMETER, "exported.bundle-v2",
				ObjectUtils.newHashSet(ExportedTask.class, ExportedTask2.class));

		runScriptTask("export");

		runScriptTask("use2");
		assertEquals(System.clearProperty(EXPORTEDTASK2_PROPERTY_NAME), "exported2_exported.bundle-v2");
		
		runScriptTask("use1");
		assertEquals(System.clearProperty(EXPORTEDTASK_PROPERTY_NAME), "exported_exported.bundle-v2");
	}

}
