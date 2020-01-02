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

import saker.nest.bundle.BundleIdentifier;

/**
 * Version range that includes versions starting at a given minimum.
 * <p>
 * This class is constructed with a minimum version number that inclusively includes version numbers starting from that.
 * <p>
 * It is recommended to use {@link VersionRange#valueOf(String)} to create new version ranges than using this class
 * directly.
 */
public final class MinimumVersionRange implements VersionRange, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Singleton {@link VersionRange} that includes <b>any</b> version numbers.
	 * <p>
	 * As the first version number is <code>0</code>, this instance is basically a minimum version range starting at
	 * <code>0</code>. (In format of <code>[0)</code>)
	 */
	public static final VersionRange ANY_VERSION_RANGE_INSTANCE = new MinimumVersionRange("0");

	private String minVersion;

	/**
	 * For {@link Externalizable}.
	 */
	public MinimumVersionRange() {
	}

	MinimumVersionRange(String minVersion) {
		this.minVersion = minVersion;
	}

	/**
	 * Creates a new instance with the given minimum version.
	 * 
	 * @param minVersion
	 *            The minimum version.
	 * @return The created version range.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the version number has invalid format.
	 */
	public static MinimumVersionRange create(String minVersion) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(minVersion, "min version");
		if (!BundleIdentifier.isValidVersionNumber(minVersion)) {
			throw new IllegalArgumentException("Invalid version number: " + minVersion);
		}
		return new MinimumVersionRange(minVersion);
	}

	/**
	 * Gets the minimum version that is included.
	 * 
	 * @return The minimum version number.
	 */
	public String getMinimumVersion() {
		return minVersion;
	}

	@Override
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) {
		Objects.requireNonNull(visitor, "visitor");
		return visitor.visit(this, param);
	}

	@Override
	public boolean includes(String version) {
		int cmp = BundleIdentifier.compareVersionNumbers(this.minVersion, version);
		return cmp <= 0;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(minVersion);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		minVersion = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((minVersion == null) ? 0 : minVersion.hashCode());
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
		MinimumVersionRange other = (MinimumVersionRange) obj;
		if (minVersion == null) {
			if (other.minVersion != null)
				return false;
		} else if (!minVersion.equals(other.minVersion))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[" + minVersion + ")";
	}

}
