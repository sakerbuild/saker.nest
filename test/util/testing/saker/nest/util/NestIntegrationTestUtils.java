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
package testing.saker.nest.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import saker.build.file.StreamWritable;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.path.SimpleProviderHolderPathKey;
import saker.build.file.provider.FileEntry;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.execution.ExecutionParametersImpl;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.ReflectUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.build.thirdparty.saker.util.io.ByteSource;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayOutputStream;

public class NestIntegrationTestUtils {
	private static final FileTime DEFAULT_ENTRY_MODIFICATION_TIME = FileTime.fromMillis(0);
	private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

	private NestIntegrationTestUtils() {
		throw new UnsupportedOperationException();
	}

	public static SakerPath getTestParameterNestRepositoryJar(Map<String, String> parameters) {
		return SakerPath.valueOf(parameters.get("NestRepositoryJar"));
	}

	public static KeyPair generateRSAKeyPair() {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(2048, new SecureRandom());
			return keyGen.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	public static ByteArrayRegion createJarBytesWithClasses(Set<Class<?>> addclasses) throws IOException {
		return createJarBytesFromDirectoryWithClasses(null, null, addclasses);
	}

	public static ByteArrayRegion createJarBytesFromDirectoryWithClasses(SakerFileProvider fp, SakerPath directory,
			Set<Class<?>> addclasses) throws IOException {
		StreamWritable sw = createStreamWritableJarFromDirectoryWithClasses(fp, directory, addclasses);
		try (UnsyncByteArrayOutputStream baos = new UnsyncByteArrayOutputStream()) {
			sw.writeTo((ByteSink) baos);
			return baos.toByteArrayRegion();
		}
	}

	public static StreamWritable createStreamWritableJarFromDirectoryWithClasses(SakerFileProvider fp,
			SakerPath directory, Set<Class<?>> addclasses) throws IOException {
		NavigableMap<SakerPath, ? extends FileEntry> entries = fp == null ? new TreeMap<>()
				: fp.getDirectoryEntriesRecursively(directory);
		SakerPath manifestpath = SakerPath.valueOf(MANIFEST_PATH);
		FileEntry manifest = entries.remove(manifestpath);
		Manifest mf;
		if (manifest != null) {
			try (ByteSource fileinput = fp.openInput(directory.resolve(manifestpath));
					InputStream manifestis = ByteSource.toInputStream(fileinput)) {
				mf = new Manifest();
				mf.read(manifestis);
			}
		} else {
			mf = null;
		}
		NavigableMap<String, Class<?>> classes;
		if (ObjectUtils.isNullOrEmpty(addclasses)) {
			classes = Collections.emptyNavigableMap();
		} else {
			classes = new TreeMap<>();
			for (Class<?> c : addclasses) {
				Class<?> prev = classes.put(c.getName(), c);
				if (prev != null) {
					throw new AssertionError("duplicate classes: " + prev + " - " + c);
				}
			}
		}
		return os -> {
			try (JarOutputStream jaros = new JarOutputStream(StreamUtils.closeProtectedOutputStream(os))) {
				if (mf != null) {
					ZipEntry manifestze = new ZipEntry(MANIFEST_PATH);
					manifestze.setLastModifiedTime(DEFAULT_ENTRY_MODIFICATION_TIME);
					jaros.putNextEntry(manifestze);
					mf.write(jaros);
					jaros.closeEntry();
				}

				for (Entry<SakerPath, ? extends FileEntry> entry : entries.entrySet()) {
					if (!entry.getValue().isRegularFile()) {
						continue;
					}
					ZipEntry ze = new ZipEntry(entry.getKey().toString());
					ze.setLastModifiedTime(DEFAULT_ENTRY_MODIFICATION_TIME);
					jaros.putNextEntry(ze);
					fp.writeTo(directory.resolve(entry.getKey()), ByteSink.valueOf(jaros));
					jaros.closeEntry();
				}
				for (Entry<String, Class<?>> entry : classes.entrySet()) {
					ZipEntry ze = new ZipEntry(entry.getKey().toString().replace('.', '/') + ".class");
					ze.setLastModifiedTime(DEFAULT_ENTRY_MODIFICATION_TIME);
					jaros.putNextEntry(ze);
					ReflectUtils.getClassBytesUsingClassLoader(entry.getValue()).writeTo(jaros);
					jaros.closeEntry();
				}
			}
		};
	}

	public static void createJarFromDirectoryWithClasses(SakerFileProvider fp, SakerPath directory, Path target,
			Set<Class<?>> addclasses) throws IOException {
		createJarFromDirectoryWithClasses(fp, directory, LocalFileProvider.getInstance().getPathKey(target),
				addclasses);
	}

	public static void createJarFromDirectoryWithClasses(SakerFileProvider fp, SakerPath directory,
			ProviderHolderPathKey target, Set<Class<?>> addclasses) throws IOException {
		UnsyncByteArrayOutputStream jarbyteos = new UnsyncByteArrayOutputStream();
		createStreamWritableJarFromDirectoryWithClasses(fp, directory, addclasses).writeTo((OutputStream) jarbyteos);
		byte[] jarbytes = jarbyteos.toByteArray();
		SakerFileProvider targetfp = target.getFileProvider();
		try {
			byte[] presentbytes = targetfp.getAllBytes(target.getPath()).copyOptionally();
			if (Arrays.equals(presentbytes, jarbytes)) {
				return;
			}
		} catch (IOException e) {
		}
		targetfp.writeToFile(new UnsyncByteArrayInputStream(jarbytes), target.getPath());
	}

	public static void createJarFromDirectory(SakerFileProvider fp, SakerPath directory, Path target)
			throws IOException {
		createJarFromDirectoryWithClasses(fp, directory, target, Collections.emptySet());
	}

	public static void createAllJarsFromDirectoriesWithClasses(SakerFileProvider fp, SakerPath directory,
			ProviderHolderPathKey targetdirectory, Map<String, ? extends Set<Class<?>>> addclasses) throws IOException {
		NavigableMap<String, ? extends FileEntry> entries = fp.getDirectoryEntries(directory);
		if (entries.isEmpty()) {
			return;
		}
		targetdirectory.getFileProvider().createDirectories(targetdirectory.getPath());
		for (Entry<String, ? extends FileEntry> entry : entries.entrySet()) {
			if (entry.getValue().isDirectory()) {
				String filename = entry.getKey();
				createJarFromDirectoryWithClasses(fp, directory.resolve(filename),
						new SimpleProviderHolderPathKey(targetdirectory,
								targetdirectory.getPath().resolve(entry.getKey() + ".jar")),
						ObjectUtils.getMapValue(addclasses, filename));
			}
		}
	}

	public static void createAllJarsFromDirectoriesWithClasses(SakerFileProvider fp, SakerPath directory,
			Path targetdirectory, Map<String, ? extends Set<Class<?>>> addclasses) throws IOException {
		createAllJarsFromDirectoriesWithClasses(fp, directory,
				LocalFileProvider.getInstance().getPathKey(targetdirectory), addclasses);
	}

	public static void createAllJarsFromDirectories(SakerFileProvider fp, SakerPath directory, Path targetdirectory)
			throws IOException {
		createAllJarsFromDirectoriesWithClasses(fp, directory, targetdirectory, Collections.emptyMap());
	}

	public static String createParameterBundlesParameter(Set<String> bundlenames, Path basedir) {
		StringBuilder sb = new StringBuilder();
		for (Iterator<String> it = bundlenames.iterator(); it.hasNext();) {
			String bn = it.next();
			sb.append("//");
			sb.append(basedir.resolve(bn + ".jar"));
			if (it.hasNext()) {
				sb.append(";");
			}
		}
		return sb.toString();
	}

	public static String createParameterBundlesUserParameter(Set<String> bundlenames, Path basedir) {
		return createParameterBundlesUserParameter(bundlenames, basedir, "params");
	}

	public static String createParameterBundlesUserParameter(Set<String> bundlenames, Path basedir,
			String storagename) {
		return "-Unest." + storagename + ".bundles=" + createParameterBundlesParameter(bundlenames, basedir);
	}

	public static void exportBundleBase64ToUserParameter(ExecutionParametersImpl parameters, Path workdir,
			String userparamname, String bundleid, Set<Class<?>> classes) throws IOException {
		StreamWritable exporter = NestIntegrationTestUtils.createStreamWritableJarFromDirectoryWithClasses(
				LocalFileProvider.getInstance(), SakerPath.valueOf(workdir.resolve("bundles").resolve(bundleid)),
				classes);
		UnsyncByteArrayOutputStream exportbytesv1 = new UnsyncByteArrayOutputStream();
		exporter.writeTo((OutputStream) exportbytesv1);
		String encoded = Base64.getEncoder().encodeToString(exportbytesv1.toByteArray());
		addUserParam(parameters, userparamname, encoded);
	}

	public static void addUserParam(ExecutionParametersImpl parameters, String userparamname, String value) {
		Map<String, String> userparams = ObjectUtils.newTreeMap(parameters.getUserParameters());
		userparams.put(userparamname, value);
		parameters.setUserParameters(userparams);
	}

	public static void appendToUserParam(ExecutionParametersImpl parameters, String userparamname, String appendvalue) {
		Map<String, String> userparams = ObjectUtils.newTreeMap(parameters.getUserParameters());
		String prev = userparams.putIfAbsent(userparamname, appendvalue);
		if (prev != null) {
			userparams.put(userparamname, prev + appendvalue);
		}
		parameters.setUserParameters(userparams);
	}
}
