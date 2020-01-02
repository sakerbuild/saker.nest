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

import java.util.Objects;
import java.util.function.Supplier;

import saker.build.util.java.JavaTools;
import saker.nest.dependency.DependencyUtils;

/**
 * Value container about the dependency resolution constraints that should can be to filter bundle dependencies.
 * <p>
 * This interface specifies the constraint values that can be used to specify the environment for bundle loading and
 * dependency resolution. The enclosed values will be used to filter out or otherwise determine if a bundle dependency
 * should be used or omitted.
 * <p>
 * Instances of this interface can be compared by equality and be serialized.
 * <p>
 * This interface is not to be implemented by clients.
 * <p>
 * Use the {@link #builder()} methods to create a new instance.
 * 
 * @see DependencyUtils#isDependencyConstraintExcludes(DependencyConstraintConfiguration, BundleDependency)
 * @see DependencyUtils#isDependencyConstraintClassPathExcludes(DependencyConstraintConfiguration, BundleInformation)
 */
public interface DependencyConstraintConfiguration {
	/**
	 * Gets the Java version that should be used as a constraint.
	 * <p>
	 * See {@link BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_JRE_VERSIONS} and
	 * {@link BundleInformation#DEPENDENCY_META_JRE_VERSION} for more information.
	 * 
	 * @return The JRE major version or <code>null</code> if there's no constraint on it.
	 * @see JavaTools#getCurrentJavaMajorVersion()
	 */
	public Integer getJreMajorVersion();

	/**
	 * Gets the Nest repository version that should be used as a constraint.
	 * <p>
	 * See {@link BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_REPOSITORY_VERSIONS} and
	 * {@link BundleInformation#DEPENDENCY_META_REPOSITORY_VERSION} for more information.
	 * <p>
	 * 
	 * @return The repository version constraint or <code>null</code> if there's no constraint on it.
	 * @see saker.nest.meta.Versions#VERSION_STRING_FULL
	 */
	public String getRepositoryVersion();

	/**
	 * Gets the saker.build system version that should be used as a constraint.
	 * <p>
	 * See {@link BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_BUILD_SYSTEM_VERSIONS} and
	 * {@link BundleInformation#DEPENDENCY_META_BUILD_SYSTEM_VERSION} for more information.
	 * 
	 * @return The build system version constraint or <code>null</code> if there's no constraint on it.
	 * @see saker.build.meta.Versions#VERSION_STRING_FULL
	 */
	public String getBuildSystemVersion();

	/**
	 * Gets the native architecture that should be used as a constraint.
	 * <p>
	 * See {@link BundleInformation#MANIFEST_NAME_CLASSPATH_SUPPORTED_ARCHITECTURES} and
	 * {@link BundleInformation#DEPENDENCY_META_NATIVE_ARCHITECTURE} for more information.
	 * <p>
	 * See {@link System#getProperties() os.arch} system property.
	 * 
	 * @return The native architecture constraint or <code>null</code> if there's no constraint on it.
	 */
	public String getNativeArchitecture();

	@Override
	public int hashCode();

	@Override
	public boolean equals(Object obj);

	/**
	 * Creates a new builder without any constraints specified.
	 * 
	 * @return The new builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a new builder with the constraints initialized using the argument.
	 * 
	 * @param copy
	 *            The constraints to copy from.
	 * @return The new builder.
	 * @throws NullPointerException
	 *             If the argument is <code>null</code>.
	 */
	public static Builder builder(DependencyConstraintConfiguration copy) throws NullPointerException {
		Objects.requireNonNull(copy, "constraint copy");
		return new Builder(copy);
	}

	/**
	 * Builder class for new {@link DependencyConstraintConfiguration} instances.
	 */
	public static final class Builder {
		private Integer jreMajorVersion;
		private String repositoryVersion;
		private String buildSystemVersion;
		private String nativeArchitecture;

		Builder() {
		}

		Builder(DependencyConstraintConfiguration copy) {
			this.jreMajorVersion = copy.getJreMajorVersion();
			this.repositoryVersion = copy.getRepositoryVersion();
			this.buildSystemVersion = copy.getBuildSystemVersion();
			this.nativeArchitecture = copy.getNativeArchitecture();
		}

		/**
		 * Sets the Java major version constraint.
		 * 
		 * @param jreMajorVersion
		 *            The constraint or <code>null</code> to unset.
		 * @return <code>this</code>
		 * @see DependencyConstraintConfiguration#getJreMajorVersion()
		 */
		public Builder setJreMajorVersion(Integer jreMajorVersion) {
			this.jreMajorVersion = jreMajorVersion;
			return this;
		}

