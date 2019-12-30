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
import saker.build.runtime.repository.TaskNotFoundException;
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
public class MultiStorageTaskTest extends CollectingMetricEnvironmentTestCase {
	//just a random uuid
	private static final String PROPERTY_NAME = "6025c0be-5db3-4ae8-9763-30ae0ffac74f";
	private static final String DEP_PROPERTY_NAME = "01521ed6-dd6c-4bc9-ada0-8d10066fc874";
	private static final String DEP_OPTIONAL_PROPERTY_NAME = "1dcf070d-6c3d-4b16-97d4-fe72d95098ad";

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

	public static class DepTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		public DepTask() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			System.out.println("MultiStorageTaskTest.DepTask.run() " + new SimpleTask());
			System.setProperty(DEP_PROPERTY_NAME, "hellodep");
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

	public static class OptionalDepTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		public OptionalDepTask() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			try {
				Class.forName("test.nest.integration.execution.MultiStorageTaskTest$SimpleTask", false,
						OptionalDepTask.class.getClassLoader());
				System.setProperty(DEP_OPTIONAL_PROPERTY_NAME, "found");
			} catch (ClassNotFoundException e) {
				System.setProperty(DEP_OPTIONAL_PROPERTY_NAME, "notfound");
			}
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
		System.clearProperty(DEP_PROPERTY_NAME);
		System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME);

		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> allbundles = TestUtils.mapBuilder(new TreeMap<String, Set<Class<?>>>())//
				.put("simple.bundle-v1", ObjectUtils.newHashSet(SimpleTask.class))//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(DepTask.class))//
				.put("dep.optional.bundle-v1", ObjectUtils.newHashSet(OptionalDepTask.class))//
				.build();
		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, allbundles);

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));

		TreeMap<String, String> userparams;
		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[base:params]");
		userparams.put("nest.base.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("simple.bundle-v1"), bundleoutdir));
		parameters.setUserParameters(userparams);

		runScriptTask("build");
		assertEquals(System.clearProperty(PROPERTY_NAME), "hello");

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);

		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[second:params, base:params]");
		userparams.put("nest.base.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("simple.bundle-v1"), bundleoutdir));
		userparams.put("nest.second.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("dep.bundle-v1"), bundleoutdir));
		parameters.setUserParameters(userparams);

		runScriptTask("dep");
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), "hellodep");

		runScriptTask("dep");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);

		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[opted:params, base:params]");
		userparams.put("nest.base.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("simple.bundle-v1"), bundleoutdir));
		userparams.put("nest.opted.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("dep.optional.bundle-v1"), bundleoutdir));
		parameters.setUserParameters(userparams);

		runScriptTask("optionaldep");
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), "found");

		runScriptTask("optionaldep");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), null);

		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[base:params, opted:params]");
		userparams.put("nest.base.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("simple.bundle-v1"), bundleoutdir));
		userparams.put("nest.opted.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("dep.optional.bundle-v1"), bundleoutdir));
		parameters.setUserParameters(userparams);

		runScriptTask("optionaldep");
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), "notfound");

		runScriptTask("optionaldep");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), null);

		//the base storage should not be visible from the first configuration list
		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[[opted:params], base:params]");
		userparams.put("nest.base.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("simple.bundle-v1"), bundleoutdir));
		userparams.put("nest.opted.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("dep.optional.bundle-v1"), bundleoutdir));
		parameters.setUserParameters(userparams);

		runScriptTask("optionaldep2");
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), "notfound");

		runScriptTask("optionaldep2");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), null);

		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[opted:params, [base:params]]");
		userparams.put("nest.base.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("simple.bundle-v1"), bundleoutdir));
		userparams.put("nest.opted.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("dep.optional.bundle-v1"), bundleoutdir));
		parameters.setUserParameters(userparams);

		runScriptTask("optionaldep3");
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), "found");

		runScriptTask("optionaldep3");
		assertEmpty(getMetric().getRunTaskIdFactories());
		assertEquals(System.clearProperty(PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_PROPERTY_NAME), null);
		assertEquals(System.clearProperty(DEP_OPTIONAL_PROPERTY_NAME), null);

		//the task should not be found, as the bundle was removed from the storage
		userparams = new TreeMap<>();
		userparams.put("nest.repository.storage.configuration", "[opted:params, [base:params]]");
		userparams.put("nest.base.bundles", NestIntegrationTestUtils
				.createParameterBundlesParameter(ObjectUtils.newTreeSet("simple.bundle-v1"), bundleoutdir));
		userparams.put("nest.opted.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(ObjectUtils.newTreeSet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		assertTaskException(TaskNotFoundException.class, () -> runScriptTask("optionaldep3"));
	}

}
