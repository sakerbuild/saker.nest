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
import java.util.Objects;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.nest.bundle.BundleDependencyInformation.LinePeekIterator;

public final class ExternalDependencyInformation implements Externalizable {
	private static final long serialVersionUID = 1L;

	private static final WildcardPath WILDCARD_SLASH = WildcardPath.valueOf(SakerPath.PATH_SLASH);

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

	public static ExternalDependencyInformation create(Map<URI, ? extends ExternalDependencyList> dependencies)
			throws NullPointerException {
		Objects.requireNonNull(dependencies, "dependencies");
		if (dependencies.isEmpty()) {
			return EMPTY;
		}
		LinkedHashMap<URI, ExternalDependencyList> thisdependencies = new LinkedHashMap<>();
		for (Entry<URI, ? extends ExternalDependencyList> entry : dependencies.entrySet()) {
			URI uri = entry.getKey();
			ExternalDependencyList dlist = entry.getValue();
			if (!dlist.isEmpty()) {
				thisdependencies.put(uri, dlist);
			}
		}
		return new ExternalDependencyInformation(ImmutableUtils.unmodifiableMap(dependencies));
	}

	public Map<URI, ? extends ExternalDependencyList> getDependencies() {
		return dependencies;
	}

	public boolean isEmpty() {
		return ObjectUtils.isNullOrEmpty(dependencies);
	}

	public static ExternalDependencyInformation readFrom(InputStream is)
			throws NullPointerException, IllegalArgumentException, IOException {
		//in the format of:

//		https://example.com/path/to/my_external_dependency.jar
//			SHA-256: 0123456789abcdef hexa
//			SHA-1: hexa
//			MD5: hexa
//			one, or, more, kinds
//				meta: abc
//				entries: lib/**.jar
//			source-attachment: https://example.com/path/to/sources-v1.jar
//				entries: lib/*-v1.jar
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
								builder.setIncludesEnclosingArchive(true);
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
				validateSha256(hashval);
				builder.setSha256Hash(hashval.toLowerCase(Locale.ENGLISH));
				break;
			}
			case "sha-1": {
				if (builder.getSha1Hash() != null) {
					throw new IllegalArgumentException("SHA-1 specified multiple times: " + it.getLineNumber());
				}
				String hashval = line.substring(cidx + 1).trim();
				validateSha1(hashval);
				builder.setSha1Hash(hashval.toLowerCase(Locale.ENGLISH));
				break;
			}
			case "md5": {
				if (builder.getMd5Hash() != null) {
					throw new IllegalArgumentException("MD5 specified multiple times: " + it.getLineNumber());
				}
				String hashval = line.substring(cidx + 1).trim();
				validateMd5(hashval);
				builder.setMd5Hash(hashval.toLowerCase(Locale.ENGLISH));
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

	private static void validateSha256(String hashval) {
		try {
			byte[] hash = StringUtils.parseHexString(hashval);
			if (hash.length != 32) {
				throw new IllegalArgumentException("Invalid SHA-256 hash length: " + hash.length + " expected 32");
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse SHA-256 hash: " + hashval, e);
		}
	}

	private static void validateSha1(String hashval) {
		try {
			byte[] hash = StringUtils.parseHexString(hashval);
			if (hash.length != 20) {
				throw new IllegalArgumentException("Invalid SHA-1 hash length: " + hash.length + " expected 20");
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse SHA-1 hash: " + hashval, e);
		}
	}

	private static void validateMd5(String hashval) {
		try {
			byte[] hash = StringUtils.parseHexString(hashval);
			if (hash.length != 16) {
				throw new IllegalArgumentException("Invalid MD5 hash length: " + hash.length + " expected 16");
			}
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to parse MD5 hash: " + hashval, e);
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
							builder.setIncludesEnclosingArchive(true);
						} else {
							builder.addEntry(wcpath);
						}
					}
					return;
				}
				if ("sha-256".equalsIgnoreCase(name)) {
					content = content.toLowerCase(Locale.ENGLISH);
					validateSha256(content);
				} else if ("sha-1".equalsIgnoreCase(name)) {
					content = content.toLowerCase(Locale.ENGLISH);
					validateSha1(content);
				} else if ("md5".equalsIgnoreCase(name)) {
					content = content.toLowerCase(Locale.ENGLISH);
					validateMd5(content);
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
	public String toString() {
		return getClass().getSimpleName() + "["
				+ (!ObjectUtils.isNullOrEmpty(dependencies) ? "dependencies=" + dependencies : "") + "]";
	}

}
