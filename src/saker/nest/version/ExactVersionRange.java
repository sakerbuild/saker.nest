package saker.nest.version;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.nest.bundle.BundleIdentifier;

/**
 * Version range that only includes a specific version.
 * <p>
 * The class only includes a version that equals to the one used to construct it.
 * <p>
 * It is recommended to use {@link VersionRange#valueOf(String)} to create new version ranges than using this class
 * directly.
 */
public final class ExactVersionRange implements VersionRange, Externalizable {
	private static final long serialVersionUID = 1L;

	private String version;

	/**
	 * For {@link Externalizable}.
	 */
	public ExactVersionRange() {
	}

	ExactVersionRange(String version) {
		this.version = version;
	}

	/**
	 * Creates a new version range that matches only the given version.
	 * 
	 * @param version
	 *            The version number.
	 * @return The created version range.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not a valid version number.
	 */
	public static ExactVersionRange create(String version) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(version, "version");
		if (!BundleIdentifier.isValidVersionNumber(version)) {
			throw new IllegalArgumentException("Invalid version number: " + version);
		}
		return new ExactVersionRange(version);
	}

	/**
	 * Gets the version number.
	 * <p>
	 * The class matches only this version by equality in {@link #includes(String)}.
	 * 
	 * @return The version number.
	 */
	public String getVersion() {
		return version;
	}

	@Override
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) {
		Objects.requireNonNull(visitor, "visitor");
		return visitor.visit(this, param);
	}

	@Override
	public boolean includes(String version) {
		return this.version.equals(version);
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
		ExactVersionRange other = (ExactVersionRange) obj;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[" + version + "]";
	}

}
