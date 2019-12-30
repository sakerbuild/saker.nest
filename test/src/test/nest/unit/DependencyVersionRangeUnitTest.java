package test.nest.unit;

import java.util.Map;

import saker.nest.version.VersionRange;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class DependencyVersionRangeUnitTest extends SakerTestCase {

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		rangeAssert("1.0").includes("1.0");
		rangeAssert("1.0").includes("1.0.1");
		rangeAssert("1.0").notIncludes("1");
		rangeAssert("1.0").notIncludes("1.1");
		rangeAssert("1.0").notIncludes("1.1.1");

		rangeAssert("1.0 & 1.0.1").includes("1.0.1").notIncludes("1.0", "1.0.0");

		rangeAssert("[1.1]").includes("1.1");
		rangeAssert("[1.1]").notIncludes("1");
		rangeAssert("[1.1]").notIncludes("1.0");
		rangeAssert("[1.1]").notIncludes("1.1.0");
		rangeAssert("[1.1]").notIncludes("1.1.1");
		rangeAssert("[1.1]").notIncludes("1.2");

		rangeAssert("[1.1)").includes("1.1.1");
		rangeAssert("[1.1)").includes("1.1");
		rangeAssert("[1.1)").includes("1.2");
		rangeAssert("[1.1)").includes("1.2.1");
		rangeAssert("[1.1)").notIncludes("1.0");
		rangeAssert("[1.1)").notIncludes("1.0.9");

		rangeAssert("(1.1]").includes("1.1");
		rangeAssert("(1.1]").notIncludes("1.1.1");
		rangeAssert("(1.1.2]").includes("1.1.1");
		rangeAssert("(1.1.2]").includes("1.1.2");
		rangeAssert("(1.1.2]").notIncludes("1.1.2.1");
		rangeAssert("(1.1.2]").notIncludes("1.1.3");

		rangeAssert("[1, 2]").includes("1", "1.0", "1.1", "2").notIncludes("2.0", "2.1");

		rangeAssert("(1.1]&[1.1)").includes("1.1").notIncludes("1.1.1", "1.2", "1.2.1", "1.0", "1.0.9");

		rangeAssert("(1.1] & 1.1").includes("1.1");

		rangeAssert("{1.1}").includes("1.1").notIncludes("1.2");
		rangeAssert("{1.1|1.2}").includes("1.2");
		rangeAssert("{1.1|1.2}").includes("1.2.1");
		rangeAssert("{1.1|1.2&1.3}").notIncludes("1.2");
		rangeAssert("{}").notIncludes("1.1");

		rangeAssert("{[1.0] | [2.0]}").includes("1.0", "2.0").notIncludes("1.1", "1.0.0", "2.1", "1", "2");

		rangeAssert("(1.1, 1.4)").notIncludes("1.0", "1.1", "1.4", "1.4.0", "1.4.1").includes("1.1.0", "1.1.1", "1.2",
				"1.3.9", "1.3.9.0");
		rangeAssert("(1.1, 1.4]").notIncludes("1.0", "1.1", "1.4.0", "1.4.1").includes("1.1.0", "1.1.1", "1.2", "1.3.9",
				"1.4");
		rangeAssert("[1.1, 1.4)").notIncludes("1.0", "1.4", "1.4.0", "1.4.1").includes("1.1", "1.1.0", "1.1.1", "1.2",
				"1.3.9");
		rangeAssert("[1.1, 1.4]").notIncludes("1.0", "1.4.0", "1.4.1").includes("1.1", "1.1.0", "1.1.1", "1.2", "1.3.9",
				"1.4");

		assertNonParseable("(1.0, 1.0)");
		assertNonParseable("(1.0, 1.0]");
		assertNonParseable("[1.0, 1.0)");
		assertNonParseable("[1.0, 1.0]");
		assertNonParseable("(1.0, 0.0)");
	}

	private static void assertNonParseable(String range) {
		try {
			VersionRange.valueOf(range);
			fail("Parsed: " + range);
		} catch (IllegalArgumentException e) {
			//expected
		}
	}

	private static final char[] CONTROL_CHARS = { '|', '{', '}', '&', '(', ')', '[', ']', ',', '.', 'a' };

	private static VersionRange parseRange(String range) {
		VersionRange result = VersionRange.valueOf(range);
		//test that inserting some control characters will make the successfully parsed range invalid
		for (int i = 0; i < CONTROL_CHARS.length; i++) {
			for (int j = 0; j < range.length(); j++) {
				assertNonParseable(range.substring(0, j) + CONTROL_CHARS[i] + range.substring(j));
			}
		}
		return result;
	}

	private static RangeAssertion rangeAssert(String range) {
		return new RangeAssertion(range);
	}

	private static class RangeAssertion {
		private VersionRange range;
		private VersionRange wrapRange;

		public RangeAssertion(String range) {
			this.range = parseRange(range);
			this.wrapRange = parseRange("{" + range + "}");

			assertEquals(parseRange(range.toString()), this.range);
		}

		public RangeAssertion includes(String... version) {
			for (String v : version) {
				assertTrue(range.includes(v), "Not includes: " + range + " - " + v);
				assertTrue(wrapRange.includes(v), "Not includes: " + wrapRange + " - " + v);
			}
			return this;
		}

		public RangeAssertion notIncludes(String... version) {
			for (String v : version) {
				assertTrue(!range.includes(v), "Includes: " + range + " - " + v);
				assertTrue(!wrapRange.includes(v), "Includes: " + wrapRange + " - " + v);
			}
			return this;
		}

	}
}
