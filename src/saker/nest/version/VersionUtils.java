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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import saker.nest.bundle.BundleIdentifier;
import saker.nest.utils.NonSpaceIterator;

class VersionUtils {
	private VersionUtils() {
		throw new UnsupportedOperationException();
	}

	static VersionRange parseDependencyVersionRange(CharSequence range)
			throws NullPointerException, IllegalArgumentException {
//		formats
//		1.0 simple version, anything that starts with it matches
//		[1.0) minimum version
//		(1.0] maximum version
//		[1.0] exactly 1.0
//		(1.0, 2.0) version range, parens are changeable
//		()&() satisfying both ranges on each side
//		{()|()|()} satisfying any of the ranges in the inner declaration
//		{} not satisfiable
		Objects.requireNonNull(range, "range");
		if (range.length() == 0) {
			throw new IllegalArgumentException("Empty range.");
		}
		NonSpaceIterator it = new NonSpaceIterator(range);
		VersionRange result = parseDependencyVersionRange(it);
		if (it.hasNext()) {
			throw new IllegalArgumentException(
					"Extra characters at index: " + it.getIndex() + " in " + it.getCharSequence());
		}
		return result;
	}

	private static VersionRange parseDependencyVersionRange(NonSpaceIterator it) throws IllegalArgumentException {
		int startidx = it.getIndex();
		while (it.hasNext()) {
			char c = it.next();
			if (c >= '0' && c <= '9') {
				StringBuilder versionsb = new StringBuilder();
				versionsb.append(c);
				while (it.hasNext()) {
					char nc = it.peek();
					if ((nc >= '0' && nc <= '9') || nc == '.') {
						versionsb.append(nc);
						it.move();
					} else if (nc == '&') {
						it.move();
						//parse the other side
						VersionRange right = parseDependencyVersionRange(it);
						String versionstr = createVersionNumber(versionsb, it);
						return IntersectionVersionRange.create(new BaseVersionVersionRange(versionstr), right);
					} else {
						break;
					}
				}
				if (versionsb.length() == 0) {
					return UnsatisfiableVersionRange.INSTANCE;
				}
				String versionstr = createVersionNumber(versionsb, it);
				if (!BundleIdentifier.isValidVersionNumber(versionstr)) {
					throw new IllegalArgumentException(
							"Invalid version number: " + versionstr + " in " + it.getCharSequence());
				}
				return new BaseVersionVersionRange(versionstr);
			} else if (c == '(' || c == '[') {
				//starts a range dependency
				StringBuilder versionsb = new StringBuilder();
				if (!it.hasNext()) {
					throw new IllegalArgumentException(
							"Missing range closing brace at index: " + it.getIndex() + " in " + it.getCharSequence());
				}
				do {
					char nc = it.peek();
					if ((nc >= '0' && nc <= '9') || nc == '.') {
						versionsb.append(nc);
						it.move();
					} else if (nc == ',') {
						//bounded range

						if (versionsb.length() == 0) {
							throw new IllegalArgumentException(
									"Empty left range bound at index:" + startidx + " in " + it.getCharSequence());
						}

						it.move();
						StringBuilder rightversionsb = new StringBuilder();
						while (it.hasNext()) {
							char rvc = it.peek();
							if ((rvc >= '0' && rvc <= '9') || rvc == '.') {
								rightversionsb.append(rvc);
								it.move();
							} else {
								break;
							}
						}
						if (rightversionsb.length() == 0) {
							throw new IllegalArgumentException(
									"Empty right range bound at index:" + startidx + " in " + it.getCharSequence());
						}
						char itpeek = it.peek();

						if (itpeek == ')' || itpeek == ']') {
							it.move();
							return createBoundedVersionRange(c, createVersionNumber(versionsb, it),
									createVersionNumber(rightversionsb, it), itpeek);
						}
						throw new IllegalArgumentException("Invalid range ending character: " + itpeek + " at index "
								+ it.getIndex() + " in " + it.getCharSequence());
					} else if (nc == ')' || nc == ']') {
						it.move();
						//closed the range
						VersionRange created = createSingleBoundVersionRange(c, versionsb, nc, it);
						if (it.hasNext()) {
							if (it.peek() == '&') {
								it.move();
								VersionRange next = parseDependencyVersionRange(it);
								return IntersectionVersionRange.create(created, next);
							}
						}
						return created;
					} else {
						throw new IllegalArgumentException("Invalid range character: " + nc + " at index "
								+ it.getIndex() + " in " + it.getCharSequence());
					}
				} while (it.hasNext());
				throw new IllegalArgumentException(
						"Missing range closing brace at index: " + it.getIndex() + " in " + it.getCharSequence());
			} else if (c == '{') {
				if (!it.hasNext()) {
					throw new IllegalArgumentException(
							"Unclosed { at index: " + it.getIndex() + " in " + it.getCharSequence());
				}
				if (it.peek() == '}') {
					//empty {}, not satisfiable
					it.move();
					return UnsatisfiableVersionRange.INSTANCE;
				}
				Set<VersionRange> orranges = new LinkedHashSet<>();
				while (true) {
					if (it.peek() == '}') {
						throw new IllegalArgumentException(
								"Unexpected character at index: " + it.getIndex() + " in " + it.getCharSequence());
					}
					VersionRange next = parseDependencyVersionRange(it);
					orranges.add(next);
					if (!it.hasNext()) {
						throw new IllegalArgumentException(
								"Unclosed { at index: " + it.getIndex() + " in " + it.getCharSequence());
					}
					char p = it.peek();
					if (p == '|') {
						it.move();
						continue;
					}
					if (p == '}') {
						break;
					}
					throw new IllegalArgumentException(
							"Unexpected character at index: " + it.getIndex() + " in " + it.getCharSequence());
				}
				//move over the '}'
				it.move();
				return UnionVersionRange.create(orranges);
			} else {
				throw new IllegalArgumentException("Invalid range character: " + c + " at index " + (it.getIndex() - 1)
						+ " in " + it.getCharSequence());
			}
		}
		throw new IllegalArgumentException("Invalid range: " + it.getCharSequence());
	}

