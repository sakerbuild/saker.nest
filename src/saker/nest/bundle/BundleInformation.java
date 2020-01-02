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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import saker.apiextract.api.PublicApi;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.build.thirdparty.saker.util.io.StreamUtils;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;
import saker.nest.NestRepositoryFactory;
import saker.nest.bundle.lookup.BundleLookup;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.exc.InvalidNestBundleException;
import saker.nest.meta.Versions;
import saker.nest.version.VersionRange;
import saker.nest.version.util.SingleComponentVersionRangeVisitor;

/**
 * Holds information about Nest bundles.
 * <p>
 * The information in this class consists of the following:
 * <ul>
 * <li>Bundle identifier</li>
 * <li>Classpath information</li>
 * <li>Dependencies</li>
 * <li>{@linkplain TaskFactory Tasks}</li>
 * <li>Other meta-data</li>
 * </ul>
 * These information is present in the manifest file of the bundle, and under the <code>META-INF/nest/</code> directory.
 * <p>
 * When an instance of this class is constructed, all of the information are validated to have a valid format. A Nest
 * bundle that is any way malformatted is considered invalid.
 * <p>
 * During construction, other structural integrity changes are also validated to prevent possible malicious use-case:
 * <ul>
 * <li>There may not be duplicate entries in the bundle. The entry names are checked in a case-insensitive manner, and
 * ensured that an entry with a given name can only occurr once.</li>
 * <li>Bundle entry names must use the forward slash as a name separator.</li>
 * <li>Bundle entry names cannot contain path names that correspond to specially interpreted relative names.
 * (<code>"."</code> and <code>".."</code>)</li>
 * <li>Bundle entry names cannot contain colon (<code>':'</code>) and semicolon (<code>';'</code>) characters.</li>
 * <li>The class files for the declared tasks must exist in the bundle.</li>
 * </ul>
 * The manifest file must contain the <code>"Nest-Bundle-Format-Version"</code> attribute that is set to a valid version
 * recognized by this class. Currently it must be <code>1</code>.
 * <p>
 * The manifest file must contain an attribute for <code>"Nest-Bundle-Identifier"</code>. However, it is not required
 * for that to contain a {@linkplain BundleIdentifier#getVersionQualifier() version qualifier}.
 * <p>
 * Any other possible and optional manifest attributes are declared in this class with the <code>MANIFEST_NAME_*</code>
 * name. Other attributes which start with <code>"Nest-"</code> are reserved, and may not appear.
 * <p>
 * The bundles can contain meta-data files under the path <code>META-INF/nest/</code> which are described in the
 * appropriate entry name declaration in this class. The bundle is not verified if it contains any extra unrecognized
 * files under the nest meta-data directory. See {@link #ENTRY_BUNDLE_TASKS} and {@link #ENTRY_BUNDLE_DEPENDENCIES}.
 * <p>
 * The class can be instantiated using one of the declared constructors, except the no-arg one which is reserved for
 * {@link Externalizable} implementation.
 * 
 * @see NestRepositoryBundle#getInformation()
 * @see BundleStorageView#getBundleInformation(BundleIdentifier)
 * @see BundleLookup#lookupBundleInformation(BundleIdentifier)
 */
@PublicApi
public final class BundleInformation implements BundleIdentifierHolder, Externalizable {
	private static final long serialVersionUID = 1L;

	/**
	 * Bundle entry name for the task declarations.
	 * <p>
	 * If a bundle entry is present at the value of this constant, then that file is used to read the declared tasks in
	 * the bundle. The file must have the following format in each line:
	 * 
	 * <pre>
	 * [task.name]=[class-name]
	 * </pre>
	 * 
	 * Extra whitespace is allowed around the equals (<code>'='</code>) sign, and before and after the line contents.
	 * Lines that contain only whitespace are ignored.
	 * <p>
	 * Comments are not allowed.
	 * <p>
	 * The task name part must have the format defined by the {@link TaskName} class. It must not have qualifiers.
	 * <p>
	 * The class name must be the binary name of the task. It will be verified that the associated class file is present
	 * in the bundle.
	 * <p>
	 * Duplicate task declarations may not exists.
	 */
	public static final String ENTRY_BUNDLE_TASKS = "META-INF/nest/tasks";
	/**
	 * Bundle entry name for the bundle dependencies.
	 * <p>
	 * The file contains {@link BundleDependencyInformation} data in the format specified by
	 * {@link BundleDependencyInformation#readFrom(InputStream, BundleIdentifier)}.
	 * <p>
	 * The <code>this</code> tokens in version ranges will be resolved to be the same as the enclosing bundle.
	 * <p>
	 * The <code>{@value #DEPENDENCY_KIND_CLASSPATH}</code> dependency kind is used to create the runtime
	 * {@link ClassLoader} for the classes in the bundle. These dependencies are transitively resolved to create the
	 * classpath for the bundle.
	 * <p>
	 * The classpath dependencies can have various meta-datas that can be used to configure the classpath loading for
	 * various environments. See the <code>DEPENDENCY_META_*</code> constants declared in this class.
	 */
	public static final String ENTRY_BUNDLE_DEPENDENCIES = "META-INF/nest/dependencies";

