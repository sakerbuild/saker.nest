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
package saker.nest.scriptinfo.reflection.annot;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.bundle.BundleInformation;

/**
 * Annotation to be placed on tasks used with the Nest repository.
 * <p>
 * Any class that is declared to be contained in a bundle in the {@linkplain BundleInformation#ENTRY_BUNDLE_TASKS task
 * declarations entry file} can be annotated with {@link NestTaskInformation}. The field(s) in the annotation can be
 * used to specify information about the task.
 * <p>
 * The annotation {@link NestParameterInformation} can be used to specify the parameters which are recognized by the
 * task. See {@link TaskInformation#getParameters()}.
 * <p>
 * The annotation {@link NestInformation} can be used to provide documentational information about the task. See
 * {@link TaskInformation#getInformation()}.
 * <p>
 * The {@linkplain TaskInformation#getTaskName() task name} of the task will be automatically determined by the
 * repository. See {@link TaskInformation#getTaskName()}.
 * <p>
 * The {@linkplain TaskInformation#isDeprecated() deprecation} is determined by checking if the task has the
 * {@link Deprecated} annotation as well. See {@link TaskInformation#isDeprecated()}.
 * <p>
 * The annotations should be placed on the class that is declared as the task class in the task declarations entry file.
 * 
 * @see TaskInformation
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface NestTaskInformation {
	/**
	 * Specifies the return type of the task.
	 * 
	 * @return The return type.
	 * @see TaskInformation#getReturnType()
	 */
	public NestTypeUsage returnType() default @NestTypeUsage(kind = TypeInformationKind.OBJECT_LITERAL,
			value = Object.class);

	/**
	 * Specifies a type from which the fields should be included as parameter informations to the task.
	 * <p>
	 * All the fields from the specified classes will be included as parameter informations to the annotated task.
	 * <p>
	 * Specifying this can be useful when a task has similar parameters as some other class has fields.
	 * 
	 * @return The types to include the field informations from.
	 * @see NestFieldInformation
	 * @see NestParameterInformation
	 */
	public Class<?>[] includeFieldsAsParametersFrom() default {};
}
