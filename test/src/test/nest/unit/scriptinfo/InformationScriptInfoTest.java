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

import saker.build.scripting.model.FormattedTextContent;
import saker.nest.scriptinfo.reflection.ReflectionInformationContext;
import saker.nest.scriptinfo.reflection.ReflectionTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class InformationScriptInfoTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, DocTestClass.class, informationcontext);
		assertEquals(docinfo.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "task_doc");
		assertEquals(docinfo.getInformation().getFormattedText(FormattedTextContent.FORMAT_MARKDOWN), "task_doc_md");

	}

	@NestInformation(format = FormattedTextContent.FORMAT_PLAINTEXT, value = "task_doc")
	@NestInformation(format = FormattedTextContent.FORMAT_MARKDOWN, value = "task_doc_md")
	private static class DocTestClass {
	}
}
