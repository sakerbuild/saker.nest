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
 * Version range that includes versions starting with a given base.
 * <p>
 * The class contains a base version that is used to check the tested version if it starts with it. If the argument has
 * the appropriate preceeding version number compontents, then it will be included.
 * <p>
 * E.g. <code>1.0</code> includes <code>1.0</code>, <code>1.0.0</code>, <code>1.0.10.1</code> but not <code>1.1</code>.
 * <p>
 * It is recommended to use {@link VersionRange#valueOf(String)} to create new version ranges than using this class
 * directly.
 */
public final class BaseVersionVersionRange implements VersionRange, Externalizable {
	private static final long serialVersionUID = 1L;

	private String version;

	/**
	 * For {@link Externalizable}.
	 */
	public BaseVersionVersionRange() {
	}

	BaseVersionVersionRange(String version) throws NullPointerException {
		this.version = version;
	}

	/**
	 * Creates a new base version range.
	 * 
	 * @param version
	 *            The version number.
	 * @return The created version range.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not a valid version number.
	 */
	public static VersionRange create(String version) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(version, "version");
		if (!BundleIdentifier.isValidVersionNumber(version)) {
			throw new IllegalArgumentException("Invalid version number: " + version);
		}
		return new BaseVersionVersionRange(version);
	}

	/**
	 * Gets the base version for inclusion.
	 * 
	 * @return The base version.
	 */
	public String getBaseVersion() {
		return version;
	}

	@Override
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) {
		Objects.requireNonNull(visitor, "visitor");
		return visitor.visit(this, param);
	}

	@Override
	public boolean includes(String version) {
		return includes(this.version, version);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(version);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		version = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((version == null) ? 0 : version.hashCode());
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
		BaseVersionVersionRange other = (BaseVersionVersionRange) obj;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return version;
	}

	/**
	 * Static utility function that evaluates the same inclusion as this class, but doesn't require creating a new
	 * instance.
	 * 
	 * @param baseversion
	 *            The base version.
	 * @param version
	 *            The version to test if included.
	 * @return <code>true</code> if the version is included.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the arguments have invalid version format.
	 */
	public static boolean includes(String baseversion, String version)
			throws NullPointerException, IllegalArgumentException {
		int cmp = BundleIdentifier.compareVersionNumbers(baseversion, version);
		if (cmp == 0) {
			return true;
		}
		if (cmp < 0 && version.startsWith(baseversion + '.')) {
			//allow versions that are only different in minors
			//e.g. this is 1.0
			//     an argument is 1.0.1
			return true;
		}
		return false;
	}

}
