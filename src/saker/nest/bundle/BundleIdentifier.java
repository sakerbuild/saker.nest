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
package saker.nest.bundle;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;

/**
 * Unique identifier in canonical format representing a bundle identifier in the Nest repository.
 * <p>
 * The class holds a canonicalized name of a bundle in the Nest repository. A bundle identifier consists of all
 * lower-case parts which are the following:
 * <ul>
 * <li><b>Name</b>: The name of the bundle. It contains one or more alphabetical (<code>a-z</code>), numeric
 * (<code>0-9</code>), underscore (<code>_</code>) or dot (<code>.</code>) characters. In general, it should be in the
 * <code>some.bundle.name</code> format, similar to Java package naming.</li>
 * <li><b>Qualifiers</b>: Bundle qualifiers are arbitrary attributes attached to the bundle name. Qualifiers can contain
 * the same characters as the name. They can be used to differentiate different bundles under the same enclosing bundle
 * name.</li>
 * <li><b>Meta-qualifiers</b>: Same as bundle qualifiers, but they are specially handled by the implementation.
 * Currently the following bundle qualifiers are recognized:
 * <ul>
 * <li><b>Bundle version</b>: Version meta-qualifiers are in the format of <code>v&lt;num&gt;[.&lt;num&gt;]*</code>.
 * They are used to distinguish different versions of the same bundles. It is ensured that a bundle identifier may
 * contain either none, or at most one version qualifier. <br>
 * For more information about version numbers, see {@link #compareVersionNumbers(String, String)}.</li>
 * </ul>
 * </li>
 * </ul>
 * The different parts of the bundle identifiers are display in a dash (<code>-</code>) separated form when normalized:
 * 
 * <pre>
 * some.bundle.name-q1-q2-v1.0
 * </pre>
 * 
 * When a bundle identifer is constructed, its parts will be normalized, meaning that the order of qualifiers and meta
 * qualifiers will not be kept, and they will be stored in alphabetical order. Duplicate qualifiers will be removed.
 * This means that the following bundle identifiers are considered to be equal:
 * 
 * <pre>
 * some.bundle.name-q1-q2-v1.0
 * some.bundle.name-q2-q1-v1.0
 * some.bundle.name-q1-v1.0-q2-q1
 * SOME.BuNdLe.name-Q1-q2-V1.0-q1
 * </pre>
 * 
 * When bundle identifiers are converted back to string representation, they will have the following format:
 * 
 * <pre>
 * name[-qualifiers]*[-metaqualifiers]*
 * </pre>
 * 
 * If there are not qualifiers, or no meta qualifiers, the separating dash character will be omitted.
 */
@PublicApi
public final class BundleIdentifier implements Comparable<BundleIdentifier>, Externalizable {
	private static final long serialVersionUID = 1L;
	private static final Pattern PATTERN_DASH_SPLIT = Pattern.compile("[-]+");
	private static final Pattern PATTERN_BUNDLE_IDENTIFIER = Pattern
			.compile("[a-zA-Z_0-9]+(\\.[a-zA-Z_0-9]+)*(-[a-zA-Z0-9_.]+)*");
	private static final Pattern PATTERN_BUNDLE_NAME = Pattern.compile("[a-zA-Z_0-9]+(\\.[a-zA-Z_0-9]+)*");

	private static final Pattern PATTERN_VERSION_QUALIFIER = Pattern
			.compile("[vV](0|([1-9][0-9]*))(\\.(0|([1-9][0-9]*)))*");
	private static final Pattern PATTERN_VERSION_NUMBER = Pattern.compile("(0|([1-9][0-9]*))(\\.(0|([1-9][0-9]*)))*");

	private String name;
	private NavigableSet<String> qualifiers;
	private NavigableSet<String> metaQualifiers;

	/**
	 * For {@link Externalizable}.
	 */
	public BundleIdentifier() {
	}

	private BundleIdentifier(String name) {
		this(name, Collections.emptyNavigableSet(), Collections.emptyNavigableSet());
	}

	private BundleIdentifier(String name, NavigableSet<String> qualifiers, NavigableSet<String> metaQualifiers) {
		this.name = name;
		this.qualifiers = qualifiers;
		this.metaQualifiers = metaQualifiers;
	}

