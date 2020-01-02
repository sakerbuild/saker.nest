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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.LiteralInformation;
import saker.build.scripting.model.info.SimpleTypeInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.NestBuildRepositoryImpl;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestScriptingInfoInternalUtils;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;

public class ReflectionInformationContext {
	private static final NestTypeUsage[] EMPTY_NEST_TYPE_USAGE_ARRAY = new NestTypeUsage[0];
	public static final String BUNDLEIDENTIFIER_CANONICAL_NAME = BundleIdentifier.class.getCanonicalName();
	private static final SimpleTypeInformation BUNDLE_IDENTIFIER_TYPE_INFORMATION = new SimpleTypeInformation(
			TypeInformationKind.LITERAL);
	static {
		BUNDLE_IDENTIFIER_TYPE_INFORMATION.setTypeSimpleName(BundleIdentifier.class.getSimpleName());
		BUNDLE_IDENTIFIER_TYPE_INFORMATION.setTypeQualifiedName(BUNDLEIDENTIFIER_CANONICAL_NAME);
	}

	private final NestBuildRepositoryImpl repository;
	private final ConcurrentSkipListMap<BundleIdentifier, LiteralInformation> bundleInformations = new ConcurrentSkipListMap<>();
	private final ConcurrentHashMap<TypeInformationKey, TypeInformation> typeInformations = new ConcurrentHashMap<>();

	public ReflectionInformationContext(NestBuildRepositoryImpl repository) {
		this.repository = repository;
	}

	public NestBuildRepositoryImpl getRepository() {
		return repository;
	}

	public TypeInformation getTypeInformation(Class<?> typeclass) {
		TypeInformation priminfo = PrimitiveTypeInformation.get(typeclass);
		if (priminfo != null) {
			return priminfo;
		}
		NestTypeInformation typeinfoannot = typeclass == null ? null
				: typeclass.getAnnotation(NestTypeInformation.class);
		List<TypeInformation> elemtypeinfos = new ArrayList<>();
		String kind = typeinfoannot == null ? null : typeinfoannot.kind();
		NestTypeUsage[] elemtypes = typeinfoannot == null ? EMPTY_NEST_TYPE_USAGE_ARRAY : typeinfoannot.elementTypes();
		ReflectionTypeInformation result = new ReflectionTypeInformation(this, typeclass, typeinfoannot, kind,
				elemtypeinfos);

		TypeInformation prev = typeInformations.putIfAbsent(new TypeInformationKey(typeclass, kind, elemtypes), result);
		if (prev != null) {
			return prev;
		}

		for (NestTypeUsage ec : elemtypes) {
			elemtypeinfos.add(getTypeInformation(ec));
		}
		return result;
	}

	public TypeInformation getTypeInformation(NestTypeUsage type) {
		if (type == null) {
			return null;
		}
		Class<?> typeclass = type.value();
		TypeInformation priminfo = PrimitiveTypeInformation.get(typeclass);
		if (priminfo != null) {
			return priminfo;
		}
		NestTypeInformation typeinfoannot = typeclass == null ? null
				: typeclass.getAnnotation(NestTypeInformation.class);

		Class<?>[] elementTypes = NestScriptingInfoInternalUtils.getElementTypesIfSpecified(type);

		String kind = type.kind();
		if (ObjectUtils.isNullOrEmpty(kind)) {
			if (typeinfoannot != null) {
				kind = typeinfoannot.kind();
			}
		}

		List<TypeInformation> elemtypeinfos = new ArrayList<>();
		ReflectionTypeInformation result = new ReflectionTypeInformation(this, typeclass, typeinfoannot, kind,
				elemtypeinfos);

		if (elementTypes == null) {
			if (typeinfoannot != null) {
				NestTypeUsage[] usageelemtypes = typeinfoannot.elementTypes();
				TypeInformation prev = typeInformations
						.putIfAbsent(new TypeInformationKey(typeclass, kind, usageelemtypes), result);
				if (prev != null) {
					return prev;
				}
				for (NestTypeUsage ec : usageelemtypes) {
					elemtypeinfos.add(getTypeInformation(ec));
				}
			} else {
				TypeInformation prev = typeInformations
						.putIfAbsent(new TypeInformationKey(typeclass, kind, EMPTY_NEST_TYPE_USAGE_ARRAY), result);
				if (prev != null) {
					return prev;
				}
			}
		} else {
			TypeInformation prev = typeInformations.putIfAbsent(new TypeInformationKey(typeclass, kind, elementTypes),
					result);
			if (prev != null) {
				return prev;
			}
			for (Class<?> ec : elementTypes) {
				elemtypeinfos.add(getTypeInformation(ec));
			}
		}

		return result;
	}

