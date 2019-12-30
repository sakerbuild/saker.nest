package test.nest.unit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;

import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.nest.bundle.BundleDependency;
import saker.nest.bundle.BundleDependency.Builder;
import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleDependencyList;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.version.VersionRange;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class BundleDependenciesFileUnitTest extends SakerTestCase {
	private Path basedir = EnvironmentTestCase.getTestingBaseWorkingDirectory()
			.resolve(getClass().getName().replace('.', '/'));

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		assertDepsMap("simple.txt")//
				.contains(bundleId("my.bundle"), depSet(depBuilder("1.0").addKind("runtime")))//
				.noRemaining();
		assertDepsMap("multideps.txt")//
				.contains(bundleId("my.bundle"), depSet(depBuilder("1.0").addKind("runtime")))//
				.contains(bundleId("other.bundle"), depSet(depBuilder("1.2").addKind("runtime")))//
				.noRemaining();
		assertDepsMap("multikind.txt")//
				.contains(bundleId("my.bundle"),
						depSet(depBuilder("1.0").addKind("runtime").addKind("core").addKind("main")))//
				.noRemaining();
		assertDepsMap("multidepkinds.txt")//
				.contains(bundleId("my.bundle"),
						depSet(depBuilder("1.0").addKind("runtime"), depBuilder("3.0").addKind("other")))//
				.noRemaining();

		assertDepsMap("metadata.txt")//
				.contains(bundleId("my.bundle"),
						depSet(depBuilder("1.0").addKind("runtime").addMetaData("optional", "true").addMetaData("meta2",
								"123"), depBuilder("3.0").addKind("other").addMetaData("meta3", "abc")))//
				.noRemaining();

		assertDepsMap("multilinemetadata.txt")//
				.contains(bundleId("my.bundle"),
						depSet(depBuilder("1.0").addKind("runtime").addMetaData("empty", "").addMetaData("slash", "\\")
								.addMetaData("emptynonq", "").addMetaData("spaced", "a b").addMetaData("meta2", "123")
								.addMetaData("qquote", "\"123\"").addMetaData("lines", "1\n2")
								.addMetaData("line3", "1\n2\n3").addMetaData("qend", "1\n\"\n2")
								.addMetaData("qline2", "1\"\n2")))//
				.contains(bundleId("other.bundle"), depSet(depBuilder("1").addKind("runtime")))//
				.noRemaining();

		assertDepsMap("escaper.txt")//
				.contains(bundleId("my.bundle"),
						depSet(depBuilder("1.0").addKind("runtime").addMetaData("meta2", "\n\\\"\n")))//
				.noRemaining();
	}

	private MapAssertion assertDepsMap(String fname) throws IOException {
		return assertMap(readDeps(fname, BundleIdentifier.valueOf("my.bundle-v1.2.3")));
	}

	private static BundleDependencyList depSet(BundleDependency.Builder... elems) {
		HashSet<BundleDependency> result = new HashSet<>();
		for (Builder b : elems) {
			result.add(b.build());
		}
		return BundleDependencyList.create(result);
	}

	private static Builder depBuilder(String range) {
		return BundleDependency.builder().setRange(VersionRange.valueOf(range));
	}

	private Map<BundleIdentifier, ? extends BundleDependencyList> readDeps(String name,
			BundleIdentifier declaringbundleid) throws IOException {
		System.out.println("BundleDependenciesFileUnitTest.readDeps() " + name);
		BundleDependencyInformation depinfo = BundleDependencyInformation.readFrom(fileInput(name), declaringbundleid);
		System.out.println("BundleDependenciesFileUnitTest.readDeps()   READ " + depinfo);
		UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream();
		depinfo.writeTo(baos);
		System.out.println("BundleDependenciesFileUnitTest.readDeps()   WROTE \n" + baos);
		BundleDependencyInformation reparsed = BundleDependencyInformation
				.readFrom(new UnsyncByteArrayInputStream(baos.toByteArrayRegion()), declaringbundleid);
		System.out.println("BundleDependenciesFileUnitTest.readDeps()   READ AGAIN " + reparsed);
		assertEquals(depinfo, reparsed);
		return depinfo.getDependencies();
	}

	private UnsyncByteArrayInputStream fileInput(String fname) throws IOException {
		return new UnsyncByteArrayInputStream(Files.readAllBytes(basedir.resolve(fname)));
	}

	private static BundleIdentifier bundleId(String id) {
		return BundleIdentifier.valueOf(id);
	}

}
