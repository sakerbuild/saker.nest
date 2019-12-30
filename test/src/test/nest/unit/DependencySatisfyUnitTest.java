package test.nest.unit;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.bundle.BundleDependency;
import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleDependencyList;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleIdentifierHolder;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.BundleKey;
import saker.nest.bundle.SimpleBundleKey;
import saker.nest.dependency.DependencyResolutionResult;
import saker.nest.dependency.DependencyUtils;
import saker.nest.version.ExactVersionRange;
import saker.nest.version.VersionRange;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
public class DependencySatisfyUnitTest extends SakerTestCase {
	@Override
	public void runTest(Map<String, String> parameters) throws Throwable {
		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0")//

				.bundle("second.bundle-v1.0.0").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0.0")//

				.bundle("second.bundle-v1.1.0").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0.0")//
		;

		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").depend("third.bundle", "1.0").build()//
				.bundle("third.bundle-v1.0").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0", "third.bundle-v1.0")//

				.bundle("third.bundle-v1.0.1").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0", "third.bundle-v1.0.1")//

				.bundle("second.bundle-v1.0.1").depend("third.bundle", "1.0.1").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0.1", "third.bundle-v1.0.1")//

				.bundle("second.bundle-v1.0.2").depend("third.bundle", "2.0").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0.1", "third.bundle-v1.0.1")//

				.bundle("first.bundle-v2").depend("second.bundle", "1.0.2").build()//
				.assertNotSatisfiable("first.bundle-v2")//
		;

		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").depend("third.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").depend("dep.bundle", "1.0").build()//
				.bundle("third.bundle-v1.0").depend("dep.bundle", "2.0").build()//
				.bundle("dep.bundle-v1.0").build()//
				.bundle("dep.bundle-v2.0").build()//
				//conflict on dep.bundle
				.assertNotSatisfiable("first.bundle-v1")//

				//fix the third with a new version
				.bundle("third.bundle-v1.0.0").depend("dep.bundle", "1.0").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0", "third.bundle-v1.0.0", "dep.bundle-v1.0")//
		;

		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").depend("third.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").depend("dep.bundle", "1.0").build()//
				.bundle("third.bundle-v1.0").depend("dep.bundle", "1.0").build()//
				//missing dependency bundle
				.assertNotSatisfiable("first.bundle-v1")//
		;

		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").depend("third.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").depend("dep.bundle", "{1.0|2.0}").build()//
				.bundle("third.bundle-v1.0").depend("dep.bundle", "{2.0|3.0}").build()//
				.bundle("dep.bundle-v1.0").build()//
				.assertNotSatisfiable("first.bundle-v1")//

				.bundle("dep.bundle-v3.0").build()//
				.assertNotSatisfiable("first.bundle-v1")//

				.bundle("dep.bundle-v2.0").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0", "third.bundle-v1.0", "dep.bundle-v2.0")//

				.bundle("dep.bundle-v2.0.1").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0", "third.bundle-v1.0", "dep.bundle-v2.0.1")//
		;

		//circular
		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").depend("third.bundle", "1.0").build()//
				.bundle("third.bundle-v1.0").depend("first.bundle", "1").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0", "third.bundle-v1.0")//
		;
		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").depend("first.bundle", "1").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0")//
		;

		lookup()//
				.bundle("a.b-v1").depend("b.b", "{1 | 2}").depend("d.b", "1").build()//
				.bundle("b.b-v2").depend("c.b", "2").build()//
				.bundle("b.b-v1").depend("c.b", "1").build()//
				.bundle("c.b-v1").build()//
				.bundle("c.b-v2").build()//
				.bundle("d.b-v1").depend("c.b", "1").build()//
				.assertSatisfiable("a.b-v1", "a.b-v1", "b.b-v1", "d.b-v1", "c.b-v1")//

