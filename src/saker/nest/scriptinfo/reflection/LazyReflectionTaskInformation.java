package saker.nest.scriptinfo.reflection;

import java.util.Collection;
import java.util.function.Supplier;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.nest.NestBuildRepositoryImpl;

public class LazyReflectionTaskInformation implements TaskInformation {
	private TaskName taskName;
	private transient Supplier<? extends TaskInformation> infoSupplier;

	public LazyReflectionTaskInformation(TaskName taskname, ReflectionInformationContext informationcontext) {
		this.taskName = taskname;
		this.infoSupplier = () -> {
			NestBuildRepositoryImpl repo = informationcontext.getRepository();
			if (repo == null) {
				infoSupplier = Functionals.nullSupplier();
				return null;
			}
			Class<?> taskclass = repo.getTaskClassForInformationProvider(taskname);
			if (taskclass == null) {
				return null;
			}
			ReflectionTaskInformation result = new ReflectionTaskInformation(taskname, taskclass, informationcontext);
			infoSupplier = Functionals.valSupplier(result);
			return result;
		};
	}

	@Override
	public TaskName getTaskName() {
		return taskName;
	}

	@Override
	public TypeInformation getReturnType() {
		TaskInformation info = infoSupplier.get();
		if (info != null) {
			return info.getReturnType();
		}
		return null;
	}

	@Override
	public FormattedTextContent getInformation() {
		TaskInformation info = infoSupplier.get();
		if (info != null) {
			return info.getInformation();
		}
		return null;
	}

	@Override
	public Collection<? extends TaskParameterInformation> getParameters() {
		TaskInformation info = infoSupplier.get();
		if (info != null) {
			return info.getParameters();
		}
		return null;
	}

	@Override
	public boolean isDeprecated() {
		TaskInformation info = infoSupplier.get();
		if (info != null) {
			return info.isDeprecated();
		}
		return false;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskName == null) ? 0 : taskName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LazyReflectionTaskInformation other = (LazyReflectionTaskInformation) obj;
		if (taskName == null) {
			if (other.taskName != null)
				return false;
		} else if (!taskName.equals(other.taskName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + taskName + "]";
	}
}
