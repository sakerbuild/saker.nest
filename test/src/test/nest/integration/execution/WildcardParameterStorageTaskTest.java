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
public class WildcardParameterStorageTaskTest extends CollectingMetricEnvironmentTestCase {
	public static class SimpleTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		public SimpleTask() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				return Class.forName("test.nest.integration.execution.WildcardParameterStorageTaskTest$DepBundleClass",
						false, this.getClass().getClassLoader()).getName();
			} catch (ClassNotFoundException e) {
			}
			return "not-found";
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

	public static class DepBundleClass {

	}

	@Override
	protected void runTestImpl() throws Throwable {
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		LocalFileProvider.getInstance().clearDirectoryRecursively(bundleoutdir);

		TreeMap<String, Set<Class<?>>> firstbundles = TestUtils.mapBuilder(new TreeMap<String, Set<Class<?>>>())//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, firstbundles);

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));

		TreeMap<String, String> userparams;
		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[:params]");
		userparams.put("nest.params.bundles", "//" + bundleoutdir.toString() + "/*.jar");
		parameters.setUserParameters(userparams);

		CombinedTargetTaskResult res;
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("val"), "not-found");

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("val"), "not-found");
		assertEmpty(getMetric().getRunTaskIdFactories());

		TreeMap<String, Set<Class<?>>> secondbundles = TestUtils.mapBuilder(new TreeMap<String, Set<Class<?>>>())//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(DepBundleClass.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles2"), bundleoutdir, secondbundles);

		res = runScriptTask("build");
		assertNotEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(res.getTargetTaskResult("val"), DepBundleClass.class.getName());

		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("val"), DepBundleClass.class.getName());
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