	/**
	 * Converts the argument to a {@link BundleIdentifier}.
	 * <p>
	 * The input is expected to start with the bundle name, and have any qualifiers appended with separating dash
	 * (<code>'-'</code>) characters:
	 * 
	 * <pre>
	 * [bundle.name][-qualifier]*
	 * </pre>
	 * 
	 * The input will be converted to a lower-case representation as required by this class.
	 * 
	 * @param input
	 *            The input string.
	 * @return The created task identifier.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the input has invalid bundle identifier format, or multiple version qualifiers are found.
	 */
	public static BundleIdentifier valueOf(String input) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(input, "bundle identifier input");
		if (!PATTERN_BUNDLE_IDENTIFIER.matcher(input).matches()) {
			throw new IllegalArgumentException("Invalid bundle identifier format: " + input);
		}
		input = input.toLowerCase(Locale.ENGLISH);
		if (input.contains("-")) {
			String[] split = PATTERN_DASH_SPLIT.split(input);
			NavigableSet<String> qualifiers = new TreeSet<>();
			NavigableSet<String> metaqualifiers = new TreeSet<>();
			String presentvq = null;
			for (int i = 1; i < split.length; i++) {
				String q = split[i];
				if (isMetaQualifier(q)) {
					if (isValidVersionQualifier(q)) {
						if (presentvq != null) {
							if (!presentvq.equals(q)) {
								throw new IllegalArgumentException(
										"Multiple version qualifiers in bundle identifier: " + input);
							}
						}
						presentvq = q;
					}
					metaqualifiers.add(q);
				} else {
					qualifiers.add(q);
				}
			}
			return new BundleIdentifier(split[0], ImmutableUtils.makeImmutableNavigableSet(qualifiers),
					ImmutableUtils.makeImmutableNavigableSet(metaqualifiers));
		}
		return new BundleIdentifier(input);
	}

	/**
	 * Gets the name part of the bundle identifier.
	 * 
	 * @return The name.
	 */
	public final String getName() {
		return name;
	}

	/**
	 * Gets the qualifier part of the bundle identifier.
	 * <p>
	 * Meta-qualifiers are not included.
	 * 
	 * @return An immutable set of bundle qualifiers, never <code>null</code>.
	 */
	public final NavigableSet<String> getBundleQualifiers() {
		return qualifiers;
	}

	/**
	 * Gets the meta-qualifier part of the bundle identifier.
	 * 
	 * @return An immutable set of bundle qualifiers, never <code>null</code>.
	 */
	public NavigableSet<String> getMetaQualifiers() {
		return metaQualifiers;
	}

	/**
	 * Gets all qualifiers in this bundle identifier.
	 * <p>
	 * Meta-qualfiers and normal qualifiers both included.
	 * <p>
	 * This method is the same as constructing a new set that includes both {@link #getBundleQualifiers()} and
	 * {@link #getMetaQualifiers()}.
	 * 
	 * @return A (possibly immutable) set of bundle qualifiers, never <code>null</code>.
	 */
	public NavigableSet<String> getAllQualifiers() {
		if (this.qualifiers.isEmpty()) {
			return this.metaQualifiers;
		}
		if (this.metaQualifiers.isEmpty()) {
			return this.qualifiers;
		}
		TreeSet<String> result = new TreeSet<>(this.qualifiers);
		result.addAll(this.metaQualifiers);
		return result;
	}

	@Override
	public int compareTo(BundleIdentifier o) {
		int cmp = name.compareTo(o.name);
		if (cmp != 0) {
			return cmp;
		}
		cmp = ObjectUtils.compareOrderedSets(qualifiers, o.qualifiers);
		if (cmp != 0) {
			return cmp;
		}
		cmp = ObjectUtils.compareOrderedSets(metaQualifiers, o.metaQualifiers);
		if (cmp != 0) {
			return cmp;
		}
		return 0;
	}

	/**
	 * Gets the version qualifier in this bundle identifier if any.
	 * <p>
	 * The returned string contains the preceeding <code>'v'</code> in it. Use {@link #getVersionNumber()} to
	 * automatically strip it.
	 * 
	 * @return The version qualifier or <code>null</code> if not present.
	 */
	public String getVersionQualifier() {
		return getVersionQualifier(metaQualifiers);
	}

	/**
	 * Gets the version number in this bundle identifier if any.
	 * <p>
	 * This method gets the version qualifier, and strips the preceeding <code>'v'</code> from it.
	 * 
	 * @return The version number or <code>null</code> if not present.
	 */
	public String getVersionNumber() {
		String q = getVersionQualifier();
		if (q == null) {
			return null;
		}
		return q.substring(1);
	}

	/**
	 * Checks if this bundle identifier has any normal or meta qualifiers.
	 * <p>
	 * This method is the same as the following:
	 * 
	 * <pre>
	 * !hasNormalQualifiers() && !hasMetaQualifiers()
	 * </pre>
	 * 
	 * @return <code>true</code> if there are any qualifiers present.
	 * @see #hasNormalQualifiers()
	 * @see #hasMetaQualifiers()
	 */
	public boolean hasAnyQualifiers() {
		return !this.qualifiers.isEmpty() || !this.metaQualifiers.isEmpty();
	}

	/**
	 * Checks if this bundle identifier has any meta qualifiers.
	 * 
	 * @return <code>true</code> if there are any meta qualifiers present.
	 * @since saker.nest 0.8.3
	 * @see #hasAnyQualifiers()
	 * @see #hasNormalQualifiers()
	 */
	public boolean hasMetaQualifiers() {
		return !this.metaQualifiers.isEmpty();
	}

	/**
	 * Checks if this bundle identifier has any normal qualifiers.
	 * <p>
	 * This check doesn't include the meta qualifiers.
	 * 
	 * @return <code>true</code> if there are any normal qualifiers present.
	 * @since saker.nest 0.8.3
	 * @see #hasAnyQualifiers()
	 * @see #hasMetaQualifiers()
	 */
	public boolean hasNormalQualifiers() {
		return !this.qualifiers.isEmpty();
	}

	/**
	 * Gets a bundle identifier derived from <code>this</code> that doesn't contain any meta qualifiers.
	 * <p>
	 * If this bundle identifier already has no meta-qualifiers, <code>this</code> is returned.
	 * 
	 * @return A bundle identifier with the same name and qualifiers as <code>this</code>, but without any
	 *             meta-qualifiers.
	 */
	public BundleIdentifier withoutMetaQualifiers() {
		if (this.metaQualifiers.isEmpty()) {
			return this;
		}
		return new BundleIdentifier(this.name, this.qualifiers, Collections.emptyNavigableSet());
	}

	/**
	 * Gets a bundle identifier derived from <code>this</code> that doesn't contain any (normal) qualifiers.
	 * <p>
	 * If this bundle identifier already has no (normal) qualifiers, <code>this</code> is returned.
	 * 
	 * @return A bundle identifier with the same name and meta-qualifiers as <code>this</code>, but without any (normal)
	 *             qualifiers.
	 */
	public BundleIdentifier withoutQualifiers() {
		if (this.qualifiers.isEmpty()) {
			return this;
		}
		return new BundleIdentifier(this.name, Collections.emptyNavigableSet(), this.metaQualifiers);
	}

	/**
	 * Gets a bundle identifier derived from <code>this</code> that doesn't contain <b>any</b> normal or meta
	 * qualifiers.
	 * <p>
	 * If this bundle identifier already has no normal or meta qualifiers, <code>this</code> is returned.
	 * 
	 * @return A bundle identifier that only contains the same name as <code>this</code>.
	 */
	public BundleIdentifier withoutAnyQualifiers() {
		if (this.qualifiers.isEmpty() && this.metaQualifiers.isEmpty()) {
			return this;
		}
		return new BundleIdentifier(this.name, Collections.emptyNavigableSet(), Collections.emptyNavigableSet());
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeUTF(name);
		SerialUtils.writeExternalCollection(out, qualifiers);
		SerialUtils.writeExternalCollection(out, metaQualifiers);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		name = in.readUTF();
		qualifiers = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		metaQualifiers = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + name.hashCode();
		result = prime * result + qualifiers.hashCode();
		result = prime * result + metaQualifiers.hashCode();
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
		BundleIdentifier other = (BundleIdentifier) obj;
		return compareTo(other) == 0;
	}

	@Override
	public String toString() {
		if (this.qualifiers.isEmpty()) {
			if (this.metaQualifiers.isEmpty()) {
				return name;
			}
			return name + "-" + StringUtils.toStringJoin("-", this.metaQualifiers);
		}
		if (this.metaQualifiers.isEmpty()) {
			return name + "-" + StringUtils.toStringJoin("-", this.qualifiers);
		}
		return name + "-" + StringUtils.toStringJoin("-", this.qualifiers) + "-"
				+ StringUtils.toStringJoin("-", this.metaQualifiers);
	}

	/**
	 * Utility function to check if the argument contains any valid version qualifiers.
	 * 
	 * @param qualifiers
	 *            The iterable of qualifier char sequences.
	 * @return <code>true</code> if the argument is non-<code>null</code>, and at least one element is recognized to be
	 *             a valid version qualifier defined by this class.
	 */
	public static boolean hasVersionQualifier(Iterable<? extends CharSequence> qualifiers) {
		if (qualifiers == null) {
			return false;
		}
		Iterator<? extends CharSequence> it = qualifiers.iterator();
		if (!it.hasNext()) {
			return false;
		}
		Matcher matcher = PATTERN_VERSION_QUALIFIER.matcher(it.next());
		if (matcher.matches()) {
			return true;
		}
		while (it.hasNext()) {
			matcher.reset(it.next());
			if (matcher.matches()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Utility function to retrieve the version qualifier from the argument qualifiers.
	 * <p>
	 * If the argument is <code>null</code>, or multiple possible version qualifiers are found, <code>null</code> is
	 * returned.
	 * 
	 * @param <S>
	 *            The type of character sequence elements.
	 * @param qualifiers
	 *            The iterable of qualifier char sequences.
	 * @return The version qualifier element if and only if one valid version qualifier is found.
	 */
	public static <S extends CharSequence> S getVersionQualifier(Iterable<S> qualifiers) {
		if (qualifiers == null) {
			return null;
		}
		Iterator<S> it = qualifiers.iterator();
		if (!it.hasNext()) {
			return null;
		}
		S q = it.next();
		Matcher matcher = PATTERN_VERSION_QUALIFIER.matcher(q);
		S res = matcher.matches() ? q : null;
		while (it.hasNext()) {
			q = it.next();
			matcher.reset(q);
			if (matcher.matches()) {
				if (res != null) {
					//multiple version qualifiers, return null as it cannot be determined
					return null;
				}
				res = q;
			}
		}
		return res;
	}

	/**
	 * Creates a new version qualifier.
	 * <p>
	 * This method simply prepends the argument with <code>"v"</code>. The method doesn't check if the argument is a
	 * valid version number.
	 * 
	 * @param versionnum
	 *            The version number part of the resulting version qualifier.
	 * @return The created qualifier.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static String makeVersionQualifier(String versionnum) throws NullPointerException {
		Objects.requireNonNull(versionnum, "version number");
		return "v" + versionnum;
	}

	/**
	 * Checks if the argument has a valid bundle identifier format.
	 * <p>
	 * If this method returns <code>true</code>, {@link #valueOf(String)} will accept it as a valid input. However, it
	 * still may throw {@link IllegalArgumentException} if the argument contains multiple version qualifiers.
	 * <p>
	 * This method doesn't semantically check the qualifier consistency, only the bundle identifier format.
	 * 
	 * @param bundleidstr
	 *            The string representation of a bundle identifier.
	 * @return <code>true</code> if it has a valid bundle identifier format.
	 */
	public static boolean isValidBundleIdentifier(String bundleidstr) {
		return bundleidstr != null && PATTERN_BUNDLE_IDENTIFIER.matcher(bundleidstr).matches();
	}

	/**
	 * Checks if the argument is a valid bundle name.
	 * <p>
	 * The argument should only contain the name part of a bundle identifier. If it contains any qualifiers, the method
	 * will return false.
	 * 
	 * @param name
	 *            The bundle name.
	 * @return <code>true</code> if the argument is a valid bundle name.
	 */
	public static boolean isValidBundleName(String name) {
		return name != null && PATTERN_BUNDLE_NAME.matcher(name).matches();
	}

	/**
	 * Checks if the argument string represents a valid version number.
	 * <p>
	 * Valid version numbers are one or more dot (<code>.</code>) separated non-negative numbers. E.g.:
	 * 
	 * <pre>
	 * 0
	 * 1
	 * 0.1
	 * 1.0
	 * 1.0.0
	 * 1.2.3
	 * </pre>
	 * 
	 * @param version
	 *            The version number string.
	 * @return <code>true</code> if the argument is non-<code>null</code> and has a valid version number format.
	 * @see #compareVersionNumbers(String, String)
	 */
	public static boolean isValidVersionNumber(String version) {
		return version != null && PATTERN_VERSION_NUMBER.matcher(version).matches();
	}

	/**
	 * Checks if the argument has a valid version qualifier format.
	 * <p>
	 * Version qualifiers consist of a preceeding <code>'v'</code> character and a version number. See
	 * {@link #isValidVersionNumber(String)} for version number format.
	 * 
	 * @param qualifier
	 *            The version qualifier.
	 * @return <code>true</code> if the argument is non-<code>null</code> and has a valid version qualifier format.
	 * @see #compareVersionNumbers(String, String)
	 */
	public static boolean isValidVersionQualifier(String qualifier) {
		return qualifier != null && PATTERN_VERSION_QUALIFIER.matcher(qualifier).matches();
	}

	/**
	 * Checks if the argument is a valid version qualifier recognized by this class.
	 * <p>
	 * Currently only {@linkplain #isValidVersionQualifier(String) version qualifiers} are considered to be
	 * meta-qualifiers.
	 * 
	 * @param qualifier
	 *            The qualifier string.
	 * @return <code>true</code> if the argument is a valid meta-qualifier.
	 */
	public static boolean isMetaQualifier(String qualifier) {
		//if any other checks are added to this, modify valueOf method 
		return qualifier != null && PATTERN_VERSION_QUALIFIER.matcher(qualifier).matches();
	}

	/**
	 * Compares two version numbers in ascending order.
	 * <p>
	 * Version numbers consist of one or more dot (<code>.</code>) separated numbers. Each consecutive number component
	 * in the version number have less significance than the preceeding one. This method compares them by comparing each
	 * number component in ascending order. Shorter version numbers are ordered first.
	 * <p>
	 * The first recognized version number is simply <code>0</code>. The following version number examples are in
	 * strictly ascending order:
	 * 
	 * <pre>
	 * 0
	 * 0.0
	 * 0.1
	 * 0.1.0
	 * 0.9
	 * 0.10
	 * 0.10.0
	 * 0.11
	 * 1.0
	 * 1.1
	 * 1.1.0
	 * 1.2
	 * 1.2.3.4.5
	 * 1.2.4
	 * 2.0
	 * 3
	 * 3.0
	 * 3.1
	 * 4
	 * 4.1
	 * </pre>
	 * 
	 * Any succeeding <code>.0</code> number parts are still considered to be part of the version number, and they are
	 * not to be emitted. Any version number appended with the string <code>".0"</code> is considered to be strictly the
	 * next in order. (See {@link #nextVersionNumberInNaturalOrder(String)}.)
	 * 
	 * @param l
	 *            The left version number.
	 * @param r
	 *            The right version number.
	 * @return The comparison result. Negative if left is less than right, zero if they equal, and positive if the right
	 *             is greater than left.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the version numbers have invalid format.
	 */
	public static int compareVersionNumbers(String l, String r) throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(l, "left");
		Objects.requireNonNull(r, "right");
		if (l.isEmpty() || r.isEmpty()) {
			throw new IllegalArgumentException("Invalid version numbers: " + l + " - " + r);
		}
		return compareVersionsImpl(l, r, 0, 0);
	}

	/**
	 * Comparator function for two version qualifiers.
	 * <p>
	 * This method compares the numbers in the argument version qualifiers using the rules specified by
	 * {@link #compareVersionNumbers(String, String)}.
	 * <p>
	 * The arguments are expected to be version numbers preceeded by a single <code>'v'</code> or <code>'V'</code>
	 * character.
	 * 
	 * @param l
	 *            The left version qualifier.
	 * @param r
	 *            The right version qualifier.
	 * @return The comparison result. Negative if left is less than right, zero if they equal, and positive if the right
	 *             is greater than left.
	 * @throws NullPointerException
	 *             If any of the arguments are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the version qualifiers have invalid format.
	 */
	public static int compareVersionQualifiers(String l, String r)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(l, "left version qualifier");
		Objects.requireNonNull(r, "right version qualifier");
		char c;
		if (l.length() < 2 || r.length() < 2 || ((c = l.charAt(0)) != 'v' && c != 'V')
				|| ((c = r.charAt(0)) != 'v' && c != 'V')) {
			throw new IllegalArgumentException("Invalid version qualifiers: " + l + " - " + r);
		}
		return compareVersionsImpl(l, r, 1, 1);
	}

	/**
	 * Gets the strictly next version number that will compare to be greater than the argument.
	 * <p>
	 * The following will be true for any valid version number argument <code>v</code> and created next version number
	 * <code>n</code>:
	 * 
	 * <pre>
	 * {@link #compareVersionNumbers(String, String) compareVersionNumbers(v, n)} &lt; 0
	 * </pre>
	 * 
	 * There exists no version number <code>x</code> that is greater than <code>v</code> and less than <code>n</code>.
	 * <p>
	 * This method will <b>not</b> check if the argument is already a valid version number.
	 * 
	 * @param version
	 *            The version number.
	 * @return The strictly next version number in ascending order.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is empty.
	 */
	public static String nextVersionNumberInNaturalOrder(String version)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(version, "version");
		if (version.isEmpty()) {
			throw new IllegalArgumentException("Empty version argument.");
		}
		return version + ".0";
	}

	/**
	 * Gets the version number part of a version qualifier argument.
	 * <p>
	 * This method will return the version number from a valid version qualifier. It will not check the argument if it
	 * is actually a valid version qualifier.
	 * <p>
	 * The method effectively removes the preceeding <code>'v'</code> character in the version qualifier.
	 * 
	 * @param versionqualfier
	 *            The version qualifier.
	 * @return The version number part.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is the empty string.
	 */
	public static String getVersionQualifierVersionNumberPart(String versionqualfier)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(versionqualfier, "version qualifier");
		if (versionqualfier.isEmpty()) {
			throw new IllegalArgumentException("Empty version qualifier argument.");
		}
		return versionqualfier.substring(1);
	}

	/**
	 * Counts the number compontents in the argument version number.
	 * <p>
	 * This method will count the number segments in the argument version number. It is expected that the argument is
	 * already a valid version number, and this method will not verify its format.
	 * <p>
	 * E.g.:
	 * <ul>
	 * <li><code>1</code>: 1 component</li>
	 * <li><code>1.0</code>: 2 components</li>
	 * <li><code>3.0.5.0</code>: 4 components</li>
	 * <li><code>1.invalid.5.format</code>: 4 components (even though invalid format)</li>
	 * </ul>
	 * This method effectively counts the number of dot (<code>.</code>) occurrences in the argument.
	 * 
	 * @param version
	 *            The version number
	 * @return The component count.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the argument is the empty string.
	 */
	public static int getVersionNumberComponentCount(String version)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(version, "version");
		if (version.isEmpty()) {
			throw new IllegalArgumentException("Empty version number argument.");
		}
		int idx = 0;
		int len = version.length();
		int result = 1;
		while (idx < len) {
			int nidx = version.indexOf('.', idx);
			if (nidx < 0) {
				break;
			}
			++result;
			idx = nidx + 1;
		}
		return result;
	}

	private static int compareVersionsImpl(String l, String r, int lnidx, int rnidx) {
		if (l.equals(r)) {
			return 0;
		}
		int llen = l.length();
		int rlen = r.length();
		while (true) {
			int lnumend = l.indexOf('.', lnidx);
			int rnumend = r.indexOf('.', rnidx);
			if (lnumend < 0) {
				lnumend = llen;
			}
			if (rnumend < 0) {
				rnumend = rlen;
			}

			int ln = Integer.parseInt(l.substring(lnidx, lnumend));
			if (ln < 0) {
				throw new NumberFormatException("Invalid version number part: " + ln + " in " + l);
			}
			int rn = Integer.parseInt(r.substring(rnidx, rnumend));
			if (rn < 0) {
				throw new NumberFormatException("Invalid version number part: " + rn + " in " + r);
			}
			int cmp = Integer.compare(ln, rn);
			if (cmp != 0) {
				return cmp;
			}
			lnidx = lnumend + 1;
			rnidx = rnumend + 1;
			if (lnidx > llen) {
				//l ended
				if (rnidx > rlen) {
					//both ended
					return 0;
				}
				//r has more version numbers
				return -1;
			}
			//l has more version numbers
			if (rnidx > rlen) {
				//r ended
				return 1;
			}
		}
	}
}