				.bundle("d.b-v1.1").depend("c.b", "2").build()//
				.assertSatisfiable("a.b-v1", "b.b-v2", "d.b-v1.1", "c.b-v2")//
		;
		lookup()//
				.bundle("a.b-v1").depend("b.b", "1").depend("d.b", "1").build()//
				.bundle("b.b-v1").depend("d.b", "1").build()//
				.bundle("d.b-v1").build()//
				.assertSatisfiable("a.b-v1", "b.b-v1", "d.b-v1")//
		;

		lookup()//
				.bundle("a.b-v1").depend("o.b", "1", true).build()//
				.assertSatisfiable("a.b-v1")//

				.bundle("o.b-v1").build()//
				.assertSatisfiable("a.b-v1", "o.b-v1")//

				.bundle("o.b-v1.1").depend("o2.b", "1").build()//
				.assertSatisfiable("a.b-v1", "o.b-v1")//

				.bundle("o2.b-v1").build()//
				.assertSatisfiable("a.b-v1", "o.b-v1.1", "o2.b-v1")//
		;
		lookup()//
				.bundle("a.b-v1").dependOptional("o1.b", "1").build()//
				.assertSatisfiable("a.b-v1")//

				.bundle("o1.b-v1").dependOptional("o2.b", "1").build()//
				.assertSatisfiable("a.b-v1", "o1.b-v1")//

				.bundle("o2.b-v1").build()//
				.assertSatisfiable("a.b-v1", "o1.b-v1", "o2.b-v1")//
		;
		lookup()//
				.bundle("a.b-v1").dependOptional("o1.b", "1").dependOptional("o2.b", "1").build()//
				.assertSatisfiable("a.b-v1")//

				.bundle("o1.b-v1").build()//
				.assertSatisfiable("a.b-v1", "o1.b-v1")//

				.bundle("o2.b-v1").build()//
				.assertSatisfiable("a.b-v1", "o1.b-v1", "o2.b-v1")//
		;

		lookup()//
				.bundle("b1.bundle-v1").depend("dep.bundle", "1.0").build()//
				.bundle("b2.bundle-v1").depend("dep.bundle", "1.0").build()//
				.bundle("dep.bundle-v1.0").build()//
				.assertMultiSatisfiable(new String[] { "b1.bundle-v1", "b2.bundle-v1" },
						new String[] { "b1.bundle-v1", "b2.bundle-v1", "dep.bundle-v1.0" })//
		;
		lookup()//
				.bundle("first.bundle-v1").depend("dep.bundle", "1").depend("intermediate.bundle", "1").build()//
				.bundle("intermediate.bundle-v1").depend("intermediate.second.bundle", "1").build()//
				.bundle("intermediate.second.bundle-v1").depend("dep.bundle", "{1|2}").build()//
				.bundle("second.bundle-v1").depend("dep.bundle", "2").depend("intermediate.bundle", "1").build()//
				.bundle("dep.bundle-v1").build()//
				.bundle("dep.bundle-v2").build()//

				.assertSatisfiable("first.bundle-v1", "dep.bundle-v1", "intermediate.bundle-v1",
						"intermediate.second.bundle-v1");

		//to check proper ordering, breadth first
		lookup()//
				.bundle("first-v1").depend("dep", "1").depend("dep2", "1").build()//
				.bundle("dep-v1").depend("third", "1").build()//
				.bundle("dep2-v1").depend("fourth", "1").build()//
				.bundle("third-v1").depend("fourth", "1").build()//
				.bundle("fourth-v1").build()//

				.assertSatisfiable("first-v1", "dep-v1", "dep2-v1", "third-v1", "fourth-v1")