	/**
	 * Manifest attribute name for the bundle information version number.
	 * <p>
	 * Allowed values are:
	 * <ul>
	 * <li><code>1</code>: The initial version.</li>
	 * </ul>
	 */
	public static final Attributes.Name MANIFEST_NAME_BUNDLE_FORMAT_VERSION = new Attributes.Name(
			"Nest-Bundle-Format-Version");
	/**
	 * Manifest attribute name for the bundle identifier.
	 * <p>
	 * The manifest attribute is <b>required</b> and must contain a valid {@link BundleIdentifier} for the bundle.
	 */
	public static final Attributes.Name MANIFEST_NAME_BUNDLE_IDENTIFIER = new Attributes.Name("Nest-Bundle-Identifier");
	/**
	 * Manifest attribute name for specifying special dependencies on the classpath of the bundle.
	 * <p>
	 * This manifest attribute can be used to specify comma separated names that should be included in the classpath
	 * when loading the {@link ClassLoader} for this bundle.
	 * <p>
	 * Currently the following special classpath dependencies are allowed:
	 * <ul>
	 * <li>{@link #SPECIAL_CLASSPATH_DEPENDENCY_JDK_TOOLS} with the value <code>jdktools</code>.</li>
	 * </ul>
	 */
	public static final Attributes.Name MANIFEST_NAME_CLASSPATH_SPECIAL_DEPENDENCY = new Attributes.Name(
			"Nest-ClassPath-Special");
	/**
	 * Manifest attribute name for specifying the suitable Java versions this bundle can load its classpath on.
	 * <p>
	 * The value of the attribute is a {@link VersionRange} that specifies which version can the enclosing bundle's
	 * classpath loaded on. The version range may only contain version numbers with a single major number. E.g.
	 * <ul>
	 * <li><code>8</code>: The bundle may be loaded only on version 8. Same as <code>[8]</code>.</li>
	 * <li><code>[8, 10]</code>: Allows the bundle to be loaded on JDK 8, 9, and 10, but not on later versions.</li>
	 * <li><code>{8, 10, 12}</code>: The bundle may be loaded only on versions 8, 10 and 12.</li>
	 * <li><code>8.0.1</code>: Invalid version range, as it may only contain version numbers with a single
	 * component.</li>
	 * </ul>
	 * 
	 * @see DependencyConstraintConfiguration
	 */
	public static final Attributes.Name MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS = new Attributes.Name(
			"Nest-ClassPath-Supported-JRE-Versions");
	/**
	 * Manifest attribute name for specifying the suitable repository versions that this bundle can load its classpath
	 * on.
	 * <p>
	 * The value of the attribute is a {@link VersionRange} that specifies the allowed repository versons on which the
	 * bundle's classpath can be loaded. The {@linkplain Versions#VERSION_STRING_FULL full version} of the Nest
	 * repository is tested for the specified range.
	 * <p>
	 * If the attribute is not specified, no restriction is placed on the repository version.
	 * 
	 * @see DependencyConstraintConfiguration
	 */
	public static final Attributes.Name MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS = new Attributes.Name(
			"Nest-ClassPath-Supported-Repository-Versions");
	/**
	 * Manifest attribute name for specifying the suitable build system versions that this bundle can load its classpath
	 * on.
	 * <p>
	 * The value of the attribute is a {@link VersionRange} that specifies the allowed build system versons on which the
	 * bundle's classpath can be loaded. The {@linkplain saker.build.meta.Versions#VERSION_STRING_FULL full version} of
	 * saker.build is tested for the specified range.
	 * <p>
	 * If the attribute is not specified, no restriction is placed on the build system version.
	 * 
	 * @see DependencyConstraintConfiguration
	 */
	public static final Attributes.Name MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS = new Attributes.Name(
			"Nest-ClassPath-Supported-Build-System-Versions");
	/**
	 * Manifest attribute name for specifying the architecture that the bundle's classpath may be loaded on.
	 * <p>
	 * The value is a comma (<code>,</code>) separated list of architecture names that the bundle classpath can be
	 * loaded on.
	 * <p>
	 * When the bundle classpath is loaded, the JVM reported architecture ({@link System#getProperties() os.arch}
	 * property) is used to test if the bundle supports it. The architecture value is searched for in the attribute
	 * value. It is compared in a case-sensitive way.
	 * <p>
	 * If the manifest attribute is not specified, no restriction is placed for the supported architectures.
	 * <p>
	 * If the attribute is specified, it must specify at least one architecture, it cannot be empty.
	 * 
	 * @see DependencyConstraintConfiguration
	 */
	public static final Attributes.Name MANIFEST_NAME_CLASSPATH_SUPPORTED_ARCHITECTURES = new Attributes.Name(
			"Nest-ClassPath-Supported-Architectures");
	/**
	 * Manifest attribute name containing a {@link BundleIdentifier} of the sources for this bundle.
	 * <p>
	 * The bundle that is specified by the value of this attribute is considered to contain the sources for this bundle.
	 * The format of the files are unspecified. It may include sources from different languages, other from Java as
	 * well.
	 */
	public static final Attributes.Name MANIFEST_NAME_SOURCE_ATTACHMENT_BUNDLE_IDENTIFIER = new Attributes.Name(
			"Nest-Bundle-Source");
	/**
	 * Manifest attribute name containing a {@link BundleIdentifier} of the associated documentation for this bundle.
	 * <p>
	 * The bundle that is specified by the value of this attribute is considered to contain the documentation for this
	 * bundle. The format of the files are unspecified. It may include documentation for different languages as well.
	 */
	public static final Attributes.Name MANIFEST_NAME_DOCUMENTATION_ATTACHMENT_BUNDLE_IDENTIFIER = new Attributes.Name(
			"Nest-Bundle-Documentation");

	private static final Set<Attributes.Name> ALLOWED_NEST_MANIFEST_NAMES = ImmutableUtils.makeImmutableHashSet(
			new Attributes.Name[] { MANIFEST_NAME_BUNDLE_FORMAT_VERSION, MANIFEST_NAME_BUNDLE_IDENTIFIER,
					MANIFEST_NAME_CLASSPATH_SPECIAL_DEPENDENCY, MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS,
					MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS,
					MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS,
					MANIFEST_NAME_SOURCE_ATTACHMENT_BUNDLE_IDENTIFIER,
					MANIFEST_NAME_DOCUMENTATION_ATTACHMENT_BUNDLE_IDENTIFIER, });

