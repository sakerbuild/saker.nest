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
public class ConflictingDependencyTaskTest extends CollectingMetricEnvironmentTestCase {
	private static final String VERSION1_CLASSNAME = "test.nest.integration.execution.ConflictingDependencyTaskTest$DependentVersion1";
	private static final String VERSION2_CLASSNAME = "test.nest.integration.execution.ConflictingDependencyTaskTest$DependentVersion2";

	public static class FirstTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		public FirstTask() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			Class<?> c = IntermediateClass.getTheClass();
			if (!c.getName().equals(VERSION1_CLASSNAME)) {
				throw new AssertionError(c);
			}
			try {
				Class.forName(VERSION2_CLASSNAME, false, this.getClass().getClassLoader());
				throw new AssertionError();
			} catch (ClassNotFoundException e) {
			}
			System.out.println("ConflictingDependencyTaskTest.FirstTask.run() " + c);
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

	public static class SecondTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
		private static final long serialVersionUID = 1L;

		public SecondTask() {
		}

		@Override
		public String run(TaskContext taskcontext) throws Exception {
			Class<?> c = IntermediateClass.getTheClass();
			if (!c.getName().equals(VERSION2_CLASSNAME)) {
				throw new AssertionError(c);
			}
			try {
				Class.forName(VERSION1_CLASSNAME, false, this.getClass().getClassLoader());
				fail();
			} catch (ClassNotFoundException e) {
			}
			System.out.println("ConflictingDependencyTaskTest.SecondTask.run() " + c);
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

	public static class DependentVersion1 {
	}

	public static class DependentVersion2 {
	}

	public static class IntermediateClass {
		public static Class<?> getTheClass() {
			try {
				return Class.forName(VERSION1_CLASSNAME, false, IntermediateClass.class.getClassLoader());
			} catch (ClassNotFoundException e) {
			}
			try {
				return Class.forName(VERSION2_CLASSNAME, false, IntermediateClass.class.getClassLoader());
			} catch (ClassNotFoundException e) {
			}
			throw new AssertionError();
		}
	}

	@Override
	protected void runTestImpl() throws Throwable {
		//first.bundle-v1:
//		dep.bundle
//			classpath: 1
//		intermediate.bundle
//			classpath: 1
		
		//intermediate.bundle-v1:
//		intermediate.second.bundle
//			classpath: 1
		
		//intermediate.second.bundle-v1:
//		dep.bundle
//			classpath: {1|2}
		
		//second.bundle-v1:
//		dep.bundle
//			classpath: 2
//		intermediate.bundle
//			classpath: 1
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("first.bundle-v1", ObjectUtils.newHashSet(FirstTask.class))//
				.put("second.bundle-v1", ObjectUtils.newHashSet(SecondTask.class))//
				.put("dep.bundle-v1", ObjectUtils.newHashSet(DependentVersion1.class))//
				.put("dep.bundle-v2", ObjectUtils.newHashSet(DependentVersion2.class))//
				.put("intermediate.bundle-v1", ObjectUtils.newHashSet())//
				.put("intermediate.second.bundle-v1", ObjectUtils.newHashSet(IntermediateClass.class))//
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

		runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdFactories());
	}

}
