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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Pattern;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.nest.bundle.BundleDependencyInformation.LinePeekIterator;

/**
 * Contains immutable information about external bundle dependencies.
 * <p>
 * The class encloses external dependencies referenced using an {@link URI} mapped to their respective
 * {@link ExternalDependencyList ExternalDependencyLists}. The associated dependency lists contain the properties of the
 * declared dependencies on the given external resource.
 * <p>
 * Use {@link #create(Map)} to create a new instance or {@link #readFrom(InputStream)} to read from an input stream.
 * 
 * @since saker.nest 0.8.5
 * @see BundleInformation#ENTRY_BUNDLE_EXTERNAL_DEPENDENCIES
 * @see BundleInformation#getExternalDependencyInformation()
 */
public final class ExternalDependencyInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	private static final WildcardPath WILDCARD_SLASH = WildcardPath.valueOf(SakerPath.PATH_SLASH);

	/**
	 * A singleton instance that contains no dependencies.
	 */
	public static final ExternalDependencyInformation EMPTY = new ExternalDependencyInformation(Collections.emptyMap());

	private Map<URI, ? extends ExternalDependencyList> dependencies;

	/**
	 * For {@link Externalizable}.
	 */
	public ExternalDependencyInformation() {
	}

	private ExternalDependencyInformation(Map<URI, ? extends ExternalDependencyList> dependencies) {
		this.dependencies = dependencies;
	}

	/**
	 * Creates a new dependency information that is populated using the argument map.
	 * 
	 * @param dependencies
	 *            The dependencies.
	 * @return The new dependency information.
	 * @throws NullPointerException
	 *             If the argument or any elements of it are <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If an {@link URI} key is encountered multiple times.
	 */
	public static ExternalDependencyInformation create(Map<URI, ? extends ExternalDependencyList> dependencies)
			throws NullPointerException, IllegalArgumentException {
		Objects.requireNonNull(dependencies, "dependencies");
		if (dependencies.isEmpty()) {
			return EMPTY;
		}
		LinkedHashMap<URI, ExternalDependencyList> thisdependencies = new LinkedHashMap<>();
		for (Entry<URI, ? extends ExternalDependencyList> entry : dependencies.entrySet()) {
			URI uri = entry.getKey();
			if (uri == null) {
				throw new NullPointerException("Null external dependency uri.");
			}
			ExternalDependencyList dlist = entry.getValue();
			if (dlist == null) {
				throw new NullPointerException("Null dependency list for: " + uri);
			}
			if (!dlist.isEmpty()) {
				ExternalDependencyList prev = thisdependencies.putIfAbsent(uri, dlist);
				if (prev != null) {
					throw new IllegalArgumentException("Duplicate external dependency: " + uri);
				}
			}
		}
		ExternalDependencyInformation result = new ExternalDependencyInformation(
				ImmutableUtils.unmodifiableMap(dependencies));
		//validate hash declarations
		BundleUtils.getExternalDependencyInformationHashes(result);
		return result;
	}

	/**
	 * Gets the dependency information.
	 * 
	 * @return An unmodifiable map of dependencies.
	 */
	public Map<URI, ? extends ExternalDependencyList> getDependencies() {
		return dependencies;
	}

	/**
	 * Checks if this dependency object is empty.
	 * 
	 * @return <code>true</code> if there are no declared dependencies in this information object.
	 */
	public boolean isEmpty() {
		return ObjectUtils.isNullOrEmpty(dependencies);
	}

	/**
	 * Parses the data from the argument input stream and constructs a new dependency information object.
	 * <p>
	 * The format of the input is the following:
	 * <p>
	 * 
	 * <pre>
	 * &lt;dependency-uri&gt;
	 * 	&lt;dependency-kind&gt;[,&lt;dependency-kind&gt;]*
	 * 		[entries: &lt;wildcard&gt;[;&lt;wildcard&gt;]*]?
	 * 		[&lt;meta-key&gt;: &lt;meta-value&gt;]*
	 * 	[SHA-256: &lt;hexa&gt;]?
	 * 	[SHA-1: &lt;hexa&gt;]?
	 * 	[MD5: &lt;hexa&gt;]?
	 * 	&lt;source-attachment|documentation-attachment&gt;: &lt;uri&gt;
	 * 		[SHA-256: &lt;hexa&gt;]?
	 * 		[SHA-1: &lt;hexa&gt;]?
	 * 		[MD5: &lt;hexa&gt;]?
	 * 		[entries: &lt;wildcard&gt;[;&lt;wildcard&gt;]*]?
	 * 		[target: &lt;wildcard&gt;[;&lt;wildcard&gt;]*]?
	 * 		[&lt;meta-key&gt;: &lt;meta-value&gt;]*
	 * </pre>
	 * 
	 * The format itself is somewhat derived from the format defined by
	 * {@link BundleDependencyInformation#readFrom(InputStream, BundleIdentifier)}. Please refer to that to get detailed
	 * information about indenting and formatting.
	 * <p>
	 * The format consists of top level {@link URI} blocks that define an external dependency. The file format doesn't
	 * impose any restriction on the scheme of the {@link URI}, however, some bundle storages may do so.
	 * <p>
	 * Each dependency declaration can have the following sub-entries:
	 * <ul>
	 * <li><b>Dependency delcaration.</b> One or multiple kinds are associated with a dependency. In addition to that,
	 * arbitrary meta-data can be declared for the dependencies.
	 * <p>
	 * The special <code>entries</code> meta-data specifies that the subject of the dependency should be taken from the
	 * declared {@link URI} by interpreting it as a ZIP archive. The <code>entries</code> meta-data declare one or more
	 * {@linkplain WildcardPath wildcards} that are used to select the entries in the archive.
	 * <p>
	 * The special <code>/</code> wildcard in the <code>entries</code> meta-data will signal that the resource ZIP
	 * archive should be used. This is the default behaviour.
	 * <p>
	 * The dependency kinds and other meta-data are interpreted the same way as in {@link BundleDependency}.</li>
	 * <li><b>Hash declarations.</b> SHA-256, SHA-1 and MD5 hashes can be declared for the referenced resource. The
	 * repository will verify that the contents of the external resource matches the specified hashes.
	 * <p>
	 * This can be useful to prevent unexpected changes to the external resources. Some bundle storages may require you
	 * to specify hashes to validate the bundle.</li>
	 * <li><b>Source and documentation attachments.</b> The <code>source-attachment</code> and
	 * <code>documentation-attachment</code> entries can be used to declare additional meta-resources that are
	 * associated with the external resource. The hash and <code>entries</code> values work the same way as previously.
	 * The <code>target</code> meta-data specify the archive entries in the main resource for which the attachments are
	 * declared.</li>
	 * </ul>
	 * An example:
	 * 
	 * <pre>
	 * https://example.com/external.jar
	 * 	classpath
	 * 	SHA-256: 1234567890123456789012345678901234567890123456789012345678901234
	 * </pre>
	 * 
	 * The above declares an external <code>classpath</code> dependency on <code>https://example.com/external.jar</code>
	 * and expects it to have the SHA-256 hash as declared above.
	 * <p>
	 * Using the <code>entries</code> attribute:
	 * 
	 * <pre>
	 * https://example.com/external.jar
	 * 	classpath
	 * 		entries: lib/*.jar
	 * </pre>
	 *
	 * The above is a dependency that loads all <code>jar</code> files in the <code>lib</code> directory of the
	 * specified resource. Note that the <code>external.jar</code> itself won't be part of the classpath! To do that,
	 * use the <code>/</code> special entry:
	 * 
	 * <pre>
	 * https://example.com/external.jar
	 * 	classpath
	 * 		entries: /;lib/*.jar
	 * </pre>
	 * 
	 * The addition of the <code>/</code> value to the <code>entries</code> property will cause the
	 * <code>external.jar</code> to be part of the classpath dependency as well as all the specified libraries in it.
	 * 
	 * @param is
	 *            The input stream to parse. UTF-8 encoding is used.
	 * @return The parsed dependency information.
	 * @throws NullPointerException
	 *             If the input stream is <code>null</code>.
	 * @throws IllegalArgumentException
	 *             If the input has invalid format.
	 * @throws IOException
	 *             In case of I/O error.
	 * @see ExternalDependencyList
	 * @see ExternalDependency
	 * @see ExternalAttachmentInformation
	 */
	public static ExternalDependencyInformation readFrom(InputStream is)
			throws NullPointerException, IllegalArgumentException, IOException {
		Objects.requireNonNull(is, "input stream");
		//in the format of:

//		https://example.com/path/to/my_external_dependency.jar
//			SHA-256: 0123456789abcdef hexa
//			SHA-1: hexa
//			MD5: hexa
//			one, or, more, kinds
//				meta: abc
//				entries: lib/**.jar
//			source-attachment: https://example.com/path/to/sources-v1.jar
//				entries: src/sources.jar
//				target: lib/*-v1.jar
//				SHA-256: hexa
//			source-attachment: https://example.com/path/to/sources-v2.jar
//				entries: lib/*-v2.jar

		Map<URI, ExternalDependencyList> dependencies = new LinkedHashMap<>();
		try (BufferedReader bufreader = new BufferedReader(
				new InputStreamReader(StreamUtils.closeProtectedInputStream(is), StandardCharsets.UTF_8))) {
			LinePeekIterator it = new LinePeekIterator(bufreader);
			while (it.hasNext()) {
				String line = it.next();
				int linenumber = it.getLineNumber();
				if (!BundleDependencyInformation.getLeadingWhitespace(line).isEmpty()) {
					throw new IllegalArgumentException(
							"Malformed external dependency at line: " + linenumber + " (Expected URI)");
				}
				URI uri;
				try {
					uri = new URI(line.trim());
				} catch (URISyntaxException e) {
					throw new IllegalArgumentException("Failed to parse dependency URI at line: " + linenumber, e);
				}
				ExternalDependencyList deplist = readExternalDependencyList(it);
				ExternalDependencyList prev = dependencies.putIfAbsent(uri, deplist);
				if (prev != null) {
					throw new IllegalArgumentException(
							"Multiple dependency declarations for URI: " + uri + " at line: " + linenumber);
				}
			}
		}
		return create(dependencies);
	}

	private static ExternalDependency parseExternalDependency(String ws, String kindsline, LinePeekIterator it)
			throws IOException {
		ExternalDependency.Builder builder = ExternalDependency.builder();
		boolean hadkind = false;
		for (String kind : BundleDependencyInformation.COMMA_WHITESPACE_SPLIT.split(kindsline)) {
			if (kind.isEmpty()) {
				continue;
			}
			if (!BundleDependency.isValidKind(kind)) {
				throw new IllegalArgumentException(
						"Invalid dependency kind: " + kind + " at line: " + it.getLineNumber() + " (Invalid format)");
			}
			if (StringUtils.startsWithIgnoreCase(kind, "nest-")) {
				throw new IllegalArgumentException(
						"Reserved dependency kind: " + kind + " at line: " + it.getLineNumber());
			}
			hadkind = true;
			builder.addKind(kind);
		}
		if (!hadkind) {
			throw new IllegalArgumentException("No dependency kind specified at line: " + it.getLineNumber());
		}
		if (it.hasNext()) {
			//there may be metadata
			String mdfirstline = it.peek();
			String mdws = BundleDependencyInformation.getLeadingWhitespace(mdfirstline);
			if (mdws.startsWith(ws) && mdws.length() > ws.length()) {
				//actually a metadata line
				BundleDependencyInformation.parseMetaDatas(it, ws, mdws, (name, content) -> {
					if ("entries".equalsIgnoreCase(name)) {
						for (String wc : Pattern.compile("[;]+").split(content)) {
							if (wc.isEmpty()) {
								continue;
							}
							WildcardPath wcpath;
							try {
								wcpath = WildcardPath.valueOf(wc);
							} catch (Exception e) {
								throw new IllegalArgumentException(
										"Failed to parse entries: " + wc + " at line: " + it.getLineNumber());
							}
							if (WILDCARD_SLASH.equals(wcpath)) {
								builder.setIncludesMainArchive(true);
							} else {
								builder.addEntry(wcpath);
							}
						}
						return;
					}
					if (builder.hasMetaData(name)) {
						throw new IllegalArgumentException(
								"Multiple metadata specified with name: " + name + " at line: " + it.getLineNumber());
					}
					builder.addMetaData(name, content);
				});
			}
		}
		return builder.build();
	}

	private static ExternalDependencyList readExternalDependencyList(LinePeekIterator it) throws IOException {
		if (!it.hasNext()) {
			return ExternalDependencyList.EMPTY;
		}
		String firstline = it.peek();
		String ws = BundleDependencyInformation.getLeadingWhitespace(firstline);
		if (ws.isEmpty()) {
			return ExternalDependencyList.EMPTY;
		}
		ExternalDependencyList.Builder builder = ExternalDependencyList.builder();
		do {
			String line = it.peek();
			if (!line.startsWith(ws)) {
				break;
			}
			it.move();
			int cidx = line.indexOf(':');
			if (cidx < 0) {
				ExternalDependency dep = parseExternalDependency(ws, line, it);
				builder.addDepdendency(dep);
			} else {
				parseDependencyListInformation(it, ws, builder, line, cidx);
			}
		} while (it.hasNext());
		return builder.build();
	}

	private static void parseDependencyListInformation(LinePeekIterator it, String ws,
			ExternalDependencyList.Builder builder, String line, int cidx) throws IOException {
		String key = line.substring(ws.length(), cidx);
		if (!BundleDependencyInformation.getLeadingWhitespace(key).isEmpty()) {
			throw new IllegalArgumentException(
					"Invalid indentation for dependency kinds at line: " + it.getLineNumber());
		}
		key = key.trim();
		switch (key.toLowerCase(Locale.ENGLISH)) {
			case "sha-256": {
				if (builder.getSha256Hash() != null) {
					throw new IllegalArgumentException("SHA-256 specified multiple times: " + it.getLineNumber());
				}
				String hashval = line.substring(cidx + 1).trim();
				builder.setSha256Hash(hashval);
				break;
			}
			case "sha-1": {
				if (builder.getSha1Hash() != null) {
					throw new IllegalArgumentException("SHA-1 specified multiple times: " + it.getLineNumber());
				}
				String hashval = line.substring(cidx + 1).trim();
				builder.setSha1Hash(hashval);
				break;
			}
			case "md5": {
				if (builder.getMd5Hash() != null) {
					throw new IllegalArgumentException("MD5 specified multiple times: " + it.getLineNumber());
				}
				String hashval = line.substring(cidx + 1).trim();
				builder.setMd5Hash(hashval);
				break;
			}
			case "source-attachment": {
				String uristr = line.substring(cidx + 1).trim();
				URI uri;
				try {
					uri = new URI(uristr);
				} catch (URISyntaxException e) {
					throw new IllegalArgumentException("Failed to parse attachment URI: " + uristr, e);
				}
				if (builder.hasSourceAttachment(uri)) {
					throw new IllegalArgumentException("Duplicate source attachment: " + uri);
				}
				builder.addSourceAttachment(uri, readAttachmentInfo(it, ws));
				break;
			}
			case "documentation-attachment": {
				String uristr = line.substring(cidx + 1).trim();
				URI uri;
				try {
					uri = new URI(uristr);
				} catch (URISyntaxException e) {
					throw new IllegalArgumentException("Failed to parse attachment URI: " + uristr, e);
				}
				if (builder.hasDocumentationAttachment(uri)) {
					throw new IllegalArgumentException("Duplicate source attachment: " + uri);
				}
				builder.addDocumentationAttachment(uri, readAttachmentInfo(it, ws));
				break;
			}
			default: {
				throw new IllegalArgumentException(
						"Unrecognized dependency information: " + key + " at line: " + it.getLineNumber());
			}
		}
	}

	private static ExternalAttachmentInformation readAttachmentInfo(LinePeekIterator it, String ws) throws IOException {
		if (!it.hasNext()) {
			return ExternalAttachmentInformation.EMPTY;
		}
		ExternalAttachmentInformation.Builder builder = ExternalAttachmentInformation.builder();
		String mdfirstline = it.peek();
		String mdws = BundleDependencyInformation.getLeadingWhitespace(mdfirstline);
		if (mdws.startsWith(ws) && mdws.length() > ws.length()) {
			BundleDependencyInformation.parseMetaDatas(it, ws, mdws, (name, content) -> {
				if ("entries".equalsIgnoreCase(name)) {
					for (String wc : Pattern.compile("[;]+").split(content)) {
						if (wc.isEmpty()) {
							continue;
						}
						WildcardPath wcpath;
						try {
							wcpath = WildcardPath.valueOf(wc);
						} catch (Exception e) {
							throw new IllegalArgumentException(
									"Failed to parse entries: " + wc + " at line: " + it.getLineNumber());
						}
						if (WILDCARD_SLASH.equals(wcpath)) {
							builder.setIncludesMainArchive(true);
						} else {
							builder.addEntry(wcpath);
						}
					}
					return;
				}
				if ("target".equalsIgnoreCase(name)) {
					for (String wc : Pattern.compile("[;]+").split(content)) {
						if (wc.isEmpty()) {
							continue;
						}
						WildcardPath wcpath;
						try {
							wcpath = WildcardPath.valueOf(wc);
						} catch (Exception e) {
							throw new IllegalArgumentException(
									"Failed to parse target: " + wc + " at line: " + it.getLineNumber());
						}
						if (WILDCARD_SLASH.equals(wcpath)) {
							builder.setTargetsMainArchive(true);
						} else {
							builder.addTargetEntry(wcpath);
						}
					}
					return;
				}
				//ignore case check
				if ("SHA-256".equalsIgnoreCase(name)) {
					if (builder.getSha256Hash() != null) {
						throw new IllegalArgumentException("SHA-256 specified multiple times: " + it.getLineNumber());
					}
					builder.setSha256Hash(content);
					return;
				}
				if ("SHA-1".equalsIgnoreCase(name)) {
					if (builder.getSha1Hash() != null) {
						throw new IllegalArgumentException("SHA-1 specified multiple times: " + it.getLineNumber());
					}
					builder.setSha1Hash(content);
					return;
				}
				if ("MD5".equalsIgnoreCase(name)) {
					if (builder.getMd5Hash() != null) {
						throw new IllegalArgumentException("MD5 specified multiple times: " + it.getLineNumber());
					}
					builder.setMd5Hash(content);
					return;
				}
				if (builder.hasMetaData(name)) {
					throw new IllegalArgumentException(
							"Multiple metadata specified with name: " + name + " at line: " + it.getLineNumber());
				}
				builder.addMetaData(name, content);
			});
		}
		return builder.build();
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
		final int prime = 31;
		int result = 1;
		result = prime * result + ((dependencies == null) ? 0 : dependencies.hashCode());
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
		ExternalDependencyInformation other = (ExternalDependencyInformation) obj;
		if (dependencies == null) {
			if (other.dependencies != null)
				return false;
		} else if (!dependencies.equals(other.dependencies))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (!ObjectUtils.isNullOrEmpty(dependencies) ? "dependencies=" + dependencies : "") + "]";
	}

	private static String validateHashWithLength(String hashval, int length, String hashname) {
		int hvlen = hashval.length();
		if (hvlen != length * 2) {
			throw new IllegalArgumentException(
					"Invalid " + hashname + " hash length: " + hashval + " expected " + length + " bytes");
		}
		boolean hadupper = false;
		for (int i = 0; i < hvlen; i++) {
			char c = hashval.charAt(i);
			if (c >= 'a' && c <= 'f') {
				continue;
			}
			if (c >= '0' && c <= '9') {
				continue;
			}
			if (c >= 'A' && c <= 'F') {
				hadupper = true;
				continue;
			}
		}

		if (hadupper) {
			hashval = hashval.toLowerCase(Locale.ENGLISH);
		}
		return hashval;
	}

	static String validateSha256(String hashval) {
		return validateHashWithLength(hashval, 32, "SHA-256");
	}

	static String validateSha1(String hashval) {
		return validateHashWithLength(hashval, 20, "SHA-1");
	}

	static String validateMd5(String hashval) {
		return validateHashWithLength(hashval, 16, "SHA-MD5");
	}
}
