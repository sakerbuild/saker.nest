package test.nest.unit.scriptinfo;

import java.util.Collection;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.scriptinfo.reflection.ReflectionInformationContext;
import saker.nest.scriptinfo.reflection.ReflectionTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestParameterInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class TaskParameterScriptInfoTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, ParamedTestClass.class,
				informationcontext);

		Collection<? extends TaskParameterInformation> params = docinfo.getParameters();
		assertEquals(params.stream().map(TaskParameterInformation::getParameterName).toArray(),
				new String[] { "Str", "Depr", "Req", "Path", "Aliased" });

		assertEquals(getParameterWithName(params, "Str").getInformation()
				.getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "str_doc");
		assertEquals(getParameterWithName(params, "Depr").getInformation(), null);
		assertTrue(getParameterWithName(params, "Depr").isDeprecated());
		assertTrue(getParameterWithName(params, "Req").isRequired());
		assertEquals(getParameterWithName(params, "Path").getTypeInformation().getTypeQualifiedName(),
				SakerPath.class.getCanonicalName());
		assertEquals(getParameterWithName(params, "Path").getTypeInformation().getKind(), TypeInformationKind.PATH);
		assertEquals(getParameterWithName(params, "Aliased").getAliases(), setOf("Other"));

		for (TaskParameterInformation p : params) {
			assertIdentityEquals(p.getTask(), docinfo);
		}
	}

	@NestParameterInformation(value = "Str", info = @NestInformation("str_doc"))
	@NestParameterInformation(value = "Depr", deprecated = true)
	@NestParameterInformation(value = "Req", required = true)
	@NestParameterInformation(value = "Path",
			type = @NestTypeUsage(kind = TypeInformationKind.PATH, value = SakerPath.class))
	@NestParameterInformation(value = "Aliased", aliases = { "Other" })
	private static class ParamedTestClass {
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
