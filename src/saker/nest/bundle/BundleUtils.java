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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.classloader.MultiClassLoader;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;
import saker.build.util.java.JavaTools;
import saker.nest.exc.IllegalArchiveEntryNameException;

public class BundleUtils {
	private static final Set<OpenOption> OPEN_OPTIONS_READ_WITHOUT_SHARING;
	static {
		Set<OpenOption> withoutsharing;
		try {
			Class<?> exooclass = Class.forName("com.sun.nio.file.ExtendedOpenOption");
			@SuppressWarnings({ "unchecked", "rawtypes" })
			OpenOption nosharewrite = (OpenOption) Enum.valueOf((Class) exooclass, "NOSHARE_WRITE");
			@SuppressWarnings({ "unchecked", "rawtypes" })
			OpenOption nosharedelete = (OpenOption) Enum.valueOf((Class) exooclass, "NOSHARE_DELETE");
			withoutsharing = ImmutableUtils
					.makeImmutableHashSet(new OpenOption[] { StandardOpenOption.READ, nosharewrite, nosharedelete });
		} catch (ClassNotFoundException | IllegalArgumentException | ClassCastException e) {
			//the enumerations somewhy couldn't be found
			withoutsharing = null;
		}
		OPEN_OPTIONS_READ_WITHOUT_SHARING = withoutsharing;
	}
	private static final ClassLoader REPOSITORY_CLASSPATH_CLASSLOADER = NestRepositoryBundleClassLoader.class
			.getClassLoader();

	public static SeekableByteChannel openExclusiveChannelForJar(Path bundlejar) throws IOException {
		if (OPEN_OPTIONS_READ_WITHOUT_SHARING != null) {
			try {
				return Files.newByteChannel(bundlejar, OPEN_OPTIONS_READ_WITHOUT_SHARING);
			} catch (UnsupportedOperationException e) {
				//not supported on mac or linux (ubuntu)
				//  we fall back to simply opening the file as it makes no sense to fail by default
				//XXX support exclusive read access to the file on mac and linux
				//    if there's no OS support for exclusive access, then we could read the JAR contents into
				//    memory and work with that to protect external malicious modifications. However, this
				//    may require significant memory usage for large bundles.
			}
		}
		return Files.newByteChannel(bundlejar, StandardOpenOption.READ);
	}

	public static BundleIdentifier requireVersioned(BundleIdentifier bundleid) {
		Objects.requireNonNull(bundleid, "bundle id");
		if (bundleid.getVersionQualifier() == null) {
			throw new IllegalArgumentException("Version qualifier is missing from bundle identifier: " + bundleid);
		}
		return bundleid;
	}

	public static boolean isTaskNameDenotesASpecificBundle(TaskName taskname) {
		String versionqualifier = BundleIdentifier.getVersionQualifier(taskname.getTaskQualifiers());
		return versionqualifier != null;
	}

	public static BundleIdentifier selectAppropriateBundleIdentifierForTask(TaskName taskname,
			Set<? extends BundleIdentifier> bundles) {
		if (ObjectUtils.isNullOrEmpty(bundles)) {
			return null;
		}
		NavigableSet<String> taskqualifiers = taskname.getTaskQualifiers();
		String versionqualifier = BundleIdentifier.getVersionQualifier(taskqualifiers);
		if (versionqualifier == null) {
			//no version specified
			//choose among the bundles with the highest version
			String highestversion = null;
			BundleIdentifier highestbundle = null;
			for (BundleIdentifier bundleid : bundles) {
				NavigableSet<String> bundlequalifiers = bundleid.getBundleQualifiers();
				if (!bundlequalifiers.equals(taskqualifiers)) {
					//bundle qualifiers mismatch
					continue;
				}
				String bundlever = bundleid.getVersionQualifier();
				if (bundlever == null) {
					//the bundle has no version qualifier either, can be used
					return bundleid;
				}
				if (highestversion == null) {
					highestversion = bundlever;
					highestbundle = bundleid;
				} else {
					if (BundleIdentifier.compareVersionQualifiers(highestversion, bundlever) < 0) {
						highestversion = bundlever;
						highestbundle = bundleid;
					}
				}
			}
			return highestbundle;
		}
		//version was specified, match all qualifiers appropriately
		for (BundleIdentifier bundleid : bundles) {
			if (!versionqualifier.equals(bundleid.getVersionQualifier())) {
				//different version
				continue;
			}
			NavigableSet<String> bundleq = bundleid.getBundleQualifiers();
			if (bundleq.size() + 1 == taskqualifiers.size() && taskqualifiers.containsAll(bundleq)) {
				return bundleid;
			}
		}
		return null;
	}

	public static Path getVersionedBundleJarPath(Path basedir, BundleIdentifier bundleid) {
		String version = bundleid.getVersionQualifier();
		Path result = basedir.resolve(bundleid.getName());
		if (version != null) {
			result = result.resolve(version);
		} else {
			//default version directory for bundles that have no version specified
			result = result.resolve("v");
		}
		return result.resolve(bundleid.toString() + ".jar");
	}

