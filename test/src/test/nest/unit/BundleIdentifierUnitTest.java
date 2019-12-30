package test.nest.unit;

import java.util.Map;

import saker.nest.bundle.BundleIdentifier;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class BundleIdentifierUnitTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "1.9"), 1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "1.9.0"), 1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "1.9.9"), 1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "2.0"), 0);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "2.0.0"), -1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "2.0.1"), -1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "2.1"), -1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "3.0"), -1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "3.0"), -1);
		assertEquals(BundleIdentifier.compareVersionNumbers("2.0", "3.0.1"), -1);

		assertEquals(BundleIdentifier.valueOf("bundle.id-v1").getVersionQualifier(), "v1");
		assertEquals(BundleIdentifier.valueOf("bundle.id-v1-v1").getVersionQualifier(), "v1");
		assertEquals(BundleIdentifier.valueOf("UPPER.CASE-BUNDLE-Q-V1"),
				BundleIdentifier.valueOf("upper.case-bundle-q-v1"));
		assertEquals(BundleIdentifier.valueOf("UPPER.CASE-BUNDLE-Q-V1")
				.compareTo(BundleIdentifier.valueOf("upper.case-bundle-q-v1")), 0);
		assertException(IllegalArgumentException.class, () -> BundleIdentifier.valueOf("bundle.id-v1-v2"));
	}
}
