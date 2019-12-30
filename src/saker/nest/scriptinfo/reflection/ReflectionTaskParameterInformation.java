package saker.nest.scriptinfo.reflection;

import java.util.Set;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;

public class ReflectionTaskParameterInformation implements TaskParameterInformation {
	private TaskInformation task;
	private NestParameterInformation info;

	private final transient LazySupplier<TypeInformation> typeComputer = LazySupplier.of(this::computeTypeInformation);
	private final transient ReflectionInformationContext informationContext;

	public ReflectionTaskParameterInformation(TaskInformation task, NestParameterInformation info,
			ReflectionInformationContext informationContext) {
		this.task = task;
		this.info = info;
		this.informationContext = informationContext;
	}

	@Override
	public TaskInformation getTask() {
		return task;
	}

	@Override
	public String getParameterName() {
		return info == null ? null : info.value();
	}

	@Override
	public boolean isRequired() {
		return info == null ? false : info.required();
	}

	@Override
	public Set<String> getAliases() {
		return ImmutableUtils.makeImmutableNavigableSet(info.aliases());
	}

	@Override
	public FormattedTextContent getInformation() {
		return info == null ? null : ReflectionExternalScriptInformationProvider.toFormattedTextContent(info.info());
	}

	@Override
	public TypeInformation getTypeInformation() {
		return typeComputer.get();
	}

	private TypeInformation computeTypeInformation() {
		return info == null ? null : informationContext.getTypeInformation(info.type());
	}

	@Override
	public boolean isDeprecated() {
		return info == null ? false : info.deprecated();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((info == null) ? 0 : info.hashCode());
		result = prime * result + ((task == null) ? 0 : task.hashCode());
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
		ReflectionTaskParameterInformation other = (ReflectionTaskParameterInformation) obj;
		if (info == null) {
			if (other.info != null)
				return false;
		} else if (!info.equals(other.info))
			return false;
		if (task == null) {
			if (other.task != null)
				return false;
		} else if (!task.equals(other.task))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ReflectionTaskParameterInformation[" + (info != null ? "info=" + info : "") + "]";
	}

}