	private static String createVersionNumber(StringBuilder versionsb, NonSpaceIterator it) {
		String result = versionsb.toString();
		if (!BundleIdentifier.isValidVersionNumber(result)) {
			throw new IllegalArgumentException("Invalid version number: " + result + " in " + it.getCharSequence());
		}
		return result;
	}

	private static VersionRange createBoundedVersionRange(char lr, String leftversionstr, String rightversionstr,
			char rr) {
		if (BundleIdentifier.compareVersionNumbers(leftversionstr, rightversionstr) >= 0) {
			throw new IllegalArgumentException(
					"Invalid range bounds: " + lr + leftversionstr + ", " + rightversionstr + rr);
		}
		switch (lr) {
			case '[': {
				if (rr == ']') {
					return new BoundedVersionRange(leftversionstr, rightversionstr,
							BoundedVersionRange.TYPE_LEFT_INCLUSIVE_RIGHT_INCLUSIVE);
				}
				return new BoundedVersionRange(leftversionstr, rightversionstr,
						BoundedVersionRange.TYPE_LEFT_INCLUSIVE_RIGHT_EXCLUSIVE);
			}
			case '(': {
				if (rr == ')') {
					return new BoundedVersionRange(leftversionstr, rightversionstr,
							BoundedVersionRange.TYPE_LEFT_EXCLUSIVE_RIGHT_EXCLUSIVE);
				}
				return new BoundedVersionRange(leftversionstr, rightversionstr,
						BoundedVersionRange.TYPE_LEFT_EXCLUSIVE_RIGHT_INCLUSIVE);
			}
			default: {
				throw new AssertionError("Unknown range bounds: " + lr);
			}
		}
	}

	private static VersionRange createSingleBoundVersionRange(char lr, StringBuilder versionsb, char rr,
			NonSpaceIterator it) {
		switch (lr) {
			case '[': {
				String versionstr = createVersionNumber(versionsb, it);
				if (rr == ']') {
					return new ExactVersionRange(versionstr);
				}
				return new MinimumVersionRange(versionstr);
			}
			case '(': {
				String versionstr = createVersionNumber(versionsb, it);
				if (rr == ')') {
					throw new IllegalArgumentException(
							"Illegal range definition: (" + versionstr + ") in " + it.getCharSequence());
				}
				return new MaximumVersionRange(versionstr);
			}
			default: {
				throw new AssertionError("Unknown range bounds: " + lr);
			}
		}
	}

}
