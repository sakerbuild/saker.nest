package saker.nest.version;

import saker.nest.bundle.BundleIdentifier;

/**
 * Predicate interface determining whether a given version number should be included in the associated operation.
 * <p>
 * A version range is used to check in various operations whether a given object should be considered. Its main purpose
 * is to serve as a programmatic representation of an user specified version range input string.
 * <p>
 * An instance of this interface can be constructed using the {@link #valueOf(String)} method. See
 * {@link #valueOf(String)} for information about the expected input format.
 * <p>
 * This interface is not intended to be subclassed by clients.
 * <p>
 * Instances of this interface can be serialized.
 */
public interface VersionRange {
	/**
	 * Checks if this version range can accept the argument version number.
	 * <p>
	 * Note that when working with {@link BundleIdentifier BundleIdentifiers}, any version qualifiers should be
	 * converted to version numbers.
	 * 
	 * @param version
	 *            The version number.
	 * @return <code>true</code> if the range includes the version number.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is not a valid version number. (Optional exception, may be silently ignored with
	 *             <code>false</code> result.)
	 */
	public boolean includes(String version) throws NullPointerException, IllegalArgumentException;

	/**
	 * Invokes the argument visitor based on the kind of this version range object.
	 * <p>
	 * This method equals to the following based on the type of this object:
	 * 
	 * <pre>
	 * return visitor.accept(this, param);
	 * </pre>
	 * 
	 * @param <R>
	 *            The result type of the visiting.
	 * @param <P>
	 *            The parameter to pass to the visitor without modification.
	 * @param visitor
	 *            The visitor.
	 * @param param
	 *            The parameter.
	 * @return The result object of the visitor call.
	 * @throws NullPointerException
	 *             If the visitor is <code>null</code>.
	 */
	public <R, P> R accept(VersionRangeVisitor<R, P> visitor, P param) throws NullPointerException;

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	/**
	 * Converts this version range to a semantically same string version range representation. The resulting string can
	 * be passed to {@link #valueOf(String)}, which will result in a {@link VersionRange} that equals to
	 * <code>this</code>.
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public String toString();

	/**
	 * Parses a string in version range format and creates a {@link VersionRange} object.
	 * <p>
	 * This method will analyze the argument and convert it to a {@link VersionRange} object. The expected format can
	 * contain the following:
	 * <ul>
	 * <li><b>Number</b>: A version number in the format defined by {@link BundleIdentifier}. It must be one or more
	 * non-negative integers separated by dot ('<code>.</code>') characters. E.g. <code>1.2.3</code>
	 * <p>
	 * Numbers without any enclosing range declaration will allow any versions that start with the given number. E.g.
	 * the input <code>1.2</code> allows <code>1.2</code>, <code>1.2.0</code>, <code>1.2.1</code>, and any following
	 * version numbers up until <code>1.3</code>.</li>
	 * <li><b>Range</b>: Two version numbers separated by comma ('<code>,</code>') enclosed in either parentheses
	 * (<code>'('</code> and <code>')'</code>) or brackets (<code>'['</code> and <code>']'</code>). The kind of
	 * enclosing characters may be used together. The enclosing characters correspond to the same semantics as in the
	 * notation used by intervals in mathematics. (Parentheses for open ended (exclusive) ranges, and brackets for
	 * closed (inclusive) ranges.)
	 * <p>
	 * The right side of the range must be greater than the left side.
	 * <p>
	 * E.g. <code>[1, 2)</code> includes any version starting from <code>1</code> and is smaller than
	 * <code>2</code>.</li>
	 * <li><b>Singular range</b>: A range declaration that only contains one version number. It can have three formats:
	 * <ul>
	 * <li><code>[1.0)</code>: meaning versions at least <code>1.0</code>, without any upper bound.</li>
	 * <li><code>(1.0]</code>: meaning versions at most <code>1.0</code>, without any lower bound. (The range is
	 * inclusive for <code>1.0</code>.) This is semantically same as <code>[0, 1.0]</code>, as the version
	 * <code>0</code> is the first one in order.</li>
	 * <li><code>[1.0]</code>: meaning exactly the version <code>1.0</code></li>
	 * </ul>
	 * Note that a singular version range with parentheses on both end is illegal.</li>
	 * <li><b>Union relation</b>: Any of the components can be enclosed in curly braces (<code>'{'</code> and
	 * <code>'}</code>) and separated by vertical bars (<code>'|'</code>). This declaration will enable versions matched
	 * by any of its compontents. E.g. <code>{[1.0] | [2.0]}</code> matches only the versions <code>1.0</code> and
	 * <code>2.0</code>. Note that an union declaration without any components is considered to include <i>no
	 * versions</i>.</li>
	 * <li><b>Intersection relation</b>: Any of the components can be enclosed in intersection relation with each other.
	 * The <code>'&amp;'</code> character can be used to require that all parts of the input is satisfied. This relation
	 * exists for completeness of the version range format, and we haven't found a significant use-case for it as of
	 * yet. In general, intersections can be represented in a range based way more appropriately.</li>
	 * </ul>
	 * Examples:
	 * <ul>
	 * <li><code>1.0</code>: Includes any version greater or equal to <code>1.0</code> and less than <code>1.1</code>.
	 * Semantically same as <code>[1.0, 1.1)</code>.</li>
	 * <li><code>{1 | 3}</code>: Includes versions starting with <code>1</code> or <code>3</code>, but doesn't include
	 * versions with other starting components. <br>
	 * Included examples: <code>1</code>, <code>1.0</code>, <code>1.1</code>, <code>3</code>, <code>3.2</code><br>
	 * Not included examples: <code>2</code>, <code>2.0</code>, <code>4.0</code></li>
	 * <li><code>{}</code>: Doesn't include any versions. Unsatisfiable.</li>
	 * <li><code>(1.1, 1.4)</code>: Includes versions greater than <code>1.1</code> and less than <code>1.4</code>. <br>
	 * Included examples: <code>1.1.0</code>, <code>1.1.1</code>, <code>1.2</code>, <code>1.3.9</code>,
	 * <code>1.3.9.0</code><br>
	 * Not included examples: <code>1.0</code>, <code>1.1</code>, <code>1.4</code>, <code>1.4.0</code></li>
	 * <li><code>{1.0}</code>: Same as <code>1.0</code>.</li>
	 * </ul>
	 * Calling {@link #toString()} on the result, and {@link #valueOf(String)} again will result in a new
	 * {@link VersionRange} that equals to the returned one.
	 * <p>
	 * The method may return a version range that doesn't exactly match the input, but only semantically. Meaning that
	 * it may perform some optimizations.
	 * 
	 * @param range
	 *            The version range in string representation.
	 * @return The parsed version range.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument has invalid format.
	 * @see BundleIdentifier#compareVersionNumbers(String, String)
	 */
	public static VersionRange valueOf(String range) throws NullPointerException, IllegalArgumentException {
		return VersionUtils.parseDependencyVersionRange(range);
	}
}