	public TaskInformation getTaskInformation(TaskName taskname) {
		//can return new, it implements equals
		return new LazyReflectionTaskInformation(taskname, this);
	}

	public TaskParameterInformation getTaskParameterInformation(ReflectionTaskInformation taskinfo,
			NestParameterInformation pinfo) {
		return new ReflectionTaskParameterInformation(taskinfo, pinfo, this);
	}

	public TaskParameterInformation forwardFieldAsParameter(TaskInformation taskinfo, FieldInformation field) {
		return new FieldForwardingTaskParameterInformation(taskinfo, field);
	}

	public LiteralInformation getBundleLiteralInformation(BundleIdentifier bundleid) {
		return bundleInformations.computeIfAbsent(bundleid, BundleIdentifierLiteralInformation::new);
	}

	private static final class BundleIdentifierLiteralInformation implements LiteralInformation {
		private final BundleIdentifier bundleid;

		BundleIdentifierLiteralInformation(BundleIdentifier bundleid) {
			this.bundleid = bundleid;
		}

		@Override
		public String getLiteral() {
			return bundleid.toString();
		}

		@Override
		public TypeInformation getType() {
			return BUNDLE_IDENTIFIER_TYPE_INFORMATION;
		}

		@Override
		public FormattedTextContent getInformation() {
			// XXX get bundle information
			return LiteralInformation.super.getInformation();
		}

		@Override
		public String getRelation() {
			return "Nest bundle";
		}

		@Override
		public boolean isDeprecated() {
			// XXX check if deprecated
			return LiteralInformation.super.isDeprecated();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((bundleid == null) ? 0 : bundleid.hashCode());
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
			BundleIdentifierLiteralInformation other = (BundleIdentifierLiteralInformation) obj;
			if (bundleid == null) {
				if (other.bundleid != null)
					return false;
			} else if (!bundleid.equals(other.bundleid))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "BundleIdentifierLiteralInformation[" + (bundleid != null ? "bundleid=" + bundleid : "") + "]";
		}
	}

	private static class TypeInformationKey {
		private final Class<?> typeClass;
		private final String kind;
		private final Object[] elementTypes;

		public TypeInformationKey(Class<?> typeClass, String kind, Class<?>[] elementTypes) {
			this.typeClass = typeClass;
			this.kind = kind;
			this.elementTypes = elementTypes;
		}

		public TypeInformationKey(Class<?> typeClass, String kind, NestTypeUsage[] elementTypes) {
			this.typeClass = typeClass;
			this.kind = kind;
			this.elementTypes = elementTypes;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + Arrays.hashCode(elementTypes);
			result = prime * result + ((kind == null) ? 0 : kind.hashCode());
			result = prime * result + ((typeClass == null) ? 0 : typeClass.hashCode());
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
			TypeInformationKey other = (TypeInformationKey) obj;
			if (!Arrays.equals(elementTypes, other.elementTypes))
				return false;
			if (kind == null) {
				if (other.kind != null)
					return false;
			} else if (!kind.equals(other.kind))
				return false;
			if (typeClass == null) {
				if (other.typeClass != null)
					return false;
			} else if (!typeClass.equals(other.typeClass))
				return false;
			return true;
		}
	}

}
