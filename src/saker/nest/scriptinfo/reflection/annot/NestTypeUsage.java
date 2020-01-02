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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import saker.build.scripting.model.info.TypeInformation;

/**
 * Annotation for referencing an used type in regards of scripting information.
 * <p>
 * This annotation cannot be used to directly annotate elements, but only by other information annotations to reference
 * other types.
 * <p>
 * As the Java annotation facility doesn't allow nesting annotations of the same type, this annotation needed to be
 * introduced in order to reference different types.
 * <p>
 * The annotation can be used to override the {@link #kind()} and {@link #elementTypes()} of the referenced type.
 * 
 * @see TypeInformation
 */
@Retention(RUNTIME)
@Target({})
public @interface NestTypeUsage {
	/**
	 * Specifies the referenced type.
	 * 
	 * @return The type that is referenced.
	 */
	public Class<?> value();

	/**
	 * Specifies the kind of the type that is referenced.
	 * <p>
	 * This will override the kind in {@link NestTypeInformation#kind()} if specified.
	 * 
	 * @return The kind.
	 * @see TypeInformation#getKind()
	 */
	public String kind() default "";

	/**
	 * Specifies the element types of the referenced type.
	 * <p>
	 * This will override the element types in {@link NestTypeInformation#elementTypes()} if specified.
	 * 
	 * @return The element types.
	 * @see TypeInformation#getElementTypes()
	 */
	public Class<?>[] elementTypes() default { TypeUnspecified.class };
}
