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
package test.nest.unit.scriptinfo;

import java.util.Map;

import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.scriptinfo.reflection.ReflectionInformationContext;
import saker.nest.scriptinfo.reflection.ReflectionTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class TypeUsageElementOverrideScriptInfoTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		{
			ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, SimpleDocTestClass.class,
					informationcontext);
			assertEquals(docinfo.getReturnType().getKind(), TypeInformationKind.OBJECT);
			assertEquals(docinfo.getReturnType().getElementTypes().get(0).getKind(), TypeInformationKind.STRING);
		}
		{
			ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, OverrideDocTestClass.class,
					informationcontext);
			assertEquals(docinfo.getReturnType().getElementTypes().get(0).getKind(), TypeInformationKind.ENUM);
			assertEquals(docinfo.getReturnType().getElementTypes().get(0).getTypeQualifiedName(),
					Overrider.class.getCanonicalName());
		}
	}

	@NestTaskInformation(returnType = @NestTypeUsage(kind = TypeInformationKind.OBJECT, value = SimpleReturnType.class))
	private static class SimpleDocTestClass {
	}

	@NestInformation(value = "ret_type_doc")
	@NestTypeInformation(elementTypes = { @NestTypeUsage(kind = TypeInformationKind.STRING, value = String.class) })
	private interface SimpleReturnType {
	}

	@NestTaskInformation(returnType = @NestTypeUsage(kind = TypeInformationKind.OBJECT,
			value = OverrideReturnType.class,
			elementTypes = { Overrider.class }))
	private static class OverrideDocTestClass {
	}

	@NestInformation(value = "ret_type_doc")
	@NestTypeInformation(elementTypes = { @NestTypeUsage(kind = TypeInformationKind.STRING, value = String.class) })
	private interface OverrideReturnType {
	}

	@NestTypeInformation(kind = TypeInformationKind.ENUM, enumValues = {})
	private static class Overrider {

	}
}
