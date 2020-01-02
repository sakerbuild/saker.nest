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
