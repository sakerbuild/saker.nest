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

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.SimpleFieldInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.function.LazySupplier;
import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;

public class ReflectionTypeInformation implements TypeInformation {
	private static final Map<Class<?>, String> DEFAULT_TYPE_INFORMATION_KINDS = new HashMap<>();
	static {
		DEFAULT_TYPE_INFORMATION_KINDS.put(String.class, TypeInformationKind.STRING);
		DEFAULT_TYPE_INFORMATION_KINDS.put(CharSequence.class, TypeInformationKind.STRING);

		DEFAULT_TYPE_INFORMATION_KINDS.put(Byte.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Short.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Integer.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Long.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Float.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Double.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Number.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Void.class, TypeInformationKind.VOID);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Boolean.class, TypeInformationKind.BOOLEAN);

		DEFAULT_TYPE_INFORMATION_KINDS.put(byte.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(short.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(int.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(long.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(float.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(double.class, TypeInformationKind.NUMBER);
		DEFAULT_TYPE_INFORMATION_KINDS.put(void.class, TypeInformationKind.VOID);
		DEFAULT_TYPE_INFORMATION_KINDS.put(boolean.class, TypeInformationKind.BOOLEAN);

		DEFAULT_TYPE_INFORMATION_KINDS.put(Map.class, TypeInformationKind.MAP);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Collection.class, TypeInformationKind.COLLECTION);
		DEFAULT_TYPE_INFORMATION_KINDS.put(List.class, TypeInformationKind.COLLECTION);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Set.class, TypeInformationKind.COLLECTION);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Iterable.class, TypeInformationKind.COLLECTION);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Object.class, TypeInformationKind.OBJECT);

		DEFAULT_TYPE_INFORMATION_KINDS.put(File.class, TypeInformationKind.PATH);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Path.class, TypeInformationKind.PATH);
		DEFAULT_TYPE_INFORMATION_KINDS.put(SakerPath.class, TypeInformationKind.PATH);
		DEFAULT_TYPE_INFORMATION_KINDS.put(WildcardPath.class, TypeInformationKind.WILDCARD_PATH);
		DEFAULT_TYPE_INFORMATION_KINDS.put(Enum.class, TypeInformationKind.ENUM);
	}
	private transient final ReflectionInformationContext informationContext;
	private final Class<?> typeClass;
	private final NestTypeInformation infoAnnot;
	private final String kind;
	private final List<TypeInformation> elemTypeInfos;

	private final transient LazySupplier<Map<String, FieldInformation>> fieldsComputer = LazySupplier
			.of(this::computeFields);
	private final transient LazySupplier<Map<String, FieldInformation>> enumValuesComputer = LazySupplier
			.of(this::computeEnumValues);
	private final transient LazySupplier<Set<TypeInformation>> superTypesComputer = LazySupplier
			.of(this::computeSuperTypes);
	private final transient LazySupplier<Set<TypeInformation>> relatedTypesComputer = LazySupplier
			.of(this::computeRelatedTypes);

	public ReflectionTypeInformation(ReflectionInformationContext informationContext, Class<?> typeclass,
			NestTypeInformation typeinfoannot, String kind, List<TypeInformation> elemtypeinfos) {
		this.informationContext = informationContext;
		this.typeClass = typeclass;
		this.infoAnnot = typeinfoannot;
		if (ObjectUtils.isNullOrEmpty(kind) && typeclass != null) {
			kind = DEFAULT_TYPE_INFORMATION_KINDS.get(typeclass);
			if (kind == null) {
				if (typeclass.isEnum()) {
					kind = TypeInformationKind.ENUM;
				} else {
					kind = TypeInformationKind.OBJECT;
				}
			}
		}
		this.kind = kind;
		this.elemTypeInfos = ImmutableUtils.unmodifiableList(elemtypeinfos);
	}

	@Override
	public String getKind() {
		return kind;
	}

	@Override
	public String getTypeQualifiedName() {
		if (infoAnnot != null) {
			String qname = infoAnnot.qualifiedName();
			if (!qname.isEmpty()) {
				if (qname.startsWith(".")) {
					String encname = ReflectUtils.getEnclosingCanonicalNameOf(typeClass);
					if (encname == null) {
						return qname.substring(1);
					}
					return encname + qname;
				}
				return qname;
			}
		}
		if (typeClass == null) {
			return null;
		}
		return typeClass.getCanonicalName();
	}

	@Override
	public String getTypeSimpleName() {
		if (infoAnnot != null) {
			String qname = infoAnnot.qualifiedName();
			if (!qname.isEmpty()) {
				if (qname.startsWith(".")) {
					return qname.substring(1);
				}
				return qname.substring(qname.lastIndexOf('.') + 1);
			}
		}
		if (typeClass == null) {
			return null;
		}
		return typeClass.getSimpleName();
	}

	@Override
	public Map<String, FieldInformation> getFields() {
		return fieldsComputer.get();
	}

	private Map<String, FieldInformation> computeFields() {
		return getFieldInformationsFromTypeClass(informationContext, this.typeClass);
	}

	public static Map<String, FieldInformation> getFieldInformationsFromTypeClass(
			ReflectionInformationContext informationContext, Class<?> typeclass) {
		if (typeclass == null) {
			return Collections.emptyMap();
		}
		TreeMap<String, FieldInformation> result = new TreeMap<>();
		for (NestFieldInformation f : typeclass.getAnnotationsByType(NestFieldInformation.class)) {
			SimpleFieldInformation fieldinfo = new SimpleFieldInformation(f.value());
			fieldinfo.setType(informationContext.getTypeInformation(f.type()));
			fieldinfo.setInformation(ReflectionExternalScriptInformationProvider.toFormattedTextContent(f.info()));
			fieldinfo.setDeprecated(f.deprecated());
			result.put(f.value(), fieldinfo);
		}
		return result;
	}

	@Override
	public Map<String, FieldInformation> getEnumValues() {
		return enumValuesComputer.get();
	}

	private Map<String, FieldInformation> computeEnumValues() {
		LinkedHashMap<String, FieldInformation> result = new LinkedHashMap<>();

		if (infoAnnot != null) {
			NestFieldInformation[] enumvalueannots = infoAnnot.enumValues();
			for (NestFieldInformation enuminfo : enumvalueannots) {
				String enuminfoname = enuminfo.value();
				if (result.containsKey(enuminfoname)) {
					continue;
				}
				SimpleFieldInformation fieldinfo = new SimpleFieldInformation(enuminfoname);
				fieldinfo.setType(this);
				fieldinfo.setInformation(
						ReflectionExternalScriptInformationProvider.toFormattedTextContent(enuminfo.info()));
				result.putIfAbsent(enuminfoname, fieldinfo);
			}
		}
		if (typeClass != null) {
			Object[] enumvals = typeClass.getEnumConstants();
			if (!ObjectUtils.isNullOrEmpty(enumvals)) {
				for (Object ev : enumvals) {
					Enum<?> en = (Enum<?>) ev;
					String enuminfoname = en.name();
					if (result.containsKey(enuminfoname)) {
						continue;
					}

					SimpleFieldInformation fieldinfo = new SimpleFieldInformation(enuminfoname);
					fieldinfo.setType(this);
					try {
						Field f = typeClass.getDeclaredField(enuminfoname);
						fieldinfo.setInformation(ReflectionExternalScriptInformationProvider
								.toFormattedTextContent(f.getAnnotationsByType(NestInformation.class)));
						fieldinfo.setDeprecated(f.isAnnotationPresent(Deprecated.class));
					} catch (NoSuchFieldException | SecurityException e) {
						//shouldn't really happen
					}
					result.putIfAbsent(enuminfoname, fieldinfo);
				}
			}
		}

		return ImmutableUtils.unmodifiableMap(result);
	}

	@Override
	public Set<TypeInformation> getSuperTypes() {
		return superTypesComputer.get();
	}

	private Set<TypeInformation> computeSuperTypes() {
		if (typeClass == null) {
			return Collections.emptySet();
		}
		Set<TypeInformation> result = new LinkedHashSet<>();
		Class<?> superc = typeClass.getSuperclass();
		if (superc != null && !(superc == Object.class || superc == Enum.class)) {
			//shouldn't care about Object, or Enum supertypes
			result.add(informationContext.getTypeInformation(superc));
		}
		for (Class<?> itf : typeClass.getInterfaces()) {
			if (itf == Annotation.class) {
				continue;
			}
			result.add(informationContext.getTypeInformation(itf));
		}
		return ImmutableUtils.unmodifiableSet(result);
	}

	@Override
	public FormattedTextContent getInformation() {
		if (typeClass == null) {
			return null;
		}
		return ReflectionExternalScriptInformationProvider
				.toFormattedTextContent(typeClass.getAnnotationsByType(NestInformation.class));
	}

	@Override
	public Set<TypeInformation> getRelatedTypes() {
		return relatedTypesComputer.get();
	}

	private Set<TypeInformation> computeRelatedTypes() {
		if (infoAnnot == null) {
			return Collections.emptySet();
		}
		Set<TypeInformation> result = new LinkedHashSet<>();
		for (NestTypeUsage ec : infoAnnot.relatedTypes()) {
			result.add(informationContext.getTypeInformation(ec));
		}
		return ImmutableUtils.unmodifiableSet(result);
	}

	@Override
	public boolean isDeprecated() {
		return typeClass != null && typeClass.isAnnotationPresent(Deprecated.class);
	}

	@Override
	public List<? extends TypeInformation> getElementTypes() {
		return elemTypeInfos;
	}

	@Override
	public String toString() {
		return "ReflectionTypeInformation[" + (typeClass != null ? "typeClass=" + typeClass + ", " : "")
				+ (infoAnnot != null ? "infoAnnot=" + infoAnnot + ", " : "")
				+ (kind != null ? "kind=" + kind + ", " : "")
				+ (elemTypeInfos != null ? "elemTypeInfos=" + elemTypeInfos : "") + "]";
	}

}
