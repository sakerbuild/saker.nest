package test.nest.unit.scriptinfo;

import java.util.Map;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.info.FieldInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.scripting.model.info.TypeInformationKind;
import saker.nest.scriptinfo.reflection.ReflectionInformationContext;
import saker.nest.scriptinfo.reflection.ReflectionTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestFieldInformation;
import saker.nest.scriptinfo.reflection.annot.NestInformation;
import saker.nest.scriptinfo.reflection.annot.NestTaskInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeInformation;
import saker.nest.scriptinfo.reflection.annot.NestTypeUsage;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
@SuppressWarnings({ "unused", "static-method" })
public class TaskInfoScriptInfoTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		testSimpleReturnType();
		testFieldedReturnType();
		testEnumReturnType();
		testFieldedEnumReturnType();
	}

	private void testSimpleReturnType() throws AssertionError {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, SimpleDocTestClass.class,
				informationcontext);

		TypeInformation rettype = docinfo.getReturnType();
		assertEquals(rettype.getKind(), TypeInformationKind.OBJECT);
		assertEquals(rettype.getTypeQualifiedName(), SimpleReturnType.class.getCanonicalName());
		assertEquals(rettype.getTypeSimpleName(), SimpleReturnType.class.getSimpleName());
		assertEquals(rettype.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "ret_type_doc");

		assertEmpty(rettype.getEnumValues());
		assertEmpty(rettype.getFields());
		assertEmpty(rettype.getSuperTypes());
		assertEmpty(rettype.getRelatedTypes());
		assertFalse(rettype.isDeprecated());
		assertEmpty(rettype.getElementTypes());
	}

	@NestTaskInformation(returnType = @NestTypeUsage(kind = TypeInformationKind.OBJECT, value = SimpleReturnType.class))
	private static class SimpleDocTestClass {
	}

	@NestInformation(value = "ret_type_doc")
	private interface SimpleReturnType {
	}

	private void testFieldedReturnType() throws AssertionError {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, FieldDocTestClass.class,
				informationcontext);

		TypeInformation rettype = docinfo.getReturnType();
		assertEquals(rettype.getKind(), TypeInformationKind.OBJECT);
		assertEquals(rettype.getTypeQualifiedName(), FieldedReturnType.class.getCanonicalName());
		assertEquals(rettype.getTypeSimpleName(), FieldedReturnType.class.getSimpleName());
		assertEquals(rettype.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT),
				"fielded_ret_type_doc");

		Map<String, FieldInformation> fields = rettype.getFields();
		assertEquals(fields.size(), 1);
		{
			FieldInformation f = fields.get("IntField");
			assertNonNull(f);
			assertEquals(f.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "intfield_doc");
			assertEquals(f.getName(), "IntField");
			TypeInformation ftype = f.getType();
			assertEquals(ftype.getKind(), TypeInformationKind.NUMBER);
			assertEquals(ftype.getTypeQualifiedName(), Integer.class.getCanonicalName());
			assertEquals(ftype.getTypeSimpleName(), Integer.class.getSimpleName());
		}

		assertEmpty(rettype.getEnumValues());
		assertEmpty(rettype.getSuperTypes());
		assertEmpty(rettype.getRelatedTypes());
		assertFalse(rettype.isDeprecated());
		assertEmpty(rettype.getElementTypes());
	}

	@NestTaskInformation(
			returnType = @NestTypeUsage(kind = TypeInformationKind.OBJECT, value = FieldedReturnType.class))
	private static class FieldDocTestClass {
	}

	@NestInformation(value = "fielded_ret_type_doc")
	@NestFieldInformation(value = "IntField",
			type = @NestTypeUsage(value = int.class, kind = TypeInformationKind.NUMBER),
			info = @NestInformation("intfield_doc"))
	private static class FieldedReturnType {
	}

	private void testEnumReturnType() throws AssertionError {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, EnumDocTestClass.class,
				informationcontext);

		TypeInformation rettype = docinfo.getReturnType();
		assertEquals(rettype.getKind(), TypeInformationKind.ENUM);
		assertEquals(rettype.getTypeQualifiedName(), EnumReturnType.class.getCanonicalName());
		assertEquals(rettype.getTypeSimpleName(), EnumReturnType.class.getSimpleName());
		assertEquals(rettype.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "enum_type_doc");

		Map<String, FieldInformation> enumvals = rettype.getEnumValues();
		assertEquals(enumvals.size(), 3);
		{
			FieldInformation first = enumvals.get("FIRST");
			FieldInformation second = enumvals.get("SECOND");
			FieldInformation deprecated = enumvals.get("DEPRECATED");
			assertNonNull(first);
			assertNonNull(second);
			assertNonNull(deprecated);
			assertEquals(first.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "first_doc");
			assertTrue(deprecated.isDeprecated());
		}

		Map<String, FieldInformation> fields = rettype.getFields();
		assertEquals(fields.size(), 1);
		{
			FieldInformation f = fields.get("Data");
			assertNonNull(f);
			assertEquals(f.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT), "data_doc");
			assertEquals(f.getName(), "Data");
			assertEquals(f.getType().getKind(), TypeInformationKind.STRING);
			assertEquals(f.getType().getTypeQualifiedName(), String.class.getCanonicalName());
		}

		assertEmpty(rettype.getSuperTypes());
		assertEmpty(rettype.getRelatedTypes());
		assertFalse(rettype.isDeprecated());
		assertEmpty(rettype.getElementTypes());
	}

	@NestTaskInformation(returnType = @NestTypeUsage(kind = TypeInformationKind.ENUM, value = EnumReturnType.class))
	private static class EnumDocTestClass {
	}

	@NestInformation(value = "enum_type_doc")
	@NestFieldInformation(value = "Data",
			type = @NestTypeUsage(value = String.class, kind = TypeInformationKind.STRING),
			info = @NestInformation("data_doc"))
	private static enum EnumReturnType {
		@NestInformation("first_doc")
		FIRST,
		SECOND,
		@Deprecated
		DEPRECATED,

		;

		public String getData() {
			return name();
		}
	}

	private void testFieldedEnumReturnType() throws AssertionError {
		ReflectionInformationContext informationcontext = new ReflectionInformationContext(null);
		ReflectionTaskInformation docinfo = new ReflectionTaskInformation(null, FieldedEnumDocTestClass.class,
				informationcontext);

		TypeInformation rettype = docinfo.getReturnType();
		assertEquals(rettype.getKind(), TypeInformationKind.ENUM);
		assertEquals(rettype.getTypeQualifiedName(), FieldedEnum.class.getCanonicalName());
		assertEquals(rettype.getTypeSimpleName(), FieldedEnum.class.getSimpleName());
		assertEquals(rettype.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT),
				"fielded_enum_type_doc");

		Map<String, FieldInformation> enumvals = rettype.getEnumValues();
		assertEquals(enumvals.keySet(), setOf("FIELD_ENUM1", "FIELD_ENUM2"));
		{
			FieldInformation e1 = enumvals.get("FIELD_ENUM1");
			FieldInformation e2 = enumvals.get("FIELD_ENUM2");
			assertNonNull(e1);
			assertNonNull(e2);
			assertEquals(e1.getInformation().getFormattedText(FormattedTextContent.FORMAT_PLAINTEXT),
					"field_enum1_doc");
		}

		assertEmpty(rettype.getFields());
		assertEmpty(rettype.getSuperTypes());
		assertEmpty(rettype.getRelatedTypes());
		assertFalse(rettype.isDeprecated());
		assertEmpty(rettype.getElementTypes());
	}

	@NestTaskInformation(returnType = @NestTypeUsage(kind = TypeInformationKind.ENUM, value = FieldedEnum.class))
	private static class FieldedEnumDocTestClass {
	}

	@NestInformation(value = "fielded_enum_type_doc")
	@NestTypeInformation(
			enumValues = { @NestFieldInformation(value = "FIELD_ENUM1", info = @NestInformation("field_enum1_doc")),
					@NestFieldInformation(value = "FIELD_ENUM2") })
	protected static class FieldedEnum {
		public static final String FIELD_ENUM1 = "FIELD_ENUM1";
		public static final String FIELD_ENUM2 = "FIELD_ENUM2";
	}
}
