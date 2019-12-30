package saker.nest.bundle;

import java.nio.file.Path;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ObjectUtils;

public class BundleUtils {
	public static BundleIdentifier requireVersioned(BundleIdentifier bundleid) {
		Objects.requireNonNull(bundleid, "bundle id");
		if (bundleid.getVersionQualifier() == null) {
			throw new IllegalArgumentException("Version qualifier is missing from bundle identifier: " + bundleid);
		}
		return bundleid;
	}

	public static boolean isTaskNameDenotesASpecificBundle(TaskName taskname) {
		String versionqualifier = BundleIdentifier.getVersionQualifier(taskname.getTaskQualifiers());
		return versionqualifier != null;
	}

	public static BundleIdentifier selectAppropriateBundleIdentifierForTask(TaskName taskname,
			Set<? extends BundleIdentifier> bundles) {
		if (ObjectUtils.isNullOrEmpty(bundles)) {
			return null;
		}
		NavigableSet<String> taskqualifiers = taskname.getTaskQualifiers();
		String versionqualifier = BundleIdentifier.getVersionQualifier(taskqualifiers);
		if (versionqualifier == null) {
			//no version specified
			//choose among the bundles with the highest version
			String highestversion = null;
			BundleIdentifier highestbundle = null;
			for (BundleIdentifier bundleid : bundles) {
				NavigableSet<String> bundlequalifiers = bundleid.getBundleQualifiers();
				if (!bundlequalifiers.equals(taskqualifiers)) {
					//bundle qualifiers mismatch
					continue;
				}
				String bundlever = bundleid.getVersionQualifier();
				if (bundlever == null) {
					//the bundle has no version qualifier either, can be used
					return bundleid;
				}
				if (highestversion == null) {
					highestversion = bundlever;
					highestbundle = bundleid;
				} else {
					if (BundleIdentifier.compareVersionQualifiers(highestversion, bundlever) < 0) {
						highestversion = bundlever;
						highestbundle = bundleid;
					}
				}
			}
			return highestbundle;
		}
		//version was specified, match all qualifiers appropriately
		for (BundleIdentifier bundleid : bundles) {
			if (!versionqualifier.equals(bundleid.getVersionQualifier())) {
				//different version
				continue;
			}
			NavigableSet<String> bundleq = bundleid.getBundleQualifiers();
			if (bundleq.size() + 1 == taskqualifiers.size() && taskqualifiers.containsAll(bundleq)) {
				return bundleid;
			}
		}
		return null;
	}

	public static Path getVersionedBundleJarPath(Path basedir, BundleIdentifier bundleid) {
		String version = bundleid.getVersionQualifier();
		Path result = basedir.resolve(bundleid.getName());
		if (version != null) {
			result = result.resolve(version);
		} else {
			//default version directory for bundles that have no version specified
			result = result.resolve("v");
		}
		return result.resolve(bundleid.toString() + ".jar");
	}

	private BundleUtils() {
		throw new UnsupportedOperationException();
	}
}
