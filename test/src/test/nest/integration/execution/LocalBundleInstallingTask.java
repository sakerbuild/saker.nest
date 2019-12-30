package test.nest.integration.execution;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Base64;
import java.util.Collection;

import saker.build.file.StreamWritable;
import saker.build.runtime.execution.ExecutionContext;
import saker.build.task.ParameterizableTask;
import saker.build.task.Task;
import saker.build.task.TaskContext;
import saker.build.task.TaskFactory;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.util.property.UserParameterExecutionProperty;
import saker.nest.bundle.NestBundleClassLoader;
import saker.nest.bundle.storage.LocalBundleStorageView;

public class LocalBundleInstallingTask implements TaskFactory<String>, ParameterizableTask<String>, Externalizable {
	private static final long serialVersionUID = 1L;

	public static final String EXPORT_JAR_BASE64_USER_PARAMETER = "export.jar.base64";

	public LocalBundleInstallingTask() {
	}

	@Override
	public String run(TaskContext taskcontext) throws Exception {
		byte[] decoded = Base64.getDecoder().decode(taskcontext.getTaskUtilities()
				.getReportExecutionDependency(new UserParameterExecutionProperty(EXPORT_JAR_BASE64_USER_PARAMETER)));
		StreamWritable exporter = os -> os.write(decoded);
		NestBundleClassLoader cl = ((NestBundleClassLoader) this.getClass().getClassLoader());
		Collection<? extends LocalBundleStorageView> localstorages = cl.getBundleStorageConfiguration()
				.getLocalStorages().values();
		if (localstorages.size() != 1) {
			throw new AssertionError(
					"Invalid number of local storages: " + localstorages.size() + " - " + localstorages);
		}
		localstorages.iterator().next().install(exporter);
		System.out.println("LocalBundleChangeTaskTest.ExportingTask.run() " + cl.getBundle().getStorage());
		return "export";
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