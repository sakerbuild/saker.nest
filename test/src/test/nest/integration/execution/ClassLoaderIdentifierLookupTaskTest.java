/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package test.nest.integration.execution;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.task.utils.annot.SakerInput;
import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.SakerTest;
import testing.saker.build.tests.TestUtils;
import testing.saker.build.tests.VariablesMetricEnvironmentTestCase;
import testing.saker.nest.util.NestIntegrationTestUtils;

/**
 * This test tests that the nest repository can properly reinstantiate the classloader for which a given class was
 * serialized.
 * <p>
 * It tests that the pinned dependencies are reconstructed appropriately for a loaded class.
 * <p>
 * This test has the following dependency structure:
 * 
 * <pre>
 * A1--->B1----[1, 2]
 *  \             |
 *   \-->C1<-----/
 * </pre>
 * 
 * So when a Class from B1 is serialized, it is properly loaded with a dependency to C1. In the failing scenario, the
 * class loader for B1 is reconstructed with C2 as the dependency, although C1 was pinned for it.
 * <p>
 * NOTE: If the dependency of B1 to C1 is modified to <code>1</code>, then the test succeeds.
 */
@SakerTest
public class ClassLoaderIdentifierLookupTaskTest extends VariablesMetricEnvironmentTestCase {

	public static class ATask implements TaskFactory<Object>, Externalizable {
		public static final class AParamTaskImpl implements ParameterizableTask<Object> {
			@SakerInput("")
			public String input;

			@Override
			public Object run(TaskContext taskcontext) throws Exception {
				return new B1(input);
			}
		}

		private static final long serialVersionUID = 1L;

		/**
		 * For {@link Externalizable}.
		 */
		public ATask() {
		}

		@Override
		public Task<? extends Object> createTask(ExecutionContext executioncontext) {
			return new AParamTaskImpl();
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		}

		@Override
		public int hashCode() {
			return getClass().getName().hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return ObjectUtils.isSameClass(this, obj);
		}
	}

	public static class B1 implements Externalizable {
		private static final long serialVersionUID = 1L;

		public C1 str;

		public B1() {
		}

		public B1(String str) {
			this.str = new C1(str);
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(str);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			str = (C1) in.readObject();
		}
	}

	public static class C1 implements Externalizable {
		private static final long serialVersionUID = 1L;

		public String str;

		public C1() {
		}

		public C1(String str) {
			this.str = str;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(str);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			str = (String) in.readObject();
		}

	}

	public static class C2 implements Serializable {
		private static final long serialVersionUID = 1L;
	}

	private String taskVal;

	@Override
	protected Map<String, ?> getTaskVariables() {
		TreeMap<String, Object> result = ObjectUtils.newTreeMap(super.getTaskVariables());
		result.put("test.input", taskVal);
		return result;
	}

	@Override
	protected void runTestImpl() throws Throwable {
		Path bundleoutdir = getBuildDirectory().resolve("bundleout");
		Path workdir = getWorkingDirectory();

		TreeMap<String, Set<Class<?>>> bundleclasses = TestUtils.<String, Set<Class<?>>>treeMapBuilder()//
				.put("a-v1", ObjectUtils.newHashSet(ATask.class, ATask.AParamTaskImpl.class))//
				.put("b-v1", ObjectUtils.newHashSet(B1.class))//
				.put("c-v1", ObjectUtils.newHashSet(C1.class))//
				.put("c-v2", ObjectUtils.newHashSet(C2.class))//
				.build();

		parameters.setRepositoryConfiguration(NestExecutionTestUtils.createRepositoryConfiguration(testParameters));
		TreeMap<String, String> userparams = new TreeMap<>();
		userparams.put("nest.server.offline", "true");
		userparams.put("nest.params.bundles",
				NestIntegrationTestUtils.createParameterBundlesParameter(bundleclasses.keySet(), bundleoutdir));
		parameters.setUserParameters(userparams);

		NestIntegrationTestUtils.createAllJarsFromDirectoriesWithClasses(LocalFileProvider.getInstance(),
				SakerPath.valueOf(workdir).resolve("bundles"), bundleoutdir, bundleclasses);

		taskVal = "VAL";

		CombinedTargetTaskResult res;
		res = runScriptTask("build");
		assertEquals(res.getTargetTaskResult("a").getClass().getName(), B1.class.getName());

		res = runScriptTask("build");
		assertEmpty(getMetric().getRunTaskIdResults());
	}

}
