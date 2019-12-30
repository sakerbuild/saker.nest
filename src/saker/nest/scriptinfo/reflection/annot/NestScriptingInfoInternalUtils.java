package saker.nest.scriptinfo.reflection.annot;

import saker.apiextract.api.ExcludeApi;

@ExcludeApi
public class NestScriptingInfoInternalUtils {
	public static boolean isElementTypesSpecified(NestTypeUsage annot) {
		if (annot == null) {
			return false;
		}
		Class<?>[] elemtypes = annot.elementTypes();
		if (elemtypes.length == 1 && elemtypes[0] == TypeUnspecified.class) {
			return false;
		}
		return true;
	}

	public static Class<?>[] getElementTypesIfSpecified(NestTypeUsage annot) {
		if (annot == null) {
			return null;
		}
		Class<?>[] elemtypes = annot.elementTypes();
		if (elemtypes.length == 1 && elemtypes[0] == TypeUnspecified.class) {
			return null;
		}
		return elemtypes;
	}

	public static Class<?>[] getIncludeFieldsAsParametersFrom(NestTaskInformation annot) {
		if (annot == null) {
			return null;
		}
		return annot.includeFieldsAsParametersFrom();
	}

	private NestScriptingInfoInternalUtils() {
		throw new UnsupportedOperationException();
	}
}
