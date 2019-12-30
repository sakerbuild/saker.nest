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
import testing.saker.SakerTest;
import testing.saker.build.tests.CollectingMetricEnvironmentTestCase;
import testing.saker.build.tests.TestUtils;
import testing.saker.nest.util.NestIntegrationTestUtils;

@SakerTest
public class ProviderInstantiateTaskTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "2124844f-214b-45c6-a08f-196e511d9169";
	private static final String PROVIDER_INSTANTIATE_PROPERTY = "e5e7f1e2-7793-4c56-8d7b-89b6ed13990d";

	public static class SimpleTaskFactory {
		public static SimpleTask provider() {
			System.setProperty(PROVIDER_INSTANTIATE_PROPERTY, "true");
			return new SimpleTask();
		}
	}

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

	@Override
	protected void runTestImpl() throws Throwable {
		System.clearProperty(PROPERTY_NAME);

		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class, SimpleTaskFactory.class))//
				.build();

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.server.offline", "true");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		runScriptTask("build");

		assertEquals(System.getProperty(PROPERTY_NAME), "hello");
		assertEquals(System.getProperty(PROVIDER_INSTANTIATE_PROPERTY), "true");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());

		System.clearProperty(PROPERTY_NAME);
		System.clearProperty(PROVIDER_INSTANTIATE_PROPERTY);
	}

}
