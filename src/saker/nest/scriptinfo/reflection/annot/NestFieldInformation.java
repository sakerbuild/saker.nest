package saker.nest.scriptinfo.reflection.annot;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;

/**
 * Annotation to specify information about a field of the annotated type.
 * <p>
 * The annotated type will have the specified field information added to it. This results in the annotated type
 * declaring a field with the given name and information for scripting purposes.
 * <p>
 * The type is not required to actually declare a field with that name.
 * 
 * @see TypeInformation#getFields()
 * @see NestTypeInformation#enumValues()
 */
@Retention(RUNTIME)
@Target({ ElementType.TYPE })
@Repeatable(NestFieldContainer.class)
public @interface NestFieldInformation {
	/**
	 * Specifies the name of the field
	 * 
	 * @return The name.
	 * @see FieldInformation#getName()
	 */
	public String value();

	/**
	 * Specifies the type of the field.
	 * 
	 * @return The type declaration.
	 * @see FieldInformation#getType()
	 */
	public NestTypeUsage type() default @NestTypeUsage(kind = TypeInformationKind.OBJECT_LITERAL, value = Object.class);

	/**
	 * Specifies the documentation informations for the field.
	 * 
	 * @return The array of informations.
	 * @see FieldInformation#getInformation()
	 */
	public NestInformation[] info() default {};

	/**
	 * Specifies if the given field is deprecated.
	 * 
	 * @return <code>true</code> if the field is deprecated.
	 * @see FieldInformation#isDeprecated()
	 */
	public boolean deprecated() default false;
}
