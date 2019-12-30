package test.nest.unit;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.nest.ConfiguredRepositoryStorage;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class StorageConfigParserUnitTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertEquals(parse(""), listOf());
		assertEquals(parse("[]"), listOf());
		assertEquals(parse("[[]]"), listOf());
		assertEquals(parse(":server"), listOf(conf("server", "server")));
		assertEquals(parse("s1:server"), listOf(conf("s1", "server")));
		assertEquals(parse("[s1:server, :local]"), listOf(conf("s1", "server"), conf("local", "local")));
		assertEquals(parse("[s1:server, :local, []]"), listOf(conf("s1", "server"), conf("local", "local")));
		assertEquals(parse("[s1:server, [s2:server, :local], [s3:server, :local]]"),
				listOf(conf("s1", "server"), listOf(conf("s2", "server"), conf("local", "local")),
						listOf(conf("s3", "server"), conf("local", "local"))));
		assertEquals(parse("[[:local, s:server], [:params, s:]]"),
				listOf(listOf(conf("local", "local"), conf("s", "server")),
						listOf(conf("params", "params"), conf("s", null))));

		parse("server: server");
		parse(":server");
		parse("[:params, :local, :server]");
		parse("[p1:params, p2:params]");
		parse("[[p3:params, :local], p4:params]");
		parse("[p3:params, [:local], p4:params]");
		parse("[[p5:params, :local], [p6:params, :local]]");

		assertException(IllegalArgumentException.class, () -> parse(":nonexistent"));
	}

	private static Entry<String, String> conf(String name, String type) {
		return ImmutableUtils.makeImmutableMapEntry(name, type);
	}

	private static final char[] CONTROL_CHARS = { ',', '(', ')', '[', ']', ':', '{', '}' };

	private static List<?> parse(String p) {
		for (int i = 0; i < CONTROL_CHARS.length; i++) {
			for (int j = 0; j < p.length(); j++) {
				assertNonParseable(p.substring(0, j) + CONTROL_CHARS[i] + p.substring(j));
			}
		}
		//just test that it is parseable
		List<?> result = ConfiguredRepositoryStorage.parseStorageConfigurationUserParameter(p);
		ConfiguredRepositoryStorage.parseStorageConfigurationUserParameter("[" + p + "]");
		return result;
	}

	private static void assertNonParseable(String p) {
		try {
			List<?> res = ConfiguredRepositoryStorage.parseStorageConfigurationUserParameter(p);
			fail("Parsed: " + p + " as " + res);
		} catch (IllegalArgumentException e) {
			//expected
		}
	}
}
