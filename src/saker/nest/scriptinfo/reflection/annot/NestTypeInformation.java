package saker.nest.scriptinfo.reflection.annot;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.thirdparty.saker.util.ReflectUtils;

/**
 * Annotation specifying scripting information about the annotated type.
 * <p>
 * The annotation contains fields that specify various aspects of a scripting type information.
 * <p>
 * The {@link NestFieldInformation} annotation can be used in conjunction with this in order to specify the
 * {@linkplain TypeInformation#getFields() field informations} of the type.
 * <p>
 * The {@link NestInformation} can be used to provide {@linkplain TypeInformation#getInformation() documentational
 * information} about the type.
 * <p>
 * Some informations like {@linkplain TypeInformation#getKind() kind} is determined by the referencing
 * {@link NestTypeUsage} annotation.
 * 
 * @see TypeInformation
 * @see NestFieldInformation
 * @see NestInformation
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface NestTypeInformation {
	/**
	 * Specifies the kind of the type.
	 * <p>
	 * The kind will be overriden by {@link NestTypeUsage#kind()} if set.
	 * <p>
	 * By default it will be determined based on the annotated type.
	 * 
	 * @return The kind.
	 * @see TypeInformation#getKind()
	 */
	public String kind() default "";

	/**
	 * Specifies the types which are related to the annotated type.
	 * 
	 * @return The related types.
	 * @see TypeInformation#getRelatedTypes()
	 */
	public NestTypeUsage[] relatedTypes() default {};

	/**
	 * Specifies the element types of the annotated type.
	 * <p>
	 * This is useful if the annotated type should be treated in a way similar to collections.
	 * <p>
	 * The element types specified in {@link NestTypeUsage#elementTypes()} will override this value if set.
	 * 
	 * @return The element types.
	 * @see TypeInformation#getElementTypes()
	 * @see NestTypeUsage#elementTypes()
	 * @see TypeInformationKind#MAP
	 * @see TypeInformationKind#COLLECTION
	 */
	public NestTypeUsage[] elementTypes() default {};

	/**
	 * Specifies the enumeration values of the type.
	 * <p>
	 * This field can be used to specify the {@linkplain TypeInformation#getEnumValues() enumeration values} in the
	 * annotated type.
	 * <p>
	 * If the annotated type is an <code>enum</code>, the it is recommended to place these annotations directly on the
	 * enumeration constants instead. Any annotation placed on the enumeration constant will overwrite the values in
	 * this field.
	 * <p>
	 * This field is useful when the annotated type is not actually an <code>enum</code>, but should be treated in an
	 * enumeration way for scripting purposes.
	 * <p>
	 * The {@link NestFieldInformation#type()} field in the specified field information annotations is <b>ignored</b>,
	 * and set to the annotated type information.
	 * 
	 * @return
	 * @see TypeInformation#getEnumValues()
	 */
	public NestFieldInformation[] enumValues() default {};

	/**
	 * Specifies the qualified name of the type that should be use when presented to the scripting runtime.
	 * <p>
	 * By default the {@linkplain Class#getCanonicalName() canonical name} of the annotated class is used.
	 * <p>
	 * If the specified qualified name starts with a dot (<code>.</code>), then the canonical name of the enclosing
	 * element of the annotated type will be prepended to it. (See
	 * {@link ReflectUtils#getEnclosingCanonicalNameOf(Class)}.)
	 * <p>
	 * The {@linkplain TypeInformation#getTypeSimpleName() simple name} of the type is determined by taking the name
	 * component after the last dot (<code>.</code>) character in the qualified name.
	 * 
	 * @return The qualified name for scripting.
	 * @see TypeInformation#getTypeQualifiedName()
	 * @see TypeInformation#getTypeSimpleName()
	 */
	public String qualifiedName() default "";
}
