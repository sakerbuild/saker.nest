package test.nest.unit;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import saker.build.file.path.WildcardPath;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.ExternalDependencyList;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;
import testing.saker.build.tests.EnvironmentTestCase;

@SakerTest
public class BundleExternalDependenciesFileUnitTest extends SakerTestCase {
	private Path basedir = EnvironmentTestCase.getTestingBaseWorkingDirectory()
			.resolve(getClass().getName().replace('.', '/'));

	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		URI examplemyjar = new URI("https://example.com/myjar.jar");
		URI examplemyjar2 = new URI("https://example.com/myjar2.jar");
		URI examplemyjarsources = new URI("https://example.com/myjar-sources.jar");
		URI examplemyjardoc = new URI("https://example.com/myjar-doc.jar");

		ExternalDependencyInformation empty = read("empty.txt");
		assertTrue(empty.isEmpty());

		ExternalDependencyInformation simple = read("simple.txt");
		assertEquals(simple.getDependencies().get(examplemyjar).getDependencies().size(), 1);
		assertEquals(simple.getDependencies().get(examplemyjar).getDependencies().iterator().next().getKinds(),
				setOf("classpath"));
		assertEquals(simple.getDependencies().get(examplemyjar2).getDependencies().iterator().next().getKinds(),
				setOf("classpath", "test", "compile"));
		assertTrue(simple.getDependencies().get(examplemyjar).getDependencies().iterator().next().isPrivate());

		ExternalDependencyInformation nodepkinds = read("nodepkinds.txt");
		assertEquals(nodepkinds.getDependencies().keySet(), setOf(examplemyjar, examplemyjar2));
		assertEquals(nodepkinds.getDependencies().get(examplemyjar), ExternalDependencyList.EMPTY);
		assertEquals(nodepkinds.getDependencies().get(examplemyjar2), ExternalDependencyList.EMPTY);

		ExternalDependencyInformation withhashes = read("withhashes.txt");
		assertEquals(withhashes.getDependencies().get(examplemyjar).getSha256Hash(),
				"1234567890123456789012345678901234567890123456789012345678901234");
		assertEquals(withhashes.getDependencies().get(examplemyjar).getSha1Hash(),
				"12345678901234567890123456789012abcdef12");
		assertEquals(withhashes.getDependencies().get(examplemyjar).getMd5Hash(), "12345678901234567890123456789012");

		ExternalDependencyInformation priv = read("private.txt");
		assertTrue(atIndex(priv.getDependencies().get(examplemyjar).getDependencies(), 0).isPrivate());
		assertFalse(atIndex(priv.getDependencies().get(examplemyjar).getDependencies(), 1).isPrivate());

		ExternalDependencyInformation attachments = read("attachments.txt");
		assertEquals(attachments.getDependencies().get(examplemyjar).getSourceAttachments().keySet(),
				setOf(examplemyjarsources));
		assertTrue(attachments.getDependencies().get(examplemyjar).getSourceAttachments().values().iterator().next()
				.isTargetsMainArchive());
		assertEquals(attachments.getDependencies().get(examplemyjar).getDocumentationAttachments().keySet(),
				setOf(examplemyjardoc));

		ExternalDependencyInformation depentries = read("depentries.txt");
		assertEquals(depentries.getDependencies().get(examplemyjar).getDependencies().iterator().next().getEntries(),
				setOf(WildcardPath.valueOf("lib/*.jar")));
		assertEquals(depentries.getDependencies().get(examplemyjar2).getDependencies().iterator().next().getEntries(),
				setOf(WildcardPath.valueOf("lib/*.jar"), WildcardPath.valueOf("second/*.jar")));

		ExternalDependencyInformation attachmentsentries = read("attachmentsentries.txt");
		assertEquals(attachmentsentries.getDependencies().get(examplemyjar).getSourceAttachments().values().iterator()
				.next().getEntries(), setOf(WildcardPath.valueOf("lib/*.jar")));
		assertEquals(
				attachmentsentries.getDependencies().get(examplemyjar).getDocumentationAttachments().values().iterator()
						.next().getEntries(),
				setOf(WildcardPath.valueOf("lib/*.jar"), WildcardPath.valueOf("second/*.jar")));

		ExternalDependencyInformation attachmentshash = read("attachmentshash.txt");
		assertEquals(attachmentshash.getDependencies().get(examplemyjar).getSourceAttachments().values().iterator()
				.next().getSha256Hash(), "1234567890123456789012345678901234567890123456789012345678901234");
		assertTrue(attachmentshash.getDependencies().get(examplemyjar).getSourceAttachments().values().iterator().next()
				.isTargetsMainArchive());
		assertEquals(attachmentshash.getDependencies().get(examplemyjar).getSourceAttachments().values().iterator()
				.next().getSha1Hash(), "12345678901234567890123456789012abcdef12");
		assertEquals(attachmentshash.getDependencies().get(examplemyjar).getSourceAttachments().values().iterator()
				.next().getMd5Hash(), "12345678901234567890123456789012");

		assertException(IllegalArgumentException.class, () -> read("conflictinghashes.txt"));

		ExternalDependencyInformation attachmentandkind = read("attachmentandkind.txt");
		assertEquals(attachmentandkind.getDependencies().get(examplemyjar).getSourceAttachments().keySet(),
				setOf(examplemyjarsources));
		assertTrue(attachmentandkind.getDependencies().get(examplemyjar).getSourceAttachments().get(examplemyjarsources)
				.isTargetsMainArchive());
	}

	private static <T> T atIndex(Iterable<T> iterable, int idx) {
		Iterator<T> it = iterable.iterator();
		while (idx > 0) {
			--idx;
			it.next();
		}
		return it.next();
	}

	private ExternalDependencyInformation read(String name) throws IOException {
		ExternalDependencyInformation result = ExternalDependencyInformation.readFrom(fileInput(name));
		System.out.println(name + " -> " + result);
		return result;
	}

	private UnsyncByteArrayInputStream fileInput(String fname) throws IOException {
		return new UnsyncByteArrayInputStream(Files.readAllBytes(basedir.resolve(fname)));
	}
}
