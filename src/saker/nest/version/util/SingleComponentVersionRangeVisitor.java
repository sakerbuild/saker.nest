package saker.nest.version.util;

import saker.nest.version.BaseVersionVersionRange;
import saker.nest.version.BoundedVersionRange;
import saker.nest.version.ExactVersionRange;
import saker.nest.version.IntersectionVersionRange;
import saker.nest.version.MaximumVersionRange;
import saker.nest.version.MinimumVersionRange;
import saker.nest.version.UnionVersionRange;
import saker.nest.version.UnsatisfiableVersionRange;
import saker.nest.version.VersionRange;
import saker.nest.version.VersionRangeVisitor;

public class SingleComponentVersionRangeVisitor implements VersionRangeVisitor<String, Void> {
	public static final SingleComponentVersionRangeVisitor INSTANCE = new SingleComponentVersionRangeVisitor();

	public String getNonSingleComponentVersion(VersionRange range) {
		return range.accept(this, null);
	}

	@Override
	public String visit(IntersectionVersionRange range, Void param) {
		for (VersionRange r : range.getRanges()) {
			String sub = r.accept(this, param);
			if (sub != null) {
				return sub;
			}
		}
		return null;
	}

	@Override
	public String visit(BoundedVersionRange range, Void param) {
		String ver = range.getLeftBoundVersion();
		if (ver.indexOf('.') >= 0) {
			return ver;
		}
		ver = range.getRightBoundVersion();
		if (ver.indexOf('.') >= 0) {
			return ver;
		}
		return null;
	}

	@Override
	public String visit(ExactVersionRange range, Void param) {
		String ver = range.getVersion();
		if (ver.indexOf('.') >= 0) {
			return ver;
		}
		return null;
	}

	@Override
	public String visit(MaximumVersionRange range, Void param) {
		String ver = range.getMaximumVersion();
		if (ver.indexOf('.') >= 0) {
			return ver;
		}
		return null;
	}

	@Override
	public String visit(MinimumVersionRange range, Void param) {
		String ver = range.getMinimumVersion();
		if (ver.indexOf('.') >= 0) {
			return ver;
		}
		return null;
	}

	@Override
	public String visit(UnionVersionRange range, Void param) {
		for (VersionRange r : range.getRanges()) {
			String sub = r.accept(this, param);
			if (sub != null) {
				return sub;
			}
		}
		return null;
	}

	@Override
	public String visit(BaseVersionVersionRange range, Void param) {
		String ver = range.getBaseVersion();
		if (ver.indexOf('.') >= 0) {
			return ver;
		}
		return null;
	}

	@Override
	public String visit(UnsatisfiableVersionRange range, Void param) {
		return null;
	}

}
