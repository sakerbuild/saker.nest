package saker.nest.scriptinfo.reflection;

import java.util.Collections;
import java.util.Set;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;

public class FieldForwardingTaskParameterInformation implements TaskParameterInformation {
	private final FieldInformation field;
	private final TaskInformation task;

	public FieldForwardingTaskParameterInformation(TaskInformation task, FieldInformation field) {
		this.task = task;
		this.field = field;
	}

	@Override
	public TaskInformation getTask() {
		return task;
	}

	@Override
	public String getParameterName() {
		return field.getName();
	}

	@Override
	public boolean isRequired() {
		return false;
	}

	@Override
	public Set<String> getAliases() {
		return Collections.emptyNavigableSet();
	}

	@Override
	public FormattedTextContent getInformation() {
		return field.getInformation();
	}

	@Override
	public TypeInformation getTypeInformation() {
		return field.getType();
	}

	@Override
	public boolean isDeprecated() {
		return field.isDeprecated();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((field == null) ? 0 : field.hashCode());
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
		FieldForwardingTaskParameterInformation other = (FieldForwardingTaskParameterInformation) obj;
		if (field == null) {
			if (other.field != null)
				return false;
		} else if (!field.equals(other.field))
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
		return getClass().getSimpleName() + "[" + (field != null ? "field=" + field : "") + "]";
	}

}
