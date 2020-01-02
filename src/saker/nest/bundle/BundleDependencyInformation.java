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

import java.io.BufferedReader;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.nest.version.VersionRange;

/**
 * Contains immutable information about bundle dependencies.
 * <p>
 * This class encloses {@link BundleIdentifier BundleIdentifiers} mapped to their respective {@link BundleDependencyList
 * BundleDependencyLists}. Each bundle identifier is associated with a dependency list that contains the kinds of
 * dependencies, allowed version ranges, and meta-datas.
 * <p>
 * The dependencies in the class are contained in an ordered map, meaning that serializing and deserializing won't
 * change the dependency iteration order.
 * <p>
 * Use {@link #create(Map)} to create a new instance or {@link #readFrom(InputStream, BundleIdentifier)} to read from an
 * input stream.
 *
 * @see BundleInformation#ENTRY_BUNDLE_DEPENDENCIES
 * @see BundleInformation#getDependencyInformation()
 */
public final class BundleDependencyInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Singleton instance contanining no dependencies.
	 */
	public static final BundleDependencyInformation EMPTY = new BundleDependencyInformation(Collections.emptyMap());

	private Map<BundleIdentifier, ? extends BundleDependencyList> dependencies;

	/**
	 * For {@link Externalizable}.
	 */
	public BundleDependencyInformation() {
	}

	private BundleDependencyInformation(Map<BundleIdentifier, ? extends BundleDependencyList> dependencies) {
		this.dependencies = dependencies;
	}

	/**
	 * Creates a new dependency information object that contains the dependencies specified in the argument.
	 * 
	 * @param dependencies
	 *            The dependencies.
	 * @return The new dependency information object.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If any of the bundle identifier keys in the argument contains
	 *             {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}.
	 */
	public static BundleDependencyInformation create(Map<BundleIdentifier, ? extends BundleDependencyList> dependencies)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(dependencies, "dependencies");
		if (dependencies.isEmpty()) {
			return EMPTY;
		}
		LinkedHashMap<BundleIdentifier, BundleDependencyList> thisdependencies = new LinkedHashMap<>();
		for (Entry<BundleIdentifier, ? extends BundleDependencyList> entry : dependencies.entrySet()) {
			BundleIdentifier bundleid = entry.getKey();
			if (bundleid.getVersionQualifier() != null) {
				throw new IllegalArgumentException(
						"Dependency bundle identifier cannot have version qualifier: " + bundleid);
			}
			BundleDependencyList dlist = entry.getValue();
			if (!dlist.isEmpty()) {
				thisdependencies.put(bundleid, dlist);
			}
		}
		return new BundleDependencyInformation(ImmutableUtils.unmodifiableMap(thisdependencies));
	}

	/**
	 * Gets the dependencies in this information.
	 * <p>
	 * The keys in the returned map will have no {@linkplain BundleIdentifier#getVersionQualifier() version qualifiers}.
	 * 
	 * @return The unmodifiable map of dependencies.
	 */
	public Map<BundleIdentifier, ? extends BundleDependencyList> getDependencies() {
		return dependencies;
	}

	/**
	 * Gets the dependency list for the argument bundle identifier.
	 * 
	 * @param bundleid
	 *            The bundle identifier.
	 * @return The associated dependency list or <code>null</code> if none.
	 */
	public BundleDependencyList getDependencyList(BundleIdentifier bundleid) {
		if (bundleid == null) {
			return null;
		}
		return dependencies.get(bundleid);
	}

	/**
	 * Creates a new dependency information by filtering the dependencies in <code>this</code> instance.
	 * <p>
	 * The arugment transformation function will be called for all dependency present in <code>this</code> instance. The
	 * function may modify or omit the dependency passed to it.
	 * <p>
	 * If the function returns <code>null</code>, the passed dependency will not be part of the result. In other cases,
	 * the returned dependency list will be added to the result.
	 * 
	 * @param transformation
	 *            The transformation function.
	 * @return The dependency information that is the result of the filtering.
	 * @throws NullPointerException
	 *             If the transformation function is <code>null</code>, and <code>this</code> is not
	 *             {@linkplain #isEmpty() empty}.
	 * @see BundleDependencyList#filter(Function)
	 */
	public BundleDependencyInformation filter(
			BiFunction<? super BundleIdentifier, ? super BundleDependencyList, ? extends BundleDependencyList> transformation)
			throws NullPointerException {
		Objects.requireNonNull(transformation, "dependency transformation function");
		if (this.isEmpty()) {
			return this;
		}
		Map<BundleIdentifier, BundleDependencyList> resultdeps = new LinkedHashMap<>();
		boolean changed = false;
		for (Entry<BundleIdentifier, ? extends BundleDependencyList> entry : getDependencies().entrySet()) {
			BundleDependencyList deplist = entry.getValue();
			BundleIdentifier depbundleid = entry.getKey();
			BundleDependencyList ndeplist = transformation.apply(depbundleid, deplist);
			if (!changed && !deplist.equals(ndeplist)) {
				changed = true;
			}
			if (ndeplist != null && !ndeplist.isEmpty()) {
				resultdeps.put(depbundleid, ndeplist);
			}
		}
		if (!changed) {
			return this;
		}
		if (resultdeps.isEmpty()) {
			return EMPTY;
		}
		return new BundleDependencyInformation(ImmutableUtils.unmodifiableMap(resultdeps));
	}

	/**
	 * Checks if this information has any dependencies.
	 * 
	 * @return <code>true</code> if this information is empty.
	 */
	public boolean isEmpty() {
		return dependencies.isEmpty();
	}

	/**
	 * Checks if this information contains any optional dependencies.
	 * 
	 * @return <code>true</code> if there is at least one optional {@link BundleDependency}.
	 * @see BundleDependency#isOptional()
	 * @see BundleDependencyList#hasOptional()
	 */
	public boolean hasOptional() {
		for (BundleDependencyList dlist : dependencies.values()) {
			if (dlist.hasOptional()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets a dependency information object that contains the same dependencies as <code>this</code>, but without the
	 * optional dependencies.
	 * 
	 * @return The dependency information without optionals.
	 */
	public BundleDependencyInformation withoutOptionals() {
		Map<BundleIdentifier, BundleDependencyList> resdeps = new LinkedHashMap<>();
		for (Entry<BundleIdentifier, ? extends BundleDependencyList> entry : this.dependencies.entrySet()) {
			BundleDependencyList without = entry.getValue().withoutOptionals();
			if (!without.isEmpty()) {
				resdeps.put(entry.getKey(), without);
			}
		}
		BundleDependencyInformation result = new BundleDependencyInformation(ImmutableUtils.unmodifiableMap(resdeps));
		return result;
	}

	private static final byte[] DOUBLE_TABS = { '\t', '\t' };
	private static final byte[] COLON_SPACE = { ':', ' ' };

	/**
	 * Writes this dependency information to the argument output stream in the format specified by
	 * {@link #readFrom(InputStream, BundleIdentifier)}.
	 * 
	 * @param os
	 *            The output stream.
	 * @throws NullPointerException
	 *             If the output is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public void writeTo(OutputStream os) throws NullPointerException, IOException {
		Objects.requireNonNull(os, "output");
		for (Entry<BundleIdentifier, ? extends BundleDependencyList> entry : dependencies.entrySet()) {
			BundleIdentifier bid = entry.getKey();
			BundleDependencyList deplist = entry.getValue();
			if (deplist.isEmpty()) {
				continue;
			}
			os.write(bid.toString().getBytes(StandardCharsets.UTF_8));
			os.write('\n');
			for (BundleDependency dep : deplist.getDependencies()) {
				os.write('\t');
				os.write(StringUtils.toStringJoin(", ", dep.getKinds()).getBytes(StandardCharsets.UTF_8));
				os.write(COLON_SPACE);
				os.write(dep.getRange().toString().getBytes(StandardCharsets.UTF_8));
				os.write('\n');

				for (Entry<String, String> md : dep.getMetaData().entrySet()) {
					os.write(DOUBLE_TABS);
					os.write(md.getKey().getBytes(StandardCharsets.UTF_8));
					os.write(COLON_SPACE);
					String metadataval = md.getValue();
					if (!metadataval.isEmpty()) {
						byte[] metadatavalbytes = metadataval.getBytes(StandardCharsets.UTF_8);
						if (metadataval.indexOf('\n') < 0) {
							if (metadatavalbytes[0] == '"') {
								os.write('\"');
								os.write(metadatavalbytes);
								os.write('\"');
							} else {
								os.write(metadatavalbytes);
							}
						} else {
							os.write('\"');
							for (int i = 0; i < metadatavalbytes.length; i++) {
								byte b = metadatavalbytes[i];
								if (b == '\n') {
									if (i > 0 && metadatavalbytes[i - 1] == '"') {
										os.write('\\');
									}
								}
								os.write(b);
							}
//						os.write(metadataval
////							.replace("\\", "\\\\")
//								.replace("\"", "\\\"").getBytes(StandardCharsets.UTF_8));
							os.write('\"');
						}
					}
					os.write('\n');
				}
			}
		}
	}

	/**
	 * Parses the bundle dependencies from the argument input stream in the format defined by this function.
	 * <p>
	 * The dependencies are declared in 3 levels of white-space indented lines.
	 * <p>
	 * The first level contains the name of the bundle identifier that the dependency is declared on:
	 * 
	 * <pre>
	 * my.bundle
	 *     ...
	 * second.dependency.bundle-q1-q2
	 *     ....
	 * </pre>
	 * 
	 * The bundle identifiers may contain {@linkplain BundleIdentifier#getBundleQualifiers() qualifiers}, but no
	 * {@linkplain BundleIdentifier#getMetaQualifiers() meta-qualifiers}. Each bundle identifier may only occurr once.
	 * All dependency information for a bundle is written in the block following the bundle identifier.
	 * <p>
	 * In each indented block, the indentation characters must be the same. You can use one or more tabs or spaces, but
	 * make sure to use the same indentation throughout an indented block.
	 * <p>
	 * A dependency on a bundle consists of one or multiple dependency kinds, a version range, and optional meta-data:
	 * 
	 * <pre>
	 * my.bundle
	 *     classpath: 1.0
	 *     kind2, kind3,, kind4: [0)
	 * </pre>
	 * 
	 * The above shows two dependency declarations. The kind <code>classpath</code> is applied with the
	 * {@linkplain VersionRange version range} of <code>1.0</code>. The dependency with kinds <code>kind2</code>,
	 * <code>kind3</code>, <code>kind4</code> is applied with the version range of <code>[0)</code>.
	 * <p>
	 * A dependency declaration syntax consists of a dependency kind and version range separated by a colon character
	 * (<code>:</code>). Multiple kinds can be present separated by commas. Extraneous commas are ignored.
	 * <p>
	 * In the above, both dependency declarations are indented with 4 spaces. As mentioned above, this must stay
	 * consistend in a given block.
	 * <p>
	 * Dependency meta-datas can be declared for a given declaration:
	 * 
	 * <pre>
	 * my.bundle
	 *     classpath: 1.0
	 *         optional: true
	 *         meta2: custom-value
	 *         with-spaces: hello world
	 *         empty:
	 *         quoted: "123"
	 *         quoted: ""word""
	 *         multi-line: "1
	 * 2"
	 *         eol-quote-escape: "1"\
	 * 2"
	 *         slash: \
	 *         slash-in-str: "\"
	 * </pre>
	 * 
	 * There's two way the value of a meta-data declaration can be specified. As simple unquoted characters, or in
	 * quotes. Without any quotation, the value will be considered to start from the colon (<code>:</code>) and last
	 * until the end of the line. Any white-space will be trimmed from it.
	 * <p>
	 * With quotes, the contents of the meta-data value will be all the characters until the first quote that is found
	 * at the end of a line. Intermediate quotes don't need to be escaped. If the value needs to have quotes at the end
	 * of a line and still continue, the backslash (<code>\</code>) character needs to be appended to continue parsing
	 * the value.
	 * <p>
	 * See the following for multi-line escaping examples:
	 * 
	 * <pre>
	 * meta1: "1st
	 * 2nd"
	 * 
	 * meta2: "some"intermediate
	 * "quoted"
	 * 
	 * meta3: "quotes
	 * at"\
	 * line\\
	 * end"
	 * </pre>
	 * 
	 * In the above, the meta-data entries will be the following, with new lines as <b>\n</b>:
	 * <ul>
	 * <li><code>meta1</code>: <code>1st</code><b>\n</b><code>2nd</code></li>
	 * <li><code>meta2</code>: <code>some"intermediate</code><b>\n</b><code>"quoted</code></li>
	 * <li><code>meta3</code>:
	 * <code>quotes</code><b>\n</b><code>at"</code><b>\n</b><code>line\</code><b>\n</b><code>end</code></li>
	 * </ul>
	 * If a backslash character is found at the end of a line in a multi-line quoted string value, then it will be
	 * omitted by the parser. Line endings are normalized to <code>\n</code>.
	 * <p>
	 * As a convenience feature, the parser allows version ranges to contain the <code>this</code> identifier. If found,
	 * then it will be substituted by the version number of the declaring bundle identifier parameter (if any) for
	 * dependencies that have the same name as the declaring bundle.
	 * <p>
	 * E.g. if the declaring bundle is <code>my.bundle-v1.0</code> then the following:
	 * 
	 * <pre>
	 * my.bundle-q1
	 *     classpath: [this]
	 * </pre>
	 * 
	 * Will be parsed as if it was the following:
	 * 
	 * <pre>
	 * my.bundle-q1
	 *     classpath: [1.0]
	 * </pre>
	 * 
	 * Note that the <code>this</code> identifier will not be substituted if it is declared on a dependency that doesn't
	 * have the same name as the declaring bundle. The following will result in a parsing error:
	 * 
	 * <pre>
	 * other.bundle
	 *     classpath: [this]
	 * </pre>
	 * 
	 * The error occurs as <code>other.bundle</code> has a different name than <code>my.bundle</code>.
	 * 
	 * @param is
	 *            The input stream to parse. UTF-8 encoding is used.
	 * @param declaringbundleid
	 *            The declaring bundle identifier to substitute <code>"this"</code> tokens in version ranges. May be
	 *            <code>null</code>.
	 * @return The parsed dependency information.
	 * @throws NullPointerException
	 *             If the input stream is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the input has invalid format.
	 * @throws IOException
	 *             In case of I/O error.
	 */
	public static BundleDependencyInformation readFrom(InputStream is, BundleIdentifier declaringbundleid)
			throws NullPointerException, IllegalArgumentException, IOException {
		//in format of:
//		my.bundle-a-b-c
//			runtime: 1.0
//				optional: true
//			main, test, classpath: 2.0
//				optional: true
//				meta-data: "{
//					... data
//				}"
//		
//		The version ranges can contain the word "this" if the dependency is on a bundle that has the same name as this one
//		in which case "this" will be replaced with the version number of this bundle.

		Objects.requireNonNull(is, "input stream");

		String declaringbundlename;
		String bundleversionnumber;
		if (declaringbundleid == null) {
			declaringbundlename = null;
			bundleversionnumber = null;
		} else {
			declaringbundlename = declaringbundleid.getName();
			bundleversionnumber = declaringbundleid.getVersionNumber();
		}

		Map<BundleIdentifier, BundleDependencyList> result = new LinkedHashMap<>();
		try (BufferedReader bufreader = new BufferedReader(
				new InputStreamReader(StreamUtils.closeProtectedInputStream(is), StandardCharsets.UTF_8))) {
			LinePeekIterator it = new LinePeekIterator(bufreader);
			readBundleDependency(it, result, declaringbundlename, bundleversionnumber);
		}
		return BundleDependencyInformation.create(result);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerialUtils.writeExternalMap(out, dependencies);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		dependencies = SerialUtils.readExternalImmutableLinkedHashMap(in);
	}

	@Override
	public int hashCode() {
		return dependencies.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BundleDependencyInformation other = (BundleDependencyInformation) obj;
		if (dependencies == null) {
			if (other.dependencies != null)
				return false;
		} else if (!dependencies.equals(other.dependencies))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + dependencies + "]";
	}

	private static final Pattern COMMA_WHITESPACE_SPLIT = Pattern.compile("[, \\t]+");

	private static void readBundleDependency(LinePeekIterator it, Map<BundleIdentifier, BundleDependencyList> result,
			String declaringbundlename, String declaringbundleversion) throws IOException {
		if (!it.hasNext()) {
			return;
		}
		do {
			String line = it.next();
			if (!getLeadingWhitespace(line).isEmpty()) {
				throw new IllegalArgumentException(
						"Malformed dependencies at line: " + it.getLineNumber() + " (Expected bundle dependency)");
			}
			BundleIdentifier bundleid;
			try {
				bundleid = BundleIdentifier.valueOf(line);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Failed to parse bundle identifier: " + line + " at line: " + it.getLineNumber(), e);
			}
			if (!bundleid.getMetaQualifiers().isEmpty()) {
				throw new IllegalArgumentException("Cannot specify meta qualifiers for bundle dependency: " + bundleid
						+ " at line: " + it.getLineNumber());
			}

			String dependencythisversion;
			if (bundleid.getName().equals(declaringbundlename)) {
				dependencythisversion = declaringbundleversion;
			} else {
				dependencythisversion = null;
			}
			BundleDependencyList prev = result.putIfAbsent(bundleid,
					readBundleDependencyContents(it, bundleid, dependencythisversion));
			if (prev != null) {
				throw new IllegalArgumentException(
						"Multiple dependency declarations for bundle: " + bundleid + " at line: " + it.getLineNumber());
			}
		} while (it.hasNext());
	}

	private static BundleDependencyList readBundleDependencyContents(LinePeekIterator it, BundleIdentifier bundleid,
			String thisversion) throws IOException {
		Collection<BundleDependency> result = new LinkedHashSet<>();
		parsingif:
		if (it.hasNext()) {
			String firstline = it.peek();
			String ws = getLeadingWhitespace(firstline);
			if (ws.isEmpty()) {
				throw new IllegalArgumentException(
						"No dependency description found for: " + bundleid + " at line: " + it.getLineNumber());
			}
			do {
				String line = it.peek();
				if (!line.startsWith(ws)) {
					break parsingif;
				}
				it.move();
				if (getLeadingWhitespace(line).equals(ws)) {
					BundleDependency.Builder builder = BundleDependency.builder();
					//a dependency kind: version range
					String depstr = line.substring(ws.length());
					int depdotidx = depstr.indexOf(':');
					if (depdotidx < 0) {
						throw new IllegalArgumentException(
								"Malformed dependency at line: " + it.getLineNumber() + " (Expected kind and version)");
					}
					String kindss = depstr.substring(0, depdotidx);
					String rangestr = depstr.substring(depdotidx + 1);
					if (thisversion != null) {
						//replace "this" references with the version of the enclosing bundle if applicable
						rangestr = rangestr.replace("this", thisversion);
					}
					try {
						builder.setRange(VersionRange.valueOf(rangestr));
					} catch (IllegalArgumentException e) {
						throw new IllegalArgumentException("Failed to parse dependency version range: " + rangestr
								+ " at line: " + it.getLineNumber(), e);
					}
					boolean hadkind = false;
					for (String kind : COMMA_WHITESPACE_SPLIT.split(kindss)) {
						if (kind.isEmpty()) {
							continue;
						}
						if (!BundleDependency.isValidKind(kind)) {
							throw new IllegalArgumentException("Invalid dependency kind: " + kind + " at line: "
									+ it.getLineNumber() + " (Invalid format)");
						}
						if (StringUtils.startsWithIgnoreCase(kind, "nest-")) {
							throw new IllegalArgumentException(
									"Reserved dependency kind: " + kind + " at line: " + it.getLineNumber());
						}
						hadkind = true;
						builder.addKind(kind);
					}
					if (!hadkind) {
						throw new IllegalArgumentException(
								"No dependency kind specified at line: " + it.getLineNumber());
					}
					if (it.hasNext()) {
						//there may be metadata
						String mdfirstline = it.peek();
						String mdws = getLeadingWhitespace(mdfirstline);
						if (mdws.startsWith(ws) && mdws.length() > ws.length()) {
							//actually a metadata line
							metadata_parser:
							do {
								String mdline = it.peek();
								if (!mdline.startsWith(mdws)) {
									break metadata_parser;
								}
								it.move();
								String mdleading = getLeadingWhitespace(mdline);
								if (mdleading.equals(mdws)) {
									//found a metadata line
									int mddotidx = mdline.indexOf(':');
									if (mddotidx < 0) {
										throw new IllegalArgumentException("Malformed metadata at line: "
												+ it.getLineNumber() + " (Expected name and content)");
									}
									String name = mdline.substring(0, mddotidx).trim();
									if (name.isEmpty()) {
										throw new IllegalArgumentException(
												"Empty metadata name at line: " + it.getLineNumber());
									}
									if (!BundleDependency.isValidMetaDataName(name)) {
										throw new IllegalArgumentException(
												"Invalid metadata name: " + name + " at line: " + it.getLineNumber());
									}
									if (builder.hasMetaData(name)) {
										throw new IllegalArgumentException("Multiple metadata specified with name: "
												+ name + " at line: " + it.getLineNumber());
									}
									if (StringUtils.startsWithIgnoreCase(name, "nest-")) {
										throw new IllegalArgumentException(
												"Reserved metadata name: " + name + " at line: " + it.getLineNumber());
									}
									String content = mdline.substring(mddotidx + 1);
									if (!content.isEmpty()) {
										if (isStartsWithQuote(content)) {
											int quotidx = content.indexOf('"');
											//multi line metadata
											StringBuilder sb = new StringBuilder();
											String l = content.substring(quotidx + 1, content.length());
											while (true) {
												int lastquotidx = l.lastIndexOf('"');
												if (lastquotidx >= 0 && isWhiteSpaceOnlyFrom(l, lastquotidx + 1)) {
													//this line closes the content
													sb.append(l, 0, lastquotidx);
													break;
												}
												int slashlastidx = l.lastIndexOf('\\');
												if (slashlastidx >= 0 && isWhiteSpaceOnlyFrom(l, slashlastidx + 1)) {
													//the line ends with a slash, and some optional whitespace
													sb.append(l, 0, slashlastidx);
												} else {
													//there are characters after the last slash
													//append the whole line
													sb.append(l);
												}
												sb.append('\n');
												if (!it.hasNext()) {
													throw new IllegalArgumentException(
															"Unclosed quotes at line: " + it.getLineNumber());
												}
												l = it.next();
											}
											content = sb.toString();
										} else {
											content = content.trim();
										}
									}
									builder.addMetaData(name, content);
								} else {
									if (mdleading.equals(ws)) {
										break metadata_parser;
									}
									throw new IllegalArgumentException(
											"Illegal indentation for dependency information at line: "
													+ it.getLineNumber() + " (Expected \"" + ws.replace("\t", "\\t")
													+ "\" or \"\")");
								}
							} while (it.hasNext());
						}
					}
					result.add(builder.build());
				} else {
					throw new IllegalArgumentException("Illegal indentation for dependency information at line: "
							+ it.getLineNumber() + " (Expected \"" + ws.replace("\t", "\\t") + "\")");
				}
			} while (it.hasNext());
		}

		if (result.isEmpty()) {
			throw new IllegalArgumentException("No dependencies specified for: " + bundleid);
		}
		return BundleDependencyList.create(result);
	}

	private static String getLeadingWhitespace(String s) {
		if (s.isEmpty()) {
			return "";
		}
		char c = s.charAt(0);
		if (c == ' ' || c == '\t') {
			//there is leading whitespace
			int i = 1;
			for (; i < s.length(); i++) {
				c = s.charAt(i);
				if (c == ' ' || c == '\t') {
					continue;
				}
				//c is not a whitespace char
				break;
			}
			return s.substring(0, i);
		}
		return "";
	}

	private static boolean isStartsWithQuote(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == ' ' || c == '\t') {
				continue;
			}
			if (c == '"') {
				return true;
			}
			return false;
		}
		return false;
	}

	private static boolean isWhiteSpaceOnly(String s) {
		return isWhiteSpaceOnlyFrom(s, 0);
	}

	private static boolean isWhiteSpaceOnlyFrom(String s, int i) {
		for (; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == ' ' || c == '\t') {
				continue;
			}
			return false;
		}
		return true;
	}

	private static class LinePeekIterator {
		private BufferedReader reader;
		private String nextLine;
		private int nextLineNumber;
		private int lineNumber;

		public LinePeekIterator(BufferedReader reader) throws IOException {
			this.reader = reader;
			moveToNext();
		}

		private void moveToNext() throws IOException {
			this.nextLineNumber = this.lineNumber;
			while (true) {
				String line = reader.readLine();
				++this.lineNumber;
				if (line == null) {
					this.nextLine = null;
					return;
				}
				if (isWhiteSpaceOnly(line)) {
					continue;
				}
				this.nextLine = line;
				break;
			}
		}

		public boolean hasNext() {
			return this.nextLine != null;
		}

		public String peek() {
			String result = this.nextLine;
			if (result == null) {
				throw new NoSuchElementException();
			}
			return result;
		}

		public void move() throws IOException {
			moveToNext();
		}

		public String next() throws IOException {
			String result = this.nextLine;
			if (result == null) {
				throw new NoSuchElementException();
			}
			moveToNext();
			return result;
		}

		public int getLineNumber() {
			return nextLineNumber;
		}

	}
}