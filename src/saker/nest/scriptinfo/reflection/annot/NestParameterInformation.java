package saker.nest.scriptinfo.reflection.annot;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.bundle.BundleInformation;

/**
 * Annotation to be placed on tasks to provide information about input parameters.
 * <p>
 * Any class that is declared to be contained in a bundle in the {@linkplain BundleInformation#ENTRY_BUNDLE_TASKS task
 * declarations entry file} can be annotated with {@link NestParameterInformation}. The fields in the annotation is used
 * to convey information about a given task parameter.
 * 
 * @see TaskParameterInformation
 */
@Retention(RUNTIME)
@Target({ ElementType.TYPE })
@Repeatable(NestParameterContainer.class)
public @interface NestParameterInformation {
	/**
	 * Specifies the name of the parameter.
	 * 
	 * @return The name.
	 * @see TaskParameterInformation#getParameterName()
	 */
	public String value();

	/**
	 * Specifies the alias names of the parameter.
	 * 
	 * @return The aliases.
	 * @see TaskParameterInformation#getAliases()
	 */
	public String[] aliases() default {};

	/**
	 * Specifies the documentational informations about the task parameter.
	 * 
	 * @return The informations.
	 * @see TaskParameterInformation#getInformation()
	 */
	public NestInformation[] info() default {};

	/**
	 * Specifies if the parameter is required.
	 * 
	 * @return <code>true</code> if required.
	 * @see TaskParameterInformation#isRequired()
	 */
	public boolean required() default false;

	/**
	 * Specifies if the parameter is deprecated.
	 * 
	 * @return <code>true</code> if deprecated.
	 * @see TaskParameterInformation#isDeprecated()
	 */
	public boolean deprecated() default false;

	/**
	 * Specifies the type information of the parameter.
	 * 
	 * @return The type information.
	 * @see TaskParameterInformation#getTypeInformation()
	 */
	public NestTypeUsage type() default @NestTypeUsage(kind = TypeInformationKind.OBJECT_LITERAL, value = Object.class);

}
