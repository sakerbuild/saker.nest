package test.nest.unit;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import saker.build.task.TaskName;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleUtils;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class BundleUtilsUnitTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertEquals(select("my.task", "a.b"), bid("a.b"));
		assertEquals(select("my.task", "a.b-v1"), bid("a.b-v1"));
		assertEquals(select("my.task", "a.b-v1", "a.b-v2"), bid("a.b-v2"));
		assertEquals(select("my.task", "a.b", "a.b-v1", "a.b-v2"), bid("a.b"));
		assertEquals(select("my.task-v1", "a.b", "a.b-v1", "a.b-v2"), bid("a.b-v1"));
		assertEquals(select("my.task-v3", "a.b", "a.b-v1", "a.b-v2"), null);
		assertEquals(select("my.task-q1", "a.b", "a.b-v1", "a.b-v2"), null);
		assertEquals(select("my.task-q1", "a.b-q1", "a.b-v1", "a.b-v2"), bid("a.b-q1"));
		assertEquals(select("my.task-q1", "a.b", "a.b-q1-v1", "a.b-q1-v2"), bid("a.b-q1-v2"));
		assertEquals(select("my.task-q1", "a.b", "a.b-q1-v1", "a.b-v2"), bid("a.b-q1-v1"));
		assertEquals(select("my.task-q1-v1", "a.b", "a.b-q1-v1", "a.b-q1-v2"), bid("a.b-q1-v1"));
		assertEquals(select("my.task-q1-q2", "a.b", "a.b-q1-v1", "a.b-v2"), null);
		assertEquals(select("my.task-q1-q2", "a.b-q1-q2", "a.b-q1-v1", "a.b-v2"), bid("a.b-q1-q2"));
		assertEquals(select("my.task-q1-q2", "a.b-q1-q2-q3", "a.b-q1-v1", "a.b-v2"), null);
		assertEquals(select("my.task-q1-q2-v1", "a.b-q1-q2-q3", "a.b-q1-v1", "a.b-q1-q2-v2"), null);
		assertEquals(select("my.task-q1-q2-v2", "a.b-q1-q2-q3", "a.b-q1-v1", "a.b-q1-q2-v2"), bid("a.b-q1-q2-v2"));
		assertEquals(select("my.task-q1-q2-v2", "a.b-q1-q2-q3", "a.b-q1-v1", "a.b-q1-q2-q3-v2"), null);
	}

	private static BundleIdentifier select(String taskname, String... bundlenames) {
		Set<BundleIdentifier> bundles = new TreeSet<>();
		for (String bn : bundlenames) {
			bundles.add(bid(bn));
		}
		return BundleUtils.selectAppropriateBundleIdentifierForTask(TaskName.valueOf(taskname), bundles);
	}

	private static BundleIdentifier bid(String input) {
		return BundleIdentifier.valueOf(input);
	}

}
