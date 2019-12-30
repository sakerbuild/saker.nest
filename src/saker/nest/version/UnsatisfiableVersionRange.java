package saker.nest.version;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;

import saker.build.thirdparty.saker.util.ObjectUtils;

/**
 * Version range that cannot be satisfied.
 * <p>
 * This version range always returns <code>false</code> for any versions.
 * <p>
 * It is recommended to use {@link VersionRange#valueOf(String)} to create new version ranges than using this class
 * directly.
 */
public final class UnsatisfiableVersionRange implements VersionRange, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * A singleton instance of unsatisfiable version range.
	 */
	public static final VersionRange INSTANCE = new UnsatisfiableVersionRange();

	/**
	 * For {@link Externalizable}.
	 * 
	 * @see #INSTANCE
	 */
	public UnsatisfiableVersionRange() {
	}

	@Override
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) {
		Objects.requireNonNull(visitor, "visitor");
		return visitor.visit(this, param);
	}

	@Override
	public boolean includes(String version) {
		return false;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
	}

	@Override
	public int hashCode() {
		return getClass().getName().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return ObjectUtils.isSameClass(this, obj);
	}

	@Override
	public String toString() {
		return "{}";
	}
}