	/**
	 * Constant for the manifest attribute {@link #MANIFEST_NAME_CLASSPATH_SPECIAL_DEPENDENCY}.
	 * <p>
	 * Requires the Java compiler related classes as the parent to the bundle class loader. <br>
	 * On JDK8 this will mean that the <code>tools.jar</code> in the JDK will be loaded and used as parent. <br>
	 * On later JDK version the platform class loader will be present as a parent class loader.
	 */
	public static final String SPECIAL_CLASSPATH_DEPENDENCY_JDK_TOOLS = "jdktools";

	/**
	 * Dependency kind for specifying that a bundle is required to be on the classpath.
	 * <p>
	 * The denoted bundle is used to lookup classes which are required by classes contained in this bundle.
	 */
	public static final String DEPENDENCY_KIND_CLASSPATH = "classpath";
	/**
	 * Bundle dependency meta data name for specifying that a dependency is optional.
	 * <p>
	 * The denoted bundle dependency is not required for the safe operation of this bundle.
	 */
	public static final String DEPENDENCY_META_OPTIONAL = "optional";

	/**
	 * Dependency meta-data name for specifying the Java Runtime versions the dependency applies to.
	 * <p>
	 * The value of the meta-data is a {@link VersionRange} that specifies the JRE major version number range that the
	 * dependency should be applied to. If the current JRE version constraint is not included in the specified version
	 * range, the associated dependency will be omitted.
	 * <p>
	 * This works similarly to {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS}, but is declared on a dependency
	 * level instead of on the bundles.
	 * <p>
	 * Dependency resolvers may or may not take this meta-data into account. The repository will use this meta-data when
	 * creating the {@link ClassLoader} for the bundle.
	 * <p>
	 * E.g. This meta-data name can be used to load different bundles on different Java versions:
	 * 
	 * <pre>
	 * bundle.for.jdk8
	 *     classpath: [0)
	 *         jre-version: 8
	* bundle.for.jdk9
	 *     classpath: [0)
	 *         jre-version: 9
	 * </pre>
	 * 
	 * This will cause the <code>bundle.for.jdk8</code> to be loaded on Java 8, and <code>bundle.for.jdk9</code> to be
	 * loaded on Java 9. Only one of them will be on the classpath.
	 * 
	 * @see DependencyConstraintConfiguration#getJreMajorVersion()
	 */
	public static final String DEPENDENCY_META_JRE_VERSION = "jre-version";
	/**
	 * Dependency meta-data name for specifying the Nest repository versions the dependency applies to.
	 * <p>
	 * The value of the meta-data is a {@link VersionRange} that specifies the Nest repository version number range that
	 * the dependency should be applied to. If the current repository version constraint is not included in the
	 * specified version range, the associated dependency will be omitted.
	 * <p>
	 * This works similarly to {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS}, but is declared on a
	 * dependency level instead of on the bundles.
	 * <p>
	 * Dependency resolvers may or may not take this meta-data into account. The repository will use this meta-data when
	 * creating the {@link ClassLoader} for the bundle.
	 * 
	 * @see DependencyConstraintConfiguration#getRepositoryVersion()
	 * @see saker.nest.meta.Versions#VERSION_STRING_FULL
	 */
	public static final String DEPENDENCY_META_REPOSITORY_VERSION = "repo-version";
	/**
	 * Dependency meta-data name for specifying the saker.build systems versions the dependency applies to.
	 * <p>
	 * The value of the meta-data is a {@link VersionRange} that specifies the build system version number range that
	 * the dependency should be applied to. If the current build system version constraint is not included in the
	 * specified version range, the associated dependency will be omitted.
	 * <p>
	 * This works similarly to {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS}, but is declared on a
	 * dependency level instead of on the bundles.
	 * <p>
	 * Dependency resolvers may or may not take this meta-data into account. The repository will use this meta-data when
	 * creating the {@link ClassLoader} for the bundle.
	 * 
	 * @see DependencyConstraintConfiguration#getBuildSystemVersion()
	 * @see saker.build.meta.Versions#VERSION_STRING_FULL
	 */
	public static final String DEPENDENCY_META_BUILD_SYSTEM_VERSION = "buildsystem-version";
	/**
	 * Dependency meta-data name for specifying the architectures that the dependency applies to.
	 * <p>
	 * The value of the meta-data is a comma separated architecture list that contains the architectures the dependency
	 * applies to. If the current architecture constraint is not included in the specified list, the associated
	 * dependency will be omitted.
	 * <p>
	 * This works similarly to {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_ARCHITECTURES}, but is declared on a dependency
	 * level instead of on the bundles.
	 * <p>
	 * Dependency resolvers may or may not take this meta-data into account. The repository will use this meta-data when
	 * creating the {@link ClassLoader} for the bundle.
	 * <p>
	 * E.g. This meta-data can be used to load different bundles for different architectures:
	 * 
	 * <pre>
	 * bundle.for.x86
	 *     classpath: [0)
	 *         native-architecture: x86
	 * bundle.for.x64
	 *     classpath: [0)
	 *         native-architecture: amd64
	 * </pre>
	 * 
	 * This will cause <code>bundle.for.x86</code> to be loaded on <code>x86</code> and <code>bundle.for.x64</code> to
	 * be loaded on <code>amd64</code>. Only one of them will be on the classpath.
	 * 
	 * @see DependencyConstraintConfiguration#getNativeArchitecture()
	 */
	public static final String DEPENDENCY_META_NATIVE_ARCHITECTURE = "native-architecture";

	private static final Collection<String> ALLOWED_SPECIAL_CLASSPATH_DEPENDENCY_KINDS = ImmutableUtils
			.makeImmutableNavigableSet(new String[] { SPECIAL_CLASSPATH_DEPENDENCY_JDK_TOOLS });

