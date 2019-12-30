package saker.nest.scriptinfo.reflection.annot;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.nest.bundle.BundleInformation;

/**
 * Annotation specifying a documentational information for the given element.
 * <p>
 * The annotation is used to provide documentational information about the element that it is placed on. The annotation
 * contains a {@linkplain #value() value} that is the content of the documentation and a {@linkplain #format() format
 * type} that specifies how the value should be interpreted.
 * <p>
 * The annotation can be placed on types, or enumeration fields. In both cases the specified information is associated
 * with the given element. The annotation cannot be used on fields which are not <code>enum</code> constants. (It
 * doesn't produce a compile error, but simply won't work.)
 * <p>
 * When the annotation is placed on a class that is declared as a {@linkplain BundleInformation#ENTRY_BUNDLE_TASKS build
 * system task}, the information will be associated with the task.
 * 
 * @see TaskInformation#getInformation()
 * @see TypeInformation#getInformation()
 * @see FieldInformation#getInformation()
 * @see TaskParameterInformation#getInformation()
 * @see FormattedTextContent
 * @see NestFieldInformation
 */
@Retention(RUNTIME)
@Target({ ElementType.FIELD, ElementType.TYPE })
@Repeatable(NestInformationContainer.class)
public @interface NestInformation {
	/**
	 * The contents of the documentational information.
	 * 
	 * @return The information.
	 * @see FormattedTextContent#getFormattedText(String)
	 */
	public String value();

	/**
	 * The format of the information specified in {@link #value()}.
	 * <p>
	 * Must be one of the <code>FORMAT_*</code> constants in {@link FormattedTextContent}.
	 * 
	 * @return The format.
	 * @see FormattedTextContent#getAvailableFormats()
	 */
	public String format() default FormattedTextContent.FORMAT_PLAINTEXT;
}