	public static NavigableSet<String> getJarEntryNames(JarFile jar) {
		NavigableSet<String> result = new TreeSet<>();
		Enumeration<JarEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			JarEntry jarentry = entries.nextElement();
			if (!jarentry.isDirectory()) {
				result.add(jarentry.getName());
			}
		}
		return ImmutableUtils.makeImmutableNavigableSet(result);
	}

	public static ByteArrayRegion getJarEntryBytes(JarFile jarfile, String name)
			throws NoSuchFileException, IOException {
		Objects.requireNonNull(jarfile, "archive file");
		Objects.requireNonNull(name, "name");
		ZipEntry je = jarfile.getEntry(name);
		if (je == null) {
			throw new NoSuchFileException(name, null, "Archive entry not found in: " + jarfile.getName());
		}
		long entrysize = je.getSize();
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream(entrysize < 0 ? 4096 : (int) entrysize);
				InputStream is = jarfile.getInputStream(je)) {
			baos.readFrom(is);
			return baos.toByteArrayRegion();
		}
	}

	public static InputStream openJarEntry(JarFile jarfile, String name) throws NoSuchFileException, IOException {
		Objects.requireNonNull(jarfile, "archive file");
		Objects.requireNonNull(name, "name");
		ZipEntry je = jarfile.getEntry(name);
		if (je == null) {
			throw new NoSuchFileException(name, null, "Archive entry not found in: " + jarfile.getName());
		}
		return jarfile.getInputStream(je);
	}

	public static ClassLoader createAppropriateParentClassLoader(NestRepositoryBundle bundle) {
		return createAppropriateParentClassLoader(bundle.getInformation());
	}

	public static ClassLoader createAppropriateParentClassLoader(BundleInformation info) {
		if (!info.isJdkToolsDependent()) {
			return REPOSITORY_CLASSPATH_CLASSLOADER;
		}
		try {
			return MultiClassLoader.create(ImmutableUtils.asUnmodifiableArrayList(JavaTools.getJDKToolsClassLoader(),
					REPOSITORY_CLASSPATH_CLASSLOADER));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create JDK tools class loader.", e);
		}
	}

	public static void checkArchiveEntryName(String ename) throws IllegalArchiveEntryNameException {
		//disallow:
		//  empty names
		//  names with \ as separators
		//  names that start with /
		//  names that contain relative names
		//  names that contain ; or : (path separators)

		if (ename.isEmpty()) {
			throw new IllegalArchiveEntryNameException("Illegal archive entry with empty name.");
		}
		if (ename.indexOf('\\') >= 0) {
			throw new IllegalArchiveEntryNameException(
					"Illegal archive entry: " + ename + " (the path name separator should be forward slash)");
		}
		if (ename.charAt(0) == '/') {
			throw new IllegalArchiveEntryNameException(
					"Illegal archive entry name: " + ename + " (name must be relative)");
		} else if ("..".equals(ename) || ename.startsWith("../") || ename.endsWith("/..") || ename.contains("/../")) {
			throw new IllegalArchiveEntryNameException(
					"Illegal archive entry name: " + ename + " (.. is an illegal path name)");
		} else if (".".equals(ename) || ename.endsWith("/.") || ename.startsWith("./") || ename.contains("/./")) {
			throw new IllegalArchiveEntryNameException(
					"Illegal archive entry name: " + ename + " (. is an illegal path name)");
		} else if (ename.indexOf(':') >= 0 || ename.indexOf(';') >= 0) {
			throw new IllegalArchiveEntryNameException(
					"Illegal archive entry name: " + ename + " (path separators ; and : are not allowed)");
		}
	}

	public static NavigableMap<URI, Hashes> getExternalDependencyInformationHashes(
			ExternalDependencyInformation depinfo) {
		if (depinfo.isEmpty()) {
			return Collections.emptyNavigableMap();
		}
		NavigableMap<URI, Hashes> result = new TreeMap<>();
		for (Entry<URI, ? extends ExternalDependencyList> entry : depinfo.getDependencies().entrySet()) {
			ExternalDependencyList deplist = entry.getValue();
			result.compute(entry.getKey(), (uri, v) -> {
				return merge(v, new Hashes(deplist.getSha256Hash(), deplist.getSha1Hash(), deplist.getMd5Hash()), uri);
			});
			for (Entry<URI, ExternalAttachmentInformation> attachmententry : deplist.getSourceAttachments().entrySet()) {
				ExternalAttachmentInformation attachmentinfo = attachmententry.getValue();
				result.compute(attachmententry.getKey(), (uri, v) -> {
					return merge(v, new Hashes(attachmentinfo.getSha256Hash(), attachmentinfo.getSha1Hash(),
							attachmentinfo.getMd5Hash()), uri);
				});
			}
			for (Entry<URI, ExternalAttachmentInformation> attachmententry : deplist.getDocumentationAttachments()
					.entrySet()) {
				ExternalAttachmentInformation attachmentinfo = attachmententry.getValue();
				result.compute(attachmententry.getKey(), (uri, v) -> {
					return merge(v, new Hashes(attachmentinfo.getSha256Hash(), attachmentinfo.getSha1Hash(),
							attachmentinfo.getMd5Hash()), uri);
				});
			}
		}

		return result;
	}

	private static Hashes merge(Hashes first, Hashes second, Object context) {
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}
		return new Hashes(mergeHash(first.sha256, second.sha256, "SHA-256", context),
				mergeHash(first.sha1, second.sha1, "SHA-1", context), mergeHash(first.md5, second.md5, "MD5", context));
	}

	private static String mergeHash(String first, String second, String name, Object context) {
		if (first == null) {
			return second;
		}
		if (second == null) {
			return first;
		}
		if (first.equals(second)) {
			return first;
		}
		throw new IllegalArgumentException(
				"Conflicing " + name + " hash declarations for: " + context + " with " + first + " and " + second);
	}

	private BundleUtils() {
		throw new UnsupportedOperationException();
	}

}