	private static final Pattern PATTERN_COMMA_WHITESPACE_SPLIT = Pattern.compile("[, \\t]+");

	private BundleIdentifier bundleId;
	private BundleDependencyInformation dependencyInformation;
	private NavigableMap<TaskName, String> taskClassNames;
	private NavigableSet<String> specialClasspathDependencies;
	private BundleIdentifier docAttachmentBundle;
	private BundleIdentifier sourceAttachmentBundle;
	private String mainClass;
	private VersionRange supportedClassPathJreVersionRange;
	private VersionRange supportedClassPathRepositoryVersionRange;
	private VersionRange supportedClassPathBuildSystemVersionRange;
	private Set<String> supportedClassPathArchitectures;

	/**
	 * For {@link Externalizable}.
	 * 
	 * @deprecated For {@link Externalizable}.
	 */
	@Deprecated
	public BundleInformation() {
	}

	private BundleInformation(Manifest bundlemanifest, ZipFile zf) throws IOException {
		if (bundlemanifest == null) {
			throw new InvalidNestBundleException("No manifest found. (" + zf.getName() + ")");
		}
		int formatversion = getBundleFormatVersion(bundlemanifest);
		if (formatversion != 1) {
			throw new InvalidNestBundleException("Invalid bundle format version: " + formatversion);
		}
		validateBundleManifest(bundlemanifest);
		Set<String> entrynames = new TreeSet<>(String::compareToIgnoreCase);
		Enumeration<? extends ZipEntry> entries = zf.entries();
		while (entries.hasMoreElements()) {
			ZipEntry zipEntry = entries.nextElement();
			String ename = zipEntry.getName();
			if (!entrynames.add(ename)) {
				throw new InvalidNestBundleException("Duplicate bundle entry: " + ename);
			}
			checkEntryName(ename);
		}

		this.bundleId = readBundleIdentifier(bundlemanifest);

		this.dependencyInformation = readDependencies(zf, this.bundleId);
		this.taskClassNames = readTaskNames(zf);
		this.mainClass = bundlemanifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
		verifyContainsRequiredClassFiles(entrynames, taskClassNames, this.mainClass);

		this.specialClasspathDependencies = getSpecialClassPathDependencies(bundlemanifest);
		this.sourceAttachmentBundle = getSourceAttachmentBundleIdentifier(bundlemanifest);
		this.docAttachmentBundle = getDocumentationAttachmentBundleIdentifier(bundlemanifest);
		this.supportedClassPathJreVersionRange = readSupportedJreVersionRange(bundlemanifest);
		this.supportedClassPathRepositoryVersionRange = readSupportedRepositoryVersionRange(bundlemanifest);
		this.supportedClassPathBuildSystemVersionRange = readSupportedBuildSystemVersionRange(bundlemanifest);
		this.supportedClassPathArchitectures = readSupportedArchitectures(bundlemanifest);

		verifyBundleDependenciesForBundleIdentifier(this.dependencyInformation, this.bundleId);
	}

	private BundleInformation(Manifest bundlemanifest, ZipInputStream jis)
			throws NullPointerException, IOException, InvalidNestBundleException {
		ByteArrayRegion dependenciesbytes = null;
		BundleDependencyInformation dependencies = null;
		NavigableMap<TaskName, String> taskClassNames = null;
		Set<String> entrynames = new TreeSet<>(String::compareToIgnoreCase);
		for (ZipEntry e; (e = jis.getNextEntry()) != null;) {
			String ename = e.getName();
			checkEntryName(ename);
			if (!entrynames.add(ename)) {
				throw new InvalidNestBundleException("Duplicate bundle entry: " + ename);
			}
			if (ENTRY_BUNDLE_TASKS.equalsIgnoreCase(ename)) {
				taskClassNames = readTaskNames(jis);
			} else if (ENTRY_BUNDLE_DEPENDENCIES.equalsIgnoreCase(ename)) {
				dependenciesbytes = StreamUtils.readStreamFully(jis);
			} else if (JarFile.MANIFEST_NAME.equalsIgnoreCase(ename)) {
				if (bundlemanifest != null) {
					throw new InvalidNestBundleException("Multiple manifests specified for bundle.");
				}
				bundlemanifest = new Manifest();
				bundlemanifest.read(jis);
			}
		}
		if (bundlemanifest == null) {
			throw new InvalidNestBundleException("No manifest found.");
		}
		int formatversion = getBundleFormatVersion(bundlemanifest);
		if (formatversion != 1) {
			throw new InvalidNestBundleException("Invalid bundle format version: " + formatversion);
		}
		validateBundleManifest(bundlemanifest);

		this.bundleId = readBundleIdentifier(bundlemanifest);

		if (dependenciesbytes != null) {
			try {
				dependencies = BundleDependencyInformation.readFrom(new UnsyncByteArrayInputStream(dependenciesbytes),
						this.bundleId);
			} catch (IllegalArgumentException e) {
				throw new InvalidNestBundleException(
						"Failed to parse bundle dependencies. (" + ENTRY_BUNDLE_DEPENDENCIES + ")", e);
			}
		}

		if (taskClassNames == null) {
			taskClassNames = Collections.emptyNavigableMap();
		}

		this.dependencyInformation = dependencies == null ? BundleDependencyInformation.EMPTY : dependencies;
		this.taskClassNames = taskClassNames;
		this.mainClass = bundlemanifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
		verifyContainsRequiredClassFiles(entrynames, taskClassNames, this.mainClass);

		this.specialClasspathDependencies = getSpecialClassPathDependencies(bundlemanifest);
		this.sourceAttachmentBundle = getSourceAttachmentBundleIdentifier(bundlemanifest);
		this.docAttachmentBundle = getDocumentationAttachmentBundleIdentifier(bundlemanifest);
		this.supportedClassPathJreVersionRange = readSupportedJreVersionRange(bundlemanifest);
		this.supportedClassPathRepositoryVersionRange = readSupportedRepositoryVersionRange(bundlemanifest);
		this.supportedClassPathBuildSystemVersionRange = readSupportedBuildSystemVersionRange(bundlemanifest);
		this.supportedClassPathArchitectures = readSupportedArchitectures(bundlemanifest);

		verifyBundleDependenciesForBundleIdentifier(dependencies, this.bundleId);
	}

