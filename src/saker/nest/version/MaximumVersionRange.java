package saker.nest.version;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.nest.bundle.BundleIdentifier;

/**
 * Version range that includes versions below or equals to a given maximum.
 * <p>
 * This class is constructed with a maximum version number that inclusively includes version numbers below that.
 * <p>
 * It is recommended to use {@link VersionRange#valueOf(String)} to create new version ranges than using this class
 * directly.
 */
public final class MaximumVersionRange implements VersionRange, Externalizable {
	private static final long serialVersionUID = 1L;

	private String maxVersion;

	/**
	 * For {@link Externalizable}.
	 */
	public MaximumVersionRange() {
	}

	MaximumVersionRange(String maxVersion) {
		this.maxVersion = maxVersion;
	}

	/**
	 * Creates a new instance with the given maximum version.
	 * 
	 * @param maxVersion
	 *            The maximum version.
	 * @return The created version range.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the version number has invalid format.
	 */
	public static MaximumVersionRange create(String maxVersion) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(maxVersion, "max version");
		if (!BundleIdentifier.isValidVersionNumber(maxVersion)) {
			throw new IllegalArgumentException("Invalid version number: " + maxVersion);
		}
		return new MaximumVersionRange(maxVersion);
	}

	/**
	 * Gets the maximum version that is included.
	 * 
	 * @return The maximum version number.
	 */
	public String getMaximumVersion() {
		return maxVersion;
	}

	@Override
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) {
		Objects.requireNonNull(visitor, "visitor");
		return visitor.visit(this, param);
	}

	@Override
	public boolean includes(String version) {
		int cmp = BundleIdentifier.compareVersionNumbers(this.maxVersion, version);
		return cmp >= 0;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(maxVersion);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		maxVersion = in.readUTF();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((maxVersion == null) ? 0 : maxVersion.hashCode());
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
		MaximumVersionRange other = (MaximumVersionRange) obj;
		if (maxVersion == null) {
			if (other.maxVersion != null)
				return false;
		} else if (!maxVersion.equals(other.maxVersion))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "(" + maxVersion + "]";
	}

}
