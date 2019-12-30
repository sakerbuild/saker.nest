package saker.nest.scriptinfo.reflection.annot;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Container annotation for the repeatable {@link NestInformation} annotation.
 */
@Retention(RUNTIME)
@Target({ ElementType.FIELD, ElementType.TYPE })
public @interface NestInformationContainer {
	/**
	 * @return The contained annotations.
	 */
	public NestInformation[] value();
}
