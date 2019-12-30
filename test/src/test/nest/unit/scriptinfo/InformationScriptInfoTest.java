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
