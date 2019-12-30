package test.nest.unit;

import saker.nest.bundle.BundleDependency;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.dependency.DependencyResolutionLogger;

public class TestDependencyResolutionLogger<BC> implements DependencyResolutionLogger<BC> {
	private StringBuilder sb = new StringBuilder();

	@Override
	public void enter(BundleIdentifier bundleid, BC bundlecontext) {
		System.out.println(sb + "Enter " + bundleid);
		sb.append("    ");
	}

	@Override
	public void enterVersion(BundleIdentifier bundleid, BC bundlecontext) {
		System.out.println(sb + "Version " + bundleid);
		sb.append("|   ");
	}

	@Override
	public void exitVersion(BundleIdentifier bundleid, BC bundlecontext) {
		sb.delete(sb.length() - 4, sb.length());
		System.out.println(sb + "|---Exit version " + bundleid);
	}

	@Override
	public void exit(BundleIdentifier bundleid, BC bundlecontext, BundleIdentifier matchedidentifier,
			BC matchedbundlecontext) {
		sb.delete(sb.length() - 4, sb.length());
		if (matchedidentifier != null) {
			System.out.println(sb + "+ Matched " + bundleid + " -> " + matchedidentifier);
		} else {
			System.out.println(sb + "- Failed " + bundleid);
		}
	}

	@Override
	public void dependencyVersionRangeMismatchForPinnedBundle(BundleDependency dependency,
			BundleIdentifier pinnedbundleid, BC pinnedbundlecontext) {
		System.out.println(sb + "Conflict on bundle: " + pinnedbundleid + " for range: " + dependency.getRange());
	}

	@Override
	public void dependencyVersionRangeMismatch(BundleDependency dependency, BundleIdentifier bundleid,
			BC bundlecontext) {
		System.out.println(sb + "Version mismatch: " + bundleid + " for: " + dependency.getRange());
	}

	@Override
	public void dependencyFoundPinned(BundleIdentifier dependency, BC bundlecontext, BundleIdentifier pinnedbundle,
			BC pinnedbundlecontext) {
		System.out.println(sb + "Found pinned: " + dependency + " -> " + pinnedbundle);
	}

	public void noBundlesFound(BundleIdentifier bundleid, BC bundlecontext) {
		System.out.println(sb + "No bundles found: " + bundleid);
	}
}