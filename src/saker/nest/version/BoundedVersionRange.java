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
 * Version range that is bounded on both ends in some way.
 * <p>
 * The class contans a left and right version bounds and defines their relation with a {@linkplain #getType() type}
 * constant.
 * <p>
 * E.g.
 * 
 * <pre>
 * [1.0, 2.0)
 * [3.0, 4.0]
 * (1, 5.0]
 * </pre>
 * 
 * It is recommended to use {@link VersionRange#valueOf(String)} to create new version ranges than using this class
 * directly.
 */
public final class BoundedVersionRange implements VersionRange, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Type constant representing the <code>(&lt;ver&gt;, &lt;ver&gt;)</code> relation.
	 * 
	 * @see #getType()
	 */
	public static final int TYPE_LEFT_EXCLUSIVE_RIGHT_EXCLUSIVE = 0b00;
	/**
	 * Type constant representing the <code>(&lt;ver&gt;, &lt;ver&gt;]</code> relation.
	 * 
	 * @see #getType()
	 */
	public static final int TYPE_LEFT_EXCLUSIVE_RIGHT_INCLUSIVE = 0b01;
	/**
	 * Type constant representing the <code>[&lt;ver&gt;, &lt;ver&gt;)</code> relation.
	 * 
	 * @see #getType()
	 */
	public static final int TYPE_LEFT_INCLUSIVE_RIGHT_EXCLUSIVE = 0b10;
	/**
	 * Type constant representing the <code>[&lt;ver&gt;, &lt;ver&gt;]</code> relation.
	 * 
	 * @see #getType()
	 */
	public static final int TYPE_LEFT_INCLUSIVE_RIGHT_INCLUSIVE = 0b11;

	private String leftVersion;
	private String rightVersion;

	private int type;

	/**
	 * For {@link Externalizable}.
	 */
	public BoundedVersionRange() {
	}

	BoundedVersionRange(String leftVersion, String rightVersion, int type) {
		this.leftVersion = leftVersion;
		this.rightVersion = rightVersion;
		this.type = type;
	}

	/**
	 * Creates a new bounded version range.
	 * 
	 * @param leftVersion
	 *            The left bounds version number.
	 * @param rightVersion
	 *            The right bounds version number.
	 * @param type
	 *            The type of the bounds. One of the <code>TYPE_*</code> constants in this class.
	 * @return The created version range.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the type is not one of the <code>TYPE_*</code> constants in this class. If the version numbers
	 *             have an invalid format. If the right version number is not greater than the left.
	 */
	public static VersionRange create(String leftVersion, String rightVersion, int type)
			throws NullPointerException, IllegalArgumentException {
		if (type < 0 || type > 0b11) {
			throw new IllegalArgumentException("Invalid type: " + type);
		}
		if (BundleIdentifier.compareVersionNumbers(leftVersion, rightVersion) >= 0) {
			throw new IllegalArgumentException(
					"Invalid range bounds: " + leftVersion + ", " + rightVersion + " (right must be greater)");
		}
		return new BoundedVersionRange(leftVersion, rightVersion, type);
	}

	/**
	 * Gets the left boundary version number.
	 * 
	 * @return The left bounds.
	 */
	public String getLeftBoundVersion() {
		return leftVersion;
	}

	/**
	 * Gets the right boundary version number.
	 * 
	 * @return The right bounds.
	 */
	public String getRightBoundVersion() {
		return rightVersion;
	}

	/**
	 * Gets the type of the bounds.
	 * <p>
	 * The type specifies how the ends of the boundary shoud be handled.
	 * 
	 * @return The type. One of <code>TYPE_*</code> constants in this class.
	 */
	public int getType() {
		return type;
	}

	@Override
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) {
		Objects.requireNonNull(visitor, "visitor");
		return visitor.visit(this, param);
	}

	@Override
	public boolean includes(String version) {
		switch (type) {
			case TYPE_LEFT_EXCLUSIVE_RIGHT_EXCLUSIVE: {
				return BundleIdentifier.compareVersionNumbers(this.leftVersion, version) < 0
						&& BundleIdentifier.compareVersionNumbers(this.rightVersion, version) > 0;
			}
			case TYPE_LEFT_EXCLUSIVE_RIGHT_INCLUSIVE: {
				return BundleIdentifier.compareVersionNumbers(this.leftVersion, version) < 0
						&& BundleIdentifier.compareVersionNumbers(this.rightVersion, version) >= 0;
			}
			case TYPE_LEFT_INCLUSIVE_RIGHT_EXCLUSIVE: {
				return BundleIdentifier.compareVersionNumbers(this.leftVersion, version) <= 0
						&& BundleIdentifier.compareVersionNumbers(this.rightVersion, version) > 0;
			}
			case TYPE_LEFT_INCLUSIVE_RIGHT_INCLUSIVE: {
				return BundleIdentifier.compareVersionNumbers(this.leftVersion, version) <= 0
						&& BundleIdentifier.compareVersionNumbers(this.rightVersion, version) >= 0;
			}
			default: {
				throw new AssertionError("Invalid type: " + type);
			}
		}
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(leftVersion == null ? "" : leftVersion);
		out.writeUTF(rightVersion == null ? "" : rightVersion);
		out.writeInt(type);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		String l = in.readUTF();
		String r = in.readUTF();
		type = in.readInt();
		leftVersion = l.isEmpty() ? null : l;
		rightVersion = r.isEmpty() ? null : r;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((leftVersion == null) ? 0 : leftVersion.hashCode());
		result = prime * result + ((rightVersion == null) ? 0 : rightVersion.hashCode());
		result = prime * result + type;
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
		BoundedVersionRange other = (BoundedVersionRange) obj;
		if (leftVersion == null) {
			if (other.leftVersion != null)
				return false;
		} else if (!leftVersion.equals(other.leftVersion))
			return false;
		if (rightVersion == null) {
			if (other.rightVersion != null)
				return false;
		} else if (!rightVersion.equals(other.rightVersion))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public String toString() {
		char l;
		char r;
		switch (type) {
			case TYPE_LEFT_EXCLUSIVE_RIGHT_EXCLUSIVE: {
				l = '(';
				r = ')';
				break;
			}
			case TYPE_LEFT_EXCLUSIVE_RIGHT_INCLUSIVE: {
				l = '(';
				r = ']';
				break;
			}
			case TYPE_LEFT_INCLUSIVE_RIGHT_EXCLUSIVE: {
				l = '[';
				r = ')';
				break;
			}
			case TYPE_LEFT_INCLUSIVE_RIGHT_INCLUSIVE: {
				l = '[';
				r = ']';
				break;
			}
			default: {
				throw new AssertionError("Invalid type: " + type);
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append(l);
		if (leftVersion != null) {
			sb.append(leftVersion);
		}
		sb.append(',');
		if (rightVersion != null) {
			sb.append(' ');
			sb.append(rightVersion);
		}
		sb.append(r);
		return sb.toString();
	}

}
