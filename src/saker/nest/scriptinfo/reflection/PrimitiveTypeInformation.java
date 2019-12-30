package saker.nest.scriptinfo.reflection;

import java.util.HashMap;
import java.util.Map;

import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.build.thirdparty.saker.util.ReflectUtils;

class PrimitiveTypeInformation implements TypeInformation {
	private transient String kind;
	private Class<?> clazz;

	private PrimitiveTypeInformation(String kind, Class<?> clazz) {
		this.kind = kind;
		this.clazz = clazz;
	}

	private static final Map<Class<?>, TypeInformation> PRIMITIVE_INFOS = new HashMap<>();
	static {
		PrimitiveTypeInformation voidtype = new PrimitiveTypeInformation(TypeInformationKind.VOID, Void.class);
		PrimitiveTypeInformation bytetype = new PrimitiveTypeInformation(TypeInformationKind.NUMBER, Byte.class);
		PrimitiveTypeInformation shorttype = new PrimitiveTypeInformation(TypeInformationKind.NUMBER, Short.class);
		PrimitiveTypeInformation inttype = new PrimitiveTypeInformation(TypeInformationKind.NUMBER, Integer.class);
		PrimitiveTypeInformation longtype = new PrimitiveTypeInformation(TypeInformationKind.NUMBER, Long.class);
		PrimitiveTypeInformation floattype = new PrimitiveTypeInformation(TypeInformationKind.NUMBER, Float.class);
		PrimitiveTypeInformation doubletype = new PrimitiveTypeInformation(TypeInformationKind.NUMBER, Double.class);
		PrimitiveTypeInformation booleantype = new PrimitiveTypeInformation(TypeInformationKind.BOOLEAN, Boolean.class);
		PrimitiveTypeInformation chartype = new PrimitiveTypeInformation(TypeInformationKind.STRING, Character.class);
		PrimitiveTypeInformation stringtype = new PrimitiveTypeInformation(TypeInformationKind.STRING, String.class);

		PRIMITIVE_INFOS.put(Void.class, voidtype);
		PRIMITIVE_INFOS.put(void.class, voidtype);

		PRIMITIVE_INFOS.put(Byte.class, bytetype);
		PRIMITIVE_INFOS.put(byte.class, bytetype);
		PRIMITIVE_INFOS.put(Short.class, shorttype);
		PRIMITIVE_INFOS.put(short.class, shorttype);
		PRIMITIVE_INFOS.put(int.class, inttype);
		PRIMITIVE_INFOS.put(Integer.class, inttype);
		PRIMITIVE_INFOS.put(Long.class, longtype);
		PRIMITIVE_INFOS.put(long.class, longtype);

		PRIMITIVE_INFOS.put(Float.class, floattype);
		PRIMITIVE_INFOS.put(float.class, floattype);
		PRIMITIVE_INFOS.put(Double.class, doubletype);
		PRIMITIVE_INFOS.put(double.class, doubletype);

		PRIMITIVE_INFOS.put(Boolean.class, booleantype);
		PRIMITIVE_INFOS.put(boolean.class, booleantype);
		PRIMITIVE_INFOS.put(Character.class, chartype);
		PRIMITIVE_INFOS.put(char.class, chartype);
		PRIMITIVE_INFOS.put(String.class, stringtype);
	}

	public static TypeInformation get(Class<?> type) {
		type = ReflectUtils.unprimitivize(type);
		return PRIMITIVE_INFOS.get(type);
	}

	@Override
	public String getKind() {
		return kind;
	}

	@Override
	public String getTypeQualifiedName() {
		return clazz.getCanonicalName();
	}

	@Override
	public String getTypeSimpleName() {
		return clazz.getSimpleName();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
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
		PrimitiveTypeInformation other = (PrimitiveTypeInformation) obj;
		if (clazz == null) {
			if (other.clazz != null)
				return false;
		} else if (!clazz.equals(other.clazz))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PrimitiveTypeInformation[" + clazz + "]";
	}

}