	private static void validateBundleManifest(Manifest manifest) {
		//disallow any special Nest- attribute. They are reserved for future compatibility.
		for (Object key : manifest.getMainAttributes().keySet()) {
			Attributes.Name name = (Name) key;
			if (StringUtils.startsWithIgnoreCase(name.toString(), "Nest-")) {
				if (!ALLOWED_NEST_MANIFEST_NAMES.contains(name)) {
					throw new InvalidNestBundleException("Invalid Nest manifest main attribute: " + name);
				}
			}
		}
		for (Entry<String, Attributes> entry : manifest.getEntries().entrySet()) {
			Attributes attrs = entry.getValue();
			for (Object key : attrs.keySet()) {
				Attributes.Name name = (Name) key;
				if (StringUtils.startsWithIgnoreCase(name.toString(), "Nest-")) {
					throw new InvalidNestBundleException(
							"Invalid Nest manifest entry attribute: " + name + " (" + entry.getKey() + ")");
				}
			}
		}
	}

	private static void verifyContainsRequiredClassFiles(Set<String> entrynames,
			NavigableMap<TaskName, String> taskClassNames, String mainclass) {
		if (mainclass != null) {
			String mainclassentryname = toClassFileEntryName(mainclass);
			if (!entrynames.contains(mainclassentryname)) {
				throw new InvalidNestBundleException(
						"Main class file not found in bundle: " + mainclass + " for " + mainclassentryname);
			}
		}
		for (Entry<TaskName, String> entry : taskClassNames.entrySet()) {
			String cname = entry.getValue();
			String entryname = toClassFileEntryName(cname);
			if (!entrynames.contains(entryname)) {
				throw new InvalidNestBundleException("Missing class file for task: " + entry.getKey() + " with name: "
						+ cname + " as entry: " + entryname);
			}
		}
	}

	private static String toClassFileEntryName(String cname) {
		return cname.replace('.', '/') + ".class";
	}

	/**
	 * Creates a new instance based on the contents of the argument ZIP file.
	 * 
	 * @param zf
	 *            The Zip file.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InvalidNestBundleException
	 *             If the bundle validation fails.
	 */
	public BundleInformation(ZipFile zf) throws NullPointerException, IOException, InvalidNestBundleException {
		this(getManifestFromZip(Objects.requireNonNull(zf, "zip file")), zf);
	}

	/**
	 * Creates a new instance based on the contents of the argument JAR file.
	 * 
	 * @param jf
	 *            The JAR file.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InvalidNestBundleException
	 *             If the bundle validation fails.
	 */
	public BundleInformation(JarFile jf) throws NullPointerException, IOException, InvalidNestBundleException {
		this(Objects.requireNonNull(jf, "jar file").getManifest(), jf);
	}

	/**
	 * Creates a new instance based on the contents of the argument JAR input stream.
	 * 
	 * @param jis
	 *            The JAR input.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 * @throws IOException
	 *             In case of I/O error.
	 * @throws InvalidNestBundleException
	 *             If the bundle validation fails.
	 */
	public BundleInformation(JarInputStream jis) throws NullPointerException, IOException, InvalidNestBundleException {
		this(Objects.requireNonNull(jis, "jar input").getManifest(), jis);
	}

	/**
	 * Gets the version range declaration of the supported classpath Java Runtime versions.
	 * <p>
	 * The version range corresponds to the manifest attribute {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS}.
	 * 
	 * @return The supported version range or <code>null</code> if not restricted by this bundle.
	 */
	public VersionRange getSupportedClassPathJreVersionRange() {
		return supportedClassPathJreVersionRange;
	}

	/**
	 * Gets the version range declaration of the supported Nest repository versions.
	 * <p>
	 * The version range corresponds to the manifest attribute
	 * {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS}.
	 * 
	 * @return The supported version range or <code>null</code> if not restricted by this bundle.
	 */
	public VersionRange getSupportedClassPathRepositoryVersionRange() {
		return supportedClassPathRepositoryVersionRange;
	}

	/**
	 * Gets the version range declaration of the supported saker.build system versions.
	 * <p>
	 * The version range corresponds to the manifest attribute
	 * {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS}.
	 * 
	 * @return The supported version range or <code>null</code> if not restricted by this bundle.
	 */
	public VersionRange getSupportedClassPathBuildSystemVersionRange() {
		return supportedClassPathBuildSystemVersionRange;
	}

	/**
	 * Gets the set of supported classpath architectures by this bundle.
	 * <p>
	 * The architectures correspond to the manifest attribute {@link #MANIFEST_NAME_CLASSPATH_SUPPORTED_ARCHITECTURES}.
	 * 
	 * @return The set os supported architectures or <code>null</code> if not restricted by this bundle.
	 */
	public Set<String> getSupportedClassPathArchitectures() {
		return supportedClassPathArchitectures;
	}

	/**
	 * Gets the declared main class in the manifest of this bundle.
	 * <p>
	 * This is determined using the <code>Main-Class</code> attribute.
	 * 
	 * @return The declared main class or <code>null</code> if none.
	 * @see Attributes.Name#MAIN_CLASS
	 */
	public String getMainClass() {
		return mainClass;
	}

	/**
	 * Gets the bundle identifier.
	 * 
	 * @return The bundle identifier. Never <code>null</code>.
	 */
	@Override
	public BundleIdentifier getBundleIdentifier() {
		return bundleId;
	}

