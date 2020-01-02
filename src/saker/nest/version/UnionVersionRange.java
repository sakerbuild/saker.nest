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
package saker.nest.version;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;
import java.util.Set;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Version range that includes any of the version ranges that are part of the union.
 * <p>
 * This class will include a version number if <i>any</i> of the enclosed version ranges include it.
 * <p>
 * It is recommended to use {@link VersionRange#valueOf(String)} to create new version ranges than using this class
 * directly.
 */
public final class UnionVersionRange implements VersionRange, Externalizable {
	private static final long serialVersionUID = 1L;

	private Set<? extends VersionRange> ranges;

	/**
	 * For {@link Externalizable}.
	 */
	public UnionVersionRange() {
	}

	private UnionVersionRange(Set<? extends VersionRange> ranges) throws NullPointerException {
		this.ranges = ranges;
	}

	@Override
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) {
		Objects.requireNonNull(visitor, "visitor");
		return visitor.visit(this, param);
	}

	/**
	 * Creates a new instance that contains the specified version ranges.
	 * <p>
	 * The method may optimize the argument, e.g. if it only contains a single element, it will be returned without
	 * constructing a new union instance, as they are semantically the same.
	 * 
	 * @param ranges
	 *            An array of version ranges.
	 * @return The version range that is the union of the argument elements.
	 * @throws NullPointerException
	 *             If the argument or any of the elements are <code>null</code>.
	 */
	public static VersionRange create(VersionRange... ranges) throws NullPointerException {
		ObjectUtils.requireNonNullElements(ranges);
		Set<VersionRange> rangeset = ImmutableUtils.makeImmutableHashSet(ranges);
		if (rangeset.isEmpty()) {
			return UnsatisfiableVersionRange.INSTANCE;
		}
		if (rangeset.size() == 1) {
			return rangeset.iterator().next();
		}
		return new UnionVersionRange(rangeset);
	}

	/**
	 * Creates a new instance that contains the specified version ranges.
	 * <p>
	 * The method may optimize the argument, e.g. if it only contains a single element, it will be returned without
	 * constructing a new union instance, as they are semantically the same.
	 * 
	 * @param ranges
	 *            A set of version ranges.
	 * @return The version range that is the union of the argument elements.
	 * @throws NullPointerException
	 *             If the argument or any of the elements are <code>null</code>.
	 */
	public static VersionRange create(Set<? extends VersionRange> ranges) throws NullPointerException {
		ObjectUtils.requireNonNullElements(ranges);
		if (ranges.isEmpty()) {
			return UnsatisfiableVersionRange.INSTANCE;
		}
		if (ranges.size() == 1) {
			return ranges.iterator().next();
		}
		return new UnionVersionRange(ImmutableUtils.makeImmutableHashSet(ranges));
	}

	/**
	 * Gets the enclosed version ranges that this union contains.
	 * 
	 * @return An immutable set of version ranges.
	 */
	public Set<? extends VersionRange> getRanges() {
		return ranges;
	}

	@Override
	public boolean includes(String version) {
		for (VersionRange r : ranges) {
			if (r.includes(version)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalCollection(out, ranges);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		ranges = SerialUtils.readExternalImmutableHashSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((ranges == null) ? 0 : ranges.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnionVersionRange other = (UnionVersionRange) obj;
		if (ranges == null) {
			if (other.ranges != null)
				return false;
		} else if (!ranges.equals(other.ranges))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return StringUtils.toStringJoin("{", " | ", ranges, "}");
	}

}