		;
	}

	private static LookupContext lookup() {
		return new LookupContext();
	}

	private static class LookupContext {
		private Map<BundleIdentifier, BundleDependencyBuilder> bundles = new LinkedHashMap<>();

		public BundleDependencyBuilder bundle(String identifier) {
			return new BundleDependencyBuilder(BundleIdentifier.valueOf(identifier));
		}

		public LookupContext assertNotSatisfiable(String bundleid) {
			DependencyResolutionResult<?, ?> res = satisfy(bundleid);
			assertNull(res, "Satisfied: " + bundleid + " with " + res);
			return this;
		}

		public LookupContext assertSatisfiable(String bundleid, String... expectedbundles) {
			DependencyResolutionResult<?, ?> satisfied = satisfy(bundleid);
			assertNonNull(satisfied, "Failed to satisfy: " + bundleid);
			Set<BundleIdentifier> bundleidset = bundleIdSetOf(bundleid, expectedbundles);
			assertEquals(satisfied.getResultInAnyOrder().values().stream().map(Entry::getKey)
					.map(BundleIdentifierHolder::getBundleIdentifier)
					.collect(Collectors.toCollection(LinkedHashSet::new)), bundleidset);
			assertEquals(satisfied.getResultInDeclarationOrder().values().stream().map(Entry::getKey)
					.map(BundleIdentifierHolder::getBundleIdentifier).toArray(), bundleidset.toArray());
			satisfied.getDependencyDomainResult().entrySet().forEach(System.out::println);
			return this;
		}

		public LookupContext assertMultiSatisfiable(String[] bundleids, String[] expectedbundles) {
			DependencyResolutionResult<?, ?> satisfied = satisfyMultiple(bundleids);
			assertNonNull(satisfied, "Failed to satisfy: " + Arrays.toString(bundleids));
			Set<BundleIdentifier> bundleidset = bundleIdSetOf("pseudo.bundle-v1", expectedbundles);
			assertEquals(satisfied.getResultInAnyOrder().values().stream().map(Entry::getKey)
					.map(BundleIdentifierHolder::getBundleIdentifier)
					.collect(Collectors.toCollection(LinkedHashSet::new)), bundleidset);
			assertEquals(satisfied.getResultInDeclarationOrder().values().stream().map(Entry::getKey)
					.map(BundleIdentifierHolder::getBundleIdentifier).toArray(), bundleidset.toArray());
			satisfied.getDependencyDomainResult().entrySet().forEach(System.out::println);
			return this;
		}

		private BundleDependencyInformation createDependencyInformation(BundleIdentifier bundleid) {
			Map<BundleIdentifier, BundleDependencyList> dependencies = new LinkedHashMap<>();
			BundleDependencyBuilder depbuilder = bundles.get(bundleid);
			for (Entry<BundleIdentifier, BundleDependency.Builder> entry : depbuilder.dependencies.entrySet()) {
				dependencies.put(entry.getKey(),
						BundleDependencyList.create(Collections.singleton(entry.getValue().build())));
			}
			return BundleDependencyInformation.create(dependencies);
		}

		public DependencyResolutionResult<?, ?> satisfyMultiple(String... bundleids) {
			System.out.println();

			Map<BundleIdentifier, BundleDependencyList> dependencies = new TreeMap<>();
			for (String bidstr : bundleids) {
				BundleIdentifier bid = BundleIdentifier.valueOf(bidstr);
				dependencies.put(bid.withoutMetaQualifiers(),
						BundleDependencyList.create(Collections.singleton(BundleDependency.builder().addKind("runtime")
								.setRange(ExactVersionRange.create(bid.getVersionNumber())).build())));
			}
			BundleDependencyInformation depinfo = BundleDependencyInformation.create(dependencies);

			BundleIdentifier basebundle = BundleIdentifier.valueOf("pseudo.bundle-v1");
			DependencyResolutionResult<?, ?> satisfyres = runSatisfy(basebundle, depinfo);
			return satisfyres;
		}

		private DependencyResolutionResult<?, ?> runSatisfy(BundleIdentifier basebundle,
				BundleDependencyInformation depinfo) {
			TestDependencyResolutionLogger<Object> logger = new TestDependencyResolutionLogger<>();
			System.out.println("Start dependency resolution...");
			DependencyResolutionResult<?, ?> satisfyres = DependencyUtils
					.satisfyDependencyRequirements(new SimpleBundleKey(basebundle, null), null, depinfo, (bid, bc) -> {
						Map<String, BundleIdentifier> lookupres = new TreeMap<>(
								Collections.reverseOrder(BundleIdentifier::compareVersionQualifiers));
						outer:
						for (Entry<BundleIdentifier, BundleDependencyBuilder> entry : bundles.entrySet()) {
							BundleIdentifier entrybid = entry.getKey();
							if (!entrybid.getName().equals(bid.getName())) {
								continue outer;
							}
							if (!entrybid.getBundleQualifiers().equals(bid.getBundleQualifiers())) {
								continue outer;
							}
							lookupres.put(entrybid.getVersionQualifier(), entrybid);
						}
						Set<Entry<BundleKey, Object>> result = ObjectUtils
								.singleValueMap(toBundleKeySet(lookupres.values()), null).entrySet();
						if (result.isEmpty()) {
							logger.noBundlesFound(bid, bc);
						}
						return result;
					}, (bid, bc) -> {
						return createDependencyInformation(bid.getBundleIdentifier());
					}, logger);
			System.out.println("End.");
			return satisfyres;
		}

		private static Set<BundleKey> toBundleKeySet(Iterable<? extends BundleIdentifier> ids) {
			Set<BundleKey> result = new LinkedHashSet<>();
			for (BundleIdentifier bid : ids) {
				result.add(new SimpleBundleKey(bid, null));
			}
			return result;
		}

		public DependencyResolutionResult<?, ?> satisfy(String bundleid) {
			System.out.println();
			BundleIdentifier basebundle = BundleIdentifier.valueOf(bundleid);

			BundleDependencyInformation depinfo = createDependencyInformation(basebundle);
			DependencyResolutionResult<?, ?> satisfyres = runSatisfy(basebundle, depinfo);
			return satisfyres;
		}

		private class BundleDependencyBuilder {
			private BundleIdentifier id;
			private Map<BundleIdentifier, BundleDependency.Builder> dependencies = new LinkedHashMap<>();

			public BundleDependencyBuilder(BundleIdentifier id) {
				this.id = id;
			}

			public BundleDependencyBuilder depend(String bundleidstr, String range, boolean optional) {
				BundleIdentifier bundleid = BundleIdentifier.valueOf(bundleidstr);
				assertNull(bundleid.getVersionQualifier(), "Version qualifier in: " + bundleid);
				BundleDependency.Builder nbuilder = BundleDependency.builder().addKind("runtime")
						.setRange(VersionRange.valueOf(range));
				if (optional) {
					nbuilder.addMetaData(BundleInformation.DEPENDENCY_META_OPTIONAL, "true");
				}
				Object prev = dependencies.put(bundleid, nbuilder);
				if (prev != null) {
					throw fail("Multiple dependencies defined for: " + bundleid);
				}
				return this;
			}

			public BundleDependencyBuilder depend(String bundleidstr, String range) {
				return depend(bundleidstr, range, false);
			}

			public BundleDependencyBuilder dependOptional(String bundleidstr, String range) {
				return depend(bundleidstr, range, true);
			}

			public LookupContext build() {
				BundleDependencyBuilder prev = LookupContext.this.bundles.put(id, this);
				if (prev != null) {
					throw fail("Bundle present multiple times: " + id);
				}
				return LookupContext.this;
			}

		}
	}

	private static Set<BundleIdentifier> bundleIdSetOf(String first, String... args) {
		Set<BundleIdentifier> result = new LinkedHashSet<>();
		result.add(BundleIdentifier.valueOf(first));
		for (String a : args) {
			result.add(BundleIdentifier.valueOf(a));
		}
		return result;
	}

}