	/**
	 * Gets the dependency information for this bundle.
	 * 
	 * @return The dependency information. Never <code>null</code>.
	 * @see #ENTRY_BUNDLE_DEPENDENCIES
	 */
	public BundleDependencyInformation getDependencyInformation() {
		return dependencyInformation;
	}

	/**
	 * Checks if this bundle requires the JDK tools to be present on the classpath.
	 * 
	 * @return <code>true</code> if the bundle requires the JDK tools classes.
	 * @see #MANIFEST_NAME_CLASSPATH_SPECIAL_DEPENDENCY
	 * @see #SPECIAL_CLASSPATH_DEPENDENCY_JDK_TOOLS
	 */
	public boolean isJdkToolsDependent() {
		return specialClasspathDependencies.contains(SPECIAL_CLASSPATH_DEPENDENCY_JDK_TOOLS);
	}

	/**
	 * Gets the declared tasks mapped to their class names in this bundle.
	 * 
	 * @return An unmodifiable map of task names to class names.
	 * @see #ENTRY_BUNDLE_TASKS
	 */
	public NavigableMap<TaskName, String> getTaskClassNames() {
		return taskClassNames;
	}

	/**
	 * Gets the source attachment for this bundle.
	 * 
	 * @return The source attachment bundle identifier or <code>null</code> if none.
	 * @see #MANIFEST_NAME_SOURCE_ATTACHMENT_BUNDLE_IDENTIFIER
	 */
	public BundleIdentifier getSourceAttachmentBundleIdentifier() {
		return sourceAttachmentBundle;
	}

	/**
	 * Gets the documentation attachment for this bundle.
	 * 
	 * @return The documentation attachment bundle identifier or <code>null</code> if none.
	 * @see #MANIFEST_NAME_DOCUMENTATION_ATTACHMENT_BUNDLE_IDENTIFIER
	 */
	public BundleIdentifier getDocumentationAttachmentBundleIdentifier() {
		return docAttachmentBundle;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeObject(bundleId);
		out.writeObject(dependencyInformation);
		SerialUtils.writeExternalMap(out, taskClassNames);
		SerialUtils.writeExternalCollection(out, specialClasspathDependencies);
		out.writeObject(docAttachmentBundle);
		out.writeObject(sourceAttachmentBundle);
		out.writeObject(mainClass);
		out.writeObject(supportedClassPathJreVersionRange);
		out.writeObject(supportedClassPathRepositoryVersionRange);
		out.writeObject(supportedClassPathBuildSystemVersionRange);
		SerialUtils.writeExternalCollection(out, supportedClassPathArchitectures);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		bundleId = (BundleIdentifier) in.readObject();
		dependencyInformation = (BundleDependencyInformation) in.readObject();
		taskClassNames = SerialUtils.readExternalSortedImmutableNavigableMap(in);
		specialClasspathDependencies = SerialUtils.readExternalSortedImmutableNavigableSet(in);
		docAttachmentBundle = (BundleIdentifier) in.readObject();
		sourceAttachmentBundle = (BundleIdentifier) in.readObject();
		mainClass = (String) in.readObject();
		supportedClassPathJreVersionRange = (VersionRange) in.readObject();
		supportedClassPathRepositoryVersionRange = (VersionRange) in.readObject();
		supportedClassPathBuildSystemVersionRange = (VersionRange) in.readObject();
		supportedClassPathArchitectures = SerialUtils.readExternalSortedImmutableNavigableSet(in);
	}

	@Override
	public int hashCode() {
		return bundleId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BundleInformation other = (BundleInformation) obj;
		if (bundleId == null) {
			if (other.bundleId != null)
				return false;
		} else if (!bundleId.equals(other.bundleId))
			return false;
		if (dependencyInformation == null) {
			if (other.dependencyInformation != null)
				return false;
		} else if (!dependencyInformation.equals(other.dependencyInformation))
			return false;
		if (docAttachmentBundle == null) {
			if (other.docAttachmentBundle != null)
				return false;
		} else if (!docAttachmentBundle.equals(other.docAttachmentBundle))
			return false;
		if (mainClass == null) {
			if (other.mainClass != null)
				return false;
		} else if (!mainClass.equals(other.mainClass))
			return false;
		if (sourceAttachmentBundle == null) {
			if (other.sourceAttachmentBundle != null)
				return false;
		} else if (!sourceAttachmentBundle.equals(other.sourceAttachmentBundle))
			return false;
		if (specialClasspathDependencies == null) {
			if (other.specialClasspathDependencies != null)
				return false;
		} else if (!specialClasspathDependencies.equals(other.specialClasspathDependencies))
			return false;
		if (supportedClassPathArchitectures == null) {
			if (other.supportedClassPathArchitectures != null)
				return false;
		} else if (!supportedClassPathArchitectures.equals(other.supportedClassPathArchitectures))
			return false;
		if (supportedClassPathBuildSystemVersionRange == null) {
			if (other.supportedClassPathBuildSystemVersionRange != null)
				return false;
		} else if (!supportedClassPathBuildSystemVersionRange.equals(other.supportedClassPathBuildSystemVersionRange))
			return false;
		if (supportedClassPathJreVersionRange == null) {
			if (other.supportedClassPathJreVersionRange != null)
				return false;
		} else if (!supportedClassPathJreVersionRange.equals(other.supportedClassPathJreVersionRange))
			return false;
		if (supportedClassPathRepositoryVersionRange == null) {
			if (other.supportedClassPathRepositoryVersionRange != null)
				return false;
		} else if (!supportedClassPathRepositoryVersionRange.equals(other.supportedClassPathRepositoryVersionRange))
			return false;
		if (taskClassNames == null) {
			if (other.taskClassNames != null)
				return false;
		} else if (!taskClassNames.equals(other.taskClassNames))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "BundleInformation[bundleId=" + bundleId + ", dependencyInformation=" + dependencyInformation
				+ ", taskClassNames=" + taskClassNames + ", specialClasspathDependencies="
				+ specialClasspathDependencies + ", docAttachmentBundle=" + docAttachmentBundle
				+ ", sourceAttachmentBundle=" + sourceAttachmentBundle + ", mainClass=" + mainClass + "]";
	}

