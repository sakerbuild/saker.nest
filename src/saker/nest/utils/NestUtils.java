package saker.nest.utils;

import java.util.Objects;

import saker.apiextract.api.PublicApi;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.NestBundleClassLoader;

/**
 * Contains utility functions that help working with the Nest repository.
 */
@PublicApi
public class NestUtils {
	private NestUtils() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Gets the bundle identifier that contains the argument class.
	 * <p>
	 * The argument class must be loaded by the Nest repository runtime. (That is, the {@link ClassLoader} of the class
	 * must be an instance of {@link NestBundleClassLoader}.)
	 * 
	 * @param c
	 *            The class to get the enclosing bundle identifier of.
	 * @return The bundle identifier of the enclosing bundle.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the class wasn't loaded by the Nest repository runtime.
	 */
	public static BundleIdentifier getClassBundleIdentifier(Class<?> c)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(c, "class");
		ClassLoader cl = c.getClassLoader();
		if (!(cl instanceof NestBundleClassLoader)) {
			throw new IllegalArgumentException("Class is not part of a Nest bundle. (" + c + ")");
		}
		NestBundleClassLoader nestcl = (NestBundleClassLoader) cl;
		return nestcl.getBundle().getBundleIdentifier();
	}
}
