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
package saker.nest.scriptinfo.reflection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestScriptingInfoInternalUtils;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;

public class ReflectionTaskInformation implements TaskInformation {
	private final TaskName taskName;
	private final Class<?> taskClass;
	private final NestTaskInformation taskInfoAnnot;

	private final transient LazySupplier<Collection<TaskParameterInformation>> parametersComputer = LazySupplier
			.of(this::computeParameters);
	private final transient LazySupplier<TypeInformation> returnTypeComputer = LazySupplier.of(this::computeReturnType);
	private final transient ReflectionInformationContext informationContext;

	public ReflectionTaskInformation(TaskName taskName, Class<?> taskClass,
			ReflectionInformationContext informationcontext) {
		this.taskName = taskName;
		this.taskClass = taskClass;
		this.informationContext = informationcontext;
		this.taskInfoAnnot = taskClass == null ? null : taskClass.getAnnotation(NestTaskInformation.class);
	}

	@Override
	public TaskName getTaskName() {
		return taskName;
	}

	@Override
	public TypeInformation getReturnType() {
		return returnTypeComputer.get();
	}

	private TypeInformation computeReturnType() {
		NestTaskInformation annot = taskInfoAnnot;
		if (annot == null) {
			return null;
		}
		return informationContext.getTypeInformation(annot.returnType());
	}

	@Override
	public FormattedTextContent getInformation() {
		if (taskClass == null) {
			return null;
		}
		return ReflectionExternalScriptInformationProvider
				.toFormattedTextContent(taskClass.getAnnotationsByType(NestInformation.class));
	}

	@Override
	public Collection<? extends TaskParameterInformation> getParameters() {
		return parametersComputer.get();
	}

	private Collection<TaskParameterInformation> computeParameters() {
		ArrayList<TaskParameterInformation> result = new ArrayList<>();
		Class<?>[] fieldinclusiontype = NestScriptingInfoInternalUtils.getIncludeFieldsAsParametersFrom(taskInfoAnnot);
		if (!ObjectUtils.isNullOrEmpty(fieldinclusiontype)) {
			for (Class<?> inctype : fieldinclusiontype) {
				Map<String, FieldInformation> fields = ReflectionTypeInformation
						.getFieldInformationsFromTypeClass(informationContext, inctype);
				if (!ObjectUtils.isNullOrEmpty(fields)) {
					//XXX supertypes?
					for (Entry<String, FieldInformation> entry : fields.entrySet()) {
						result.add(informationContext.forwardFieldAsParameter(this, entry.getValue()));
					}
				}
			}
		}
		if (taskClass != null) {
			NestParameterInformation[] params = taskClass.getAnnotationsByType(NestParameterInformation.class);
			for (NestParameterInformation pinfo : params) {
				result.add(informationContext.getTaskParameterInformation(this, pinfo));
			}
		}
		return ImmutableUtils.unmodifiableList(result);
	}

	@Override
	public boolean isDeprecated() {
		return taskClass.isAnnotationPresent(Deprecated.class);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((taskClass == null) ? 0 : taskClass.hashCode());
		result = prime * result + ((taskInfoAnnot == null) ? 0 : taskInfoAnnot.hashCode());
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
		ReflectionTaskInformation other = (ReflectionTaskInformation) obj;
		if (taskClass == null) {
			if (other.taskClass != null)
				return false;
		} else if (!taskClass.equals(other.taskClass))
			return false;
		if (taskInfoAnnot == null) {
			if (other.taskInfoAnnot != null)
				return false;
		} else if (!taskInfoAnnot.equals(other.taskInfoAnnot))
			return false;
		if (taskName == null) {
			if (other.taskName != null)
				return false;
		} else if (!taskName.equals(other.taskName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ReflectionTaskInformation[" + (taskName != null ? "taskName=" + taskName + ", " : "")
				+ (taskClass != null ? "taskClass=" + taskClass + ", " : "")
				+ (taskInfoAnnot != null ? "taskInfoAnnot=" + taskInfoAnnot : "") + "]";
	}

}
