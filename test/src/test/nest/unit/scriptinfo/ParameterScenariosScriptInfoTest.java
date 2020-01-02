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

import java.util.Collection;
import java.util.Map;

import saker.build.file.path.WildcardPath;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.scriptinfo.reflection.ReflectionInformationContext;
import saker.nest.scriptinfo.reflection.ReflectionTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class ParameterScenariosScriptInfoTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, DocTestTaskFactory.class,
				informationcontext);

		Collection<? extends TaskParameterInformation> params = docinfo.getParameters();
		assertEquals(params.stream().map(TaskParameterInformation::getParameterName).toArray(),
				new String[] { "WildcardInput", "CustomColl" });

		assertEquals(getParameterWithName(params, "WildcardInput").getAliases(), setOf(""));
		assertEquals(getParameterWithName(params, "WildcardInput").getTypeInformation().getKind(),
				TypeInformationKind.WILDCARD_PATH);
		assertEquals(getParameterWithName(params, "WildcardInput").getTypeInformation().getTypeQualifiedName(),
				WildcardPath.class.getCanonicalName());

		{
			TypeInformation customcolltype = getParameterWithName(params, "CustomColl").getTypeInformation();
			TypeInformation elemtype = customcolltype.getElementTypes().get(0);

			assertEquals(customcolltype.getKind(), TypeInformationKind.COLLECTION);
			assertEquals(elemtype.getTypeQualifiedName(), "test.CustomCollElem");
			assertEquals(elemtype.getFields().keySet(), setOf("CustomField"));
			assertEquals(elemtype.getFields().get("CustomField").getInformation()
					.getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "doc_custom_field");
		}
	}

	@NestParameterInformation(value = "WildcardInput",
			aliases = { "" },
			type = @NestTypeUsage(kind = TypeInformationKind.WILDCARD_PATH, value = WildcardPath.class))
	@NestParameterInformation(value = "CustomColl",
			type = @NestTypeUsage(kind = TypeInformationKind.COLLECTION,
					value = Collection.class,
					elementTypes = { doc_CustomCollElem.class }))
	private static class DocTestTaskFactory {
	}

	@NestTypeInformation(qualifiedName = "test.CustomCollElem")
	@NestFieldInformation(value = "CustomField", info = @NestInformation("doc_custom_field"))
	private static class doc_CustomCollElem {
	}

	private static TaskParameterInformation getParameterWithName(Iterable<? extends TaskParameterInformation> params,
			String name) {
		for (TaskParameterInformation pinfo : params) {
			if (name.equals(pinfo.getParameterName())) {
				return pinfo;
			}
		}
		return null;
	}
}
