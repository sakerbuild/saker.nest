/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package test.nest.unit;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.function.Functionals;
import saker.nest.bundle.BundleDependency;
import saker.nest.bundle.BundleDependencyInformation;
import saker.nest.bundle.BundleDependencyList;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleIdentifierHolder;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.BundleKey;
import saker.nest.bundle.SimpleBundleKey;
import saker.nest.dependency.DependencyDomainResolutionResult;
import saker.nest.dependency.DependencyResolutionResult;
import saker.nest.dependency.DependencyUtils;
import saker.nest.version.ExactVersionRange;
import saker.nest.version.VersionRange;
import testing.saker.SakerTest;
import testing.saker.SakerTestCase;

@SakerTest
@SuppressWarnings("deprecation")
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
		System.out.println("Circular");
		lookup()//
				.bundle("first.bundle-v1").depend("second.bundle", "1.0").build()//
				.bundle("second.bundle-v1.0").depend("third.bundle", "1.0").build()//
				.bundle("third.bundle-v1.0").depend("first.bundle", "1").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1.0", "third.bundle-v1.0")//
				.assertSatisfiable("second.bundle-v1.0", "third.bundle-v1.0", "first.bundle-v1")//
				.assertSatisfiable("third.bundle-v1.0", "first.bundle-v1", "second.bundle-v1.0")//
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
				.bundle("c.b-v2").build()//
				.bundle("c.b-v1").build()//
				.bundle("d.b-v1").depend("c.b", "1").build()//
				.assertSatisfiable("a.b-v1", "b.b-v1", "d.b-v1", "c.b-v1")//

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
				.bundle("a.b-v1").dependOptional("o.b", "1").build()//
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
				.bundle("a.b-v1").depend("o1.b", "1").dependOptional("o2.b", "1").build()//
				.assertNotSatisfiable("a.b-v1")//

				.bundle("o1.b-v1").build()//
				.assertSatisfiable("a.b-v1", "o1.b-v1")//

				.bundle("o2.b-v1").build()//
				.assertSatisfiable("a.b-v1", "o1.b-v1", "o2.b-v1")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").depend("b", "2").build()//
				.assertNotSatisfiable("a-v1")//
		;

		lookup()//
				.bundle("b1.bundle-v1").depend("dep.bundle", "1").build()//
				.bundle("b2.bundle-v1").depend("dep.bundle", "1").build()//
				.bundle("dep.bundle-v1").build()//
				.assertMultiSatisfiable(new String[] { "b1.bundle-v1", "b2.bundle-v1" },
						new String[] { "b1.bundle-v1", "b2.bundle-v1", "dep.bundle-v1" })//
		;
		lookup()//
				.bundle("first.bundle-v1").depend("dep.bundle", "1").depend("intermediate.bundle", "1").build()//
				.bundle("intermediate.bundle-v1").depend("intermediate.second.bundle", "1").build()//
				.bundle("intermediate.second.bundle-v1").depend("dep.bundle", "{1|2}").build()//
				.bundle("second.bundle-v1").depend("dep.bundle", "2").depend("intermediate.bundle", "1").build()//
				.bundle("dep.bundle-v1").build()//
				.bundle("dep.bundle-v2").build()//

				.assertSatisfiable("first.bundle-v1", "dep.bundle-v1", "intermediate.bundle-v1",
						"intermediate.second.bundle-v1")//
		;

		//to check proper ordering, breadth first
		lookup()//
				.bundle("first-v1").depend("dep", "1").depend("dep2", "1").build()//
				.bundle("dep-v1").depend("third", "1").build()//
				.bundle("dep2-v1").depend("fourth", "1").build()//
				.bundle("third-v1").depend("fourth", "1").build()//
				.bundle("fourth-v1").build()//

				.assertSatisfiable("first-v1", "dep-v1", "dep2-v1", "third-v1", "fourth-v1")//
		;

		//the test from the related issue: https://github.com/sakerbuild/saker.nest/issues/5
		lookup()//
				.bundle("a-v1").depend("c", "1").depend("b", "1").build()//
				.bundle("b-v1").dependPrivate("c", "[1, 3]").build()//
				.bundle("c-v1").build()//
				.bundle("c-v2").build()//
				.bundle("c-v3").build()//

				.assertSatisfiable("a-v1", "c-v1", "b-v1", "c-v3")//
		;

		//circular private
		System.out.println("Circular private");
		lookup()//
				.bundle("first.bundle-v1").dependPrivate("second.bundle", "1").build()//
				.bundle("second.bundle-v1").dependPrivate("third.bundle", "1").build()//
				.bundle("third.bundle-v1").dependPrivate("first.bundle", "1").build()//
				.assertSatisfiable("first.bundle-v1", "second.bundle-v1", "third.bundle-v1")//
				.assertSatisfiable("second.bundle-v1", "third.bundle-v1", "first.bundle-v1")//
				.assertSatisfiable("third.bundle-v1", "first.bundle-v1", "second.bundle-v1")//
		;

		//alternating circular private
		lookup()//
				.bundle("a-v1").depend("x", "1").build()//
				.bundle("x-v1").dependPrivate("b", "1").build()//
				.bundle("b-v1").depend("y", "1").build()//
				.bundle("y-v1").dependPrivate("c", "1").build()//
				.bundle("c-v1").depend("z", "1").build()//
				.bundle("z-v1").dependPrivate("a", "1").build()//
				.assertSatisfiable("a-v1", "x-v1", "b-v1", "y-v1", "c-v1", "z-v1")//
				.assertSatisfiable("x-v1", "b-v1", "y-v1", "c-v1", "z-v1", "a-v1")//
				.assertSatisfiable("b-v1", "y-v1", "c-v1", "z-v1", "a-v1", "x-v1")//
				.assertSatisfiable("y-v1", "c-v1", "z-v1", "a-v1", "x-v1", "b-v1")//
				.assertSatisfiable("c-v1", "z-v1", "a-v1", "x-v1", "b-v1", "y-v1")//
				.assertSatisfiable("z-v1", "a-v1", "x-v1", "b-v1", "y-v1", "c-v1")//
		;

		//satisfy optionals of private dependencies
		lookup()//
				.bundle("first-v1").dependPrivate("dep", "1").build()//
				.bundle("dep-v1").dependOptional("opt", "1").build()//
				.assertSatisfiable("first-v1", "dep-v1")

				.bundle("opt-v1").build()//
				.assertSatisfiable("first-v1", "dep-v1", "opt-v1")//
		;

		//check that outside pinned bundle affects private resolution
		lookup()//
				.bundle("first-v1").depend("opt", "1").dependPrivate("dep", "1").build()//
				.bundle("dep-v1").dependOptional("opt", "2").build()//
				.bundle("opt-v1").build()//
				.bundle("opt-v2").build()//
				.assertSatisfiable("first-v1", "opt-v1", "dep-v1")//
		;
		//the non-private version shouldn't include v2
		lookup()//
				.bundle("first-v1").depend("opt", "1").depend("dep", "1").build()//
				.bundle("dep-v1").dependOptional("opt", "2").build()//
				.bundle("opt-v1").build()//
				.bundle("opt-v2").build()//
				.assertSatisfiable("first-v1", "opt-v1", "dep-v1")//
		;
		//the non-private non-optional version should fail
		lookup()//
				.bundle("first-v1").depend("opt", "1").depend("dep", "1").build()//
				.bundle("dep-v1").depend("opt", "2").build()//
				.bundle("opt-v1").build()//
				.bundle("opt-v2").build()//
				.assertNotSatisfiable("first-v1")//
		;
		lookup()//
				.bundle("first-v1").depend("opt", "1").dependPrivate("dep", "1").build()//
				.bundle("dep-v1").depend("opt", "2").build()//
				.bundle("opt-v1").build()//
				.bundle("opt-v2").build()//
				.assertNotSatisfiable("first-v1")//
		;

		lookup()//
				.bundle("bundle-v1").dependPrivateOptional("dep", "1").build()//
				.assertSatisfiable("bundle-v1")//

				.bundle("dep-v1").build()//
				.assertSatisfiable("bundle-v1", "dep-v1")//
		;

		lookup()//
				.bundle("bundle-v1").dependPrivate("dep", "1").build()//
				.bundle("dep-v2").build()//

				.assertNotSatisfiable("bundle-v1")//
		;
		lookup()//
				.bundle("bundle-v1").depend("dep", "1").dependPrivateOptional("opt", "1").build()//
				.assertNotSatisfiable("bundle-v1")//

				.bundle("dep-v1").dependOptional("opt", "2").build()//
				.assertSatisfiable("bundle-v1", "dep-v1")//

				.bundle("opt-v1").build()//
				.assertSatisfiable("bundle-v1", "dep-v1", "opt-v1")//
				.bundle("opt-v2").build()//
				.assertSatisfiable("bundle-v1", "dep-v1", "opt-v1")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").build()//
				.bundle("b-v1").depend("c", "1").build()//
				.bundle("b-v2").depend("c", "1").build()//
				.bundle("c-v1").dependPrivate("d", "1").build()//
				.bundle("d-v1").depend("b", "2").build()//
				.assertSatisfiable("a-v1", "b-v1", "c-v1", "d-v1", "b-v2")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").depend("d", "2").build()//
				.bundle("b-v1").depend("c", "1").build()//
				.bundle("b-v2").depend("c", "1").build()//
				.bundle("c-v1").dependPrivate("d", "1").build()//
				.bundle("d-v1").depend("b", "1").build()//
				.bundle("d-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "d-v2", "c-v1", "d-v1")//
		;

		//DifferentDomainPrivateDependencyTest with invalid
		lookup()//
				.bundle("a-v1").dependPrivate("b", "1").dependPrivate("c", "1").build()//
				.bundle("b-v1").depend("x", "1").depend("d", "2").build()//
				.bundle("c-v1").depend("x", "1").depend("d", "1").build()//
				.bundle("d-v1").build()//
				.bundle("d-v2").build()//
				.bundle("x-v1").depend("d", "[1, 2]").build()//
				.assertSatisfiable("b-v1", "x-v1", "d-v2")//
				.assertSatisfiable("c-v1", "x-v1", "d-v1")//
				.assertNotSatisfiable("a-v1")//
		;

		//DifferentDomainPrivateDependencyTest with valid
		lookup()//
				.bundle("a-v1").depend("b", "1").depend("c", "1").build()//
				.bundle("b-v1").dependPrivate("x", "1").dependPrivate("d", "2").build()//
				.bundle("c-v1").dependPrivate("x", "1").dependPrivate("d", "1").build()//
				.bundle("d-v1").build()//
				.bundle("d-v2").build()//
				.bundle("x-v1").depend("d", "[1, 2]").build()//
				.assertSatisfiable("b-v1", "x-v1", "d-v2")//
				.assertSatisfiable("c-v1", "x-v1", "d-v1")//
				.assertRecurrSatisfiable(false,"a-v1", "b-v1", "c-v1", "x-v1", "d-v2", "d-v1")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").build()//
				.bundle("b-v1").depend("c", "1").build()//
				.bundle("b-v2").depend("c", "1").build()//
				.bundle("c-v1").dependPrivate("a", "2").build()//
				.assertNotSatisfiable("a-v1")//
				.bundle("a-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "c-v1", "a-v2")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "[1, 2]").depend("b", "[2, 3]").build()//
				.bundle("b-v1").build()//
				.assertNotSatisfiable("a-v1")//
				.bundle("b-v3").build()//
				.assertNotSatisfiable("a-v1")//
				.bundle("b-v2").build()//
				.assertSatisfiable("a-v1", "b-v2")//
		;

		lookup()//
				.bundle("a-v1").dependPrivate("b", "1").dependPrivate("b", "2").build()//
				.assertNotSatisfiable("a-v1")//
				.bundle("b-v1").build()//
				.assertNotSatisfiable("a-v1")//
				.bundle("b-v2").build()//
				.assertNotSatisfiable("a-v1")//
		;

		lookup()//
				.bundle("a-v1").depend("p", "1").dependPrivate("b", "1").build()//
				.bundle("b-v1").depend("p", "[1, 2]").dependPrivate("c", "1").build()//
				.bundle("c-v1").depend("p", "[1, 3]").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.bundle("p-v3").build()//
				.assertSatisfiable("a-v1", "p-v1", "b-v1", "c-v1")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivate("p", "[1,2]").build()//
				.bundle("b-v1").depend("p", "2").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "p-v2")//
		;
		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivate("p", "[1,2]").build()//
				.bundle("b-v1").dependOptional("p", "1").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "p-v2")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivate("p", "1").build()//
				.bundle("b-v1").depend("p", "2").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertNotSatisfiable("a-v1")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivate("p", "1").build()//
				.bundle("b-v1").dependOptional("p", "2").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "p-v1")//
		;

		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivate("p", "1").build()//
				.bundle("b-v1").dependPrivate("p", "2").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "p-v1", "p-v2")//
		;
		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivate("p", "1").build()//
				.bundle("b-v1").dependPrivateOptional("p", "2").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "p-v1", "p-v2")//
		;
		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivateOptional("p", "1").build()//
				.bundle("b-v1").dependPrivate("p", "2").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "p-v1", "p-v2")//
		;
		lookup()//
				.bundle("a-v1").depend("b", "1").dependPrivateOptional("p", "1").build()//
				.bundle("b-v1").dependPrivateOptional("p", "2").build()//
				.bundle("p-v1").build()//
				.bundle("p-v2").build()//
				.assertSatisfiable("a-v1", "b-v1", "p-v1", "p-v2")//
		;

		lookup()//
				.bundle("sjc-api-v1").depend("sjc-impl", "1").build()//
				.bundle("sjc-impl-v1").depend("sjc-api", "1").build()//
				.assertSatisfiable("sjc-api-v1", "sjc-impl-v1")//
				.assertSatisfiable("sjc-impl-v1", "sjc-api-v1")//
				.assertNonRecurringDomains("sjc-api-v1")//
		;
	}

	private static LookupContext lookup() {
		return new LookupContext();
	}

	private static class LookupContext {
		private static final BundleIdentifier PSEUDO_BASE_BUNDLE_ID = BundleIdentifier.valueOf("pseudo.bundle-v1");

		private Map<BundleIdentifier, BundleDependencyBuilder> bundles = new LinkedHashMap<>();

		public BundleDependencyBuilder bundle(String identifier) {
			return new BundleDependencyBuilder(BundleIdentifier.valueOf(identifier));
		}

		public void assertNonRecurringDomains(String bundleid) {
			DependencyDomainResolutionResult<?, ?> res = satisfy(bundleid);
			assertNonNull(res, "Failed to satisfy: " + bundleid);
			printSatisfiedDomain(bundleid, res);
			assertNonRecurringDomainsImpl(bundleid, res);
		}

		private void assertNonRecurringDomainsImpl(String bundleid, DependencyDomainResolutionResult<?, ?> res) {
			HashMap<BundleIdentifier, DependencyDomainResolutionResult<?, ?>> domains = new HashMap<>();
			domains.put(BundleIdentifier.valueOf(bundleid), res);
			assertNonRecurringDomainsImpl(res, domains);
		}

		private void assertNonRecurringDomainsImpl(DependencyDomainResolutionResult<?, ?> domain,
				Map<BundleIdentifier, DependencyDomainResolutionResult<?, ?>> found) {
			for (Entry<? extends Entry<? extends BundleIdentifierHolder, ?>, ? extends DependencyDomainResolutionResult<?, ?>> entry : domain
					.getDirectDependencies().entrySet()) {
				BundleIdentifier bid = entry.getKey().getKey().getBundleIdentifier();
				DependencyDomainResolutionResult<?, ?> prev = found.putIfAbsent(bid, entry.getValue());
				if (prev != null) {
					if (!prev.equals(entry.getValue())) {
//					if (prev != entry.getValue()) {
						System.out.println("First: ");
						printSatisfiedDomain(bid.toString(), prev);
						System.out.println("Second: ");
						printSatisfiedDomain(bid.toString(), entry.getValue());
						System.out.println("The domains equal: " + prev.equals(entry.getValue()));
						throw new AssertionError("Duplicate domains for: " + bid);
					}
				} else {
					assertNonRecurringDomainsImpl(entry.getValue(), found);
				}
			}

		}

		public LookupContext assertNotSatisfiable(String bundleid) {
			DependencyDomainResolutionResult<?, ?> res = satisfy(bundleid);
			if (res != null) {
				fail("Satisfied: " + bundleid + " with " + Arrays
						.toString(toBundleIdentifiers(res, getBaseBundleEntry(BundleIdentifier.valueOf(bundleid)))));
			}
			return this;
		}

		public LookupContext assertRecurrSatisfiable(boolean checkrecurring, String bundleid,
				String... expectedbundles) {
			DependencyDomainResolutionResult<?, ?> satisfied = satisfy(bundleid);
			assertNonNull(satisfied, "Failed to satisfy: " + bundleid);
			printSatisfiedDomain(bundleid, satisfied);
			Set<BundleIdentifier> bundleidset = bundleIdSetOf(bundleid, expectedbundles);

			if (checkrecurring) {
				assertNonRecurringDomainsImpl(bundleid, satisfied);
			}

			assertResults(satisfied, bundleidset, getBaseBundleEntry(BundleIdentifier.valueOf(bundleid)));

			try {
				DependencyResolutionResult<?, ?> satisfiedold = satisfyOld(bundleid);
				assertNonNull(satisfiedold, "Failed to satisfy: " + bundleid);
				assertResultsOld(satisfiedold, bundleidset, getBaseBundleEntry(BundleIdentifier.valueOf(bundleid)));
			} catch (UnsupportedOperationException e) {
				System.out.println(e);
			}

			return this;
		}

		public LookupContext assertSatisfiable(String bundleid, String... expectedbundles) {
			return assertRecurrSatisfiable(true, bundleid, expectedbundles);
		}

		private static BundleIdentifier[] toBundleIdentifiers(DependencyDomainResolutionResult<?, ?> satisfied,
				Entry<? extends BundleIdentifierHolder, ?> bundlekeyentry) {
			LinkedHashSet<BundleIdentifier> result = new LinkedHashSet<>();
			result.add(bundlekeyentry.getKey().getBundleIdentifier());
			HashSet<Entry<? extends BundleIdentifierHolder, ?>> collecteds = new HashSet<>();
			collecteds.add(bundlekeyentry);
			collectBundleIdentifiers(satisfied, result, collecteds);
			return result.toArray(new BundleIdentifier[0]);
		}

		private static void collectBundleIdentifiers(DependencyDomainResolutionResult<?, ?> satisfied,
				Set<BundleIdentifier> bundleids, Set<Entry<? extends BundleIdentifierHolder, ?>> collectedbundles) {
			Map<? extends Entry<? extends BundleIdentifierHolder, ?>, ? extends DependencyDomainResolutionResult<?, ?>> deps = satisfied
					.getDirectDependencies();
			//add first and collect later for order
			for (Entry<? extends Entry<? extends BundleIdentifierHolder, ?>, ? extends DependencyDomainResolutionResult<?, ?>> entry : deps
					.entrySet()) {
				bundleids.add(entry.getKey().getKey().getBundleIdentifier());
			}
			for (Entry<? extends Entry<? extends BundleIdentifierHolder, ?>, ? extends DependencyDomainResolutionResult<?, ?>> entry : deps
					.entrySet()) {
				if (!collectedbundles.add(entry.getKey())) {
					continue;
				}
				collectBundleIdentifiers(entry.getValue(), bundleids, collectedbundles);
			}
		}

		private static void assertResultsOld(DependencyResolutionResult<?, ?> satisfied,
				Set<BundleIdentifier> bundleidset, Entry<? extends BundleIdentifierHolder, ?> basebundleentry) {
			BundleIdentifier[] domainresultbundleidarray = satisfied.getDependencyDomainResult().keySet().stream()
					.map(Entry::getKey).map(BundleIdentifierHolder::getBundleIdentifier)
					.toArray(BundleIdentifier[]::new);
			assertResults(bundleidset, domainresultbundleidarray);

			BundleIdentifier[] declresultbundleidarray = satisfied.getResultInDeclarationOrder().values().stream()
					.map(Entry::getKey).map(BundleIdentifierHolder::getBundleIdentifier)
					.toArray(BundleIdentifier[]::new);
			assertResults(bundleidset, declresultbundleidarray);
		}

		private static void assertResults(DependencyDomainResolutionResult<?, ?> satisfied,
				Set<BundleIdentifier> bundleidset, Entry<? extends BundleIdentifierHolder, ?> basebundleentry)
				throws AssertionError {
			//assert that it contains the same bundles

			BundleIdentifier[] domainresultbundleidarray = toBundleIdentifiers(satisfied, basebundleentry);
			assertResults(bundleidset, domainresultbundleidarray);
		}

		private static void assertResults(Set<BundleIdentifier> bundleidset, BundleIdentifier[] resultbundleidarray)
				throws AssertionError {
			assertEquals(ObjectUtils.newLinkedHashSet(resultbundleidarray), bundleidset);
			//assert that it contains the same bundles in the same order
			assertEquals(resultbundleidarray, bundleidset.toArray(new BundleIdentifier[0]));
		}

		public LookupContext assertMultiSatisfiable(String[] bundleids, String[] expectedbundles) {
			DependencyDomainResolutionResult<?, ?> satisfied = satisfyMultiple(bundleids);
			assertNonNull(satisfied, "Failed to satisfy: " + Arrays.toString(bundleids));
			printSatisfiedDomain(null, satisfied);
			Set<BundleIdentifier> bundleidset = bundleIdSetOf(PSEUDO_BASE_BUNDLE_ID.toString(), expectedbundles);

			assertResults(satisfied, bundleidset, getBaseBundleEntry(PSEUDO_BASE_BUNDLE_ID));

			try {
				DependencyResolutionResult<?, ?> satisfiedold = satisfyMultipleOld(bundleids);
				assertNonNull(satisfiedold, "Failed to satisfy: " + Arrays.toString(bundleids));
				assertResultsOld(satisfiedold, bundleidset, getBaseBundleEntry(PSEUDO_BASE_BUNDLE_ID));
			} catch (UnsupportedOperationException e) {
				System.out.println(e);
			}

			return this;
		}

		private BundleDependencyInformation createDependencyInformation(BundleIdentifier bundleid) {
			Map<BundleIdentifier, BundleDependencyList> dependencies = new LinkedHashMap<>();
			BundleDependencyBuilder depbuilder = bundles.get(bundleid);
			for (Entry<BundleIdentifier, List<BundleDependency>> entry : depbuilder.dependencies.entrySet()) {
				dependencies.put(entry.getKey(), BundleDependencyList.create(entry.getValue()));
			}
			return BundleDependencyInformation.create(dependencies);
		}

		public DependencyDomainResolutionResult<?, ?> satisfyMultiple(String... bundleids) {
			System.out.println();

			Map<BundleIdentifier, BundleDependencyList> dependencies = new TreeMap<>();
			for (String bidstr : bundleids) {
				BundleIdentifier bid = BundleIdentifier.valueOf(bidstr);
				dependencies.put(bid.withoutMetaQualifiers(),
						BundleDependencyList.create(Collections.singleton(BundleDependency.builder().addKind("runtime")
								.setRange(ExactVersionRange.create(bid.getVersionNumber())).build())));
			}
			BundleDependencyInformation depinfo = BundleDependencyInformation.create(dependencies);

			DependencyDomainResolutionResult<?, ?> satisfyres = runSatisfy(PSEUDO_BASE_BUNDLE_ID, depinfo);
			return satisfyres;
		}

		public DependencyResolutionResult<?, ?> satisfyMultipleOld(String... bundleids) {
			System.out.println();

			Map<BundleIdentifier, BundleDependencyList> dependencies = new TreeMap<>();
			for (String bidstr : bundleids) {
				BundleIdentifier bid = BundleIdentifier.valueOf(bidstr);
				dependencies.put(bid.withoutMetaQualifiers(),
						BundleDependencyList.create(Collections.singleton(BundleDependency.builder().addKind("runtime")
								.setRange(ExactVersionRange.create(bid.getVersionNumber())).build())));
			}
			BundleDependencyInformation depinfo = BundleDependencyInformation.create(dependencies);

			DependencyResolutionResult<?, ?> satisfyres = runSatisfyOld(PSEUDO_BASE_BUNDLE_ID, depinfo);
			return satisfyres;
		}

		private static Entry<? extends BundleIdentifierHolder, ?> getBaseBundleEntry(BundleIdentifier bundleid) {
			return ImmutableUtils.makeImmutableMapEntry(new SimpleBundleKey(bundleid, null), null);
		}

		private DependencyDomainResolutionResult<?, ?> runSatisfy(BundleIdentifier basebundle,
				BundleDependencyInformation depinfo) {
			TestDependencyResolutionLogger<Object> logger = new TestDependencyResolutionLogger<>();
			System.out.println("Start dependency resolution...");
			DependencyDomainResolutionResult<?, ?> satisfyres = DependencyUtils
					.satisfyDependencyDomain(new SimpleBundleKey(basebundle, null), null, depinfo, (bid, bc) -> {
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

		private DependencyResolutionResult<?, ?> runSatisfyOld(BundleIdentifier basebundle,
				BundleDependencyInformation depinfo) {
			TestDependencyResolutionLogger<Object> logger = new TestDependencyResolutionLogger<>();
			System.out.println("Start dependency resolution... (old)");
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

		public DependencyDomainResolutionResult<?, ?> satisfy(String bundleid) {
			System.out.println();
			BundleIdentifier basebundle = BundleIdentifier.valueOf(bundleid);

			BundleDependencyInformation depinfo = createDependencyInformation(basebundle);
			DependencyDomainResolutionResult<?, ?> satisfyres = runSatisfy(basebundle, depinfo);
			return satisfyres;
		}

		public DependencyResolutionResult<?, ?> satisfyOld(String bundleid) {
			System.out.println();
			BundleIdentifier basebundle = BundleIdentifier.valueOf(bundleid);

			BundleDependencyInformation depinfo = createDependencyInformation(basebundle);
			DependencyResolutionResult<?, ?> satisfyres = runSatisfyOld(basebundle, depinfo);
			return satisfyres;
		}

		private class BundleDependencyBuilder {
			private BundleIdentifier id;
			private Map<BundleIdentifier, List<BundleDependency>> dependencies = new LinkedHashMap<>();

			private Set<String> usedDepKinds = new TreeSet<>();
			//set seed for determinism
			private Random random = new Random(123456789L);

			public BundleDependencyBuilder(BundleIdentifier id) {
				this.id = id;
			}

			private String getDependencyKindName() {
				byte[] bytes = new byte[16];
				while (true) {
					random.nextBytes(bytes);
					String name = StringUtils.toHexString(bytes);
					if (usedDepKinds.add(name)) {
						return name;
					}
				}
			}

			public BundleDependencyBuilder depend(String bundleidstr, String range, Boolean optional,
					Boolean privatedep) {
				BundleIdentifier bundleid = BundleIdentifier.valueOf(bundleidstr);
				assertNull(bundleid.getVersionQualifier(), "Version qualifier in: " + bundleid);
				BundleDependency.Builder nbuilder = BundleDependency.builder().addKind(getDependencyKindName())
						.setRange(VersionRange.valueOf(range));
				if (optional != null) {
					nbuilder.addMetaData(BundleInformation.DEPENDENCY_META_OPTIONAL, optional.toString());
				}
				if (privatedep != null) {
					nbuilder.addMetaData(BundleInformation.DEPENDENCY_META_PRIVATE, privatedep.toString());
				}
				dependencies.computeIfAbsent(bundleid, Functionals.arrayListComputer()).add(nbuilder.build());
				return this;
			}

			public BundleDependencyBuilder depend(String bundleidstr, String range, Boolean optional) {
				return depend(bundleidstr, range, optional, null);
			}

			public BundleDependencyBuilder depend(String bundleidstr, String range) {
				return depend(bundleidstr, range, null);
			}

			public BundleDependencyBuilder dependOptional(String bundleidstr, String range) {
				return depend(bundleidstr, range, true);
			}

			public BundleDependencyBuilder dependPrivate(String bundleidstr, String range) {
				return depend(bundleidstr, range, null, true);
			}

			public BundleDependencyBuilder dependPrivateOptional(String bundleidstr, String range) {
				return depend(bundleidstr, range, true, true);
			}

			public LookupContext build() {
				BundleDependencyBuilder prev = LookupContext.this.bundles.put(id, this);
				if (prev != null) {
					throw fail("Bundle present multiple times: " + id);
				}
				return LookupContext.this;
			}

		}

		private static void printSatisfiedDomain(String startbundle, DependencyDomainResolutionResult<?, ?> satisfied) {
			String str = satisfied.toString();
			StringBuilder sb = new StringBuilder();
			if (startbundle != null) {
				sb.append(startbundle);
				sb.append(": ");
			}
			int len = str.length();
			String tab = "";
			for (int i = 0; i < len; i++) {
				char c = str.charAt(i);
				if (c == '}') {
					tab = tab.substring(0, tab.length() - 4);
					sb.append('\n');
					sb.append(tab);
					sb.append('}');
					if (i + 1 < len) {
						char n = str.charAt(i + 1);
						if (n == ',') {
							++i;
							if (str.charAt(i + 1) == ' ') {
								++i;
							}
							sb.append(',');
							sb.append('\n');
							sb.append(tab);
							continue;
						}
					}
				} else if (c == '{') {
					if (str.charAt(i + 1) == '}') {
						sb.append('{');
						sb.append('}');
						++i;
						continue;
					}
					tab += "    ";
					sb.append('{');
					sb.append('\n');
					sb.append(tab);
				} else if (c == ',') {
					if (str.charAt(i + 1) == ' ') {
						++i;
					}
					sb.append(',');
					sb.append('\n');
					sb.append(tab);
				} else {
					sb.append(c);
				}
			}
			System.out.println(sb);
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
