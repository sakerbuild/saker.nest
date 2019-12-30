package test.nest.unit.scriptinfo;

import java.util.Map;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.scriptinfo.reflection.ReflectionInformationContext;
import saker.nest.scriptinfo.reflection.ReflectionTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class FieldForwardingParameterScriptInfoTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, TaskDocClass.class, informationcontext);
		assertEquals(getParameterWithName(docinfo.getParameters(), "TheField").getInformation()
				.getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "doc_the_field");
		assertFalse(getParameterWithName(docinfo.getParameters(), "TheField").isDeprecated());
		assertEquals(getParameterWithName(docinfo.getParameters(), "TheField").getTypeInformation().getKind(),
				TypeInformationKind.STRING);

		assertTrue(getParameterWithName(docinfo.getParameters(), "DeprField").isDeprecated());
		assertNonNull(getParameterWithName(docinfo.getParameters(), "SecondField"));
	}

	@NestTaskInformation(includeFieldsAsParametersFrom = { Fielded.class, Second.class })
	private static class TaskDocClass {
	}

	@NestFieldInformation(value = "TheField",
			info = @NestInformation("doc_the_field"),
			type = @NestTypeUsage(String.class),
			deprecated = false)
	@NestFieldInformation(value = "DeprField", deprecated = true)
	private static class Fielded {
	}

	@NestFieldInformation("SecondField")
	private static class Second {

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
