package saker.nest.scriptinfo.reflection.annot;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Container annotation for the repeatable {@link NestFieldInformation} annotation.
 */
@Retention(RUNTIME)
@Target({ TYPE })
public @interface NestFieldContainer {
	/**
	 * @return The contained annotations.
	 */
	public NestFieldInformation[] value();
}