	private static void checkEntryName(String ename) {
		//disallow:
		//  empty names
		//  names with \ as separators
		//  names that start with /
		//  names that contain relative names
		//  names that contain ; or : (path separators)

		if (ename.isEmpty()) {
			throw new InvalidNestBundleException("Illegal bundle entry with empty name.");
		}
		if (ename.indexOf('\\') >= 0) {
			throw new InvalidNestBundleException("Illegal " + NestRepositoryFactory.IDENTIFIER + " bundle entry: "
					+ ename + " (the path name separator should be forward slash)");
		}
		if (ename.charAt(0) == '/' || "..".equals(ename) || ".".equals(ename) || ename.startsWith("./")
				|| ename.startsWith("../") || ename.endsWith("/..") || ename.endsWith("/.") || ename.contains("/../")
				|| ename.contains("/./") || ename.indexOf(':') >= 0 || ename.indexOf(';') >= 0) {
			throw new InvalidNestBundleException("Illegal bundle entry name: " + ename);
		}
	}

	private static NavigableSet<String> getSpecialClassPathDependencies(Manifest bundlemanifest) {
		String attr = bundlemanifest.getMainAttributes().getValue(MANIFEST_NAME_CLASSPATH_SPECIAL_DEPENDENCY);
		if (attr == null) {
			return Collections.emptyNavigableSet();
		}
		NavigableSet<String> result = new TreeSet<>();
		for (String dep : PATTERN_COMMA_WHITESPACE_SPLIT.split(attr)) {
			if (dep.isEmpty()) {
				continue;
			}
			if (!ALLOWED_SPECIAL_CLASSPATH_DEPENDENCY_KINDS.contains(dep)) {
				throw new InvalidNestBundleException("Invalid special classpath dependency specified: " + dep
						+ ". Allowed: " + StringUtils.toStringJoin(", ", ALLOWED_SPECIAL_CLASSPATH_DEPENDENCY_KINDS));
			}
			result.add(dep);
		}
		return ImmutableUtils.unmodifiableNavigableSet(result);
	}

	private static int getBundleFormatVersion(Manifest bundlemanifest) {
		String versionstr = bundlemanifest.getMainAttributes().getValue(MANIFEST_NAME_BUNDLE_FORMAT_VERSION);
		if (versionstr == null) {
			throw new InvalidNestBundleException(
					"Manifest attribute " + MANIFEST_NAME_BUNDLE_FORMAT_VERSION + " is missing.");
		}
		try {
			return Integer.parseInt(versionstr);
		} catch (NumberFormatException e) {
			throw new InvalidNestBundleException(
					"Failed to parse manifest attribute: " + MANIFEST_NAME_BUNDLE_FORMAT_VERSION + ": " + versionstr,
					e);
		}
	}

	private static BundleIdentifier getManifestAttributeBundleIdentifier(Manifest bundlemanifest,
			Attributes.Name name) {
		String manifestentryvalue = bundlemanifest.getMainAttributes().getValue(name);
		if (manifestentryvalue == null) {
			return null;
		}
		try {
			BundleIdentifier result = BundleIdentifier.valueOf(manifestentryvalue);
			if (result.getVersionQualifier() == null) {
				throw new InvalidNestBundleException("Missing version qualifier for bundle identifier attribute: "
						+ name + " with " + manifestentryvalue);
			}
			return result;
		} catch (IllegalArgumentException e) {
			throw new InvalidNestBundleException("Invalid bundle identifier for attribute: " + name, e);
		}
	}

	private static BundleIdentifier getSourceAttachmentBundleIdentifier(Manifest bundlemanifest) {
		return getManifestAttributeBundleIdentifier(bundlemanifest, MANIFEST_NAME_SOURCE_ATTACHMENT_BUNDLE_IDENTIFIER);
	}

	private static BundleIdentifier getDocumentationAttachmentBundleIdentifier(Manifest bundlemanifest) {
		return getManifestAttributeBundleIdentifier(bundlemanifest,
				MANIFEST_NAME_DOCUMENTATION_ATTACHMENT_BUNDLE_IDENTIFIER);
	}

	private static Manifest getManifestFromZip(ZipFile zf) throws IOException {
		ZipEntry manifestentry = zf.getEntry(JarFile.MANIFEST_NAME);
		if (manifestentry == null) {
			return null;
		}
		Manifest bundlemanifest;
		try (InputStream manifestin = zf.getInputStream(manifestentry)) {
			bundlemanifest = new Manifest(manifestin);
		}
		return bundlemanifest;
	}