		/**
		 * Sets the Nest repository version constraint.
		 * 
		 * @param repositoryVersion
		 *            The version number or <code>null</code> to unset.
		 * @return <code>this</code>
		 * @throws IllegalArgumentException
		 *             If the argument is non-<code>null</code> and is an invalid version number.
		 * @see DependencyConstraintConfiguration#getRepositoryVersion()
		 */
		public Builder setRepositoryVersion(String repositoryVersion) throws IllegalArgumentException {
			if (repositoryVersion != null && !BundleIdentifier.isValidVersionNumber(repositoryVersion)) {
				throw new IllegalArgumentException("Invalid version number format: " + repositoryVersion);
			}
			this.repositoryVersion = repositoryVersion;
			return this;
		}

		/**
		 * Sets the saker.build system version constraint.
		 * 
		 * @param buildSystemVersion
		 *            The version number or <code>null</code> to unset.
		 * @return <code>this</code>
		 * @throws IllegalArgumentException
		 *             If the argument is non-<code>null</code> and is an invalid version number.
		 */
		public Builder setBuildSystemVersion(String buildSystemVersion) throws IllegalArgumentException {
			if (buildSystemVersion != null && !BundleIdentifier.isValidVersionNumber(buildSystemVersion)) {
				throw new IllegalArgumentException("Invalid version number format: " + buildSystemVersion);
			}
			this.buildSystemVersion = buildSystemVersion;
			return this;
		}

		/**
		 * Sets the native architecture constraint.
		 * 
		 * @param nativeArchitecture
		 *            The architecture string or <code>null</code> to unset.
		 * @return <code>this</code>
		 */
		public Builder setNativeArchitecture(String nativeArchitecture) {
			this.nativeArchitecture = nativeArchitecture;
			return this;
		}

		/**
		 * Builds a new constraint configuration with the constraints set in this builder.
		 * <p>
		 * The builder can be reused after this call.
		 * 
		 * @return The created constraint configuration.
		 */
		public DependencyConstraintConfiguration build() {
			return new SimpleDependencyConstraintConfiguration(jreMajorVersion, repositoryVersion, buildSystemVersion,
					nativeArchitecture);
		}

		/**
		 * Builds a new constraint configuration with the constraints in <code>this</code> builder, and initializes
		 * unset (<code>null</code>) constraints from the argument defaults.
		 * <p>
		 * The builder can be reused after this call.
		 * 
		 * @param defaults
		 *            The defaults to initialize unset constraints.
		 * @return The created constraint configuration.
		 * @throws NullPointerException
		 *             If the defaults is <code>null</code>. (May only be thrown if there are unset constraints.)
		 */
		public DependencyConstraintConfiguration buildWithDefaults(DependencyConstraintConfiguration defaults)
				throws NullPointerException {
			Integer jremajor = jreMajorVersion;
			String repoversion = repositoryVersion;
			String buildsystemversion = buildSystemVersion;
			String architecture = nativeArchitecture;
			if (jremajor == null) {
				jremajor = defaults.getJreMajorVersion();
			}
			if (repoversion == null) {
				repoversion = defaults.getRepositoryVersion();
			}
			if (buildsystemversion == null) {
				buildsystemversion = defaults.getBuildSystemVersion();
			}
			if (architecture == null) {
				architecture = defaults.getNativeArchitecture();
			}
			return new SimpleDependencyConstraintConfiguration(jremajor, repoversion, buildsystemversion, architecture);
		}

		/**
		 * Builds a new constraint configuration with the constraints in <code>this</code> builder, and initializes
		 * unset (<code>null</code>) constraints from the argument defaults {@link Supplier}.
		 * <p>
		 * The builder can be reused after this call.
		 * 
		 * @param defaults
		 *            The supplier of defaults to initialize unset constraints.
		 * @return The created constraint configuration.
		 * @throws NullPointerException
		 *             If the defaults supplier or the retrieved defaults is <code>null</code>. (May only be thrown if
		 *             there are unset constraints.)
		 */
		public DependencyConstraintConfiguration buildWithDefaults(
				Supplier<? extends DependencyConstraintConfiguration> defaults) throws NullPointerException {
			if (jreMajorVersion == null || repositoryVersion == null || buildSystemVersion == null
					|| nativeArchitecture == null) {
				Objects.requireNonNull(defaults, "default constraints supplier");
				return buildWithDefaults(defaults.get());
			}
			return new SimpleDependencyConstraintConfiguration(jreMajorVersion, repositoryVersion, buildSystemVersion,
					nativeArchitecture);
		}

	}
}
