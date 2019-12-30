package saker.nest.bundle;

/**
 * Simple interface specifying the trait that the implementing class encloses a {@link BundleIdentifier}.
 * <p>
 * This interface may be implemented by clients.
 */
public interface BundleIdentifierHolder {
	/**
	 * Gets the bundle identifier associated with the declaring class.
	 * 
	 * @return The bundle identifier. May be <code>null</code>.
	 */
	public BundleIdentifier getBundleIdentifier();
}