	private static BundleDependencyInformation readDependencies(ZipFile jf, BundleIdentifier declaringbundleid)
			throws IOException {
		ZipEntry propentry = jf.getEntry(ENTRY_BUNDLE_DEPENDENCIES);
		if (propentry == null) {
			return BundleDependencyInformation.EMPTY;
		}

		try (InputStream eis = jf.getInputStream(propentry)) {
			try {
				return BundleDependencyInformation.readFrom(eis, declaringbundleid);
			} catch (IllegalArgumentException e) {
				throw new InvalidNestBundleException(
						"Failed to parse bundle dependencies. (" + ENTRY_BUNDLE_DEPENDENCIES + ")", e);
			}
		}
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

	private static boolean isWhiteSpaceOnly(String s) {
		return isWhiteSpaceOnlyFrom(s, 0);
	}

	private static void verifyBundleDependenciesForBundleIdentifier(BundleDependencyInformation depinfo,
			BundleIdentifier thisbundleid) {
		if (depinfo == null || depinfo.isEmpty()) {
			return;
		}
		BundleIdentifier simplebundleid = thisbundleid.withoutMetaQualifiers();
		for (BundleIdentifier bundleid : depinfo.getDependencies().keySet()) {
			if (simplebundleid.equals(bundleid)) {
				throw new InvalidNestBundleException(
						"Cannot specify bundle dependency on itself: " + bundleid + " for " + thisbundleid);
			}
		}
	}

	private static NavigableMap<TaskName, String> readTaskNames(ZipFile jf) throws IOException {
		ZipEntry propentry = jf.getEntry(ENTRY_BUNDLE_TASKS);
		if (propentry == null) {
			return Collections.emptyNavigableMap();
		}
		try (InputStream eis = jf.getInputStream(propentry)) {
			return readTaskNames(eis);
		}
	}

	private static NavigableMap<TaskName, String> readTaskNames(InputStream eis) throws IOException {
		try (BufferedReader is = new BufferedReader(
				new InputStreamReader(StreamUtils.closeProtectedInputStream(eis), StandardCharsets.UTF_8))) {
			NavigableMap<TaskName, String> result = new TreeMap<>();
			for (String line; (line = is.readLine()) != null;) {
				if (line.isEmpty()) {
					continue;
				}
				int idx = line.indexOf('=');
				if (idx < 0) {
					if (isWhiteSpaceOnly(line)) {
						continue;
					}
					throw new InvalidNestBundleException("Invalid task name line: " + line);
				}
				String trimmedtn = line.substring(0, idx).trim();
				TaskName tn;
				try {
					tn = TaskName.valueOf(trimmedtn);
				} catch (IllegalArgumentException e) {
					throw new InvalidNestBundleException("Invalid task name: " + trimmedtn, e);
				}
				if (!tn.getTaskQualifiers().isEmpty()) {
					throw new InvalidNestBundleException("Task names must not contain any qualifiers. (" + tn + ")");
				}
				String cname = line.substring(idx + 1).trim();
				String prev = result.putIfAbsent(tn, cname);
				if (prev != null) {
					throw new InvalidNestBundleException(
							"Multiple definitions for task: " + tn + " with " + cname + " and " + prev);
				}
			}
			return ImmutableUtils.unmodifiableNavigableMap(result);
		}
	}

	private static BundleIdentifier readBundleIdentifier(Manifest manifest) {
		String name = manifest.getMainAttributes().getValue(MANIFEST_NAME_BUNDLE_IDENTIFIER);
		if (ObjectUtils.isNullOrEmpty(name)) {
			throw new InvalidNestBundleException(
					"Bundle identifier attribute not found in manifest: " + MANIFEST_NAME_BUNDLE_IDENTIFIER);
		}
		try {
			BundleIdentifier result = BundleIdentifier.valueOf(name);
			if (result.getVersionQualifier() == null) {
				throw new InvalidNestBundleException("Version qualifier not found in bundle identifier: " + name);
			}
			return result;
		} catch (InvalidNestBundleException e) {
			throw e;
		} catch (IllegalArgumentException e) {
			throw new InvalidNestBundleException("Invalid bundle identifier: " + name, e);
		}
	}

	private static VersionRange readSupportedRepositoryVersionRange(Manifest manifest) {
		String val = manifest.getMainAttributes().getValue(MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS);
		if (val == null) {
			return null;
		}
		try {
			return VersionRange.valueOf(val);
		} catch (IllegalArgumentException e) {
			throw new InvalidNestBundleException("Failed to parse supported repository version range: " + val + " ("
					+ MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS + ")", e);
		}
	}

	private static VersionRange readSupportedBuildSystemVersionRange(Manifest manifest) {
		String val = manifest.getMainAttributes().getValue(MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS);
		if (val == null) {
			return null;
		}
		try {
			return VersionRange.valueOf(val);
		} catch (IllegalArgumentException e) {
			throw new InvalidNestBundleException("Failed to parse supported build system version range: " + val + " ("
					+ MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS + ")", e);
		}
	}

	private static VersionRange readSupportedJreVersionRange(Manifest manifest) {
		String val = manifest.getMainAttributes().getValue(MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS);
		if (val == null) {
			return null;
		}
		try {
			VersionRange range = VersionRange.valueOf(val);
			String nonsingleversion = SingleComponentVersionRangeVisitor.INSTANCE.getNonSingleComponentVersion(range);
			if (nonsingleversion != null) {
				throw new InvalidNestBundleException(
						"Supported JRE version range should contain versions with single components only: " + val
								+ " with " + nonsingleversion);
			}
			return range;
		} catch (IllegalArgumentException e) {
			throw new InvalidNestBundleException("Failed to parse supported JRE version range: " + val + " ("
					+ MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS + ")", e);
		}
	}

	private static Set<String> readSupportedArchitectures(Manifest manifest) {
		String val = manifest.getMainAttributes().getValue(MANIFEST_NAME_CLASSPATH_SUPPORTED_ARCHITECTURES);
		if (val == null) {
			return null;
		}
		String[] splits = PATTERN_COMMA_WHITESPACE_SPLIT.split(val);
		NavigableSet<String> result = new TreeSet<>();
		for (String arch : splits) {
			if (ObjectUtils.isNullOrEmpty(arch)) {
				continue;
			}
			result.add(arch);
		}
		if (result.isEmpty()) {
			throw new InvalidNestBundleException(
					"Empty architectures in: " + MANIFEST_NAME_CLASSPATH_SUPPORTED_ARCHITECTURES);
		}
		return ImmutableUtils.unmodifiableNavigableSet(result);
	}

}
